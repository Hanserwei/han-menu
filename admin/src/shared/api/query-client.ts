import { QueryClient } from '@tanstack/vue-query'

/** 查询不自动掩盖权限或故障；退出时统一清空，业务写操作从不自动重试。 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false, staleTime: 30_000, refetchOnWindowFocus: true },
    mutations: { retry: false },
  },
})
