<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import {
  Button,
  Form,
  FormItem,
  Input,
  Select,
  DatePicker,
  Table,
  Tag,
  Drawer,
  Descriptions,
  DescriptionsItem,
  Skeleton,
} from 'antdv-next'
import dayjs, { type Dayjs } from 'dayjs'
import { financeApi, type Payment, type Refund } from '../api/finance'
import {
  compileFilter,
  emptyFilter,
  readFilter,
  refundFilter,
  statusLabel,
  type Kind,
} from '../model/filters'
import { optionalId } from '@/shared/lib/query-filters'
import { money } from '@/shared/lib/display'
import { dateTime } from '@/shared/lib/time'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
import ResourceId from '@/shared/ui/ResourceId.vue'
import PagePagination from '@/shared/ui/PagePagination.vue'
const route = useRoute(),
  router = useRouter()
const kind = computed<Kind>(() => (route.path.includes('/refunds') ? 'refunds' : 'payments'))
const title = computed(() => (kind.value === 'payments' ? '支付流水' : '退款流水'))
const draft = reactive(emptyFilter()),
  applied = ref(emptyFilter()),
  page = ref(0),
  size = ref(20)
const dates = ref<[Dayjs, Dayjs]>(),
  error = ref<unknown>(null),
  advanced = ref(false)
watch(
  () => [route.query, kind.value],
  () => {
    try {
      const parsed = readFilter(route.query, kind.value)
      applied.value = parsed.filter
      Object.assign(draft, parsed.filter)
      page.value = parsed.page
      size.value = parsed.size
      dates.value =
        draft.fromDate && draft.toDate ? [dayjs(draft.fromDate), dayjs(draft.toDate)] : undefined
      error.value = null
    } catch (failure) {
      error.value = failure
    }
  },
  { immediate: true },
)
const list = useQuery({
  queryKey: computed(() => ['finance-list', kind.value, applied.value, page.value, size.value]),
  enabled: computed(() => !error.value),
  queryFn: ({ signal }) =>
    kind.value === 'payments'
      ? financeApi.payments(
          { ...compileFilter(applied.value, 'payments'), page: page.value, size: size.value },
          signal,
        )
      : financeApi.refunds(
          { ...refundFilter(applied.value), page: page.value, size: size.value },
          signal,
        ),
})
const selected = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''))
const detail = useQuery({
  queryKey: computed(() => ['finance-detail', kind.value, selected.value]),
  enabled: computed(() => !!selected.value),
  queryFn: ({ signal }) => {
    optionalId(selected.value)
    return kind.value === 'payments'
      ? financeApi.payment(selected.value, signal)
      : financeApi.refund(selected.value, signal)
  },
})
const payment = computed(() => detail.data.value as Payment | undefined),
  refund = computed(() => detail.data.value as Refund | undefined)
