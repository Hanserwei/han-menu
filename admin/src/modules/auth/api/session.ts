import { api } from '@/shared/api/client'
import { ApiProblem, problemFromResponse } from '@/shared/api/problem'
import { parseSession } from '../model/session-storage'

export interface StaffIdentity {
  id: string
  username: string
  displayName: string
  role: 'ADMIN' | 'STAFF'
}

/** 编译类型不是运行时校验；身份和权限字段必须完整才能进入应用。 */
export function parseIdentity(value: unknown): StaffIdentity {
  const data = value && typeof value === 'object' ? (value as Record<string, unknown>) : {}
  if (
    typeof data.id !== 'string' ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(data.id) ||
    typeof data.username !== 'string' ||
    typeof data.displayName !== 'string' ||
    (data.role !== 'ADMIN' && data.role !== 'STAFF')
  )
    throw new ApiProblem(502, '服务器返回的身份信息不完整', 'INVALID_IDENTITY')
  return {
    id: data.id,
    username: data.username,
    displayName: data.displayName,
    role: data.role,
  }
}
export async function createSession(username: string, password: string) {
  const result = await api.POST('/api/v1/sessions', { body: { username, password } })
  if (!result.response.ok) throw problemFromResponse(result.error, result.response)
  const session = parseSession(JSON.stringify(result.data))
  if (!session || result.data?.tokenType !== 'Bearer')
    throw new ApiProblem(502, '服务器返回的登录状态不完整', 'INVALID_SESSION')
  return session
}
export async function currentIdentity(): Promise<StaffIdentity> {
  const result = await api.GET('/api/v1/me')
  if (!result.response.ok) throw problemFromResponse(result.error, result.response)
  return parseIdentity(result.data)
}
export async function revokeSession() {
  const result = await api.DELETE('/api/v1/sessions/current')
  if (!result.response.ok && result.response.status !== 401)
    throw problemFromResponse(result.error, result.response)
}
export async function changePassword(currentPassword: string, newPassword: string) {
  const result = await api.PUT('/api/v1/me/password', { body: { currentPassword, newPassword } })
  if (!result.response.ok) throw problemFromResponse(result.error, result.response)
}
