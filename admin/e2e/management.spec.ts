import { test, expect, type APIRequestContext, type Page } from '@playwright/test'
const backend = 'http://127.0.0.1:18081'
let token = '',
  expiresAt = ''
const prefix = `pc2_${Date.now()}`
const headers = () => ({ Authorization: `Bearer ${token}` })
async function post(request: APIRequestContext, path: string, data: unknown) {
  const response = await request.post(backend + path, { headers: headers(), data })
  expect(response.ok()).toBe(true)
  return response.json()
}
async function get(request: APIRequestContext, path: string) {
  const response = await request.get(backend + path, { headers: headers() })
  expect(response.ok()).toBe(true)
  return response.json()
}
async function category(request: APIRequestContext, kind = 'DISH') {
  return post(request, '/api/v1/catalog/categories', {
    kind,
    name: `${prefix}_${kind}_${crypto.randomUUID().slice(0, 6)}`,
    sortOrder: 0,
  })
}
async function product(
  request: APIRequestContext,
  categoryId: string,
  name: string,
  flavors: unknown[] = [],
) {
  return post(request, '/api/v1/catalog/products', {
    kind: 'DISH',
    details: { categoryId, name, description: '测试资料', price: 18.5, flavors, components: [] },
  })
}
async function confirm(page: Page) {
  await page
    .getByRole('dialog')
    .getByRole('button', { name: /确\s*认/ })
    .click()
}
async function select(page: Page, label: string, text: string) {
  await page.getByLabel(label, { exact: true }).click()
  await page.getByTitle(text, { exact: true }).last().click()
}
async function filterCategory(page: Page, name: string) {
  await select(page, '商品分类筛选', name)
}
test.beforeAll(async ({ request }) => {
  const response = await request.post(backend + '/api/v1/sessions', {
    data: { username: 'pc1admin', password: 'Pc1-local-admin-2026!' },
  })
  expect(response.ok()).toBe(true)
  const value = await response.json()
  token = value.accessToken
  expiresAt = value.expiresAt
})
test.beforeEach(async ({ page }) => {
  await page.addInitScript(
    ({ accessToken, expiresAt }) =>
      sessionStorage.setItem(
        'han-menu.staff-session.v1',
        JSON.stringify({ accessToken, expiresAt }),
      ),
    { accessToken: token, expiresAt },
  )
})

