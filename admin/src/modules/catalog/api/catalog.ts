import { api } from '@/shared/api/client'
import { resource, complete } from '@/shared/api/result'
import type { components } from '@/shared/api/schema'
export type Product = components['schemas']['ProductView']
export type Category = components['schemas']['CategoryView']
export type Details = components['schemas']['ProductDetails']
export type Kind = 'DISH' | 'SET_MEAL'

/** 目录查询严格使用后端可用的种类与分类筛选，不在当前页冒充全量搜索。 */
export const catalogApi = {
  categories: async (signal?: AbortSignal) =>
    resource(await api.GET('/api/v1/catalog/categories', { signal })),
  category: async (id: string) =>
    resource(await api.GET('/api/v1/catalog/categories/{id}', { params: { path: { id } } })),
  createCategory: async (body: components['schemas']['CategoryCreate']) =>
    resource(await api.POST('/api/v1/catalog/categories', { body })),
  updateCategory: async (id: string, body: components['schemas']['CategoryUpdate']) =>
    resource(await api.PUT('/api/v1/catalog/categories/{id}', { params: { path: { id } }, body })),
  deleteCategory: async (id: string, version: number) =>
    complete(
      await api.DELETE('/api/v1/catalog/categories/{id}', {
        params: { path: { id }, query: { version } },
      }),
    ),
  products: async (
    kind: Kind,
    categoryId: string | undefined,
    page: number,
    size: number,
    signal?: AbortSignal,
  ) =>
    resource(
      await api.GET('/api/v1/catalog/products', {
        params: { query: { kind, categoryId, page, size } },
        signal,
      }),
    ),
  product: async (id: string, signal?: AbortSignal) =>
    resource(await api.GET('/api/v1/catalog/products/{id}', { params: { path: { id } }, signal })),
  create: async (kind: Kind, details: Details) =>
    resource(await api.POST('/api/v1/catalog/products', { body: { kind, details } })),
  update: async (id: string, details: Details, version: number) =>
    resource(
      await api.PUT('/api/v1/catalog/products/{id}', {
        params: { path: { id } },
        body: { details, version },
      }),
    ),
  sale: async (id: string, status: 'ON_SALE' | 'OFF_SALE', version: number) =>
    resource(
      await api.PATCH('/api/v1/catalog/products/{id}/status', {
        params: { path: { id } },
        body: { status, version },
      }),
    ),
  remove: async (id: string, version: number) =>
    complete(
      await api.DELETE('/api/v1/catalog/products/{id}', {
        params: { path: { id }, query: { version } },
      }),
    ),
  image: async (id: string, signal?: AbortSignal) =>
    resource(await api.GET('/api/v1/catalog/images/{id}', { params: { path: { id } }, signal })),
  /** 自定义 multipart 序列化保持统一认证、超时与会话隔离；不自行设置 boundary。 */
  upload: async (file: File) =>
    resource(
      await api.POST('/api/v1/catalog/images', {
        body: { file: '' },
        bodySerializer: () => {
          const data = new FormData()
          data.append('file', file)
          return data
        },
      }),
    ),
}
