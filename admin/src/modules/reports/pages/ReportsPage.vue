<script setup lang="ts">
import { ref, computed, defineAsyncComponent, onBeforeUnmount } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Button, Select, Table, Alert, Skeleton } from 'antdv-next'
import { reportsApi } from '../api/reports'
import { recentDays, sameProjection } from '../model/reporting'
import PeriodFilter from '../ui/PeriodFilter.vue'
import ProjectionStamp from '../ui/ProjectionStamp.vue'
import TrendUnavailable from '../ui/TrendUnavailable.vue'
import { money } from '@/shared/lib/display'
import { sessionBridge } from '@/shared/api/session-bridge'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
const TrendChart = defineAsyncComponent(() =>
  import('../ui/TrendChart.vue').then((module) => module.default).catch(() => TrendUnavailable),
)
const range = ref(recentDays(7)),
  limit = ref(10),
  metric = ref<'turnover' | 'submittedOrders' | 'newCustomers'>('turnover')
const operations = useQuery({
  queryKey: computed(() => ['reports-operations', range.value]),
  queryFn: ({ signal }) => reportsApi.operations(range.value, signal),
})
const sales = useQuery({
  queryKey: computed(() => ['reports-sales', range.value, limit.value]),
  queryFn: ({ signal }) => reportsApi.sales(range.value, limit.value, signal),
})
const summary = computed(() => operations.data.value?.summary)
const label = computed(
  () =>
    ({ turnover: '营业额', submittedOrders: '创建订单', newCustomers: '新增顾客' })[metric.value],
)
const metrics = computed(() => [
  { label: '营业额', value: money(summary.value?.turnover), help: '按订单完成日' },
  {
    label: '完成订单',
    value: summary.value?.completedOrders ?? '—',
    help: '包含以前日期创建的订单',
  },
  {
    label: '客单价',
    value: money(summary.value?.averageOrderValue),
    help: '已完成营业额 / 完成订单数',
  },
  {
    label: '新增顾客',
    value: summary.value?.newCustomers ?? '—',
    help: '历史注册事实，含已停用账号',
  },
])
const exporting = ref(false),
  exportError = ref<unknown>(null),
  controller = new AbortController()
