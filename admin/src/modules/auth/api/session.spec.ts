import { describe, expect, it } from 'vitest'
import { parseIdentity } from './session'

const identity = {
  id: 'e4bfdf43-1483-4f34-81cb-5c76cdb32864',
  username: 'staff',
  displayName: '员工',
  role: 'STAFF',
}

describe('身份响应运行时校验', () => {
  it('只接受明确的角色字符串，不把数组或未知角色隐式转换为权限', () => {
    expect(parseIdentity(identity).role).toBe('STAFF')
    for (const role of [['ADMIN'], { role: 'ADMIN' }, 'SUPER_ADMIN', null]) {
      expect(() => parseIdentity({ ...identity, role })).toThrow('身份信息不完整')
    }
  })
  it('拒绝不完整身份和伪造的 UUID 形状', () => {
    expect(() => parseIdentity({ ...identity, id: '-'.repeat(36) })).toThrow('身份信息不完整')
    expect(() => parseIdentity({ ...identity, displayName: null })).toThrow('身份信息不完整')
  })
})
