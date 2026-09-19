/** HTTP 层的会话端口：公共代码不反向依赖 Pinia 或身份业务模块。 */
export class SessionBridge {
  private token: string | null = null
  private generation = 0
  private controller = new AbortController()
  private expiredListener: () => void = () => undefined

  /** 切换凭证同时取消旧请求；递增代数让不支持取消的迟到响应也失效。 */
  replace(token: string | null): void {
    this.controller.abort()
    this.controller = new AbortController()
    this.token = token
    this.generation++
  }

  snapshot() {
    return { token: this.token, generation: this.generation, signal: this.controller.signal }
  }
  isCurrent(generation: number) {
    return generation === this.generation
  }
  onExpired(listener: () => void) {
    this.expiredListener = listener
  }
  expire(generation: number) {
    if (this.isCurrent(generation)) this.expiredListener()
  }
}
export const sessionBridge = new SessionBridge()
