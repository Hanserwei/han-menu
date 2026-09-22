import { test, expect, type Page } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { readFile } from 'node:fs/promises'
const backend = 'http://127.0.0.1:18081'
let accessToken = '',
  expiresAt = '',
  staffToken = ''
const headers = () => ({ Authorization: `Bearer ${accessToken}` })
function orders(options: Record<string, unknown> = {}) {
  return JSON.parse(
    execFileSync(process.execPath, ['scripts/seed-order-test.mjs', JSON.stringify(options)], {
      encoding: 'utf8',
    }),
  ) as { id: string; paymentId: string; customerId: string }[]
}
function notice() {
  return JSON.parse(
    execFileSync(
      process.execPath,
      ['scripts/seed-notification-test.mjs', JSON.stringify({ exhausted: true })],
      { encoding: 'utf8' },
    ),
  )[0] as { id: string; sequence: number }
}
async function install(page: Page, token = accessToken) {
  await page.addInitScript(
    ({ accessToken, expiresAt }) =>
      sessionStorage.setItem(
        'han-menu.staff-session.v1',
        JSON.stringify({ accessToken, expiresAt }),
      ),
    { accessToken: token, expiresAt },
  )
}
async function confirm(page: Page) {
  await page.getByRole('dialog').last().getByRole('button', { name: '确认', exact: true }).click()
}
test.beforeAll(async ({ request }) => {
  let response = await request.post(backend + '/api/v1/sessions', {
    data: { username: 'pc1admin', password: 'Pc1-local-admin-2026!' },
  })
  expect(response.status()).toBe(201)
  const session = await response.json()
  accessToken = session.accessToken
  expiresAt = session.expiresAt
  const username = `pc5_${Date.now()}`
  response = await request.post(backend + '/api/v1/employees', {
    headers: headers(),
    data: { username, displayName: '报表权限验收', phone: '', password: 'Pc5-staff-test-2026!' },
  })
  expect(response.status()).toBe(201)
  response = await request.post(backend + '/api/v1/sessions', {
    data: { username, password: 'Pc5-staff-test-2026!' },
  })
  expect(response.status()).toBe(201)
  staffToken = (await response.json()).accessToken
})
test('支付与退款真实筛选、分页、引用跳转和待确认状态', async ({ page }) => {
  const customerId = crypto.randomUUID()
  orders({ count: 21, customerId })
  const refunding = orders({ status: 'REFUNDING', customerId })[0]!
  await install(page)
  await page.goto(`/finance/payments?customerId=${customerId}`)
  await expect(page.locator('tbody tr[data-row-key]')).toHaveCount(20)
  await expect(page.getByText('共 22 条', { exact: true })).toBeVisible()
  await page.getByTitle('2', { exact: true }).click()
  await expect(page.locator('tbody tr[data-row-key]')).toHaveCount(2)
  await page.goto(`/finance/payments/${refunding.paymentId}`)
  const detail = page.getByRole('dialog')
  await expect(detail).toContainText('支付成功')
  await detail.getByRole('link', { name: '查询关联退款', exact: true }).click()
  await expect(page).toHaveURL(new RegExp(`paymentId=${refunding.paymentId}`))
  await expect(page.locator('tbody tr[data-row-key]')).toHaveCount(1)
  await expect(page.locator('tbody')).toContainText('退款处理中')
  await page.getByRole('button', { name: '查看详情', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('退款确认时间')
  await expect(
    page.getByRole('dialog').getByRole('link', { name: refunding.paymentId, exact: true }),
  ).toBeVisible()
  await page.getByRole('dialog').getByRole('link', { name: refunding.id, exact: true }).click()
  await expect(page.getByRole('region', { name: '订单详情' })).toContainText('退款处理中')
})
test('财务日期边界、无匹配和非法UUID恢复', async ({ page }) => {
  const customerId = crypto.randomUUID()
  orders({ customerId, createdAt: '2026-09-20T15:59:59Z' })
  orders({ customerId, createdAt: '2026-09-20T16:00:00Z' })
  orders({ customerId, createdAt: '2026-09-21T16:00:00Z' })
  await install(page)
  await page.goto(
    `/finance/payments?customerId=${customerId}&fromDate=2026-09-21&toDate=2026-09-21`,
  )
  await expect(page.locator('tbody tr[data-row-key]')).toHaveCount(1)
  await page.getByLabel('订单编号筛选').fill('invalid')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('UUID')
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveCount(0)
  await page.getByLabel('订单编号筛选').fill(crypto.randomUUID())
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByText('没有符合条件的流水', { exact: true })).toBeVisible()
})
test('真实投影重建、报表口径、销量、XLSX与对账', async ({ page, request }) => {
  orders({ status: 'COMPLETED', count: 2 })
  await install(page)
  const before = await (
    await request.get(backend + '/api/v1/reports/projection', { headers: headers() })
  ).json()
  await page.goto('/settings/maintenance')
  await page.getByRole('button', { name: '重建统计投影', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('不修改订单、资金')
  await page.getByRole('dialog').getByRole('button', { name: '返回', exact: true }).click()
  expect(
    (
      await (
        await request.get(backend + '/api/v1/reports/projection', { headers: headers() })
      ).json()
    ).version,
  ).toBe(before.version)
  await page.getByRole('button', { name: '重建统计投影', exact: true }).click()
  await confirm(page)
  await expect(page.getByText('统计投影已重建', { exact: true })).toBeVisible()
  const after = await (
    await request.get(backend + '/api/v1/reports/projection', { headers: headers() })
  ).json()
  expect(after.generation).toBeGreaterThan(before.generation)
  await page.goto('/reports')
  await expect(page.locator('.report-chart svg')).toBeVisible()
  const requestUrl = await page.evaluate(() =>
    performance
      .getEntriesByType('resource')
      .map((entry) => entry.name)
      .find((name) => name.includes('/reports/operations?')),
  )
  const report = await (await request.get(requestUrl!, { headers: headers() })).json()
  const turnover = page
    .locator('.metric')
    .filter({ has: page.getByText('营业额', { exact: true }) })
  await expect(turnover).toContainText(`¥ ${Number(report.summary.turnover).toFixed(2)}`)
  await page.getByText('查看经营日账', { exact: true }).click()
  await expect(page.locator('tr[data-row-key]')).not.toHaveCount(0)
  await expect(page.getByRole('heading', { name: '商品销量', exact: true })).toBeVisible()
  const downloading = page.waitForEvent('download')
  await page.getByRole('button', { name: '下载 XLSX 报表', exact: true }).click()
  const download = await downloading
  expect(download.suggestedFilename()).toMatch(/\.xlsx$/)
  const path = await download.path()
  const bytes = await readFile(path!)
  expect(bytes.subarray(0, 2).toString()).toBe('PK')
  await page.goto('/finance/reconciliation')
  await expect(page.getByText('全店当前（不按日期截断）', { exact: true })).toBeVisible()
  await expect(page.getByText('净收款', { exact: true })).toBeVisible()
})
test('报表不可用和导出Problem不会伪装零值或下载成功', async ({ page }) => {
  await install(page)
  await page.route('**/reports/operations?*', (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      body: JSON.stringify({ detail: '统计尚未就绪', code: 'REPORTING_NOT_READY' }),
    }),
  )
  await page.route('**/reports/export?*', (route) =>
    route.fulfill({
      status: 403,
      contentType: 'application/problem+json',
      body: JSON.stringify({ detail: '没有导出权限', traceId: 'pc5-export' }),
    }),
  )
  let downloaded = false
  page.on('download', () => {
    downloaded = true
  })
  await page.goto('/reports')
  await expect(page.getByRole('alert')).toContainText('统计尚未就绪')
  await expect(page.locator('.metrics-band')).toHaveCount(0)
  await page.getByRole('button', { name: '下载 XLSX 报表', exact: true }).click()
  await expect(page.getByText('没有导出权限', { exact: true })).toBeVisible()
  expect(downloaded).toBe(false)
  await page.unroute('**/reports/operations?*')
  await page.getByRole('button', { name: '刷新报表', exact: true }).click()
  await expect(page.locator('.report-chart svg')).toBeVisible()
})
test('安全审计组合筛选来自后端，不显示自由日志正文', async ({ page, request }) => {
  await install(page)
  const me = await (await request.get(backend + '/api/v1/me', { headers: headers() })).json()
  await page.goto('/settings/audit')
  await page.getByLabel('操作者UUID').fill(me.id)
  await page.getByLabel('安全事件筛选').click()
  await page.getByTitle('登录', { exact: true }).click()
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('tbody tr[data-row-key]').first()).toContainText('登录')
  await expect(
    page.locator('tbody tr[data-row-key]').first().locator('.resource-id').first(),
  ).toBeVisible()
  await page.getByLabel('目标UUID').fill(crypto.randomUUID())
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByText('没有符合条件的安全事件', { exact: true })).toBeVisible()
})
test('耗尽通知的轨迹、确认重投与阅读进度独立', async ({ page, request }) => {
  const item = notice()
  const before = await (
    await request.get(backend + '/api/v1/notifications/receipt', { headers: headers() })
  ).json()
  await install(page)
  await page.goto(`/settings/maintenance?noticeId=${item.id}&sequence=${item.sequence}`)
  const drawer = page.getByRole('dialog').first()
  await expect(drawer).toContainText('重试耗尽')
  await expect(drawer.locator('tbody tr[data-row-key]')).toHaveCount(5)
  await drawer.getByRole('button', { name: '重新投递', exact: true }).click()
  await expect(page.getByRole('dialog').last()).toContainText('不改变任何员工的已读进度')
  await confirm(page)
  await expect(page.getByText('已重新登记投递，请刷新查看实际结果', { exact: true })).toBeVisible()
  await expect(drawer.getByRole('button', { name: '重新投递', exact: true })).toBeDisabled()
  expect(
    (
      await (
        await request.get(backend + '/api/v1/notifications/receipt', { headers: headers() })
      ).json()
    ).sequence,
  ).toBe(before.sequence)
})
test('投影409与通知409要求显式重读，不自动重复命令', async ({ page, request }) => {
  await install(page)
  await page.goto('/settings/maintenance')
  await expect(page.getByRole('button', { name: '重建统计投影', exact: true })).toBeEnabled()
  const state = await (
    await request.get(backend + '/api/v1/reports/projection', { headers: headers() })
  ).json()
  expect(
    (
      await request.post(backend + '/api/v1/reports/projection/rebuild', {
        headers: headers(),
        data: { version: state.version },
      })
    ).ok(),
  ).toBe(true)
  await page.getByRole('button', { name: '重建统计投影', exact: true }).click()
  await confirm(page)
  await expect(
    page.getByText('数据可能已变化，请刷新投影状态后重新确认', { exact: true }),
  ).toBeVisible()
  await expect(page.getByRole('button', { name: '重建统计投影', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '刷新投影状态', exact: true }).click()
  await expect(page.getByRole('button', { name: '重建统计投影', exact: true })).toBeEnabled()
  const item = notice()
  await page.goto(`/settings/maintenance?noticeId=${item.id}&sequence=${item.sequence}`)
  await expect(page.getByRole('button', { name: '重新投递', exact: true })).toBeEnabled()
  expect(
    (
      await request.post(`${backend}/api/v1/notifications/${item.id}/redelivery`, {
        headers: headers(),
        data: { version: 0 },
      })
    ).ok(),
  ).toBe(true)
  await page.getByRole('button', { name: '重新投递', exact: true }).click()
  await confirm(page)
  await expect(
    page.getByText('请重新读取通知后确认，不能直接重复提交', { exact: true }),
  ).toBeVisible()
  await page.getByRole('button', { name: '重新读取通知', exact: true }).click()
  await expect(
    page.getByText('请重新读取通知后确认，不能直接重复提交', { exact: true }),
  ).toHaveCount(0)
})
test('STAFF不能访问所有PC-5页面和后端能力', async ({ page, request }) => {
  await install(page, staffToken)
  for (const path of [
    '/finance/payments',
    '/finance/refunds',
    '/finance/reconciliation',
    '/reports',
    '/settings/audit',
    '/settings/maintenance',
  ]) {
    await page.goto(path)
    await expect(page).toHaveURL(/\/forbidden$/)
  }
  for (const path of [
    '/management/payments',
    '/management/refunds',
    '/management/audit-events',
    '/reports/projection',
    '/reports/export?from=2026-09-01&to=2026-09-22',
  ]) {
    expect(
      (
        await request.get(backend + '/api/v1' + path, {
          headers: { Authorization: `Bearer ${staffToken}` },
        })
      ).status(),
    ).toBe(403)
  }
})
test('经营报表与流水桌面适配，无控制台错误', async ({ page }) => {
  await install(page)
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  for (const width of [1440, 1366, 1920, 1024]) {
    await page.setViewportSize({ width, height: 1024 })
    await page.goto('/reports')
    await expect(page.locator('.report-chart svg')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    if (width === 1440 || width === 1024)
      await page.screenshot({
        path: `test-results/pc5-reports-${width}.png`,
        animations: 'disabled',
      })
  }
  for (const [path, name] of [
    ['/finance/payments', 'payments'],
    ['/finance/reconciliation', 'reconciliation'],
    ['/settings/audit', 'audit'],
    ['/settings/maintenance', 'maintenance'],
  ]) {
    await page.goto(path!)
    await expect(page.locator('.ant-table-tbody')).toBeVisible()
    await expect(page.locator('.ant-spin-spinning')).toHaveCount(0)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: `test-results/pc5-${name}.png`, animations: 'disabled' })
  }
  expect(errors).toEqual([])
})

