import { test, expect, type Page } from '@playwright/test'
import { execFileSync } from 'node:child_process'
const backend = 'http://127.0.0.1:18081'
interface SeedOrder {
  id: string
  customerId: string
  version: number
  createdAt: string
  status: string
}
/** 测试事实仅写入运行器新建的_test临时schema；不是应用接口，也不连接支付宝。 */
function seed(options: Record<string, unknown> = {}): SeedOrder[] {
  return JSON.parse(
    execFileSync(process.execPath, ['scripts/seed-order-test.mjs', JSON.stringify(options)], {
      encoding: 'utf8',
    }),
  )
}
let adminToken = '',
  staffToken = '',
  expiresAt = ''
const headers = () => ({ Authorization: `Bearer ${adminToken}` })
async function confirm(page: Page, label: string) {
  await page.getByRole('dialog').getByRole('button', { name: label, exact: true }).click()
}
async function detail(page: Page, id: string) {
  await page.goto(`/orders/${id}`)
  await expect(page.getByRole('region', { name: '订单详情' })).toContainText('清汤牛肉面')
}
test.beforeAll(async ({ request }) => {
  let result = await request.post(backend + '/api/v1/sessions', {
    data: { username: 'pc1admin', password: 'Pc1-local-admin-2026!' },
  })
  expect(result.ok()).toBe(true)
  let login = await result.json()
  adminToken = login.accessToken
  expiresAt = login.expiresAt
  const username = `pc3staff_${Date.now()}`
  result = await request.post(backend + '/api/v1/employees', {
    headers: headers(),
    data: { username, displayName: '接单员工', phone: '', password: 'Pc3-test-staff-2026!' },
  })
  expect(result.status()).toBe(201)
  result = await request.post(backend + '/api/v1/sessions', {
    data: { username, password: 'Pc3-test-staff-2026!' },
  })
  expect(result.ok()).toBe(true)
  login = await result.json()
  staffToken = login.accessToken
})
test.beforeEach(async ({ page }) => {
  await page.addInitScript(
    ({ accessToken, expiresAt }) =>
      sessionStorage.setItem(
        'han-menu.staff-session.v1',
        JSON.stringify({ accessToken, expiresAt }),
      ),
    { accessToken: staffToken, expiresAt },
  )
})

test('普通员工从工作台接单，配送完成后终态只读且刷新保留', async ({ page, request }) => {
  const order = seed()[0]!
  const state = await request.get(backend + '/api/v1/reports/projection', { headers: headers() })
  const { version } = await state.json()
  expect(
    (
      await request.post(backend + '/api/v1/reports/projection/rebuild', {
        headers: headers(),
        data: { version },
      })
    ).ok(),
  ).toBe(true)
  await page.goto('/workspace')
  await expect(page.getByRole('heading', { name: '待接订单' })).toBeVisible()
  await expect(page.locator('.ant-spin-spinning')).toHaveCount(0)
  await page.screenshot({ path: 'test-results/pc3-workspace.png', fullPage: false })
  await page.getByRole('button', { name: '去处理订单' }).click()
  await expect(page).toHaveURL(/status=PAID/)
  await page.getByLabel('订单编号筛选').fill(order.id)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await page.getByRole('button', { name: '查看详情', exact: true }).click()
  const panel = page.getByRole('region', { name: '订单详情' })
  await expect(panel).toContainText('下单时快照')
  await expect(panel).not.toContainText('+13800138009')
  await panel.getByRole('button', { name: '显示完整号码' }).click()
  await expect(panel).toContainText('+13800138009')
  await panel.getByText('查看套餐组成').click()
  await expect(panel).toContainText('辣度：不辣')
  await panel.getByRole('button', { name: '接单', exact: true }).click()
  await confirm(page, '接单')
  await expect(panel.getByRole('button', { name: '开始配送', exact: true })).toBeVisible()
  await expect(page.getByRole('region', { name: '订单列表' })).toContainText('没有符合条件的订单')
  await panel.getByRole('button', { name: '开始配送', exact: true }).click()
  await confirm(page, '开始配送')
  await expect(panel.getByRole('button', { name: '确认完成', exact: true })).toBeVisible()
  await panel.getByRole('button', { name: '确认完成', exact: true }).click()
  await confirm(page, '确认完成')
  await expect(panel).toContainText('当前状态无可用履约操作')
  await page.reload()
  await expect(panel).toContainText('已完成')
  const result = await request.get(`${backend}/api/v1/management/orders/${order.id}`, {
    headers: headers(),
  })
  expect((await result.json()).status).toBe('COMPLETED')
})

