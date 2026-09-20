import type { OrderAction, OrderDetail, OrderStatus } from '../api/orders'
export const statuses: Record<OrderStatus, { label: string; color: string }> = {
  UNPAID: { label: '待付款', color: 'default' },
  PAID: { label: '待接单', color: 'warning' },
  ACCEPTED: { label: '待配送', color: 'processing' },
  DELIVERING: { label: '配送中', color: 'processing' },
  COMPLETED: { label: '已完成', color: 'success' },
  CANCELLING: { label: '取消处理中', color: 'warning' },
  REFUNDING: { label: '退款处理中', color: 'warning' },
  CANCELLED: { label: '已取消', color: 'default' },
}
export const actions: Record<
  OrderAction,
  { label: string; description: string; danger?: boolean }
> = {
  acceptance: { label: '接单', description: '请确认商品、规格与收货信息。接单后开始准备餐点。' },
  rejection: {
    label: '拒单并退款',
    description: '拒绝此订单并申请全额退款，退款需等待服务端确认。',
  },
  cancellation: {
    label: '取消并退款',
    description: '取消已付款订单并申请全额退款，不能把受理当作退款完成。',
  },
  delivery: {
    label: '开始配送',
    description: '请确认餐点已备妥并开始配送。进入配送后不能通过商家取消。',
  },
  completion: { label: '确认完成', description: '请确认餐点已经送达，订单完成后不可再次履约。' },
}
actions.rejection.danger = true
actions.cancellation.danger = true
/** 可见操作遵循领域状态机；未知状态失败关闭，服务器仍做最终版本与状态检查。 */
export function availableActions(status?: string): OrderAction[] {
  switch (status) {
    case 'PAID':
      return ['acceptance', 'rejection', 'cancellation']
    case 'ACCEPTED':
      return ['delivery', 'cancellation']
    case 'DELIVERING':
      return ['completion']
    default:
      return []
  }
}
export function statusView(status?: string) {
  return status && Object.hasOwn(statuses, status)
    ? statuses[status as OrderStatus]
    : { label: '未知状态', color: 'default' }
}
export function shouldPoll(order: OrderDetail) {
  return (
    ['UNPAID', 'PAID', 'ACCEPTED', 'DELIVERING', 'CANCELLING', 'REFUNDING'].includes(
      order.status || '',
    ) || order.lifecycle?.refundStatus === 'PENDING'
  )
}
export const refundLabels: Record<string, string> = {
  NONE: '无需退款',
  PENDING: '退款处理中',
  SUCCEEDED: '已退款',
}
export const cancelLabels: Record<string, string> = {
  CUSTOMER: '顾客取消',
  TIMEOUT: '支付超时',
  MERCHANT_REJECTED: '商家拒单',
  MERCHANT_CANCELLED: '商家取消',
  PAYMENT_CLOSED: '支付已关闭',
}
/** 仅展示服务器确实存在的时间，不补造退款时间或预计送达。 */
export function timeline(order: OrderDetail) {
  return [
    { label: '已下单', time: order.createdAt },
    { label: '支付成功', time: order.lifecycle?.paidAt },
    { label: '商家接单', time: order.lifecycle?.acceptedAt },
    { label: '开始配送', time: order.lifecycle?.deliveredAt },
    { label: '订单完成', time: order.lifecycle?.completedAt },
    { label: '订单取消', time: order.cancelledAt },
  ].filter((value) => !!value.time)
}
