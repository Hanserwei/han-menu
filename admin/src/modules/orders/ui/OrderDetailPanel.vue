<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useSessionStore } from '@/modules/auth'
import { Button, Tag, Descriptions, DescriptionsItem, Alert, Skeleton } from 'antdv-next'
import { CloseOutlined, ReloadOutlined } from '@antdv-next/icons'
import type { OrderDetail, OrderAction } from '../api/orders'
import {
  availableActions,
  actions,
  statusView,
  timeline,
  refundLabels,
  cancelLabels,
} from '../model/order-state'
import ResourceId from '@/shared/ui/ResourceId.vue'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
import WriteFeedback from '@/shared/ui/WriteFeedback.vue'
import { money, maskPhone } from '@/shared/lib/display'
import { dateTime } from '@/shared/lib/time'
const props = defineProps<{
  order?: OrderDetail
  loading: boolean
  loadError: unknown
  writeError: unknown
  busy: boolean
  blocked: boolean
  valid: boolean
  drawer?: boolean
}>()
defineEmits<{ close: []; reload: []; act: [action: OrderAction] }>()
const session = useSessionStore()
const showPhone = ref(false)
watch(
  () => props.order?.id,
  () => (showPhone.value = false),
)
const status = computed(() => statusView(props.order?.status))
const permitted = computed(() => availableActions(props.order?.status))
const disabled = computed(
  () => props.busy || props.loading || !!props.loadError || props.blocked || !props.valid,
)
const selections = (values?: Record<string, string>) =>
  Object.entries(values || {})
    .map(([key, value]) => `${key}：${value}`)
    .join(' / ')
</script>
<template>
  <section class="order-detail-panel" :class="{ 'in-drawer': drawer }" aria-label="订单详情">
    <header class="order-detail-header">
      <h2>订单详情</h2>
      <div class="row-actions">
        <Button
          type="text"
          aria-label="刷新订单详情"
          :disabled="busy || loading"
          @click="$emit('reload')"
          ><ReloadOutlined /></Button
        ><Button
          v-if="!drawer"
          type="text"
          aria-label="关闭订单详情"
          :disabled="busy"
          @click="$emit('close')"
          ><CloseOutlined
        /></Button>
      </div>
    </header>
    <div class="order-detail-scroll">
      <ProblemAlert :error="loadError" retry @retry="$emit('reload')" /><WriteFeedback
        :error="writeError"
        :needs-reload="blocked"
        :pending="busy || loading"
        @reload="$emit('reload')"
      />
      <Skeleton v-if="loading && !order" active :paragraph="{ rows: 8 }" />
      <template v-if="order">
        <div class="order-identity">
          <ResourceId :value="order.id" /><Tag :color="status.color">{{ status.label }}</Tag>
        </div>
        <Alert
          v-if="
            order.status === 'CANCELLING' ||
            order.status === 'REFUNDING' ||
            order.lifecycle?.refundStatus === 'PENDING'
          "
          class="form-notice"
          type="info"
          show-icon
          title="正在处理，请等待服务端确认"
          description="付款、关单与退款结果可能稍后更新，请勿重复提交。"
        />
        <ol class="order-timeline">
          <li v-for="item in timeline(order)" :key="item.label">
            <span>{{ item.label }}</span
            ><time>{{ dateTime(item.time) }}</time>
          </li>
        </ol>
        <section class="order-detail-section">
          <h3>商品信息</h3>
          <div v-for="line in order.items" :key="line.id" class="order-line">
            <div class="order-line-title">
              <strong>{{ line.name }}</strong
              ><span>× {{ line.quantity }}</span
              ><span>{{ money(line.subtotal) }}</span>
            </div>
            <p class="muted">
              单价 {{ money(line.unitPrice) }}
              <span v-if="Object.keys(line.selections || {}).length">
                · {{ selections(line.selections) }}</span
              >
            </p>
            <details v-if="line.components?.length">
              <summary>查看套餐组成</summary>
              <div
                v-for="(part, index) in line.components"
                :key="`${part.productId}-${index}`"
                class="meal-snapshot"
              >
                <span>{{ part.name }} × {{ part.quantity }}</span
                ><small>{{ selections(part.selections) }}</small>
              </div>
            </details>
          </div>
          <div class="order-total">
            <span>{{ order.lifecycle?.paidAt ? '实付金额' : '订单金额' }}</span
            ><strong>{{ money(order.total) }}</strong>
          </div>
        </section>
        <section class="order-detail-section">
          <div class="section-title">
            <h3>收货信息</h3>
            <small class="muted">下单时快照</small>
          </div>
          <p>
            {{ order.address?.recipientName || '—' }}
            <span>{{ showPhone ? order.address?.phone : maskPhone(order.address?.phone) }}</span
            ><Button type="link" @click="showPhone = !showPhone">{{
              showPhone ? '隐藏号码' : '显示完整号码'
            }}</Button>
          </p>
          <p class="muted">
            {{ order.address?.province }} {{ order.address?.city }} {{ order.address?.district }}
            {{ order.address?.detail }}
          </p>
        </section>
        <section class="order-detail-section">
          <h3>订单记录</h3>
          <Descriptions :column="1" :colon="false"
            ><DescriptionsItem label="退款状态">{{
              refundLabels[order.lifecycle?.refundStatus || ''] || '未知状态'
            }}</DescriptionsItem
            ><DescriptionsItem v-if="order.lifecycle?.cancelReason" label="取消原因">{{
              cancelLabels[order.lifecycle.cancelReason] || '未知原因'
            }}</DescriptionsItem
            ><DescriptionsItem label="催单次数">{{ order.reminderCount ?? 0 }}</DescriptionsItem
            ><DescriptionsItem v-if="order.lastRemindedAt" label="最近催单">{{
              dateTime(order.lastRemindedAt)
            }}</DescriptionsItem
            ><DescriptionsItem v-if="order.status === 'UNPAID'" label="付款截止">{{
              dateTime(order.expiresAt)
            }}</DescriptionsItem
            ><DescriptionsItem v-if="order.lifecycle?.paymentId" label="支付编号"
              ><RouterLink
                v-if="session.identity?.role === 'ADMIN'"
                :to="`/finance/payments/${order.lifecycle.paymentId}`"
                >查看支付流水</RouterLink
              ><ResourceId v-else :value="order.lifecycle.paymentId" /></DescriptionsItem
            ><DescriptionsItem v-if="order.lifecycle?.refundId" label="退款编号"
              ><RouterLink
                v-if="session.identity?.role === 'ADMIN'"
                :to="`/finance/refunds/${order.lifecycle.refundId}`"
                >查看退款流水</RouterLink
              ><ResourceId v-else :value="order.lifecycle.refundId" /></DescriptionsItem
          ></Descriptions>
        </section>
      </template>
    </div>
    <footer v-if="order" class="order-action-footer">
      <span v-if="!permitted.length" class="muted">当前状态无可用履约操作</span>
      <div v-else class="row-actions">
        <Button
          v-for="(action, index) in permitted"
          :key="action"
          :type="index === 0 ? 'primary' : 'default'"
          :danger="actions[action].danger"
          :disabled="disabled"
          :loading="busy && index === 0"
          @click="$emit('act', action)"
          >{{ actions[action].label }}</Button
        >
      </div>
    </footer>
  </section>
</template>
