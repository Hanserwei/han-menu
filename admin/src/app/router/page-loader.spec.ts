import { describe, it, expect, vi } from 'vitest'
import { recoverPage } from './page-loader'
import PageLoadFailure from '../layouts/PageLoadFailure.vue'
vi.mock('antdv-next', () => ({
  Result: { template: '<div />' },
  Button: { template: '<button />' },
}))
describe('路由资源加载恢复', () => {
  it('不提前加载业务页面，并交付真实组件', async () => {
    const page = { default: { template: '<div>订单</div>' } },
      loader = vi.fn().mockResolvedValue(page)
    const load = recoverPage(loader)
    expect(loader).not.toHaveBeenCalled()
    expect(await load()).toBe(page.default)
  })
  it('首次深链接加载失败仍有本地恢复页，不自动重试或重载', async () => {
    const loader = vi
      .fn()
      .mockRejectedValue(new TypeError('Failed to fetch dynamically imported module'))
    expect(await recoverPage(loader)()).toBe(PageLoadFailure)
    expect(loader).toHaveBeenCalledOnce()
  })
})
