import { api } from '@/shared/api/client'
import { resource } from '@/shared/api/result'
import { parsePage, parseReceipt, parseTicket, PAGE_SIZE } from '../model/protocol'

/** 所有HTTP请求复用员工会话客户端；确认与补查使用不同资源，不附带任意消息正文。 */
export const notificationApi = {
  receipt: async (signal?: AbortSignal) =>
    parseReceipt(resource(await api.GET('/api/v1/notifications/receipt', { signal }))),
  page: async (after: number, signal?: AbortSignal) =>
    parsePage(
      resource(
        await api.GET('/api/v1/notifications', {
          params: { query: { after, limit: PAGE_SIZE } },
          signal,
        }),
      ),
      after,
    ),
  acknowledge: async (sequence: number, version: number, signal?: AbortSignal) =>
    parseReceipt(
      resource(
        await api.PUT('/api/v1/notifications/receipt', { body: { sequence, version }, signal }),
      ),
    ),
  ticket: async (signal?: AbortSignal) =>
    parseTicket(resource(await api.POST('/api/v1/notifications/stream-tickets', { signal }))),
}
