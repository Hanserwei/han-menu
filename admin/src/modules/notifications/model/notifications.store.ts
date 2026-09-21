import { shallowRef, computed } from 'vue'
import { defineStore } from 'pinia'
import { notificationApi } from '../api/notifications'
import { NotificationRuntime, emptyState } from './notification-runtime'
import { streamUrl, type FeedPage } from './protocol'
import { sessionBridge } from '@/shared/api/session-bridge'
import { queryClient } from '@/shared/api/query-client'

/** Pinia只发布非敏感连接状态。运行器与票据不进入开发工具或浏览器持久化存储。 */
export const useNotificationsStore = defineStore('notifications', () => {
  const state = shallowRef(emptyState())
  const runtime = new NotificationRuntime({
    api: notificationApi,
    socket: (ticket) =>
      new WebSocket(streamUrl(window.location.origin), [
        ticket.protocol,
        `ticket.${ticket.ticket}`,
      ]),
    changed: (value) => {
      state.value = value
    },
    invalidateOrders: () => {
      void queryClient.invalidateQueries({ queryKey: ['orders-list'] })
      void queryClient.invalidateQueries({ queryKey: ['workspace'] })
    },
    online: () => navigator.onLine,
    visible: () => document.visibilityState === 'visible',
    now: () => Date.now(),
    random: () => Math.random(),
  })
  let listening = false
  let sessionAbort: AbortSignal | undefined
  const hasUnread = computed(
    () => !!state.value.receipt && state.value.cursor > state.value.receipt.sequence,
  )
  const wake = () => runtime.wake()
  function stop() {
    runtime.stop()
    window.removeEventListener('online', wake)
    window.removeEventListener('offline', wake)
    document.removeEventListener('visibilitychange', wake)
    sessionAbort?.removeEventListener('abort', stop)
    sessionAbort = undefined
    listening = false
  }
  function start() {
    const current = sessionBridge.snapshot()
    if (!current.token || current.signal.aborted) {
      stop()
      return
    }
    if (listening && sessionAbort === current.signal) return
    stop()
    sessionAbort = current.signal
    sessionAbort.addEventListener('abort', stop, { once: true })
    window.addEventListener('online', wake)
    window.addEventListener('offline', wake)
    document.addEventListener('visibilitychange', wake)
    listening = true
    runtime.start()
  }
  return {
    state,
    hasUnread,
    start,
    stop,
    refresh: () => runtime.refresh(),
    reconnect: () => runtime.reconnect(),
    acknowledge: (page: FeedPage) => runtime.acknowledge(page),
  }
})
