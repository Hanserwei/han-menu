import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import timezone from 'dayjs/plugin/timezone'
import 'dayjs/locale/zh-cn'

dayjs.extend(utc)
dayjs.extend(timezone)
dayjs.locale('zh-cn')
/** 经营日期统一展示为上海时区；空值不伪装成当前时间。 */
export function dateTime(value?: string) {
  return value ? dayjs(value).tz('Asia/Shanghai').format('YYYY-MM-DD HH:mm') : '尚未更新'
}
export function businessDate(value?: string) {
  return value ? dayjs(value).format('YYYY年M月D日 dddd') : '正在读取经营日期'
}