let objectUrl: string | undefined
onBeforeUnmount(() => {
  controller.abort()
  if (objectUrl) URL.revokeObjectURL(objectUrl)
})
/** 下载期间固定请求日期，会话切换或离开页面后不得触发迟到下载。 */
async function download() {
  if (exporting.value) return
  exporting.value = true
  exportError.value = null
  const period = { ...range.value },
    epoch = sessionBridge.snapshot().generation
  try {
    const blob = await reportsApi.export(period, controller.signal)
    if (controller.signal.aborted || !sessionBridge.isCurrent(epoch)) return
    if (objectUrl) URL.revokeObjectURL(objectUrl)
    objectUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = `经营报表_${period.from}_${period.to}.xlsx`
    document.body.append(link)
    link.click()
    link.remove()
  } catch (failure) {
    if (!controller.signal.aborted && sessionBridge.isCurrent(epoch)) exportError.value = failure
  } finally {
    exporting.value = false
  }
}
function refresh() {
  void operations.refetch()
  void sales.refetch()
}
const dayColumns = [
  { title: '经营日期', dataIndex: 'date', width: 130 },
  { title: '营业额', key: 'turnover', width: 115 },
  { title: '创建订单', dataIndex: 'submittedOrders', width: 100 },
  { title: '完成订单', dataIndex: 'completedOrders', width: 100 },
  { title: '群组完成', dataIndex: 'completedCohort', width: 100 },
  { title: '群组取消', dataIndex: 'cancelledCohort', width: 100 },
  { title: '群组完成率', key: 'completionRatePercent', width: 115 },
  { title: '实收款', key: 'receivedAmount', width: 115 },
  { title: '退款', key: 'refundedAmount', width: 115 },
  { title: '净收款', key: 'netReceivedAmount', width: 115 },
  { title: '新增顾客', dataIndex: 'newCustomers', width: 100 },
  { title: '累计顾客', dataIndex: 'cumulativeCustomers', width: 100 },
]
</script>
<template>
  <div class="page-heading heading-inline">
    <div>
      <h1>经营分析</h1>
      <p class="muted">以订单完成日期统计营业额，经营时区为北京时间。</p>
    </div>
    <div class="row-actions">
      <Button :loading="operations.isFetching.value || sales.isFetching.value" @click="refresh"
        >刷新报表</Button
      ><Button type="primary" :loading="exporting" @click="download">下载 XLSX 报表</Button>
    </div>
  </div>
  <PeriodFilter :value="range" :disabled="exporting" @change="range = $event" /><ProblemAlert
    :error="exportError"
  />
  <ProblemAlert :error="operations.error.value" retry @retry="operations.refetch()" />
  <Skeleton v-if="operations.isPending.value" active :paragraph="{ rows: 6 }" />
  <template v-else-if="operations.data.value">
    <div class="metrics-band report-metrics">
      <div v-for="item in metrics" :key="item.label" class="metric">
        <span>{{ item.label }}</span
        ><strong>{{ item.value }}</strong
        ><small class="muted">{{ item.help }}</small>
      </div>
    </div>
    <section class="surface-section section-gap">
      <div class="section-title">
        <h2>{{ label }}趋势</h2>
        <Select
          v-model:value="metric"
          aria-label="趋势指标"
          style="width: 140px"
          :options="[
            { label: '营业额', value: 'turnover' },
            { label: '创建订单', value: 'submittedOrders' },
            { label: '新增顾客', value: 'newCustomers' },
          ]"
        />
      </div>
      <TrendChart :days="operations.data.value.days || []" :metric="metric" :label="label" />
      <p class="muted">营业额按完成日；创建订单按下单日；新增顾客按注册日。今日数据尚未完结。</p>
      <ProjectionStamp :value="operations.data.value.projection" />
    </section>
  </template>
  <section class="surface-section section-gap">
    <div class="section-title">
      <h2>商品销量</h2>
      <Select
        v-model:value="limit"
        aria-label="销量排行条数"
        style="width: 120px"
        :options="[10, 20, 50, 100].map((value) => ({ value, label: `前${value}名` }))"
      />
    </div>
    <Alert
      v-if="
        operations.data.value &&
        sales.data.value &&
        !sameProjection(operations.data.value.projection, sales.data.value.projection)
      "
      type="info"
      show-icon
      title="数据正在同步"
      description="趋势与销量来自不同投影快照，可刷新后继续核对。"
    />
    <ProblemAlert :error="sales.error.value" retry @retry="sales.refetch()" />
    <Table
      :columns="[
        { title: '商品', dataIndex: 'name' },
        { title: '类型', key: 'kind', width: 100 },
        { title: '销量', dataIndex: 'quantity', width: 100, align: 'right' },
        { title: '成交金额', key: 'amount', width: 140, align: 'right' },
      ]"
      :data-source="sales.data.value?.items || []"
      row-key="productId"
      :pagination="false"
      :loading="sales.isFetching.value"
      :locale="{ emptyText: sales.isError.value ? '销量暂不可用' : '区间内暂无已完成商品销量' }"
      ><template #bodyCell="{ column, record }"
        ><template v-if="column.key === 'kind'">{{
          record.kind === 'SET_MEAL' ? '套餐' : '菜品'
        }}</template
        ><template v-if="column.key === 'amount'">{{ money(record.amount) }}</template></template
      ></Table
    >
    <p class="muted">仅统计已完成订单的成交商品；套餐按套餐计件，不重复计算组成菜品。</p>
    <ProjectionStamp v-if="sales.data.value" :value="sales.data.value.projection" />
  </section>
  <section v-if="operations.data.value" class="surface-section section-gap">
    <h2>订单与顾客汇总</h2>
    <div class="report-facts">
      <span>创建订单 {{ summary?.submittedOrders ?? '—' }}</span
      ><span>创建群组已完成 {{ summary?.completedCohort ?? '—' }}</span
      ><span>创建群组已取消 {{ summary?.cancelledCohort ?? '—' }}</span
      ><span>群组完成率 {{ summary?.completionRatePercent?.toFixed(2) ?? '—' }}%</span
      ><span>期末累计顾客 {{ summary?.totalCustomers ?? '—' }}</span>
    </div>
    <p class="muted">完成率按同一创建日期群组计算；累计顾客包含区间之前注册的顾客。</p>
    <details>
      <summary>查看经营日账</summary>
      <Table
        :columns="dayColumns"
        :data-source="operations.data.value.days || []"
        row-key="date"
        :pagination="false"
        :scroll="{ x: 1400, y: 440 }"
        ><template #bodyCell="{ column, record }"
          ><template v-if="column.key === 'completionRatePercent'"
            >{{ record.completionRatePercent?.toFixed(2) ?? '—' }}%</template
          ><template v-else-if="column.key">{{
            money(
              record[
                column.key as 'turnover' | 'receivedAmount' | 'refundedAmount' | 'netReceivedAmount'
              ],
            )
          }}</template></template
        ></Table
      >
    </details>
  </section>
  <div class="row-actions section-gap">
    <RouterLink to="/finance/reconciliation">查看资金对账</RouterLink
    ><RouterLink to="/settings/maintenance">统计投影维护</RouterLink>
  </div>
</template>