const timestamp = (value?: string) => (value ? dateTime(value) : '—')
const columns = [
  { title: '流水编号', key: 'id', width: 180 },
  { title: '订单编号', key: 'orderId', width: 180 },
  { title: '金额', key: 'amount', width: 110, align: 'right' as const },
  { title: '状态', key: 'status', width: 120 },
  { title: '创建时间', key: 'createdAt', width: 175 },
  { title: '操作', key: 'actions', width: 100 },
]
async function apply() {
  try {
    const filter = {
      ...draft,
      fromDate: dates.value?.[0].format('YYYY-MM-DD') || '',
      toDate: dates.value?.[1].format('YYYY-MM-DD') || '',
    }
    compileFilter(filter, kind.value)
    await router.replace({
      path: `/finance/${kind.value}`,
      query: { ...filter, page: '0', size: String(size.value) },
    })
    error.value = null
  } catch (failure) {
    error.value = failure
  }
}
async function reset() {
  Object.assign(draft, emptyFilter())
  dates.value = undefined
  await apply()
}
function paginate(p: number, s: number) {
  return router.replace({
    path: route.path,
    query: { ...applied.value, page: String(s === size.value ? p : 0), size: String(s) },
  })
}
function open(id: string) {
  return router.push({ path: `/finance/${kind.value}/${id}`, query: route.query })
}
function close() {
  return router.push({ path: `/finance/${kind.value}`, query: route.query })
}
</script>
<template>
  <div class="page-heading heading-inline">
    <div>
      <h1>{{ title }}</h1>
      <p class="muted">支付宝沙箱持久化流水 · 刷新读取已登记事实，确认结果以服务端为准。</p>
    </div>
    <Button :loading="list.isFetching.value" :disabled="!!error" @click="list.refetch()"
      >刷新列表</Button
    >
  </div>
  <div class="row-actions section-gap">
    <RouterLink to="/finance/payments">支付流水</RouterLink
    ><RouterLink to="/finance/refunds">退款流水</RouterLink
    ><RouterLink to="/finance/reconciliation">资金对账</RouterLink>
  </div>
  <Form :model="draft" layout="inline" class="filter-bar order-filters" @finish="apply">
    <FormItem label="状态"
      ><Select
        v-model:value="draft.status"
        aria-label="流水状态"
        :options="[
          { label: '全部', value: '' },
          ...['PENDING', 'SUCCEEDED', ...(kind === 'payments' ? ['CLOSED'] : [])].map((value) => ({
            value,
            label: statusLabel(value, kind),
          })),
        ]"
    /></FormItem>
    <FormItem label="订单编号"
      ><Input v-model:value="draft.orderId" aria-label="订单编号筛选" placeholder="完整订单UUID"
    /></FormItem>
    <FormItem label="创建日期"
      ><DatePicker.RangePicker v-model:value="dates" :placeholder="['开始日期', '结束日期']"
    /></FormItem>
    <Button html-type="submit">查询</Button><Button @click="reset">重置</Button
    ><Button type="link" @click="advanced = !advanced">更多筛选</Button>
    <div v-if="advanced" class="advanced-filters">
      <FormItem label="顾客编号"
        ><Input v-model:value="draft.customerId" aria-label="顾客编号筛选" /></FormItem
      ><FormItem label="原支付编号"
        ><Input v-model:value="draft.paymentId" aria-label="原支付编号筛选"
      /></FormItem>
    </div>
  </Form>
  <ProblemAlert :error="error" /><ProblemAlert
    :error="list.error.value"
    retry
    @retry="list.refetch()"
  />
  <Table
    :columns="columns"
    :data-source="error ? [] : list.data.value?.items || []"
    row-key="id"
    :pagination="false"
    :loading="list.isFetching.value"
    :scroll="{ x: 900 }"
    :locale="{
      emptyText: error
        ? '请修正筛选条件'
        : list.isError.value
          ? '流水暂不可用'
          : '没有符合条件的流水',
    }"
  >
    <template #bodyCell="{ column, record }"
      ><ResourceId
        v-if="column.key === 'id' || column.key === 'orderId'"
        :value="record[column.key]"
      /><template v-if="column.key === 'amount'">{{ money(record.amount) }}</template
      ><Tag
        v-if="column.key === 'status'"
        :color="
          record.status === 'SUCCEEDED'
            ? 'success'
            : record.status === 'PENDING'
              ? 'warning'
              : 'default'
        "
        >{{ statusLabel(record.status, kind) }}</Tag
      ><template v-if="column.key === 'createdAt'">{{ timestamp(record.createdAt) }}</template
      ><Button v-if="column.key === 'actions'" type="link" @click="open(record.id!)"
        >查看详情</Button
      ></template
    >
  </Table>
  <PagePagination
    :page="page"
    :size="size"
    :total="error ? 0 : list.data.value?.totalElements || 0"
    :disabled="!!error"
    @change="paginate"
  />
  <Drawer
    :title="kind === 'payments' ? '支付详情' : '退款详情'"
    :open="!!selected"
    :size="580"
    @close="close"
  >
    <ProblemAlert :error="detail.error.value" retry @retry="detail.refetch()" /><Skeleton
      v-if="detail.isPending.value"
      active
    />
    <template v-else-if="detail.data.value"
      ><Button :loading="detail.isFetching.value" @click="detail.refetch()">刷新详情</Button
      ><Descriptions :column="1" bordered class="section-gap">
        <DescriptionsItem label="流水编号"
          ><ResourceId :value="detail.data.value.id"
        /></DescriptionsItem>
        <DescriptionsItem label="订单编号"
          ><RouterLink :to="`/orders/${detail.data.value.orderId}`">{{
            detail.data.value.orderId
          }}</RouterLink></DescriptionsItem
        >
        <DescriptionsItem label="顾客编号"
          ><RouterLink :to="`/customers/${detail.data.value.customerId}`">{{
            detail.data.value.customerId
          }}</RouterLink></DescriptionsItem
        >
        <DescriptionsItem v-if="kind === 'refunds'" label="原支付编号"
          ><RouterLink :to="`/finance/payments/${refund?.paymentId}`">{{
            refund?.paymentId
          }}</RouterLink></DescriptionsItem
        >
        <DescriptionsItem label="金额"
          >{{ money(detail.data.value.amount) }} {{ detail.data.value.currency }}</DescriptionsItem
        >
        <DescriptionsItem label="状态">{{
          statusLabel(detail.data.value.status, kind)
        }}</DescriptionsItem>
        <DescriptionsItem label="渠道交易号">{{
          detail.data.value.tradeNo || '—'
        }}</DescriptionsItem>
        <DescriptionsItem label="创建时间">{{
          timestamp(detail.data.value.createdAt)
        }}</DescriptionsItem>
        <DescriptionsItem :label="kind === 'payments' ? '付款时间' : '退款确认时间'">{{
          timestamp(kind === 'payments' ? payment?.paidAt : refund?.confirmedAt)
        }}</DescriptionsItem>
        <DescriptionsItem v-if="kind === 'payments'" label="过期时间">{{
          timestamp(payment?.expiresAt)
        }}</DescriptionsItem>
        <DescriptionsItem v-if="kind === 'payments'" label="关单已请求">{{
          payment?.closeRequested ? '是' : '否'
        }}</DescriptionsItem>
        <DescriptionsItem label="下次处理时间">{{
          timestamp(detail.data.value.nextAttemptAt)
        }}</DescriptionsItem>
        <DescriptionsItem label="最近失败分类">{{
          detail.data.value.lastFailure || '—'
        }}</DescriptionsItem>
        <DescriptionsItem label="资源版本">{{
          detail.data.value.version ?? '—'
        }}</DescriptionsItem> </Descriptions
      ><RouterLink
        v-if="kind === 'payments'"
        :to="{ path: '/finance/refunds', query: { paymentId: selected } }"
        >查询关联退款</RouterLink
      ></template
    >
  </Drawer>
</template>
