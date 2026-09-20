import { test, expect, type Page, type APIRequestContext } from '@playwright/test'

// 全部凭证仅适用于自动建立并删除的测试 schema，禁止使用开发管理员进行改密验收。
const password = 'Pc1-test-staff-2026!'
const backend = 'http://127.0.0.1:18081'
let adminToken = ''
let sequence = 0
async function staff(request: APIRequestContext) {
  const username = `staff_${Date.now()}_${sequence++}`
  const result = await request.post(`${backend}/api/v1/employees`, {
    headers: { Authorization: `Bearer ${adminToken}` },
    data: { username, displayName: '验收员工', phone: '', password },
  })
  expect(result.status()).toBe(201)
  return { username, ...((await result.json()) as { id: string; version: number }) }
}
async function login(page: Page, username: string, value: string) {
  await page.goto('/login')
  await page.getByLabel('用户名', { exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill(value)
  await page.getByRole('button', { name: '登录', exact: false }).click()
  await expect(page).toHaveURL(/\/workspace$/)
}
test.beforeAll(async ({ request }) => {
  const response = await request.post(`${backend}/api/v1/sessions`, {
    data: { username: 'pc1admin', password: 'Pc1-local-admin-2026!' },
  })
  expect(response.status()).toBe(201)
  adminToken = (await response.json()).accessToken
})

test('管理员登录、真实概况、刷新恢复与服务端退出', async ({ page, request }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await login(page, 'pc1admin', 'Pc1-local-admin-2026!')
  await expect(page.getByRole('heading', { name: '工作台', exact: true })).toBeVisible()
  await expect(page.getByText('今日经营概况', { exact: true })).toBeVisible()
  await expect(page.getByText('顾客管理', { exact: true })).toBeVisible()
  await expect(page.getByRole('alert')).toHaveCount(0)
  const session = await page.evaluate(() =>
    JSON.parse(sessionStorage.getItem('han-menu.staff-session.v1') || 'null'),
  )
  await page.reload()
  await expect(page.getByRole('heading', { name: '工作台', exact: true })).toBeVisible()
  await page.screenshot({ path: 'test-results/workspace-admin.png', fullPage: true })
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page).toHaveURL(/\/login/)
  expect(
    (
      await request.get(`${backend}/api/v1/me`, {
        headers: { Authorization: `Bearer ${session.accessToken}` },
      })
    ).status(),
  ).toBe(401)
  expect(errors).toEqual([])
})

test('普通员工隐藏管理导航且直接路由和真实接口均拒绝越权', async ({ page, request }) => {
  const employee = await staff(request)
  await login(page, employee.username, password)
  await expect(page.getByText('顾客管理', { exact: true })).toHaveCount(0)
  const session = await page.evaluate(() =>
    JSON.parse(sessionStorage.getItem('han-menu.staff-session.v1') || 'null'),
  )
  expect(
    (
      await request.get(`${backend}/api/v1/management/customers`, {
        headers: { Authorization: `Bearer ${session.accessToken}` },
      })
    ).status(),
  ).toBe(403)
  for (const path of [
    '/settings/employees',
    '/settings/shop',
    '/customers',
    '/catalog/categories',
    '/catalog/dishes',
    '/catalog/meals/new',
  ]) {
    await page.goto(path)
    await expect(page.getByText('你没有访问此页面的权限')).toBeVisible()
  }
  await page.setViewportSize({ width: 1024, height: 768 })
  await page.goto('/workspace')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
  await page.setViewportSize({ width: 768, height: 768 })
  await page.getByRole('button', { name: '打开导航' }).click()
  await expect(page.getByRole('menuitem', { name: /工作台/ })).toBeVisible()
})

test('停用员工后的旧会话在刷新时失效', async ({ page, request }) => {
  const employee = await staff(request)
  await login(page, employee.username, password)
  const result = await request.patch(`${backend}/api/v1/employees/${employee.id}/status`, {
    headers: { Authorization: `Bearer ${adminToken}` },
    data: { status: 'DISABLED', version: employee.version },
  })
  expect(result.status()).toBe(204)
  await page.reload()
  await expect(page).toHaveURL(/\/login/)
  expect(await page.evaluate(() => sessionStorage.getItem('han-menu.staff-session.v1'))).toBeNull()
})

test('改密真实撤销旧会话并可使用新密码登录', async ({ page, request }) => {
  const employee = await staff(request)
  await login(page, employee.username, password)
  await page.goto('/account')
  const replacement = 'Pc1-replaced-staff-2026!'
  await page.getByLabel('当前密码', { exact: true }).fill(password)
  await page.getByLabel('新密码', { exact: true }).fill(replacement)
  await page.getByLabel('确认新密码', { exact: true }).fill(replacement)
  await page.getByRole('button', { name: '更新密码' }).click()
  await expect(page).toHaveURL(/\/login/)
  await expect(page.getByText('密码已修改，请重新登录')).toBeVisible()
  await login(page, employee.username, replacement)
})

test('身份恢复503不会进入应用，重试后沿用原会话验证', async ({ page, request }) => {
  const employee = await staff(request)
  await login(page, employee.username, password)
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        detail: '身份服务暂不可用',
        code: 'UNAVAILABLE',
        traceId: 'e2e-recovery',
      }),
    }),
  )
  await page.reload()
  await expect(page.getByRole('alert')).toContainText('身份服务暂不可用')
  await expect(page.getByRole('heading', { name: '工作台', exact: true })).toHaveCount(0)
  await page.unroute('**/api/v1/me')
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page).toHaveURL(/\/workspace$/)
})

test('表单、中文日期、上传选择、表格、消息与抽屉兼容', async ({ page }) => {
  await login(page, 'pc1admin', 'Pc1-local-admin-2026!')
  await page.goto('/_dev/components')
  await page.getByRole('button', { name: '验证表单与抽屉' }).click()
  await expect(page.getByText('请输入名称')).toBeVisible()
  await page.getByLabel('名称', { exact: true }).fill('中文主题验证')
  await page.getByPlaceholder('选择日期').click()
  await expect(page.locator('.ant-picker-dropdown')).toBeVisible()
  await page.keyboard.press('Escape')
  await page.locator('input[type=file]').setInputFiles({
    name: 'component.png',
    mimeType: 'image/png',
    buffer: Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=',
      'base64',
    ),
  })
  await expect(page.getByText('component.png', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '验证表单与抽屉' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await expect(page.getByText('名称：中文主题验证')).toBeVisible()
  await page.getByRole('button', { name: '完成验证' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
})
