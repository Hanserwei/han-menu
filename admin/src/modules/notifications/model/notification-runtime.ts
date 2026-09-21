import { ApiProblem } from '@/shared/api/problem'
import {
  PROTOCOL,
  parseNotice,
  type Notice,
  type Receipt,
  type FeedPage,
  type Ticket,
} from './protocol'

export interface RuntimeState {
  connection: 'stopped' | 'connecting' | 'connected' | 'reconnecting' | 'offline'
  receipt: Receipt | null
  cursor: number
  syncing: boolean
  caughtUp: boolean
  error: unknown
  lastSyncedAt: number | null
  activity: { revision: number; notice: Notice } | null
  pendingAck: boolean
}
export const emptyState = (): RuntimeState => ({
  connection: 'stopped',
  receipt: null,
  cursor: 0,
  syncing: false,
  caughtUp: false,
  error: null,
  lastSyncedAt: null,
  activity: null,
  pendingAck: false,
})
export interface SocketPort {
  onopen: ((event: Event) => void) | null
  onmessage: ((event: MessageEvent) => void) | null
  onerror: ((event: Event) => void) | null
  onclose: ((event: CloseEvent) => void) | null
  send(data: string): void
  close(): void
}
export interface RuntimeDependencies {
  api: {
    receipt(signal: AbortSignal): Promise<Receipt>
    page(after: number, signal: AbortSignal): Promise<FeedPage>
    ticket(signal: AbortSignal): Promise<Ticket>
    acknowledge(sequence: number, version: number, signal: AbortSignal): Promise<Receipt>
  }
  socket(ticket: Ticket): SocketPort
  changed(state: RuntimeState): void
  invalidateOrders(): void
  online(): boolean
  visible(): boolean
  now(): number
  random(): number
}
/** 每个应用实例一个运行器；WebSocket帧只提示补查，HTTP读取游标和员工已读游标相互独立。 */
export class NotificationRuntime {
  private state = emptyState()
  private generation = 0
  private controller = new AbortController()
  private active = false
  private socket: SocketPort | null = null
  private connecting = false
  private ready = false
  private failures = 0
  private baseline = false
  private syncPromise: Promise<void> | null = null
  private resync = false
  private pollTimer?: ReturnType<typeof setTimeout>
  private reconnectTimer?: ReturnType<typeof setTimeout>
  private readyTimer?: ReturnType<typeof setTimeout>
  private pingTimer?: ReturnType<typeof setTimeout>
  private pongTimer?: ReturnType<typeof setTimeout>
  constructor(private readonly deps: RuntimeDependencies) {}
  private emit() {
    this.deps.changed({
      ...this.state,
      receipt: this.state.receipt ? { ...this.state.receipt } : null,
    })
  }
  private current(epoch: number) {
    return this.active && epoch === this.generation && !this.controller.signal.aborted
  }