test('拒单与配送前取消都进入退款处理中，不显示伪成功', async ({ page, request }) => {
  for (const state of ['PAID', 'ACCEPTED']) {
    const order = seed({ status: state })[0]!
    await detail(page, order.id)
    const panel = page.getByRole('region', { name: '订单详情' })
    const label = state === 'PAID' ? '拒单并退款' : '取消并退款'
    await panel.getByRole('button', { name: label, exact: true }).click()
    await confirm(page, label)
    await expect(panel).toContainText('退款处理中')
    await expect(panel).toContainText('当前状态无可用履约操作')
    await expect(panel).not.toContainText('已退款')
    const result = await request.get(`${backend}/api/v1/management/orders/${order.id}`, {
      headers: headers(),
    })
    const value = await result.json()
    expect(value.status).toBe('REFUNDING')
    expect(value.lifecycle.refundStatus).toBe('PENDING')
  }
})

test('八种状态正确显示，未付款无商家取消，迟到退款独立展示', async ({ page }) => {
  for (const status of [
    'UNPAID',
    'PAID',
    'ACCEPTED',
    'DELIVERING',
    'COMPLETED',
    'CANCELLING',
    'REFUNDING',
    'CANCELLED',
  ]) {
    const order = seed({ status, reminders: status === 'PAID' ? 2 : 0 })[0]!
    await detail(page, order.id)
    const panel = page.getByRole('region', { name: '订单详情' })
    if (['UNPAID', 'COMPLETED', 'CANCELLING', 'REFUNDING', 'CANCELLED'].includes(status)) {
      await expect(panel).toContainText('当前状态无可用履约操作')
      await expect(panel.getByRole('button', { name: '取消并退款' })).toHaveCount(0)
    }
    if (status === 'PAID') await expect(panel).toContainText('最近催单')
  }
  const late = seed({ status: 'CANCELLED', refundStatus: 'PENDING' })[0]!
  await detail(page, late.id)
  await expect(page.getByRole('region', { name: '订单详情' })).toContainText('退款处理中')
})

test('真实版本竞争阻止陈旧接单，重新读取后才显示新操作', async ({ page, request }) => {
  const order = seed()[0]!
  await detail(page, order.id)
  const panel = page.getByRole('region', { name: '订单详情' })
  await panel.getByRole('button', { name: '接单', exact: true }).click()
  const response = await request.post(
    `${backend}/api/v1/management/orders/${order.id}/acceptance`,
    { headers: headers(), data: { version: 0 } },
  )
  expect(response.ok()).toBe(true)
  await confirm(page, '接单')
  await expect(panel.getByRole('button', { name: '接单', exact: true })).toBeDisabled()
  await expect(panel).toContainText('重新读取')
  await panel.getByRole('button', { name: '重新读取', exact: true }).click()
  await expect(panel.getByRole('button', { name: '开始配送', exact: true })).toBeEnabled()
  await expect(panel.getByRole('button', { name: '接单', exact: true })).toHaveCount(0)
})

test('组合检索按完整UUID、快照电话、日期和状态在数据库分页', async ({ page }) => {
  const customerId = crypto.randomUUID()
  const orders = seed({ customerId, count: 21, createdAt: '2026-09-19T16:00:00Z' })
  await page.goto(
    `/orders?customerId=${customerId}&status=PAID&fromDate=2026-09-20&toDate=2026-09-20`,
  )
  await expect(page.getByText('共 21 条', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '下一页', exact: true }).click()
  await expect(page.locator('tbody tr[data-row-key]')).toHaveCount(1)
  await page.getByRole('button', { name: '查看详情', exact: true }).click()
  await page.getByRole('button', { name: '关闭订单详情' }).click()
  await expect(page).toHaveURL(/page=1/)
  await page.getByRole('button', { name: '更多筛选' }).click()
  await page.getByLabel('收货手机号筛选').fill('13800138009')
  await page.getByLabel('订单编号筛选').fill(orders[0]!.id)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByText('共 1 条', { exact: true })).toBeVisible()
  expect(page.url()).not.toContain('13800138009')
  await page.getByLabel('收货手机号筛选').fill('13800138008')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByText('共 0 条', { exact: true })).toBeVisible()
  await page.getByLabel('订单编号筛选').fill('short-id')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('完整有效')
})

