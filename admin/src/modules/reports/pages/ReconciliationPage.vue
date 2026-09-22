<script setup lang="ts">
import { ref, computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Button, Skeleton, Table, Alert } from 'antdv-next'
import { reportsApi } from '../api/reports'
import { recentDays } from '../model/reporting'
import PeriodFilter from '../ui/PeriodFilter.vue'
import ProjectionStamp from '../ui/ProjectionStamp.vue'
import { money } from '@/shared/lib/display'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
const range = ref(recentDays(7))
const query = useQuery({
  queryKey: computed(() => ['reports-reconciliation', range.value]),
  queryFn: ({ signal }) => reportsApi.reconciliation(range.value, signal),
})
const metrics = computed(() => [
  { title: '实际收款', value: money(query.data.value?.receivedAmount) },
  { title: '已确认退款', value: money(query.data.value?.refundedAmount) },
  { title: '净收款', value: money(query.data.value?.netReceivedAmount) },
])
const differences = computed(() => [
  {
    key: 'missingOrders',
    title: '收款缺少订单',
    scope: '区间内收款',
    count: query.data.value?.missingOrders,
  },
  {
    key: 'paymentMismatches',
    title: '收款金额或引用不符',
    scope: '区间内收款',
    count: query.data.value?.paymentMismatches,
  },
  {
    key: 'missingPayments',
    title: '退款缺少原支付',
    scope: '区间内退款',
    count: query.data.value?.missingPayments,
  },
  {
    key: 'refundMismatches',
    title: '退款订单或金额不符',
    scope: '区间内退款',
    count: query.data.value?.refundMismatches,
  },
  {
    key: 'ordersMissingReceipts',
    title: '已付款订单缺少收款事实',
    scope: '全店当前',
    count: query.data.value?.ordersMissingReceipts,
  },
  {
    key: 'ordersMissingRefunds',
    title: '已退款订单缺少退款事实',
    scope: '全店当前',
    count: query.data.value?.ordersMissingRefunds,
  },
  {
    key: 'pendingRefunds',
    title: '待退款订单',
    scope: '全店当前（不按日期截断）',
    count: query.data.value?.pendingRefunds,
  },
])
</script>
<template>
  <div class="page-heading heading-inline">
    <div>
      <h1>资金对账</h1>
      <p class="muted">收退款按确认日统计，与按完成日统计的营业额分别核对。</p>
    </div>
    <Button :loading="query.isFetching.value" @click="query.refetch()">刷新对账</Button>
  </div>
  <PeriodFilter :value="range" @change="range = $event" /><ProblemAlert
    :error="query.error.value"
    retry
    @retry="query.refetch()"
  /><Skeleton v-if="query.isPending.value" active />
  <template v-else-if="query.data.value"
    ><div class="metrics-band report-metrics">
      <div v-for="item in metrics" :key="item.title" class="metric">
        <span>{{ item.title }}</span
        ><strong>{{ item.value }}</strong>
      </div>
    </div>
    <Alert
      type="info"
      show-icon
      title="差异计数可能重叠，事件同步延迟也可能形成暂时差异"
      description="净收款为实际收款减已确认退款，跨日退款可能使当日净收款为负。投影重建只更新统计，不修改真实订单或资金状态。"
      class="section-gap" /><Table
      class="section-gap"
      :columns="[
        { title: '核对项', dataIndex: 'title' },
        { title: '范围', dataIndex: 'scope' },
        { title: '数量', dataIndex: 'count', width: 120 },
      ]"
      :data-source="differences"
      row-key="key"
      :pagination="false" /><ProjectionStamp :value="query.data.value.projection"
  /></template>
  <div class="row-actions section-gap">
    <RouterLink to="/finance/payments">查询支付流水</RouterLink
    ><RouterLink to="/finance/refunds">查询退款流水</RouterLink
    ><RouterLink to="/settings/maintenance">统计投影维护</RouterLink>
  </div>
</template>