  start() {
    if (this.active) return
    this.active = true
    this.generation++
    this.controller = new AbortController()
    this.state = emptyState()
    this.baseline = false
    this.failures = 0
    void this.connect()
    void this.refresh()
    this.poll()
  }
  /** 先标记代数失效，再取消请求／定时器／Socket，任何迟到事件都不能重开旧会话。 */
  stop() {
    this.active = false
    this.generation++
    this.controller.abort()
    this.disposeSocket()
    clearTimeout(this.pollTimer)
    clearTimeout(this.reconnectTimer)
    this.reconnectTimer = undefined
    this.syncPromise = null
    this.connecting = false
    this.resync = false
    this.state = emptyState()
    this.emit()
  }
  private disposeSocket() {
    clearTimeout(this.readyTimer)
    clearTimeout(this.pingTimer)
    clearTimeout(this.pongTimer)
    this.ready = false
    const socket = this.socket
    this.socket = null
    if (socket) {
      socket.onopen = null
      socket.onmessage = null
      socket.onclose = null
      socket.onerror = null
      socket.close()
    }
  }
  private poll() {
    clearTimeout(this.pollTimer)
    if (this.active)
      this.pollTimer = setTimeout(
        () => {
          void this.refresh()
          this.poll()
        },
        this.deps.visible() ? 15000 : 60000,
      )
  }
  /** 浏览器网络／可见性变化只唤醒既有实例，不在页面切换时创建第二条连接。 */
  wake() {
    if (!this.active) return
    if (!this.deps.online()) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = undefined
      this.disposeSocket()
      this.state.connection = 'offline'
      this.emit()
      return
    }
    void this.refresh()
    if (!this.socket && !this.connecting && !this.reconnectTimer) void this.connect()
    this.poll()
  }
  reconnect() {
    if (!this.active || this.connecting) return
    clearTimeout(this.reconnectTimer)
    this.reconnectTimer = undefined
    this.disposeSocket()
    void this.connect()
    void this.refresh()
  }
  private retryDelay(error?: unknown) {
    const backoff = Math.min(30000, 1000 * 2 ** Math.min(this.failures++, 5))
    return Math.max(
      backoff + Math.round(this.deps.random() * 500),
      error instanceof ApiProblem ? error.retryAfter * 1000 : 0,
    )
  }
  private lost(error?: unknown) {
    if (!this.active) return
    this.disposeSocket()
    this.state.connection = this.deps.online() ? 'reconnecting' : 'offline'
    this.emit()
    void this.refresh() // 1008也须经HTTP复验，401由统一会话层清理，不能仅凭关闭码猜测身份。
    clearTimeout(this.reconnectTimer)
    if (this.deps.online())
      this.reconnectTimer = setTimeout(() => {
        this.reconnectTimer = undefined
        void this.connect()
      }, this.retryDelay(error))
  }
  private async connect() {
    if (!this.active || this.connecting || this.socket) return
    if (!this.deps.online()) {
      this.state.connection = 'offline'
      this.emit()
      return
    }
    const epoch = this.generation
    this.connecting = true
    this.state.connection = this.failures ? 'reconnecting' : 'connecting'
    this.emit()
    try {
      const ticket = await this.deps.api.ticket(this.controller.signal)
      if (!this.current(epoch)) return
      if (!this.deps.online()) {
        this.state.connection = 'offline'
        this.emit()
        return
      }
      const socket = this.deps.socket(ticket)
      this.socket = socket
      const live = () => this.current(epoch) && this.socket === socket
      socket.onopen = () => {
        /* 收到READY才视为可用，TCP建立不代表业务握手完成。 */
      }
      socket.onmessage = (event) => {
        if (!live()) return
        try {
          if (typeof event.data !== 'string' || event.data.length > 4096)
            throw new Error('invalid frame')
          const frame: unknown = JSON.parse(event.data)
          if (!frame || typeof frame !== 'object') throw new Error('invalid frame')
          const value = frame as Record<string, unknown>
          if (value.type === 'READY' && value.protocol === PROTOCOL) {
            if (this.ready) return
            clearTimeout(this.readyTimer)
            this.ready = true
            this.failures = 0
            this.state.connection = 'connected'
            this.emit()
            this.heartbeat(epoch, socket)
            void this.refresh()
          } else if (value.type === 'PONG' && this.ready) {
            clearTimeout(this.pongTimer)
            this.heartbeat(epoch, socket)
          } else if (this.ready) {
            const notice = parseNotice(value)
            if (notice.sequence > this.state.cursor) void this.refresh()
          } else throw new Error('missing ready')
        } catch {
          this.lost()
        } // 不记录原始帧或含票据的网络对象。
      }
      socket.onerror = () => {
        if (live()) this.lost()
      }
      socket.onclose = () => {
        if (live()) this.lost()
      }
      this.readyTimer = setTimeout(() => {
        if (live() && !this.ready) this.lost()
      }, 10000)
    } catch (error) {
      if (this.current(epoch)) this.lost(error)
    } finally {
      if (this.current(epoch)) this.connecting = false
    }
  }
  private heartbeat(epoch: number, socket: SocketPort) {
    clearTimeout(this.pingTimer)
    this.pingTimer = setTimeout(() => {
      if (!this.current(epoch) || this.socket !== socket) return
      try {
        socket.send('ping')
        this.pongTimer = setTimeout(() => {
          if (this.socket === socket) this.lost()
        }, 8000)
      } catch {
        this.lost()
      }
    }, 15000)
  }
  /** 迟到GET不能覆盖已成功PUT或另一设备推进的已读状态。 */
  private mergeReceipt(value: Receipt) {
    const previous = this.state.receipt
    if (previous && value.version < previous.version) return
    if (
      previous &&
      (value.sequence < previous.sequence ||
        (value.version === previous.version && value.sequence !== previous.sequence))
    )
      throw new ApiProblem(502, '阅读进度不一致，请重新同步')
    this.state.receipt = value
    this.state.cursor = Math.max(this.state.cursor, value.sequence)
  }
  refresh(): Promise<void> {
    if (!this.active || !this.deps.online()) return Promise.resolve()
    if (this.syncPromise) {
      this.resync = true
      return this.syncPromise
    }
    const epoch = this.generation
    const promise = this.synchronize(epoch).finally(() => {
      if (this.current(epoch)) this.syncPromise = null
    })
    this.syncPromise = promise
    return promise
  }
  private async synchronize(epoch: number) {
    this.state.syncing = true
    this.emit()
    try {
      do {
        this.resync = false
        const receipt = await this.deps.api.receipt(this.controller.signal)
        if (!this.current(epoch)) return
        this.mergeReceipt(receipt)
        this.emit()
        let more = true,
          latest: Notice | undefined
        // 逐页验证，内存只保留游标和最新提示；历史页面由UI按页读取，不堆积全部未读记录。
        while (more && this.current(epoch)) {
          const page = await this.deps.api.page(this.state.cursor, this.controller.signal)
          if (!this.current(epoch)) return
          this.state.cursor = Math.max(this.state.cursor, page.nextCursor)
          more = page.hasMore
          latest = page.items.at(-1) || latest
          this.emit()
        }
        if (!this.current(epoch)) return
        if (latest && this.baseline) {
          this.state.activity = {
            revision: (this.state.activity?.revision || 0) + 1,
            notice: latest,
          }
          this.deps.invalidateOrders()
        }
        this.baseline = true
        this.state.caughtUp = true
        this.state.error = null
        this.state.lastSyncedAt = this.deps.now()
      } while (this.resync && this.current(epoch))
    } catch (error) {
      if (this.current(epoch)) {
        this.state.error = error
        this.state.caughtUp = false
      }
    } finally {
      if (this.current(epoch)) {
        this.state.syncing = false
        this.emit()
      }
    }
  }
  /** 已读确认只接受当前已读下界起的实际页面；多设备冲突后重读，不自动再PUT。 */
  async acknowledge(page: FeedPage): Promise<void> {
    const receipt = this.state.receipt
    if (
      !this.active ||
      this.state.pendingAck ||
      !receipt ||
      !page.items.length ||
      page.after !== receipt.sequence ||
      page.nextCursor > this.state.cursor
    )
      throw new ApiProblem(409, '阅读进度或页面已变化，请重新读取本页')
    const epoch = this.generation
    this.state.pendingAck = true
    this.emit()
    try {
      const value = await this.deps.api.acknowledge(
        page.nextCursor,
        receipt.version,
        this.controller.signal,
      )
      if (!this.current(epoch)) return
      if (value.sequence < page.nextCursor)
        throw new ApiProblem(502, '阅读确认结果不完整，请重新同步')
      this.mergeReceipt(value)
      this.emit()
    } catch (error) {
      if (this.current(epoch)) {
        await this.refresh()
        throw error
      }
    } finally {
      if (this.current(epoch)) {
        this.state.pendingAck = false
        this.emit()
      }
    }
  }
}
