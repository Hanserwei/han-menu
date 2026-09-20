import type { Product, Details, Kind } from '../api/catalog'

export interface FlavorDraft {
  name: string
  options: string[]
  required: boolean
}
export interface ComponentDraft {
  dishId: string
  quantity: number
  selections: Record<string, string>
}
export interface ProductDraft {
  name: string
  categoryId: string
  description: string
  price: string
  imageId?: string
  flavors: FlavorDraft[]
  components: ComponentDraft[]
}
/** 编辑副本与查询缓存隔离，禁止异步刷新覆盖尚未提交的输入。 */
export function productDraft(value?: Product): ProductDraft {
  return {
    name: value?.name || '',
    categoryId: value?.categoryId || '',
    description: value?.description || '',
    price: value?.price?.toFixed(2) || '',
    imageId: value?.imageId,
    flavors: (value?.flavors || []).map((f) => ({
      name: f.name || '',
      options: [...(f.options || [])],
      required: !!f.required,
    })),
    components: (value?.components || []).map((c) => ({
      dishId: c.dishId || '',
      quantity: c.quantity || 1,
      selections: { ...c.selections },
    })),
  }
}
/** 价格先按十进制文本校验，不能悄悄舍入用户输入；这里只转换单价，不在浏览器计算应付金额。 */
export function priceValue(input: string): number {
  if (!/^(?:0|[1-9]\d{0,5})(?:\.\d{1,2})?$/.test(input) || Number(input) < 0.01)
    throw new Error('价格需为0.01—999999.99，最多两位小数')
  return Number(input)
}
/** 在服务端复验之前给出可定位的规格提示，不绕过目录引用与可售性约束。 */
export function detailsFor(
  kind: Kind,
  draft: ProductDraft,
  dishes: Record<string, Product> = {},
): Details {
  const name = draft.name.trim()
  if (!name || name.length > 100 || !draft.categoryId) throw new Error('请填写名称并选择分类')
  if (draft.description.length > 1000) throw new Error('描述最多1000个字符')
  const flavors = draft.flavors.map((f) => ({
    name: f.name.trim(),
    options: f.options.map((o) => o.trim()),
    required: f.required,
  }))
  if (kind === 'DISH') {
    if (flavors.length > 10) throw new Error('最多10个口味组')
    if (new Set(flavors.map((f) => f.name)).size !== flavors.length)
      throw new Error('口味组名称不能重复')
    for (const f of flavors)
      if (
        !f.name ||
        f.name.length > 30 ||
        !f.options.length ||
        f.options.length > 20 ||
        f.options.some((o) => !o || o.length > 30) ||
        new Set(f.options).size !== f.options.length
      )
        throw new Error('口味组需有效名称及1—20个不重复选项，每项最多30字')
  } else {
    if (!draft.components.length || draft.components.length > 50)
      throw new Error('套餐必须有1—50个组成菜品')
    if (new Set(draft.components.map((c) => c.dishId)).size !== draft.components.length)
      throw new Error('同一菜品只能出现一次')
    for (const c of draft.components) {
      if (!c.dishId || !Number.isInteger(c.quantity) || c.quantity < 1 || c.quantity > 99)
        throw new Error('组成菜品数量应为1—99整数')
      const dish = dishes[c.dishId]
      if (!dish || dish.kind !== 'DISH') throw new Error('请等待组成菜品读取完成或重新选择')
      for (const f of dish.flavors || [])
        if (
          f.name &&
          ((f.required && !c.selections[f.name]) ||
            (c.selections[f.name] && !f.options?.includes(c.selections[f.name]!)))
        )
          throw new Error(`请补全“${dish.name}”的有效口味选择`)
      if (
        Object.keys(c.selections).some((key) => !(dish.flavors || []).some((f) => f.name === key))
      )
        throw new Error('套餐包含已失效口味，请重新读取菜品')
    }
  }
  return {
    name,
    categoryId: draft.categoryId,
    description: draft.description,
    price: priceValue(draft.price),
    imageId: draft.imageId,
    flavors: kind === 'DISH' ? flavors : [],
    components: kind === 'SET_MEAL' ? draft.components : [],
  }
}
