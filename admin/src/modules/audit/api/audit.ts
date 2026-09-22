import { api } from '@/shared/api/client'
import { resource } from '@/shared/api/result'
import type { operations } from '@/shared/api/schema'
export type AuditFilter = NonNullable<operations['search_2']['parameters']['query']>
export const auditActions: Record<NonNullable<AuditFilter['action']>, string> = {
  BOOTSTRAP: '初始化管理员',
  LOGIN: '登录',
  LOGOUT: '退出',
  CREATE_EMPLOYEE: '创建员工',
  UPDATE_EMPLOYEE: '修改员工',
  CHANGE_STATUS: '更改员工状态',
  CHANGE_CUSTOMER_STATUS: '更改顾客状态',
  CHANGE_PASSWORD: '修改密码',
  AUTHORIZATION_DENIED: '拒绝授权',
  LOGIN_LIMITED: '登录限流',
}
/** 安全审计仅展示身份模块实际登记的固定事件，不读取日志正文。 */
export async function searchAudit(query: AuditFilter, signal?: AbortSignal) {
  return resource(await api.GET('/api/v1/management/audit-events', { params: { query }, signal }))
}