test('重建已提交但响应丢失时先查状态，禁止重复命令', async ({ page }) => {
  await install(page)
  let calls = 0
  await page.route('**/reports/projection/rebuild', async (route) => {
    calls++
    const response = await route.fetch()
    expect(response.ok()).toBe(true)
    await route.abort('failed')
  })
  await page.goto('/settings/maintenance')
  await page.getByRole('button', { name: '重建统计投影', exact: true }).click()
  await confirm(page)
  await expect(page.getByText('重建结果尚未确认', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '重建统计投影', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '刷新投影状态', exact: true }).click()
  await expect(page.getByText('重建结果尚未确认', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '重建统计投影', exact: true })).toBeEnabled()
  expect(calls).toBe(1)
})

test('独立报表快照不一致显示同步提示，图表切换保留后端日账', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await install(page)
  await page.route('**/reports/sales?*', async (route) => {
    const response = await route.fetch(),
      value = await response.json()
    value.projection.generation += 1
    await route.fulfill({ response, json: value })
  })
  await page.goto('/reports')
  await expect(page.getByText('数据正在同步', { exact: true })).toBeVisible()
  await page.getByLabel('趋势指标').click()
  await page.getByTitle('新增顾客', { exact: true }).click()
  await expect(page.getByRole('img', { name: '新增顾客趋势，详细数值见经营日账' })).toBeVisible()
  await page.unroute('**/reports/sales?*')
  await page.getByRole('button', { name: '刷新报表', exact: true }).click()
  await expect(page.getByText('数据正在同步', { exact: true })).toHaveCount(0)
  expect(errors).toEqual([])
})
