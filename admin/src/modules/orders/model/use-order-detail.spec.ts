import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { ApiProblem } from '@/shared/api/problem'
const mocks = vi.hoisted(() => ({
  detail: vi.fn(),
  act: vi.fn(),
  confirm: vi.fn(),
  success: vi.fn(),
  invalidate: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('../api/orders', () => ({ ordersApi: { detail: mocks.detail, act: mocks.act } }))
vi.mock('antdv-next', () => ({
  App: {
    useApp: () => ({ modal: { confirm: mocks.confirm }, message: { success: mocks.success } }),
  },
}))
vi.mock('@/shared/api/query-client', () => ({
  queryClient: { invalidateQueries: mocks.invalidate },
}))
import { useOrderDetail } from './use-order-detail'
const first = '11111111-1111-4111-8111-111111111111',
  second = '22222222-2222-4222-8222-222222222222'
let clean: () => void
function setup() {
  const id = ref(first)
  let model!: ReturnType<typeof useOrderDetail>
  const wrapper = mount(
    defineComponent({
      setup() {
        model = useOrderDetail(id)
        return () => null
      },
    }),
  )
  clean = () => wrapper.unmount()
  return { id, model }
}
beforeEach(() => {
  vi.clearAllMocks()
  mocks.detail.mockResolvedValue({ id: first, status: 'PAID', version: 4 })
  mocks.act.mockResolvedValue({ id: first, status: 'ACCEPTED', version: 5 })
  mocks.confirm.mockImplementation((options) => options.onOk())
})
afterEach(() => clean?.())
describe('详情异步与并发隔离', () => {
  it('快速切换订单时拒绝旧详情覆盖新订单', async () => {
    let resolve!: (value: unknown) => void
    mocks.detail
      .mockImplementationOnce(() => new Promise((done) => (resolve = done)))
      .mockResolvedValueOnce({ id: second, status: 'UNPAID', version: 1 })
    const { id, model } = setup()
    id.value = second
    await flushPromises()
    resolve({ id: first, status: 'PAID', version: 4 })
    await flushPromises()
    expect(model.order.value?.id).toBe(second)
  })
  it('确认期间不刷新版本，重复点击只发一次命令', async () => {
    let approve!: () => void
    mocks.confirm.mockImplementation((options) => (approve = options.onOk))
    const { model } = setup()
    await flushPromises()
    const command = model.act('acceptance')
    expect(model.busy.value).toBe(true)
    await model.act('acceptance')
    await model.load()
    expect(mocks.detail).toHaveBeenCalledTimes(1)
    approve()
    await command
    expect(mocks.act).toHaveBeenCalledExactlyOnceWith(first, 'acceptance', 4)
    expect(model.order.value?.status).toBe('ACCEPTED')
  })
  it('409禁止重复提交与轮询清除保护，只有显式读取恢复', async () => {
    mocks.act.mockRejectedValue(new ApiProblem(409, '版本变化'))
    const { model } = setup()
    await flushPromises()
    await model.act('acceptance')
    expect(model.blocked.value).toBe(true)
    await model.act('acceptance')
    await model.load()
    expect(mocks.act).toHaveBeenCalledTimes(1)
    expect(mocks.detail).toHaveBeenCalledTimes(1)
    mocks.detail.mockResolvedValue({ id: first, status: 'ACCEPTED', version: 5 })
    await model.load(true)
    expect(model.blocked.value).toBe(false)
    expect(model.order.value?.status).toBe('ACCEPTED')
  })
  it('确认框打开后更换订单不会把原命令应用到新订单', async () => {
    let approve!: () => void
    mocks.confirm.mockImplementation((options) => (approve = options.onOk))
    const { id, model } = setup()
    await flushPromises()
    const command = model.act('acceptance')
    id.value = second
    await flushPromises()
    approve()
    await command
    expect(mocks.act).not.toHaveBeenCalled()
  })
  it('退款受理保持服务端处理中，不显示退款成功', async () => {
    mocks.act.mockResolvedValue({
      id: first,
      status: 'REFUNDING',
      version: 5,
      lifecycle: { refundStatus: 'PENDING' },
    })
    const { model } = setup()
    await flushPromises()
    await model.act('rejection')
    expect(model.order.value?.status).toBe('REFUNDING')
    expect(mocks.success).toHaveBeenCalledWith('已提交，等待服务端确认')
  })
  it('资源缺失或读取失败不会继续提供旧状态操作', async () => {
    const { model } = setup()
    await flushPromises()
    mocks.detail.mockRejectedValue(new ApiProblem(404, '不存在'))
    await model.load(true)
    expect(model.order.value).toBeUndefined()
    await model.act('acceptance')
    expect(mocks.act).not.toHaveBeenCalled()
  })
})
