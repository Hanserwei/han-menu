import { api } from '@/shared/api/client'
import { resource } from '@/shared/api/result'
import type { components } from '@/shared/api/schema'
export type Shop = components['schemas']['ShopView']
/** 门店资料与营业状态分开提交，状态更改不隐式保存编辑草稿。 */
export const shopApi = {
  get: async () => resource(await api.GET('/api/v1/shop')),
  update: async (body: components['schemas']['Profile']) =>
    resource(await api.PUT('/api/v1/shop', { body })),
  status: async (status: 'OPEN' | 'CLOSED', version: number) =>
    resource(await api.PATCH('/api/v1/shop/status', { body: { status, version } })),
}
