import type { RuntimeState } from './notification-runtime'

/** 连接成功不等于补查完成，离线和服务故障都不伪装成没有新通知。 */
export function connectionView(state: RuntimeState) {
  if (state.connection === 'stopped') return { label: '通知已断开', color: 'default' }
  if (state.connection === 'offline') return { label: '网络已断开', color: 'warning' }
  if (state.error) return { label: '通知同步暂不可用', color: 'warning' }
  if (state.syncing || !state.caughtUp) return { label: '正在同步通知', color: 'processing' }
  if (state.connection === 'connected') return { label: '实时通知已连接', color: 'success' }
  return { label: '定期补查中', color: 'warning' }
}
export const noticeLabel = (type: string) => (type === 'NEW_ORDER' ? '新订单' : '顾客催单')
