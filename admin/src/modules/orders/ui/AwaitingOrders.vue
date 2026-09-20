<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { Button, Table } from 'antdv-next'
import { ordersApi } from '../api/orders'
import ResourceId from '@/shared/ui/ResourceId.vue'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
import { dateTime } from '@/shared/lib/time'
import { money } from '@/shared/lib/display'
import { useVisiblePoll } from '@/shared/lib/use-visible-poll'
const router = useRouter()
/** 待接单列表直接读取权威订单，不能用工作台投影数量推算列表或限制为今天。 */
const query = useQuery({
  queryKey: ['orders-list', 'awaiting'],
  queryFn: ({ signal }) => ordersApi.list({ status: 'PAID' }, 0, 5, signal),
})
const columns = [
  { title: '订单编号', key: 'id', width: 165 },
  { title: '下单时间', key: 'createdAt', width: 170 },
  { title: '订单金额', key: 'total', width: 100 },
  { title: '操作', key: 'actions', width: 100 },
]
useVisiblePoll(
  () => query.refetch(),
  () => !query.isFetching.value,
)
</script>
<template>
  <section class="surface-section awaiting-orders">
    <div class="section-title">
      <div>
        <h2>待接订单</h2>
        <p class="muted">已付款，等待商家接单。</p>
      </div>
      <Button type="primary" @click="router.push('/orders?status=PAID')">去处理订单</Button>
    </div>
    <ProblemAlert :error="query.error.value" retry @retry="query.refetch()" /><Table
      :columns="columns"
      :data-source="query.data.value?.items || []"
      row-key="id"
      :pagination="false"
      :loading="query.isFetching.value"
      :scroll="{ x: 535 }"
      :locale="{ emptyText: query.isError.value ? '待接订单暂不可用' : '当前没有待接订单' }"
      ><template #bodyCell="{ column, record }"
        ><ResourceId v-if="column.key === 'id'" :value="record.id" /><template
          v-if="column.key === 'createdAt'"
          >{{ dateTime(record.createdAt) }}</template
        ><template v-if="column.key === 'total'">{{ money(record.total) }}</template
        ><Button
          v-if="column.key === 'actions'"
          type="link"
          @click="router.push({ path: `/orders/${record.id}`, query: { status: 'PAID' } })"
          >查看详情</Button
        ></template
      ></Table
    >
  </section>
</template>