test('切换详情忽略迟到响应，失效资源不能操作，窄屏抽屉可关闭', async ({ page }) => {
  const [first, second] = seed({ count: 2 })
  let release!: () => void
  const delay = new Promise<void>((done) => (release = done))
  await page.route(`**/api/v1/management/orders/${first!.id}`, async (route) => {
    await delay
    await route.continue()
  })
  await page.goto(`/orders/${first!.id}`)
  await page.goto(`/orders/${second!.id}`)
  release()
  const panel = page.getByRole('region', { name: '订单详情' })
  await expect(panel).toContainText('清汤牛肉面')
  await expect(
    panel.getByText(`${second!.id.slice(0, 8)}…${second!.id.slice(-4)}`, { exact: true }),
  ).toBeVisible()
  await page.unroute(`**/api/v1/management/orders/${first!.id}`)
  await page.setViewportSize({ width: 1024, height: 768 })
  await expect(page.getByRole('dialog')).toBeVisible()
  await page
    .getByRole('dialog')
    .getByRole('button', { name: /关闭|Close/ })
    .first()
    .click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.goto(`/orders/${crypto.randomUUID()}`)
  await expect(page.getByRole('alert')).toContainText('不存在')
  await expect(page.getByRole('button', { name: '接单', exact: true })).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('顾客档案关联订单与工作台待办保留无日期限制', async ({ page, request }) => {
  const phone = `137${Date.now().toString().slice(-8)}`
  const response = await request.post(backend + '/api/v1/customer/accounts', {
    data: { phone, displayName: '关联订单顾客', password: 'Pc3-customer-test-2026!' },
  })
  expect(response.status()).toBe(201)
  const customer = await response.json()
  seed({ customerId: customer.id, createdAt: '2026-09-10T00:00:00Z' })
  await page.addInitScript(
    ({ accessToken, expiresAt }) =>
      sessionStorage.setItem(
        'han-menu.staff-session.v1',
        JSON.stringify({ accessToken, expiresAt }),
      ),
    { accessToken: adminToken, expiresAt },
  )
  await page.goto(`/customers/${customer.id}`)
  await page.getByRole('button', { name: '查看关联订单' }).click()
  await expect(page).toHaveURL(new RegExp(customer.id))
  await expect(page.getByText('共 1 条', { exact: true })).toBeVisible()
  await page.goto('/workspace')
  await page.getByRole('button', { name: /待接单/ }).click()
  await expect(page).toHaveURL(/status=PAID/)
  expect(page.url()).not.toContain('fromDate')
})

test('取消确认不发写请求，提交超时不重复写，重读真实状态', async ({ page, request }) => {
  const order = seed()[0]!
  await detail(page, order.id)
  const panel = page.getByRole('region', { name: '订单详情' })
  await panel.getByRole('button', { name: '接单', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '返回', exact: true }).click()
  let response = await request.get(`${backend}/api/v1/management/orders/${order.id}`, {
    headers: headers(),
  })
  expect((await response.json()).status).toBe('PAID')
  // 模拟响应丢失：请求确实在后端执行，但浏览器看不到结果；不模拟付款成功。
  await page.route(`**/api/v1/management/orders/${order.id}/acceptance`, async (route) => {
    await route.fetch()
    await route.abort('failed')
  })
  await panel.getByRole('button', { name: '接单', exact: true }).click()
  await confirm(page, '接单')
  await expect(panel.getByRole('button', { name: '接单', exact: true })).toBeDisabled()
  await page.unroute(`**/api/v1/management/orders/${order.id}/acceptance`)
  await panel.getByRole('button', { name: '重新读取', exact: true }).click()
  await expect(panel.getByRole('button', { name: '开始配送', exact: true })).toBeEnabled()
  response = await request.get(`${backend}/api/v1/management/orders/${order.id}`, {
    headers: headers(),
  })
  expect((await response.json()).version).toBe(1)
})

test('订单页桌面与窄屏视觉、控制台及个人信息默认掩码', async ({ page }) => {
  const order = seed()[0]!
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await detail(page, order.id)
  await expect(page.getByRole('button', { name: '查看详情', exact: true }).first()).toBeVisible()
  await expect(page.locator('.ant-spin-spinning')).toHaveCount(0)
  const accept = page
    .getByRole('region', { name: '订单详情' })
    .getByRole('button', { name: '接单', exact: true })
  await expect(accept).toBeInViewport()
  await page.screenshot({ path: 'test-results/pc3-orders-desktop.png', fullPage: false })
  await page.setViewportSize({ width: 1024, height: 768 })
  await expect(page.getByRole('dialog')).toBeVisible()
  await expect(
    page.getByRole('dialog').getByRole('button', { name: '接单', exact: true }),
  ).toBeInViewport()
  await expect
    .poll(async () =>
      page.getByRole('dialog').evaluate((element) => {
        const b = element.getBoundingClientRect()
        return b.right <= innerWidth + 1 && b.left >= 0
      }),
    )
    .toBe(true)
  await page.screenshot({ path: 'test-results/pc3-orders-1024.png', fullPage: false })
  expect(errors).toEqual([])
})

test('可见详情轮询读取其他员工的履约结果，不恢复已失效操作', async ({ page, request }) => {
  const order = seed()[0]!
  await page.clock.install()
  await detail(page, order.id)
  expect(
    (
      await request.post(`${backend}/api/v1/management/orders/${order.id}/acceptance`, {
        headers: headers(),
        data: { version: 0 },
      })
    ).ok(),
  ).toBe(true)
  await page.clock.fastForward(16000)
  const panel = page.getByRole('region', { name: '订单详情' })
  await expect(panel.getByRole('button', { name: '开始配送', exact: true })).toBeVisible()
  await expect(panel.getByRole('button', { name: '接单', exact: true })).toHaveCount(0)
})
