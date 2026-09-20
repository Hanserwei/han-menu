<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { AwaitingOrders } from '@/modules/orders'
import { useVisiblePoll } from '@/shared/lib/use-visible-poll'
import { useQuery } from '@tanstack/vue-query'
import { Button, Skeleton, Tag } from 'antdv-next'
import { ReloadOutlined, ShopOutlined } from '@antdv-next/icons'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
import { businessDate, dateTime } from '@/shared/lib/time'
import { getWorkspace } from '../api/workspace'

const query = useQuery({ queryKey: ['workspace'], queryFn: ({ signal }) => getWorkspace(signal) })
const router = useRouter()
useVisiblePoll(
  () => query.refetch(),
  () => !query.isFetching.value,
)
const data = query.data
const metrics = computed(() => [
  { title: '待接单', status: 'PAID', value: data.value?.awaitingAcceptance },
  { title: '待配送', status: 'ACCEPTED', value: data.value?.accepted },
  { title: '配送中', status: 'DELIVERING', value: data.value?.delivering },
  { title: '取消处理中', status: 'CANCELLING', value: data.value?.cancelling, pending: true },
  { title: '退款处理中', status: 'REFUNDING', value: data.value?.refunding, pending: true },
])
</script>
<template>
  <div class="page-heading heading-inline">
    <div>
      <h1>工作台</h1>
      <p class="muted">{{ businessDate(data?.businessDate) }}</p>
    </div>
    <Button :loading="query.isFetching.value" @click="query.refetch()"
      ><ReloadOutlined />刷新概况</Button
    >
  </div>
  <ProblemAlert :error="query.error.value" retry @retry="query.refetch()" />
  <Button v-if="query.isError.value" @click="router.push('/orders')">前往订单中心</Button>
  <Skeleton v-if="query.isPending.value" active :paragraph="{ rows: 8 }" />
  <template v-else-if="data">
    <div class="metrics-band">
      <button
        v-for="metric in metrics"
        :key="metric.title"
        class="metric metric-link"
        @click="router.push({ path: '/orders', query: { status: metric.status } })"
      >
        <span>{{ metric.title }}</span
        ><strong :class="{ pending: metric.pending }">{{ metric.value ?? '—' }}</strong>
      </button>
    </div>
    <div class="workspace-columns">
      <AwaitingOrders />
      <section class="surface-section store-section">
        <h2>今日概况</h2>
        <div class="today-metrics">
          <div>
            <span>今日下单</span><strong>{{ data.createdToday ?? '—' }}</strong>
          </div>
          <div>
            <span>今日完成</span><strong>{{ data.completedToday ?? '—' }}</strong>
          </div>
        </div>
        <div class="section-title">
          <h2>门店与商品</h2>
          <ShopOutlined />
        </div>
        <div class="summary-line">
          <span>营业状态</span
          ><Tag :color="data.shopStatus === 'OPEN' ? 'success' : 'default'">{{
            data.shopStatus === 'OPEN' ? '营业中' : '已打烊'
          }}</Tag>
        </div>
        <div class="summary-line">
          <strong>菜品</strong
          ><span
            >在售 {{ data.dishesOnSale ?? '—' }} <i /> 下架 {{ data.dishesOffSale ?? '—' }}</span
          >
        </div>
        <div class="summary-line">
          <strong>套餐</strong
          ><span>在售 {{ data.mealsOnSale ?? '—' }} <i /> 下架 {{ data.mealsOffSale ?? '—' }}</span>
        </div>
        <p class="muted store-caption">清晰的商品与营业状态，<br />是每一份好服务的开始。</p>
      </section>
    </div>
    <p class="data-timestamp">
      投影更新于 {{ dateTime(data.projection?.updatedAt) }} · 经营时间：北京时间
    </p>
  </template>
</template>
