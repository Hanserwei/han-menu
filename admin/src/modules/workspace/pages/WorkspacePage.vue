<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Button, Skeleton, Tag } from 'antdv-next'
import { ReloadOutlined, ClockCircleOutlined, ShopOutlined } from '@antdv-next/icons'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
import { businessDate, dateTime } from '@/shared/lib/time'
import { getWorkspace } from '../api/workspace'

const query = useQuery({ queryKey: ['workspace'], queryFn: ({ signal }) => getWorkspace(signal) })
const data = query.data
const metrics = computed(() => [
  { title: '待接单', value: data.value?.awaitingAcceptance },
  { title: '待配送', value: data.value?.accepted },
  { title: '配送中', value: data.value?.delivering },
  { title: '取消处理中', value: data.value?.cancelling, pending: true },
  { title: '退款处理中', value: data.value?.refunding, pending: true },
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
  <Skeleton v-if="query.isPending.value" active :paragraph="{ rows: 8 }" />
  <template v-else-if="data">
    <div class="metrics-band">
      <div v-for="metric in metrics" :key="metric.title" class="metric">
        <span>{{ metric.title }}</span
        ><strong :class="{ pending: metric.pending }">{{ metric.value ?? '—' }}</strong>
      </div>
    </div>
    <div class="workspace-columns">
      <section class="surface-section today-section">
        <div class="section-title">
          <h2>今日经营概况</h2>
          <ClockCircleOutlined />
        </div>
        <p class="muted">掌握今日订单进展，从容安排门店工作。</p>
        <div class="today-metrics">
          <div>
            <span>今日下单</span><strong>{{ data.createdToday ?? '—' }}</strong>
          </div>
          <div>
            <span>今日完成</span><strong>{{ data.completedToday ?? '—' }}</strong>
          </div>
        </div>
        <div class="workspace-note">
          <h3>每一步服务，都有迹可循</h3>
          <p>以上为门店当前订单状态与今日经营摘要。</p>
          <p>取消和退款以服务端确认的最终结果为准。</p>
        </div>
      </section>
      <section class="surface-section store-section">
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
