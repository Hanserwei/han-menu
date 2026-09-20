import { api } from '@/shared/api/client'
import { resource, complete } from '@/shared/api/result'
import type { components } from '@/shared/api/schema'
export type Employee = components['schemas']['EmployeeView']
/** 员工资料与状态使用独立命令，更新204后由页面重新读取版本。 */
export const employeesApi = {
  list: async (name: string | undefined, page: number, size: number, signal?: AbortSignal) =>
    resource(
      await api.GET('/api/v1/employees', { params: { query: { name, page, size } }, signal }),
    ),
  get: async (id: string) =>
    resource(await api.GET('/api/v1/employees/{id}', { params: { path: { id } } })),
  create: async (body: components['schemas']['CreateEmployee']) =>
    resource(await api.POST('/api/v1/employees', { body })),
  update: async (id: string, body: components['schemas']['UpdateEmployee']) =>
    complete(await api.PUT('/api/v1/employees/{id}', { params: { path: { id } }, body })),
  status: async (id: string, status: 'ACTIVE' | 'DISABLED', version: number) =>
    complete(
      await api.PATCH('/api/v1/employees/{id}/status', {
        params: { path: { id } },
        body: { status, version },
      }),
    ),
}
