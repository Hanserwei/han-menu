import { test, expect, type Page, type APIRequestContext } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { readFile } from 'node:fs/promises'
const origin = 'https://127.0.0.1:15174'
const username = 'pc1admin',
  password = 'Pc1-local-admin-2026!'
const entry = (await readFile('dist/index.html', 'utf8')).match(/<script[^>]*src="([^"]+)"/)![1]!
interface Session {
  accessToken: string
  expiresAt: string
}
async function session(request: APIRequestContext): Promise<Session> {
  const response = await request.post('/api/v1/sessions', { data: { username, password } })
  expect(response.status()).toBe(201)
  return response.json()
}
async function install(page: Page, request: APIRequestContext) {
  const value = await session(request)
  await page.addInitScript(
    (value) => sessionStorage.setItem('han-menu.staff-session.v1', JSON.stringify(value)),
    value,
  )
  return value
}
function seed(script: string, options: Record<string, unknown> = {}) {
  return JSON.parse(
    execFileSync(process.execPath, [`scripts/${script}`, JSON.stringify(options)], {
      encoding: 'utf8',
    }),
  )
}
async function login(page: Page) {
  await page.getByLabel('用户名', { exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
}

test('真实Nginx的TLS、缓存、深链接、静态404和API错误边界', async ({ request }) => {
  const html = await request.get('/orders/00000000-0000-4000-8000-000000000001')
  expect(html.status()).toBe(200)
  expect(html.headers()['cache-control']).toBe('no-store')
  expect(await html.text()).toContain('lang="zh-CN"')
  const asset = await request.get(entry)
  expect(asset.status()).toBe(200)
  expect(asset.headers()['cache-control']).toContain('immutable')
  expect(asset.headers()['content-type']).toContain('javascript')
  expect(asset.headers()['x-content-type-options']).toBe('nosniff')
  for (const path of [
    '/assets/missing.js',
    '/.env',
    '/src/main.ts',
    '/node_modules/vue/package.json',
    '/actuator/health',
    '/v3/api-docs',
  ])
    expect((await request.get(path)).status()).toBe(404)
  const anonymous = await request.get('/api/v1/me')
  expect(anonymous.status()).toBe(401)
  expect(anonymous.headers()['content-type']).toContain('application/problem+json')
  expect((await anonymous.json()).status).toBe(401)
  const unavailable = await request.get('https://127.0.0.1:15175/api/v1/me')
  expect(unavailable.status()).toBe(503)
  expect(unavailable.headers()['cache-control']).toBe('no-store')
  expect((await unavailable.json()).code).toBe('UPSTREAM_UNAVAILABLE')
})

test('生产登录深链接、会话恢复、键盘跳转与真实WSS通知', async ({ page, request }) => {
  await page.goto('/orders?status=PAID')
  await expect(page).toHaveURL(/\/login\?redirect=/)
  await login(page)
  await expect(page).toHaveURL(/\/orders\?status=PAID$/)
  await expect(page.getByRole('heading', { name: '订单中心', exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByText('实时通知已连接', { exact: true })).toBeVisible()
  await page.keyboard.press('Tab')
  await expect(page.getByRole('link', { name: '跳到主要内容', exact: true })).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.locator('#main-content')).toBeFocused()
  const frames: string[] = []
  page.on('websocket', (socket) => {
    if (!socket.url().includes('/notifications/stream')) return
    expect(socket.url()).toBe(origin.replace('https:', 'wss:') + '/api/v1/notifications/stream')
    socket.on('framereceived', (frame) => frames.push(String(frame.payload)))
  })
  await page.reload()
  await expect(page.getByText('实时通知已连接', { exact: true })).toBeVisible()
  const notice = seed('seed-notification-test.mjs')[0]
  await expect
    .poll(() => frames.some((frame) => frame.includes(notice.id)), { timeout: 15000 })
    .toBe(true)
  await page.getByRole('button', { name: '通知中心，有待阅读通知', exact: true }).click()
  await expect(page.getByRole('button', { name: '将本页标为已读', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '将本页标为已读', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认本页已读', exact: true }).click()
  await expect(page.getByText('当前没有待阅读通知', { exact: true })).toBeVisible()
  const stored = await page.evaluate(() =>
    JSON.parse(sessionStorage.getItem('han-menu.staff-session.v1')!),
  )
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page).toHaveURL(/\/login/)
  expect(
    (
      await request.get('/api/v1/me', {
        headers: { Authorization: `Bearer ${stored.accessToken}` },
      })
    ).status(),
  ).toBe(401)
})

test('生产管理资料写入和订单履约直至服务端完成', async ({ page, request }) => {
  const current = await install(page, request)
  const name = `交付分类_${Date.now()}`
  await page.goto('/catalog/categories')
  await page.getByRole('button', { name: '新增分类', exact: true }).click()
  await page.getByLabel('分类名称', { exact: true }).fill(name)
  await page.getByRole('button', { name: '保存分类', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.getByRole('row').filter({ hasText: name })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('row').filter({ hasText: name })).toBeVisible()
  const order = seed('seed-order-test.mjs')[0]
  await page.goto(`/orders/${order.id}`)
  const panel = page.getByRole('region', { name: '订单详情' })
  for (const label of ['接单', '开始配送', '确认完成']) {
    await panel.getByRole('button', { name: label, exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: label, exact: true }).click()
  }
  await expect(panel).toContainText('已完成')
  const response = await request.get(`/api/v1/management/orders/${order.id}`, {
    headers: { Authorization: `Bearer ${current.accessToken}` },
  })
  expect((await response.json()).status).toBe('COMPLETED')
  await page.goto('/reports')
  await expect(page.locator('.report-chart svg')).toBeVisible()
  const downloading = page.waitForEvent('download')
  await page.getByRole('button', { name: '下载 XLSX 报表', exact: true }).click()
  const download = await downloading
  expect((await readFile((await download.path())!)).subarray(0, 2).toString()).toBe('PK')
})

test('生产页面分包初始加载和站内跳转失败都有显式恢复入口', async ({ page, request }) => {
  await install(page, request)
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.route('**/assets/TransactionsPage-*.js', (route) => route.abort())
  await page.goto('/finance/payments')
  await expect(page.getByText('页面未能加载', { exact: true })).toBeVisible()
  await page.unroute('**/assets/TransactionsPage-*.js')
  await page.getByRole('button', { name: '重新加载此页', exact: true }).click()
  await expect(page.getByRole('heading', { name: '支付流水', exact: true })).toBeVisible()
  await page.route('**/assets/ReportsPage-*.js', (route) => route.abort())
  await page.getByRole('menuitem', { name: /经营分析/ }).click()
  await expect(page.getByText('页面未能加载', { exact: true })).toBeVisible()
  await page.unroute('**/assets/ReportsPage-*.js')
  await page.getByRole('button', { name: '重新加载此页', exact: true }).click()
  await expect(page.getByRole('heading', { name: '经营分析', exact: true })).toBeVisible()
  expect(errors).toEqual([])
})

test('主入口资源失败时原生HTML仍提供重新加载', async ({ page }) => {
  await page.route('**' + entry, (route) => route.abort())
  await page.goto('/login')
  await expect(page.getByRole('link', { name: '重新加载', exact: true })).toBeVisible()
  await page.unroute('**' + entry)
  await page.getByRole('link', { name: '重新加载', exact: true }).click()
  await expect(page.getByRole('heading', { name: '登录商家管理', exact: true })).toBeVisible()
})

test('生产断网恢复与真实401清空会话', async ({ page, context, request }) => {
  const current = await install(page, request)
  await page.goto('/finance/payments')
  await expect(page.getByRole('heading', { name: '支付流水', exact: true })).toBeVisible()
  await context.setOffline(true)
  await page.getByRole('button', { name: '刷新列表', exact: true }).click()
  await expect(page.getByText('网络已断开', { exact: true }).first()).toBeVisible()
  await context.setOffline(false)
  await page.getByRole('button', { name: '刷新列表', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveCount(0)
  expect(
    (
      await request.delete('/api/v1/sessions/current', {
        headers: { Authorization: `Bearer ${current.accessToken}` },
      })
    ).status(),
  ).toBe(204)
  // 实时连接的会话复验也会触发401清理，允许它先于下一次手动刷新完成退出。
  await expect(page).toHaveURL(/\/login/, { timeout: 15000 })
  expect(await page.evaluate(() => sessionStorage.getItem('han-menu.staff-session.v1'))).toBeNull()
})

test('登录429尊重Retry-After，恢复后可登录', async ({ page }) => {
  await page.route('**/api/v1/sessions', (route) =>
    route.fulfill({
      status: 429,
      headers: { 'Retry-After': '2' },
      contentType: 'application/problem+json',
      body: JSON.stringify({ detail: '登录过于频繁', code: 'LOGIN_LIMITED' }),
    }),
  )
  await page.goto('/login')
  await login(page)
  await expect(page.getByRole('alert')).toContainText('登录过于频繁')
  await expect(page.getByRole('button', { name: /秒后重试/ })).toBeDisabled()
  await page.unroute('**/api/v1/sessions')
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeEnabled()
  await login(page)
  await expect(page).toHaveURL(/\/workspace$/)
})

test('生产全部管理页面的三档桌面适配、404与开发页面隔离', async ({ page, request }) => {
  test.setTimeout(90000)
  await install(page, request)
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  const routes = [
    '/workspace',
    '/orders',
    '/notifications',
    '/catalog/categories',
    '/catalog/dishes',
    '/catalog/dishes/new',
    '/catalog/meals',
    '/customers',
    '/finance/payments',
    '/finance/refunds',
    '/finance/reconciliation',
    '/reports',
    '/settings/employees',
    '/settings/shop',
    '/settings/audit',
    '/settings/maintenance',
    '/account',
  ]
  for (const width of [1366, 1440, 1920]) {
    await page.setViewportSize({ width, height: 900 })
    for (const path of routes) {
      await page.goto(path)
      await expect(page.locator('#main-content h1')).toBeVisible()
      await expect(page.locator('.ant-spin-spinning')).toHaveCount(0)
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
        `${path} @ ${width}`,
      ).toBe(true)
    }
    await page.screenshot({
      path: `test-results/production/pc6-desktop-${width}.png`,
      animations: 'disabled',
    })
  }
  for (const path of ['/_dev/components', '/not-a-page']) {
    await page.goto(path)
    await expect(
      page.locator('#main-content').getByText('页面不存在', { exact: true }),
    ).toBeVisible()
    await page.getByRole('button', { name: '返回工作台', exact: true }).click()
    await expect(page).toHaveURL(/\/workspace$/)
  }
  expect(errors).toEqual([])
})

test('图表资源失败保留真实报表与日账，恢复后重新加载', async ({ page, request }) => {
  await install(page, request)
  await page.route('**/assets/TrendChart-*.js', (route) => route.abort())
  await page.goto('/reports')
  await expect(page.getByText('趋势图暂不可用', { exact: true })).toBeVisible()
  await expect(page.locator('.report-metrics')).toBeVisible()
  await page.getByText('查看经营日账', { exact: true }).click()
  await expect(page.locator('tr[data-row-key]').first()).toBeVisible()
  await expect(page.getByRole('button', { name: '下载 XLSX 报表', exact: true })).toBeEnabled()
  await page.unroute('**/assets/TrendChart-*.js')
  await page.getByRole('button', { name: '重新加载图表', exact: true }).click()
  await expect(page.locator('.report-chart svg')).toBeVisible()
})
