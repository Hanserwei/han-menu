<script setup lang="ts">
import { computed, reactive, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { Form, FormItem, Input, Select, DatePicker, Button, Table, Tag, Drawer } from 'antdv-next'
import dayjs, { type Dayjs } from 'dayjs'
import { ordersApi } from '../api/orders'
import { statuses, statusView } from '../model/order-state'
import { compileFilter, readFilter, emptyFilter, filterQuery } from '../model/order-filter'
import { useOrderDetail } from '../model/use-order-detail'
import OrderDetailPanel from '../ui/OrderDetailPanel.vue'
import ResourceId from '@/shared/ui/ResourceId.vue'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
import PagePagination from '@/shared/ui/PagePagination.vue'
import { money } from '@/shared/lib/display'
import { dateTime } from '@/shared/lib/time'
import { useEditorGuard } from '@/shared/model/use-editor-guard'
import { useVisiblePoll } from '@/shared/lib/use-visible-poll'
const route = useRoute(),
  router = useRouter()
const draft = reactive(emptyFilter()),
  applied = ref(emptyFilter()),
  page = ref(0),
  size = ref(20),
  filterError = ref<unknown>(null),
  advanced = ref(false),
  dates = ref<[Dayjs, Dayjs]>()
const filterRevision = ref(0),
  scope = crypto.randomUUID()
// 电话留在当前页面内存，路由可恢复其余筛选；查询键不包含个人资料。
let previousQuery = ''
watch(
  () => route.query,
  (query) => {
    const fingerprint = JSON.stringify(query)
    if (fingerprint === previousQuery) return
    previousQuery = fingerprint
    try {
      const parsed = readFilter(query)
      parsed.draft.phone = applied.value.phone
      Object.assign(draft, parsed.draft)
      applied.value = { ...parsed.draft }
      page.value = parsed.page
      size.value = parsed.size
      dates.value =
        parsed.draft.fromDate && parsed.draft.toDate
          ? [dayjs(parsed.draft.fromDate), dayjs(parsed.draft.toDate)]
          : undefined
      filterError.value = null
      filterRevision.value++
    } catch (error) {
      filterError.value = error
    }
  },
  { immediate: true },
)
const query = useQuery({
  queryKey: computed(() => ['orders-list', scope, filterRevision.value, page.value, size.value]),
  enabled: computed(() => !filterError.value),
  queryFn: ({ signal }) =>
    ordersApi.list(compileFilter(applied.value), page.value, size.value, signal),
  gcTime: 0,
})
const selectedId = computed(() => String(route.params.id || ''))
const detail = useOrderDetail(selectedId),
  { order, loading, loadError, writeError, busy, blocked, valid } = detail
useEditorGuard(
  computed(() => false),
  busy,
)
const width = ref(window.innerWidth),
  wide = computed(() => width.value >= 1366)
function resize() {
  width.value = window.innerWidth
}
onMounted(() => window.addEventListener('resize', resize))
onBeforeUnmount(() => window.removeEventListener('resize', resize))
async function apply() {
  try {
    draft.fromDate = dates.value?.[0]?.format('YYYY-MM-DD') || ''
    draft.toDate = dates.value?.[1]?.format('YYYY-MM-DD') || ''
    const normalized = {
      ...draft,
      orderId: draft.orderId.trim(),
      customerId: draft.customerId.trim(),
      phone: draft.phone.trim(),
    }
    compileFilter(normalized)
    applied.value = normalized
    page.value = 0
    filterError.value = null
    filterRevision.value++
    await router.replace({ path: route.path, query: filterQuery(applied.value, 0, size.value) })
  } catch (error) {
    filterError.value = error
  }
}
async function reset() {
  Object.assign(draft, emptyFilter())
  dates.value = undefined
  await apply()
}
async function changeStatus(status: string) {
  draft.status = status
  await apply()
}
async function paginate(p: number, s: number) {
  page.value = s === size.value ? p : 0
  size.value = s
  await router.replace({
    path: route.path,
    query: filterQuery(applied.value, page.value, size.value),
  })
}
async function open(id: string) {
  if (!busy.value)
    await router.push({
      path: `/orders/${id}`,
      query: filterQuery(applied.value, page.value, size.value),
    })
}
async function close() {
  if (!busy.value)
    await router.push({
      path: '/orders',
      query: filterQuery(applied.value, page.value, size.value),
    })
}
const columns = computed(() => [
  { title: '订单编号', key: 'id', width: 180 },
  { title: '下单时间', key: 'createdAt', width: wide.value && selectedId.value ? 110 : 175 },
  { title: '金额', key: 'total', width: 110 },
  { title: '状态', key: 'status', width: 110 },
  { title: '操作', key: 'actions', width: 100 },
])
const statusOptions = Object.entries(statuses).map(([value, item]) => ({
  label: item.label,
  value,
}))
useVisiblePoll(
  () => query.refetch(),
  () => !busy.value && !query.isFetching.value && !filterError.value,
)
</script>
<template>
  <div class="orders-page">
    <div class="page-heading heading-inline">
      <div>
        <h1>订单中心</h1>
        <p class="muted">查看订单，处理接单与配送。</p>
      </div>
      <Button
        :loading="query.isFetching.value"
        :disabled="busy || !!filterError"
        @click="query.refetch()"
        >刷新列表</Button
      >
    </div>
    <div class="order-status-tabs" role="group" aria-label="订单状态">
      <Button
        v-for="tab in [
          { label: '全部', value: '' },
          { label: '待接单', value: 'PAID' },
          { label: '待配送', value: 'ACCEPTED' },
          { label: '配送中', value: 'DELIVERING' },
        ]"
        :key="tab.value"
        :type="applied.status === tab.value ? 'primary' : 'text'"
        :disabled="busy"
        @click="changeStatus(tab.value)"
        >{{ tab.label }}</Button
      ><Select
        :value="applied.status || undefined"
        aria-label="全部订单状态"
        placeholder="更多状态"
        :options="statusOptions"
        allow-clear
        :disabled="busy"
        @change="(value) => changeStatus((value as string) || '')"
      />
    </div>
    <Form
      :model="draft"
      layout="inline"
      class="filter-bar order-filters"
      :disabled="busy"
      @finish="apply"
      ><FormItem label="订单编号"
        ><Input
          v-model:value="draft.orderId"
          aria-label="订单编号筛选"
          placeholder="请输入完整订单UUID"
          :maxlength="36" /></FormItem
      ><FormItem label="创建日期"
        ><DatePicker.RangePicker
          v-model:value="dates"
          :placeholder="['开始日期', '结束日期']" /></FormItem
      ><Button html-type="submit">查询</Button><Button @click="reset">重置</Button
      ><Button type="link" @click="advanced = !advanced">{{
        advanced ? '收起筛选' : '更多筛选'
      }}</Button>
      <div v-if="advanced" class="advanced-filters">
        <FormItem label="顾客编号"
          ><Input
            v-model:value="draft.customerId"
            aria-label="顾客编号筛选"
            placeholder="完整顾客UUID"
            :maxlength="36" /></FormItem
        ><FormItem label="收货手机号"
          ><Input
            v-model:value="draft.phone"
            aria-label="收货手机号筛选"
            placeholder="下单时完整收货手机号"
            :maxlength="16"
        /></FormItem></div
    ></Form>
    <ProblemAlert :error="filterError" /><ProblemAlert
      :error="query.error.value"
      retry
      @retry="query.refetch()"
    />
    <div class="order-layout" :class="{ 'has-detail': selectedId && wide }">
      <section class="order-list-region" aria-label="订单列表">
        <Table
          :columns="columns"
          :data-source="filterError ? [] : query.data.value?.items || []"
          row-key="id"
          :pagination="false"
          :loading="query.isFetching.value"
          :row-class-name="(record) => (record.id === selectedId ? 'selected-order' : '')"
          :scroll="{ x: wide && selectedId ? 610 : 760 }"
          :locale="{
            emptyText: filterError
              ? '请修正筛选条件'
              : query.isError.value
                ? '订单列表暂不可用'
                : '没有符合条件的订单',
          }"
          ><template #bodyCell="{ column, record }"
            ><ResourceId v-if="column.key === 'id'" :value="record.id" /><template
              v-if="column.key === 'createdAt'"
              >{{ dateTime(record.createdAt) }}</template
            ><template v-if="column.key === 'total'">{{ money(record.total) }}</template
            ><Tag v-if="column.key === 'status'" :color="statusView(record.status).color">{{
              statusView(record.status).label
            }}</Tag
            ><Button
              v-if="column.key === 'actions'"
              type="link"
              :disabled="busy"
              @click="open(record.id!)"
              >查看详情</Button
            ></template
          ></Table
        ><PagePagination
          :page="page"
          :size="size"
          :total="filterError ? 0 : query.data.value?.totalElements || 0"
          :disabled="busy || !!filterError"
          @change="paginate"
        />
      </section>
      <OrderDetailPanel
        v-if="selectedId && wide"
        :key="selectedId"
        :order="order"
        :loading="loading"
        :load-error="loadError"
        :write-error="writeError"
        :busy="busy"
        :blocked="blocked"
        :valid="valid"
        @close="close"
        @reload="detail.load(true)"
        @act="detail.act"
      />
    </div>
    <Drawer
      title="订单详情"
      :open="!!selectedId && !wide"
      :size="Math.min(560, width - 32)"
      :closable="!busy"
      :mask-closable="!busy"
      :keyboard="!busy"
      @close="close"
      ><OrderDetailPanel
        v-if="selectedId && !wide"
        :key="selectedId"
        drawer
        :order="order"
        :loading="loading"
        :load-error="loadError"
        :write-error="writeError"
        :busy="busy"
        :blocked="blocked"
        :valid="valid"
        @close="close"
        @reload="detail.load(true)"
        @act="detail.act"
    /></Drawer>
  </div>
</template>
