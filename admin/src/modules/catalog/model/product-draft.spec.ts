import { describe, it, expect } from 'vitest'
import { productDraft, detailsFor, priceValue } from './product-draft'
import type { Product } from '../api/catalog'

describe('商品编辑约束', () => {
  it('价格不接受隐式舍入、科学计数法及超范围值', () => {
    for (const value of ['0', '1.001', '1e2', '1000000', '-1', '01.2'])
      expect(() => priceValue(value)).toThrow()
    expect(priceValue('999999.99')).toBe(999999.99)
    expect(priceValue('0.01')).toBe(0.01)
  })
  it('草稿与已读聚合的规格及套餐选择隔离', () => {
    const source: Product = {
      flavors: [{ name: '辣度', options: ['微辣'], required: true }],
      components: [{ dishId: 'dish', quantity: 1, selections: { 辣度: '微辣' } }],
    }
    const draft = productDraft(source)
    draft.flavors[0]!.options[0] = '不辣'
    draft.components[0]!.selections.辣度 = '不辣'
    expect(source.flavors![0]!.options).toEqual(['微辣'])
    expect(source.components![0]!.selections).toEqual({ 辣度: '微辣' })
  })
  it('拒绝去空白后的重复口味组及选项', () => {
    const draft = { ...productDraft(), name: '面条', categoryId: 'category', price: '18.50' }
    draft.flavors = [{ name: '辣度', options: ['微辣', ' 微辣 '], required: true }]
    expect(() => detailsFor('DISH', draft)).toThrow('不重复')
    draft.flavors = [
      { name: '辣度', options: ['微辣'], required: true },
      { name: ' 辣度 ', options: ['不辣'], required: true },
    ]
    expect(() => detailsFor('DISH', draft)).toThrow('不能重复')
  })
  it('套餐必选规格必须存在并属于服务器返回的合法选项', () => {
    const draft = {
      ...productDraft(),
      name: '套餐',
      categoryId: 'category',
      price: '18.50',
      components: [{ dishId: 'dish', quantity: 1, selections: {} as Record<string, string> }],
    }
    const dishes = {
      dish: {
        kind: 'DISH',
        name: '面条',
        flavors: [{ name: '辣度', options: ['微辣'], required: true }],
      },
    }
    expect(() => detailsFor('SET_MEAL', draft, dishes)).toThrow('补全')
    draft.components[0]!.selections = { 辣度: '微辣' }
    expect(detailsFor('SET_MEAL', draft, dishes).components).toHaveLength(1)
    draft.components.push({ ...draft.components[0]! })
    expect(() => detailsFor('SET_MEAL', draft, dishes)).toThrow('只能出现一次')
  })
  it('新增和移除图片不改写销售状态，菜品不携带套餐组成', () => {
    const draft = {
      ...productDraft(),
      name: '面条',
      categoryId: 'category',
      price: '18.50',
      imageId: undefined,
    }
    const body = detailsFor('DISH', draft)
    expect(body.components).toEqual([])
    expect(body).not.toHaveProperty('status')
    expect(body.imageId).toBeUndefined()
  })
})
