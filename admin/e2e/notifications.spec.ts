import { test, expect, type APIRequestContext, type Page } from '@playwright/test'
import { execFileSync } from 'node:child_process'
const backend = 'http://127.0.0.1:18081'
const password = 'Pc4-notice-staff-2026!'
let adminToken = '',
  sequence = 0
interface User {
  id: string
  username: string
  accessToken: string
  expiresAt: string
  baseline: number
}
interface NoticeFixture {
  id: string
  sequence: number
  orderId: string
  type: string
}
function seed(options: Record<string, unknown> = {}): NoticeFixture[] {
  return JSON.parse(
    execFileSync(
      process.execPath,
      ['scripts/seed-notification-test.mjs', JSON.stringify(options)],
      { encoding: 'utf8' },
    ),
  )
}
async function head(request: APIRequestContext, token: string) {
  let after = 0
  for (;;) {
    const result = await request.get(`${backend}/api/v1/notifications?after=${after}&limit=100`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(result.ok()).toBe(true)
    const value = await result.json()
    after = value.nextCursor
    if (!value.hasMore) return after
  }
}
async function user(request: APIRequestContext): Promise<User> {
  const username = `pc4_${Date.now()}_${sequence++}`
  const created = await request.post(backend + '/api/v1/employees', {
    headers: { Authorization: `Bearer ${adminToken}` },
    data: { username, displayName: '通知验收员工', phone: '', password },
  })
  expect(created.status()).toBe(201)
  const account = await created.json(),
    login = await request.post(backend + '/api/v1/sessions', { data: { username, password } })
  expect(login.status()).toBe(201)
  const session = await login.json(),
    baseline = await head(request, session.accessToken)
  if (baseline)
    expect(
      (
        await request.put(backend + '/api/v1/notifications/receipt', {
          headers: { Authorization: `Bearer ${session.accessToken}` },
          data: { sequence: baseline, version: 0 },
        })
      ).ok(),
    ).toBe(true)
  return { ...account, ...session, username, baseline }
}
async function install(page: Page, account: User) {
  await page.addInitScript(
    ({ accessToken, expiresAt }) =>
      sessionStorage.setItem(
        'han-menu.staff-session.v1',
        JSON.stringify({ accessToken, expiresAt }),
      ),
    account,
  )
}
async function receipt(request: APIRequestContext, account: User) {
  const response = await request.get(backend + '/api/v1/notifications/receipt', {
    headers: { Authorization: `Bearer ${account.accessToken}` },
  })
  expect(response.ok()).toBe(true)
  return response.json()
}
async function ready(page: Page) {
  await expect(page.getByText('实时通知已连接', { exact: true }).first()).toBeVisible({
    timeout: 15000,
  })
}
async function confirmRead(page: Page) {
  await page.getByRole('button', { name: '将本页标为已读', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认本页已读', exact: true }).click()
}
test.beforeAll(async ({ request }) => {
  const login = await request.post(backend + '/api/v1/sessions', {
    data: { username: 'pc1admin', password: 'Pc1-local-admin-2026!' },
  })
  expect(login.status()).toBe(201)
  adminToken = (await login.json()).accessToken
})

test('真实WebSocket来单提示、单实例跨路由复用与显式阅读', async ({ page, request }) => {
  const account = await user(request)
  await install(page, account)
  const sockets: string[] = [],
    frames: string[] = []
  page.on('websocket', (socket) => {
    if (new URL(socket.url()).pathname !== '/api/v1/notifications/stream') return
    sockets.push(socket.url())
    socket.on('framereceived', (frame) => frames.push(String(frame.payload)))
  })
  await page.goto('/workspace')
  await ready(page)
  const notices = seed()
  await expect
    .poll(() => frames.some((value) => value.includes(notices[0]!.id)), { timeout: 15000 })
    .toBe(true)
  await expect(
    page.getByRole('button', { name: '通知中心，有待阅读通知', exact: true }),
  ).toBeVisible()
  expect((await receipt(request, account)).sequence).toBe(account.baseline)
  await page.getByRole('button', { name: '通知中心，有待阅读通知', exact: true }).click()
  await expect(page.getByRole('heading', { name: '通知中心', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '将本页标为已读', exact: true })).toBeEnabled()
  await expect(page.locator('tbody tr[data-row-key]')).toHaveCount(1)
  await confirmRead(page)
  await expect(page.getByText('当前没有待阅读通知', { exact: true })).toBeVisible()
  expect((await receipt(request, account)).sequence).toBe(notices[0]!.sequence)
  await page.goto('/workspace')
  await ready(page) // 完整刷新会创建新应用，下面只验证应用内部导航。
  const current = sockets.length
  await page.getByRole('button', { name: '通知中心', exact: true }).click()
  await page.getByRole('link', { name: 'HAN MENU 工作台', exact: true }).click()
  expect(sockets.length).toBe(current)
  expect(
    sockets.every((url) => !url.includes('?') && url.endsWith('/api/v1/notifications/stream')),
  ).toBe(true)
  expect(
    await page.evaluate(() =>
      Object.values(sessionStorage).some((value) => String(value).includes('hmw_')),
    ),
  ).toBe(false)
})

test('超过一页的积压逐页确认，不自动读到后台补查头', async ({ page, request }) => {
  const account = await user(request)
  seed({ count: 55 })
  await install(page, account)
  await page.goto('/notifications')
  await expect(page.locator('tbody tr[data-row-key]')).toHaveCount(50)
  expect((await receipt(request, account)).sequence).toBe(account.baseline)
  await expect(page.getByRole('button', { name: '将本页标为已读', exact: true })).toBeEnabled()
  await confirmRead(page)
  await expect(page.locator('tbody tr[data-row-key]')).toHaveCount(5)
  expect((await receipt(request, account)).sequence).toBe(account.baseline + 50)
  await expect(
    page.getByRole('button', { name: '通知中心，有待阅读通知', exact: true }),
  ).toBeVisible()
  await page.getByRole('button', { name: '全部记录', exact: true }).click()
  await expect(page.locator('tbody tr[data-row-key]')).toHaveCount(50)
  await expect(page.getByRole('button', { name: '将本页标为已读', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '下一页', exact: true }).click()
  await expect(page.getByRole('button', { name: '上一页', exact: true })).toBeEnabled()
  expect((await receipt(request, account)).sequence).toBe(account.baseline + 50)
})

test('断网期间保留阅读进度，恢复后申请新票据并补齐消息', async ({ page, context, request }) => {
  const account = await user(request)
  await install(page, account)
  let tickets = 0
  page.on('request', (req) => {
    if (req.url().endsWith('/notifications/stream-tickets')) tickets++
  })
  await page.goto('/notifications')
  await ready(page)
  const before = tickets
  await context.setOffline(true)
  await expect(page.getByText('网络已断开', { exact: true }).first()).toBeVisible()
  const notice = seed({ type: 'ORDER_REMINDER' })[0]!
  await context.setOffline(false)
  await ready(page)
  await expect.poll(() => tickets).toBeGreaterThan(before)
  await page.getByRole('button', { name: '刷新通知', exact: false }).click()
  await expect(page.locator('tbody tr[data-row-key]')).toHaveCount(1)
  await expect(page.locator('tbody')).toContainText('顾客催单')
  expect((await receipt(request, account)).sequence).toBe(account.baseline)
  await confirmRead(page)
  expect((await receipt(request, account)).sequence).toBe(notice.sequence)
})

test('两设备冲突不会自动确认另一设备尚未展示的消息', async ({ page, browser, request }) => {
  const account = await user(request)
  seed({ count: 55 })
  await install(page, account)
  const login = await request.post(backend + '/api/v1/sessions', {
    data: { username: account.username, password },
  })
  const second = { ...account, ...(await login.json()) }
  const otherContext = await browser.newContext()
  const other = await otherContext.newPage()
  await install(other, second)
  try {
    await page.goto('/notifications')
    await other.goto('http://127.0.0.1:15173/notifications')
    await expect(page.locator('tbody tr[data-row-key]')).toHaveCount(50)
    await expect(other.locator('tbody tr[data-row-key]')).toHaveCount(50)
    await other.getByRole('button', { name: '将本页标为已读', exact: true }).click()
    await confirmRead(page)
    await expect(page.locator('tbody tr[data-row-key]')).toHaveCount(5)
    await other
      .getByRole('dialog')
      .getByRole('button', { name: '确认本页已读', exact: true })
      .click()
    await expect(other.getByRole('button', { name: '将本页标为已读', exact: true })).toBeDisabled()
    expect((await receipt(request, account)).sequence).toBe(account.baseline + 50)
    await other.getByRole('button', { name: '刷新通知', exact: false }).click()
    await expect(other.locator('tbody tr[data-row-key]')).toHaveCount(5)
  } finally {
    await otherContext.close()
  }
})

test('退出关闭连接，重新登录其他员工不继承已读和通知状态', async ({ page, request }) => {
  const first = await user(request),
    second = await user(request)
  seed()
  await install(page, first)
  let closed = false
  page.on('websocket', (socket) => socket.on('close', () => (closed = true)))
  await page.goto('/notifications')
  await ready(page)
  await expect(page.locator('tbody tr[data-row-key]')).toHaveCount(1)
  await confirmRead(page)
  await expect(page.getByText('当前没有待阅读通知', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page).toHaveURL(/\/login/)
  await expect.poll(() => closed).toBe(true)
  await page.getByLabel('用户名', { exact: true }).fill(second.username)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: /登录/ }).click()
  await expect(page).toHaveURL(/\/workspace$/)
  await page.getByRole('button', { name: /通知中心/ }).click()
  await expect(page.locator('tbody tr[data-row-key]')).toHaveCount(1)
  expect((await receipt(request, second)).sequence).toBe(second.baseline)
})

test('服务端停用自动撤销活动连接并退出，不依赖页面刷新', async ({ page, request }) => {
  const account = await user(request)
  seed()
  await install(page, account)
  await page.goto('/notifications')
  await ready(page)
  await expect(page.getByRole('button', { name: '将本页标为已读', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '将本页标为已读', exact: true }).click()
  const response = await request.patch(`${backend}/api/v1/employees/${account.id}/status`, {
    headers: { Authorization: `Bearer ${adminToken}` },
    data: { status: 'DISABLED', version: 0 },
  })
  expect(response.status()).toBe(204)
  await expect(page).toHaveURL(/\/login/, { timeout: 15000 })
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(await page.evaluate(() => sessionStorage.getItem('han-menu.staff-session.v1'))).toBeNull()
})

test('WebSocket不可用时HTTP补查仍能读取与确认，故障不冒充零消息', async ({ page, request }) => {
  const account = await user(request)
  seed()
  await install(page, account)
  await page.route('**/notifications/stream-tickets', (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      body: JSON.stringify({ detail: '连接暂不可用', code: 'UNAVAILABLE' }),
    }),
  )
  await page.goto('/notifications')
  await expect(page.getByText('定期补查中', { exact: true }).first()).toBeVisible()
  await expect(page.locator('tbody tr[data-row-key]')).toHaveCount(1)
  await page.route('**/api/v1/notifications?*', (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      body: JSON.stringify({ detail: '补查暂不可用', code: 'UNAVAILABLE' }),
    }),
  )
  await page.getByRole('button', { name: '刷新通知', exact: false }).click()
  await expect(page.getByRole('alert').first()).toContainText('补查暂不可用')
  await expect(page.getByRole('button', { name: '将本页标为已读', exact: true })).toBeDisabled()
  await page.unroute('**/api/v1/notifications?*')
  await page.getByRole('button', { name: '刷新通知', exact: false }).click()
  await expect(page.getByRole('button', { name: '将本页标为已读', exact: true })).toBeEnabled()
  await confirmRead(page)
  await expect(page.getByText('当前没有待阅读通知', { exact: true })).toBeVisible()
})

test('真实顾客催单产生通知，查看订单不自动改变阅读进度', async ({ page, request }) => {
  const account = await user(request)
  await install(page, account)
  const phone = `136${Date.now().toString().slice(-8)}`
  let response = await request.post(backend + '/api/v1/customer/accounts', {
    data: { phone, displayName: '催单验收顾客', password: 'Pc4-customer-2026!' },
  })
  expect(response.status()).toBe(201)
  const customer = await response.json()
  const orders = JSON.parse(
    execFileSync(
      process.execPath,
      ['scripts/seed-order-test.mjs', JSON.stringify({ customerId: customer.id })],
      { encoding: 'utf8' },
    ),
  )
  response = await request.post(backend + '/api/v1/customer/sessions', {
    data: { phone, password: 'Pc4-customer-2026!' },
  })
  const customerToken = (await response.json()).accessToken
  await page.goto('/notifications')
  await ready(page)
  response = await request.post(`${backend}/api/v1/orders/${orders[0].id}/reminders`, {
    headers: { Authorization: `Bearer ${customerToken}` },
    data: { version: 0 },
  })
  expect(response.status()).toBe(200)
  await expect(
    page.getByRole('button', { name: '通知中心，有待阅读通知', exact: true }),
  ).toBeVisible({ timeout: 15000 })
  await page.getByRole('button', { name: '刷新通知', exact: false }).click()
  await expect(page.locator('tbody')).toContainText('顾客催单')
  await page.locator('tbody').getByRole('button', { name: '查看订单', exact: true }).click()
  await expect(page.getByRole('region', { name: '订单详情' })).toContainText('最近催单')
  expect((await receipt(request, account)).sequence).toBe(account.baseline)
})

test('通知布局、窄屏、错误控制台与阅读确认取消', async ({ page, request }) => {
  const account = await user(request)
  seed({ count: 3 })
  await install(page, account)
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.goto('/notifications')
  await ready(page)
  await expect(page.locator('tbody tr[data-row-key]')).toHaveCount(3)
  await expect(page.getByRole('button', { name: '将本页标为已读', exact: true })).toBeEnabled()
  await expect(page.locator('.ant-spin-blur')).toHaveCount(0)
  await page.screenshot({
    path: 'test-results/pc4-notifications-desktop.png',
    fullPage: false,
    animations: 'disabled',
  })
  await page.getByRole('button', { name: '将本页标为已读', exact: true }).click()
  await page.getByRole('button', { name: '继续查看', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect((await receipt(request, account)).sequence).toBe(account.baseline)
  await page.setViewportSize({ width: 1024, height: 768 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({
    path: 'test-results/pc4-notifications-1024.png',
    fullPage: false,
    animations: 'disabled',
  })
  expect(errors).toEqual([])
})
