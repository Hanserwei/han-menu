<script setup lang="ts">
import { ref, reactive, computed, onBeforeUnmount, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Button, Table, Input, InputPassword, Drawer, Form, FormItem, Tag } from 'antdv-next'
import { employeesApi, type Employee } from '../api/employees'
import { revision } from '@/shared/api/result'
import { useCommand } from '@/shared/model/use-command'
import { useEditorGuard } from '@/shared/model/use-editor-guard'
import { dateTime } from '@/shared/lib/time'
import { maskPhone } from '@/shared/lib/display'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
import WriteFeedback from '@/shared/ui/WriteFeedback.vue'
import PagePagination from '@/shared/ui/PagePagination.vue'

const name = ref(''),
  applied = ref(''),
  page = ref(0),
  size = ref(20)
const scope = `employees-${crypto.randomUUID()}`
const filterRevision = ref(0)
watch(applied, () => filterRevision.value++)
const query = useQuery({
  gcTime: 0,
  queryKey: computed(() => [scope, filterRevision.value, page.value, size.value]),
  queryFn: ({ signal }) =>
    employeesApi.list(applied.value || undefined, page.value, size.value, signal),
})
const requestedId = ref<string>()
const open = ref(false),
  loading = ref(false),
  current = ref<Employee>(),
  baseline = ref('')
const form = reactive({ username: '', displayName: '', phone: '', password: '' })
const command = useCommand(),
  { pending, error, needsReload } = command
