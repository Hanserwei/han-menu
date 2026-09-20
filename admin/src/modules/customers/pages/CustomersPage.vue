<script setup lang="ts">
import { reactive, ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import {
  Form,
  FormItem,
  Input,
  Select,
  DatePicker,
  Button,
  Table,
  Drawer,
  Descriptions,
  DescriptionsItem,
  Tag,
} from 'antdv-next'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import { customersApi, type CustomerFilter, type Customer } from '../api/customers'
import { revision } from '@/shared/api/result'
import { useEditorGuard } from '@/shared/model/use-editor-guard'
import { useCommand } from '@/shared/model/use-command'
import { dateTime } from '@/shared/lib/time'
import { maskPhone } from '@/shared/lib/display'
import PagePagination from '@/shared/ui/PagePagination.vue'
import WriteFeedback from '@/shared/ui/WriteFeedback.vue'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
const route = useRoute(),
  router = useRouter()
const draft = reactive({ name: '', phone: '', enabled: undefined as string | undefined })
const dates = ref<[Dayjs, Dayjs]>()
const filter = ref<CustomerFilter>({}),
  page = ref(0),
  size = ref(20),
  filterRevision = ref(0)
// 查询键不含个人筛选值，筛选仅在当前页面内存保存，离页清理对应缓存。
const scope = `customers-${crypto.randomUUID()}`
const query = useQuery({
  queryKey: computed(() => [scope, filterRevision.value, page.value, size.value]),
  queryFn: ({ signal }) => customersApi.list(filter.value, page.value, size.value, signal),
  gcTime: 0,
})
function search() {
  filter.value = {
    name: draft.name.trim() || undefined,
    phone: draft.phone.trim() || undefined,
    enabled: draft.enabled === undefined ? undefined : draft.enabled === 'true',
    from: dates.value?.[0]
      ? dayjs.tz(dates.value[0].format('YYYY-MM-DD'), 'Asia/Shanghai').toISOString()
      : undefined,
    to: dates.value?.[1]
      ? dayjs.tz(dates.value[1].add(1, 'day').format('YYYY-MM-DD'), 'Asia/Shanghai').toISOString()
      : undefined,
  }
  page.value = 0
  filterRevision.value++
}
function reset() {
  Object.assign(draft, { name: '', phone: '', enabled: undefined })
  dates.value = undefined
  search()
}
const command = useCommand(),
  { pending, error, needsReload } = command
useEditorGuard(
  computed(() => false),
  pending,
)
const current = ref<Customer>(),
  loading = ref(false),
  showPhone = ref(false),
  detailError = ref<unknown>(null)
let detailRequest = 0
async function loadDetail() {
  const id = String(route.params.id || '')
  const request = ++detailRequest
  current.value = undefined
  showPhone.value = false
  detailError.value = null
  if (!id) return
  loading.value = true
  try {
    const value = await customersApi.get(id)
    if (request === detailRequest) {
      current.value = value
      error.value = null
    }
  } catch (failure) {
    if (request === detailRequest) detailError.value = failure
  } finally {
    if (request === detailRequest) loading.value = false
  }
}
watch(() => route.params.id, loadDetail, { immediate: true })
onBeforeUnmount(() => detailRequest++)
async function toggle() {
  if (
    !current.value ||
    !(await command.confirm(
      current.value.enabled ? '停用顾客？' : '启用顾客？',
      '停用使旧会话失效，重新启用需要重新登录；历史订单保留。',
    ))
  )
    return
  await command.run(async () => {
    const { id, version } = revision(current.value!)
    current.value = await customersApi.status(id, !current.value!.enabled, version)
    await query.refetch()
  }, '顾客状态已更新')
}
const columns = [
  { title: '昵称', dataIndex: 'displayName' },
  { title: '手机号', key: 'phone' },
  { title: '状态', key: 'enabled' },
  { title: '注册时间', key: 'createdAt' },
  { title: '操作', key: 'actions' },
]
</script>
<template>
  <div class="page-heading">
    <h1>顾客管理</h1>
    <p class="muted">查看顾客档案，管理账号启停用。</p>
  </div>
  <Form :model="draft" layout="inline" class="filter-bar" @finish="search"
    ><FormItem name="name"
      ><Input
        v-model:value="draft.name"
        aria-label="顾客名称筛选"
        placeholder="昵称"
        :maxlength="50" /></FormItem
    ><FormItem
      name="phone"
      :rules="[{ pattern: /^(\+?[1-9][0-9]{6,14})?$/, message: '请输入完整手机号' }]"
      ><Input
        v-model:value="draft.phone"
        aria-label="顾客手机号筛选"
        placeholder="完整手机号"
        :maxlength="16" /></FormItem
    ><Select
      v-model:value="draft.enabled"
      aria-label="顾客状态筛选"
      placeholder="全部状态"
      allow-clear
      :options="[
        { label: '启用', value: 'true' },
        { label: '停用', value: 'false' },
      ]"
    /><DatePicker.RangePicker
      v-model:value="dates"
      :placeholder="['注册开始日期', '注册结束日期']"
    /><Button html-type="submit">查询</Button><Button @click="reset">重置</Button></Form
  >
  <ProblemAlert :error="query.error.value" retry @retry="query.refetch()" />
  <Table
    :columns="columns"
    :data-source="query.data.value?.items || []"
    row-key="id"
    :pagination="false"
    :loading="query.isFetching.value"
    :locale="{ emptyText: query.isError.value ? '顾客列表暂不可用' : '没有符合条件的顾客' }"
    :scroll="{ x: 760 }"
    ><template #bodyCell="{ column, record }"
      ><template v-if="column.key === 'phone'">{{ maskPhone(record.phone) }}</template
      ><Tag v-if="column.key === 'enabled'" :color="record.enabled ? 'success' : 'default'">{{
        record.enabled ? '启用' : '停用'
      }}</Tag
      ><template v-if="column.key === 'createdAt'">{{ dateTime(record.createdAt) }}</template
      ><Button
        v-if="column.key === 'actions'"
        type="link"
        @click="router.push(`/customers/${record.id}`)"
        >查看详情</Button
      ></template
    ></Table
  >
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
    title="顾客详情"
    :open="!!route.params.id"
    :loading="loading"
    :size="560"
    @close="!pending && router.push('/customers')"
    ><ProblemAlert :error="detailError" retry @retry="loadDetail" /><WriteFeedback
      :error="error"
      :needs-reload="needsReload"
      :pending="pending"
      @reload="loadDetail"
    />
    <template v-if="current"
      ><Descriptions :column="1"
        ><DescriptionsItem label="顾客编号">{{ current.id }}</DescriptionsItem
        ><DescriptionsItem label="昵称">{{ current.displayName }}</DescriptionsItem
        ><DescriptionsItem label="手机号"
          >{{ showPhone ? current.phone : maskPhone(current.phone)
          }}<Button type="link" @click="showPhone = !showPhone">{{
            showPhone ? '隐藏号码' : '显示完整号码'
          }}</Button></DescriptionsItem
        ><DescriptionsItem label="注册时间">{{ dateTime(current.createdAt) }}</DescriptionsItem
        ><DescriptionsItem label="更新时间">{{ dateTime(current.updatedAt) }}</DescriptionsItem
        ><DescriptionsItem label="账号状态">{{
          current.enabled ? '启用' : '停用'
        }}</DescriptionsItem></Descriptions
      >
      <Button
        :danger="current.enabled"
        :loading="pending"
        :disabled="needsReload"
        @click="toggle"
        >{{ current.enabled ? '停用顾客' : '启用顾客' }}</Button
      >
      <p class="muted top-space">关联订单查询将在订单中心开放后提供。</p></template
    >
  </Drawer>
</template>
