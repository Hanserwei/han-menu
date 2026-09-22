<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Form, FormItem, Input, Select, DatePicker, Button, Table, Tag } from 'antdv-next'
import type { Dayjs } from 'dayjs'
import { searchAudit, auditActions, type AuditFilter } from '../api/audit'
import { optionalId, instantRange } from '@/shared/lib/query-filters'
import { dateTime } from '@/shared/lib/time'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
import ResourceId from '@/shared/ui/ResourceId.vue'
import PagePagination from '@/shared/ui/PagePagination.vue'
const empty = () => ({
  action: '' as NonNullable<AuditFilter['action']> | '',
  actorId: '',
  subjectId: '',
  successful: '',
})
const draft = reactive(empty()),
  dates = ref<[Dayjs, Dayjs]>(),
  applied = ref<AuditFilter>({}),
  page = ref(0),
  size = ref(20),
  error = ref<unknown>(null)
const query = useQuery({
  queryKey: computed(() => ['audit', applied.value, page.value, size.value]),
  queryFn: ({ signal }) =>
    searchAudit({ ...applied.value, page: page.value, size: size.value }, signal),
})
function apply() {
  try {
    applied.value = {
      action: draft.action || undefined,
      actorId: optionalId(draft.actorId),
      subjectId: optionalId(draft.subjectId),
      successful: draft.successful === '' ? undefined : draft.successful === 'true',
      ...instantRange(
        dates.value?.[0].format('YYYY-MM-DD') || '',
        dates.value?.[1].format('YYYY-MM-DD') || '',
      ),
    }
    page.value = 0
    error.value = null
  } catch (failure) {
    error.value = failure
  }
}
function reset() {
  Object.assign(draft, empty())
  dates.value = undefined
  apply()
}
function paginate(p: number, s: number) {
  page.value = s === size.value ? p : 0
  size.value = s
}
const columns = [
  { title: '发生时间', key: 'occurredAt', width: 180 },
  { title: '安全事件', key: 'action', width: 150 },
  { title: '操作者', key: 'actorId', width: 220 },
  { title: '目标', key: 'subjectId', width: 220 },
  { title: '结果', key: 'successful', width: 90 },
]
</script>
<template>
  <div class="page-heading heading-inline">
    <div>
      <h1>安全审计</h1>
      <p class="muted">身份认证与权限安全事件，不包含全部商品、订单或门店操作。</p>
    </div>
    <Button :loading="query.isFetching.value" @click="query.refetch()">刷新审计</Button>
  </div>
  <Form :model="draft" layout="inline" class="filter-bar order-filters" @finish="apply"
    ><FormItem label="事件"
      ><Select
        v-model:value="draft.action"
        aria-label="安全事件筛选"
        :options="[
          { value: '', label: '全部' },
          ...Object.entries(auditActions).map(([value, label]) => ({ value, label })),
        ]" /></FormItem
    ><FormItem label="结果"
      ><Select
        v-model:value="draft.successful"
        aria-label="结果筛选"
        :options="[
          { value: '', label: '全部' },
          { value: 'true', label: '成功' },
          { value: 'false', label: '失败' },
        ]" /></FormItem
    ><FormItem label="发生日期"
      ><DatePicker.RangePicker
        v-model:value="dates"
        :placeholder="['开始日期', '结束日期']" /></FormItem
    ><FormItem label="操作者"
      ><Input
        v-model:value="draft.actorId"
        aria-label="操作者UUID"
        placeholder="完整UUID" /></FormItem
    ><FormItem label="目标"
      ><Input
        v-model:value="draft.subjectId"
        aria-label="目标UUID"
        placeholder="完整UUID" /></FormItem
    ><Button html-type="submit">查询</Button><Button @click="reset">重置</Button></Form
  >
  <ProblemAlert :error="error" /><ProblemAlert
    :error="query.error.value"
    retry
    @retry="query.refetch()"
  />
  <Table
    :columns="columns"
    :data-source="query.data.value?.items || []"
    row-key="id"
    :pagination="false"
    :loading="query.isFetching.value"
    :scroll="{ x: 860 }"
    :locale="{ emptyText: query.isError.value ? '审计暂不可用' : '没有符合条件的安全事件' }"
    ><template #bodyCell="{ column, record }"
      ><template v-if="column.key === 'occurredAt'">{{ dateTime(record.occurredAt) }}</template
      ><template v-if="column.key === 'action'">{{
        auditActions[record.action as keyof typeof auditActions] || record.action
      }}</template
      ><ResourceId
        v-if="column.key === 'actorId' || column.key === 'subjectId'"
        :value="record[column.key]"
      /><Tag v-if="column.key === 'successful'" :color="record.successful ? 'success' : 'error'">{{
        record.successful ? '成功' : '失败'
      }}</Tag></template
    ></Table
  >
  <PagePagination
    :page="page"
    :size="size"
    :total="query.data.value?.totalElements || 0"
    @change="paginate"
  />
</template>