const dirty = computed(() => open.value && JSON.stringify(form) !== baseline.value)
const { confirmDiscard } = useEditorGuard(dirty, pending)
const columns = [
  { title: '用户名', dataIndex: 'username' },
  { title: '姓名', dataIndex: 'displayName' },
  { title: '联系电话', key: 'phone' },
  { title: '角色', key: 'role' },
  { title: '状态', key: 'status' },
  { title: '创建时间', key: 'createdAt' },
  { title: '操作', key: 'actions', width: 170 },
]
const rules = {
  username: [
    {
      required: true,
      pattern: /^[a-zA-Z][a-zA-Z0-9_]{2,31}$/,
      message: '字母开头，3—32 位字母、数字或下划线',
    },
  ],
  displayName: [{ required: true, whitespace: true, max: 50, message: '请输入姓名，最多50个字符' }],
  phone: [{ pattern: /^(\+?[1-9][0-9]{6,14})?$/, message: '请输入完整联系电话' }],
  password: [{ required: true, min: 12, max: 64, message: '初始密码需12—64个字符' }],
}
let requestId = 0
/** 抽屉每次读取最新档案；切换或关闭后不接收旧的异步结果。 */
async function edit(id?: string) {
  requestedId.value = id
  const request = ++requestId
  error.value = null
  current.value = undefined
  Object.assign(form, { username: '', displayName: '', phone: '', password: '' })
  baseline.value = JSON.stringify(form)
  open.value = true
  loading.value = false
  if (!id) return
  loading.value = true
  try {
    const value = await employeesApi.get(id)
    if (request !== requestId) return
    current.value = value
    Object.assign(form, {
      username: value.username,
      displayName: value.displayName,
      phone: value.phone || '',
    })
    baseline.value = JSON.stringify(form)
  } catch (failure) {
    if (request === requestId) error.value = failure
  } finally {
    if (request === requestId) loading.value = false
  }
}
async function close() {
  if (await confirmDiscard()) {
    ++requestId
    open.value = false
    form.password = ''
  }
}
async function reload() {
  if (!(await confirmDiscard())) return
  if (open.value && requestedId.value) await edit(requestedId.value)
  else {
    error.value = null
    await query.refetch()
  }
}
async function save() {
  const success = await command.run(async () => {
    const profile = {
      username: form.username.trim(),
      displayName: form.displayName.trim(),
      phone: form.phone.trim(),
    }
    if (current.value) {
      const { id, version } = revision(current.value)
      await employeesApi.update(id, { ...profile, version })
    } else await employeesApi.create({ ...profile, password: form.password })
    baseline.value = JSON.stringify(form)
    open.value = false
    form.password = ''
    await query.refetch()
  })
  if (success) current.value = undefined
}
async function changeStatus(value: Employee) {
  if (
    value.role === 'ADMIN' ||
    !(await command.confirm(
      value.status === 'ACTIVE' ? '停用员工？' : '启用员工？',
      '状态变更会使旧登录状态失效，重新启用后需要重新登录。',
    ))
  )
    return
  await command.run(async () => {
    const { id, version } = revision(value)
    await employeesApi.status(id, value.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE', version)
    await query.refetch()
  }, '员工状态已更新')
}
onBeforeUnmount(() => {
  ++requestId
  form.password = ''
})
</script>
<template>
  <div class="page-heading heading-inline">
    <div>
      <h1>员工管理</h1>
      <p class="muted">维护员工资料与账号状态。</p>
    </div>
    <Button type="primary" :disabled="pending" @click="edit()">新增员工</Button>
  </div>
  <div class="filter-bar">
    <Input
      v-model:value="name"
      aria-label="员工姓名筛选"
      placeholder="按姓名查询"
      :maxlength="50"
      @press-enter="
        () => {
          applied = name.trim()
          page = 0
        }
      "
    /><Button
      @click="
        () => {
          applied = name.trim()
          page = 0
        }
      "
      >查询</Button
    ><Button
      @click="
        () => {
          name = ''
          applied = ''
          page = 0
        }
      "
      >重置</Button
    >
  </div>
  <ProblemAlert :error="query.error.value" retry @retry="query.refetch()" />
  <WriteFeedback
    v-if="!open"
    :error="error"
    :needs-reload="needsReload"
    :pending="pending"
    @reload="reload"
  />
  <Table
    :columns="columns"
    :data-source="query.data.value?.items || []"
    row-key="id"
    :pagination="false"
    :loading="query.isFetching.value"
    :locale="{
      emptyText: query.isError.value
        ? '员工列表暂不可用'
        : applied
          ? '没有符合条件的员工'
          : '暂无员工',
    }"
    :scroll="{ x: 850 }"
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'phone'">{{ maskPhone(record.phone) }}</template
      ><template v-if="column.key === 'role'">{{
        record.role === 'ADMIN' ? '管理员' : '普通员工'
      }}</template>
      <Tag
        v-if="column.key === 'status'"
        :color="record.status === 'ACTIVE' ? 'success' : 'default'"
        >{{ record.status === 'ACTIVE' ? '启用' : '停用' }}</Tag
      >
      <template v-if="column.key === 'createdAt'">{{ dateTime(record.createdAt) }}</template>
      <div v-if="column.key === 'actions'" class="row-actions">
        <Button type="link" :disabled="pending" @click="edit(record.id)">编辑</Button
        ><Button
          v-if="record.role !== 'ADMIN'"
          type="link"
          :danger="record.status === 'ACTIVE'"
          :disabled="pending || needsReload"
          @click="changeStatus(record)"
          >{{ record.status === 'ACTIVE' ? '停用' : '启用' }}</Button
        >
      </div>
    </template>
  </Table>
  <PagePagination
    :page="page"
    :size="size"
    :total="query.data.value?.totalElements || 0"
    @change="
      (p, s) => {
        page = p
        size = s
      }
    "
  />
  <Drawer
    :title="current ? '编辑员工' : '新增员工'"
    :open="open"
    :size="560"
    :loading="loading"
    @close="close"
  >
    <WriteFeedback :error="error" :needs-reload="needsReload" :pending="pending" @reload="reload" />
    <Button v-if="requestedId && !current && !loading" @click="reload">重新读取员工</Button>
    <Form
      :model="form"
      :rules="rules"
      layout="vertical"
      :disabled="pending || loading"
      @finish="save"
    >
      <FormItem label="用户名" name="username"
        ><Input v-model:value="form.username" :maxlength="32" autocomplete="off" /></FormItem
      ><FormItem label="姓名" name="displayName"
        ><Input v-model:value="form.displayName" :maxlength="50" /></FormItem
      ><FormItem label="联系电话" name="phone"
        ><Input v-model:value="form.phone" :maxlength="16"
      /></FormItem>
      <FormItem v-if="!current" label="初始密码" name="password"
        ><InputPassword v-model:value="form.password" :maxlength="64" autocomplete="new-password"
      /></FormItem>
      <p class="muted">
        {{
          current?.role === 'ADMIN'
            ? '管理员角色不可更改。'
            : '角色固定为普通员工，不可通过资料编辑提权。'
        }}
      </p>
      <Button
        type="primary"
        html-type="submit"
        :loading="pending"
        :disabled="needsReload || loading || (!!requestedId && !current)"
        >保存员工</Button
      >
    </Form>
  </Drawer>
</template>