test('分类新增编辑、真实引用删除冲突与无引用删除', async ({ page, request }) => {
  await page.goto('/catalog/categories')
  await page.getByRole('button', { name: '新增分类' }).click()
  await page.getByLabel('分类名称', { exact: true }).fill(prefix + '_界面分类')
  await page.getByRole('button', { name: '保存分类' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  let row = page.getByRole('row').filter({ hasText: prefix + '_界面分类' })
  await row.getByRole('button', { name: '编辑' }).click()
  await page.getByLabel('分类名称', { exact: true }).fill(prefix + '_已编辑分类')
  await page.getByRole('button', { name: '保存分类' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  row = page.getByRole('row').filter({ hasText: prefix + '_已编辑分类' })
  await row.getByRole('button', { name: '删除' }).click()
  await confirm(page)
  await expect(row).toHaveCount(0)
  const cat = await category(request)
  await product(request, cat.id, prefix + '_引用菜品')
  await page.reload()
  row = page.getByRole('row').filter({ hasText: cat.name })
  await row.getByRole('button', { name: '删除' }).click()
  await confirm(page)
  await expect(page.getByRole('alert').first()).toBeVisible()
  await expect(row).toHaveCount(1)
})

test('菜品创建、真实图片上传、口味编辑、上下架与删除', async ({ page, request }) => {
  const cat = await category(request)
  await page.goto('/catalog/dishes/new')
  await page.getByLabel('商品名称', { exact: true }).fill(prefix + '_图片面条')
  await select(page, '所属分类', cat.name)
  await page.getByLabel('售价（元）', { exact: true }).fill('32.00')
  await page.getByRole('button', { name: '添加口味组' }).click()
  await page.getByRole('textbox', { name: '口味组1名称', exact: true }).fill('辣度')
  await page.getByRole('textbox', { name: '口味组1选项1', exact: true }).fill('微辣')
  const uploadResponse = page.waitForResponse(
    (r) => r.url().endsWith('/api/v1/catalog/images') && r.request().method() === 'POST',
  )
  await page.locator('input[type=file]').setInputFiles('e2e/fixtures/upload.png')
  expect((await uploadResponse).status()).toBe(201)
  await expect(page.getByRole('button', { name: '更换图片' })).toBeVisible()
  await page.getByRole('button', { name: '保存菜品' }).click()
  await expect(page).toHaveURL(/\/catalog\/dishes$/)
  await filterCategory(page, cat.name)
  let row = page.getByRole('row').filter({ hasText: prefix + '_图片面条' })
  await expect(row).toContainText('32.00')
  await row.getByRole('button', { name: '编辑', exact: true }).click()
  await page.getByLabel('商品名称', { exact: true }).fill(prefix + '_修改面条')
  await page.getByRole('button', { name: '保存菜品' }).click()
  await expect(page).toHaveURL(/\/catalog\/dishes$/)
  await filterCategory(page, cat.name)
  row = page.getByRole('row').filter({ hasText: prefix + '_修改面条' })
  await row.getByRole('button', { name: '上架', exact: true }).click()
  await confirm(page)
  await expect(row).toContainText('在售')
  await row.getByRole('button', { name: '下架', exact: true }).click()
  await confirm(page)
  await expect(row).toContainText('下架')
  await row.getByRole('button', { name: '删除', exact: true }).click()
  await confirm(page)
  await expect(row).toHaveCount(0)
})

test('套餐选择菜品及固定口味，引用约束阻止菜品下架', async ({ page, request }) => {
  const cat = await category(request),
    mealCat = await category(request, 'SET_MEAL')
  const dish = await product(request, cat.id, prefix + '_套餐菜品', [
    { name: '辣度', options: ['微辣', '不辣'], required: true },
  ])
  const response = await request.patch(`${backend}/api/v1/catalog/products/${dish.id}/status`, {
    headers: headers(),
    data: { status: 'ON_SALE', version: dish.version },
  })
  expect(response.ok()).toBe(true)
  await page.goto('/catalog/meals/new')
  await page.getByLabel('商品名称', { exact: true }).fill(prefix + '_午间套餐')
  await select(page, '所属分类', mealCat.name)
  await page.getByLabel('售价（元）', { exact: true }).fill('36')
  await page.getByRole('button', { name: '添加组成菜品' }).click()
  await select(page, '组成菜品分类', cat.name)
  await page
    .getByRole('dialog')
    .getByRole('row')
    .filter({ hasText: dish.name })
    .getByRole('button', { name: '添加', exact: true })
    .click()
  await page.getByRole('button', { name: '保存套餐' }).click()
  await expect(page.getByRole('alert')).toContainText('补全')
  await select(page, `${dish.name}-辣度`, '微辣')
  await page.getByRole('button', { name: '保存套餐' }).click()
  await expect(page).toHaveURL(/\/catalog\/meals$/)
  await filterCategory(page, mealCat.name)
  const row = page.getByRole('row').filter({ hasText: prefix + '_午间套餐' })
  await row.getByRole('button', { name: '上架', exact: true }).click()
  await confirm(page)
  await expect(row).toContainText('在售')
  await page.goto('/catalog/dishes')
  await filterCategory(page, cat.name)
  await page
    .getByRole('row')
    .filter({ hasText: dish.name })
    .getByRole('button', { name: '下架', exact: true })
    .click()
  await confirm(page)
  await expect(page.getByRole('alert').first()).toBeVisible()
  await expect(page.getByRole('row').filter({ hasText: dish.name })).toContainText('在售')
  const latest = await get(request, `/api/v1/catalog/products/${dish.id}`)
  expect(latest.status).toBe('ON_SALE')
})

test('员工新建编辑及启停用，管理员没有停用按钮', async ({ page }) => {
  await page.goto('/settings/employees')
  await page.getByRole('button', { name: '新增员工' }).click()
  await page.getByLabel('用户名', { exact: true }).fill(prefix + '_staff')
  await page.getByLabel('姓名', { exact: true }).fill('新员工')
  await page.getByLabel('初始密码', { exact: true }).fill('Pc2-employee-test-2026!')
  await page.getByRole('button', { name: '保存员工' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  const row = page.getByRole('row').filter({ hasText: prefix + '_staff' })
  await row.getByRole('button', { name: '编辑' }).click()
  await page.getByLabel('姓名', { exact: true }).fill('编辑员工')
  await expect(page.getByLabel('初始密码', { exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '保存员工' }).click()
  await expect(row).toContainText('编辑员工')
  await row.getByRole('button', { name: '停用', exact: true }).click()
  await confirm(page)
  await expect(row.getByRole('button', { name: '启用', exact: true })).toBeVisible()
  await row.getByRole('button', { name: '启用', exact: true }).click()
  await confirm(page)
  await expect(row.getByRole('button', { name: '停用', exact: true })).toBeVisible()
  await expect(
    page
      .getByRole('row')
      .filter({ hasText: 'pc1admin' })
      .getByRole('button', { name: '停用', exact: true }),
  ).toHaveCount(0)
})

test('顾客筛选、详情及启停用撤销真实顾客会话', async ({ page, request }) => {
  const phone = '139' + Date.now().toString().slice(-8),
    password = 'Pc2-customer-2026!'
  let response = await request.post(backend + '/api/v1/customer/accounts', {
    data: { phone, displayName: prefix + '_顾客', password },
  })
  expect(response.status()).toBe(201)
  const customer = await response.json()
  response = await request.post(backend + '/api/v1/customer/sessions', {
    data: { phone, password },
  })
  expect(response.ok()).toBe(true)
  const customerToken = (await response.json()).accessToken
  await page.goto('/customers')
  await page.getByRole('textbox', { name: '顾客手机号筛选' }).fill(phone)
  await page.getByRole('button', { name: /查\s*询/, exact: true }).click()
  const row = page.getByRole('row').filter({ hasText: prefix + '_顾客' })
  await row.getByRole('button', { name: '查看详情' }).click()
  await expect(page).toHaveURL(new RegExp(customer.id))
  await page.getByRole('button', { name: '停用顾客' }).click()
  await confirm(page)
  await expect(page.getByRole('button', { name: '启用顾客' })).toBeVisible()
  expect(
    (
      await request.get(backend + '/api/v1/customer/me', {
        headers: { Authorization: `Bearer ${customerToken}` },
      })
    ).status(),
  ).toBe(401)
  await page.getByRole('button', { name: '启用顾客' }).click()
  await confirm(page)
  await expect(page.getByRole('button', { name: '停用顾客' })).toBeVisible()
  expect(
    (
      await request.get(backend + '/api/v1/customer/me', {
        headers: { Authorization: `Bearer ${customerToken}` },
      })
    ).status(),
  ).toBe(401)
})

test('门店资料、开店打烊与真实版本冲突保留草稿', async ({ page, request }) => {
  await page.goto('/settings/shop')
  await page.getByLabel('门店名称', { exact: true }).fill('PC2验收门店')
  await page.getByLabel('联系电话', { exact: true }).fill('13800138000')
  await page.getByLabel('门店地址', { exact: true }).fill('杭州市西湖区测试路')
  await page.getByRole('button', { name: '保存资料' }).click()
  await expect(page.getByRole('button', { name: '开始营业' })).toBeEnabled()
  await page.getByRole('button', { name: '开始营业' }).click()
  await confirm(page)
  await expect(page.getByRole('button', { name: '打烊', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '打烊', exact: true }).click()
  await confirm(page)
  await expect(page.getByRole('button', { name: '开始营业' })).toBeVisible()
  const latest = await get(request, '/api/v1/shop')
  const response = await request.put(backend + '/api/v1/shop', {
    headers: headers(),
    data: {
      name: '并发修改门店',
      phone: latest.phone,
      address: latest.address,
      version: latest.version,
    },
  })
  expect(response.ok()).toBe(true)
  await page.getByLabel('门店名称', { exact: true }).fill('保留我的草稿')
  await page.getByRole('button', { name: '保存资料' }).click()
  await expect(page.getByRole('alert').first()).toBeVisible()
  await expect(page.getByLabel('门店名称', { exact: true })).toHaveValue('保留我的草稿')
  await expect(page.getByRole('button', { name: '保存资料' })).toBeDisabled()
  await page.getByRole('button', { name: '重新读取', exact: true }).first().click()
  await page.getByRole('dialog').getByRole('button', { name: '放弃修改' }).click()
  await expect(page.getByLabel('门店名称', { exact: true })).toHaveValue('并发修改门店')
})

test('编辑菜品时图片失败保留草稿，离开提示且深链接刷新可恢复', async ({ page, request }) => {
  const cat = await category(request),
    dish = await product(request, cat.id, prefix + '_编辑保护')
  await page.goto(`/catalog/dishes/${dish.id}/edit`)
  await expect(page.getByLabel('商品名称', { exact: true })).toHaveValue(dish.name)
  await page.getByLabel('商品名称', { exact: true }).fill('未提交草稿')
  await page.locator('input[type=file]').setInputFiles({
    name: 'fake.png',
    mimeType: 'image/png',
    buffer: Buffer.from('invalid image'),
  })
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.getByLabel('商品名称', { exact: true })).toHaveValue('未提交草稿')
  await page.getByRole('button', { name: '返回菜品列表' }).click()
  await expect(page.getByRole('dialog')).toContainText('放弃尚未保存')
  await page.getByRole('button', { name: '继续编辑' }).click()
  await expect(page.getByLabel('商品名称', { exact: true })).toHaveValue('未提交草稿')
  await page.screenshot({ path: 'test-results/pc2-dish-editor.png', fullPage: true })
  await page.getByRole('button', { name: '返回菜品列表' }).click()
  await page.getByRole('button', { name: '放弃修改' }).click()
  await expect(page).toHaveURL(/\/catalog\/dishes$/)
})

test('商品分页有真实总量，直接编辑时409保留草稿并可重新读取', async ({ page, request }) => {
  const cat = await category(request)
  const created = []
  for (let i = 0; i < 21; i++)
    created.push(await product(request, cat.id, `${prefix}_分页菜品${i}`))
  await page.goto('/catalog/dishes')
  await filterCategory(page, cat.name)
  await expect(page.getByText('共 21 条', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '下一页', exact: true }).click()
  await expect(page.locator('tbody tr[data-row-key]')).toHaveCount(1)
  const dish = created[0]!
  await page.goto(`/catalog/dishes/${dish.id}/edit`)
  await expect(page.getByLabel('商品名称', { exact: true })).toHaveValue(dish.name)
  await page.reload()
  await expect(page.getByLabel('商品名称', { exact: true })).toHaveValue(dish.name)
  const response = await request.put(`${backend}/api/v1/catalog/products/${dish.id}`, {
    headers: headers(),
    data: {
      version: dish.version,
      details: {
        name: '服务端新名称',
        categoryId: cat.id,
        description: '',
        price: 20,
        flavors: [],
        components: [],
      },
    },
  })
  expect(response.ok()).toBe(true)
  await page.getByLabel('商品名称', { exact: true }).fill('我的冲突草稿')
  await page.getByRole('button', { name: '保存菜品' }).click()
  await expect(page.getByRole('button', { name: '保存菜品' })).toBeDisabled()
  await expect(page.getByLabel('商品名称', { exact: true })).toHaveValue('我的冲突草稿')
  await page.getByRole('button', { name: '重新读取', exact: true }).click()
  await page.getByRole('button', { name: '放弃修改' }).click()
  await expect(page.getByLabel('商品名称', { exact: true })).toHaveValue('服务端新名称')
})

test('菜品编辑与窄桌面布局无溢出，控制台无组件错误', async ({ page, request }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
  const cat = await category(request)
  const dish = await product(request, cat.id, '清汤牛肉面', [
    { name: '辣度', options: ['不辣', '微辣', '中辣'], required: true },
  ])
  await page.goto(`/catalog/dishes/${dish.id}/edit`)
  await expect(page.getByLabel('商品名称', { exact: true })).toHaveValue('清汤牛肉面')
  await page.screenshot({ path: 'test-results/pc2-editor-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 1024, height: 768 })
  await expect(page.getByRole('menuitem', { name: '菜品管理', exact: true })).toBeHidden()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name: '保存菜品' }).scrollIntoViewIfNeeded()
  await expect(page.getByRole('button', { name: '保存菜品' })).toBeVisible()
  await page.screenshot({ path: 'test-results/pc2-editor-1024.png', fullPage: true })
  await page.goto('/settings/employees')
  await page.getByRole('button', { name: '新增员工' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  expect(errors).toEqual([])
})
