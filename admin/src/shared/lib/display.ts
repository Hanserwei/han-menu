/** 联系方式只在已授权详情中主动展开，列表避免暴露完整个人资料。 */
export function maskPhone(phone?: string) {
  return phone ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : '—'
}
export function money(value?: number) {
  return value == null ? '—' : `¥ ${value.toFixed(2)}`
}
