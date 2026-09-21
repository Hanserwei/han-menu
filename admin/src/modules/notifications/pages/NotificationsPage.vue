<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { App, Button, Table, Tag, Alert, Skeleton } from 'antdv-next'
import { ReloadOutlined } from '@antdv-next/icons'
import { useNotificationsStore } from '../model/notifications.store'
import { notificationApi } from '../api/notifications'
import { connectionView, noticeLabel } from '../model/connection-view'
import type { FeedPage } from '../model/protocol'
import { ApiProblem } from '@/shared/api/problem'
import { sessionBridge } from '@/shared/api/session-bridge'
import { dateTime } from '@/shared/lib/time'
import ResourceId from '@/shared/ui/ResourceId.vue'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
import { useEditorGuard } from '@/shared/model/use-editor-guard'

const store = useNotificationsStore(),
  router = useRouter(),
  { modal, message } = App.useApp()
const mode = ref<'unread' | 'history'>('unread')
const page = ref<FeedPage>(),
  loading = ref(false),
  confirming = ref(false),
  error = ref<unknown>(null),
  outcomeUnknown = ref(false)
const historyAfter = ref(0),
  historyBack = ref<number[]>([])
let dismissConfirmation: (() => void) | undefined
let generation = 0,
  controller: AbortController | undefined
const busy = computed(() => confirming.value || store.state.pendingAck)
useEditorGuard(
  computed(() => false),
  busy,
)
const connection = computed(() => connectionView(store.state))
const newPageAvailable = computed(
  () =>
    mode.value === 'unread' &&
    !!page.value &&
    !page.value.hasMore &&
    store.state.cursor > page.value.nextCursor,
)
const stale = computed(
  () =>
    mode.value === 'unread' && !!page.value && page.value.after !== store.state.receipt?.sequence,
)
const canAcknowledge = computed(
  () =>
    mode.value === 'unread' &&
    !!page.value?.items.length &&
    !stale.value &&
    !busy.value &&
    !loading.value &&
    !error.value &&
    !outcomeUnknown.value &&
    page.value.nextCursor <= store.state.cursor,
)
const columns = [
  { title: '通知类型', key: 'type', width: 130 },
  { title: '订单编号', key: 'order', width: 215 },
  { title: '发生时间', key: 'time', width: 180 },
  { title: '阅读状态', key: 'read', width: 120 },
  { title: '操作', key: 'actions', width: 110 },
]
/** 页面快照与后台拉取独立；新帧不改变当前确认范围，翻页只使用上一完整响应的游标。 */
async function load() {
  if (busy.value) return
  const epoch = ++generation,
    session = sessionBridge.snapshot().generation
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  error.value = null
  try {
    if (mode.value === 'unread') await store.refresh()
    if (epoch !== generation || !sessionBridge.isCurrent(session)) return
    if (mode.value === 'unread' && (!store.state.receipt || store.state.error))
      throw store.state.error || new ApiProblem(503, '阅读进度尚未就绪')
    const after = mode.value === 'unread' ? store.state.receipt!.sequence : historyAfter.value
    const result = await notificationApi.page(after, controller.signal)
    if (epoch !== generation || !sessionBridge.isCurrent(session)) return
    page.value = result
    outcomeUnknown.value = false
  } catch (failure) {
    if (
      epoch === generation &&
      sessionBridge.isCurrent(session) &&
      !(failure instanceof DOMException && failure.name === 'AbortError')
    )
      error.value = failure
  } finally {
    if (epoch === generation) loading.value = false
  }
}
// 首次权威已读下界到达后装载页面；后续外部变更只提示重读，不自动替换员工正在查看的一页。
watch(
  () => store.state.receipt !== null,
  (ready) => {
    if (ready && !page.value && !loading.value) void load()
  },
  { immediate: true },
)
async function switchMode(value: 'unread' | 'history') {
  if (busy.value) return
  mode.value = value
  page.value = undefined
  historyAfter.value = 0
  historyBack.value = []
  await load()
}
async function nextHistory() {
  if (!page.value?.hasMore || loading.value || busy.value || error.value) return
  historyBack.value.push(historyAfter.value)
  historyAfter.value = page.value.nextCursor
  await load()
}
async function previousHistory() {
  if (!historyBack.value.length || loading.value || busy.value || error.value) return
  historyAfter.value = historyBack.value.pop()!
  await load()
}
async function acknowledge() {
  if (!canAcknowledge.value || !page.value) return
  const displayed = page.value,
    session = sessionBridge.snapshot().generation,
    epoch = generation
  confirming.value = true
  const approved = await new Promise<boolean>((resolve) => {
    const dialog = modal.confirm({
      title: '将本页通知标为已读？',
      content: `确认当前连续页面的 ${displayed.items.length} 条通知。新的后续消息不会一并确认。`,
      okText: '确认本页已读',
      cancelText: '继续查看',
      onOk: () => {
        dismissConfirmation = undefined
        resolve(true)
      },
      onCancel: () => {
        dismissConfirmation = undefined
        resolve(false)
      },
    })
    dismissConfirmation = () => {
      dialog.destroy()
      resolve(false)
    }
  })
  confirming.value = false
  if (!approved || epoch !== generation || !sessionBridge.isCurrent(session)) return
  try {
    await store.acknowledge(displayed)
    if (!sessionBridge.isCurrent(session)) return
    message.success('本页阅读进度已保存')
    await load()
  } catch (failure) {
    if (sessionBridge.isCurrent(session)) {
      error.value = failure
      outcomeUnknown.value = true
    }
  }
}
onBeforeUnmount(() => {
  dismissConfirmation?.()
  ++generation
  controller?.abort()
})
</script>
<template>
  <div class="page-heading heading-inline">
    <div>
      <h1>通知中心</h1>
      <p class="muted">来单与催单及时可查，阅读进度按员工账号保存。</p>
    </div>
    <Button :loading="loading || store.state.syncing" :disabled="busy" @click="load"
      ><ReloadOutlined />刷新通知</Button
    >
  </div>
  <div class="notification-status-bar">
    <Tag :color="connection.color">{{ connection.label }}</Tag
    ><span class="muted">{{
      store.state.lastSyncedAt
        ? `最近同步 ${dateTime(new Date(store.state.lastSyncedAt).toISOString())}`
        : '正在读取服务端进度'
    }}</span
    ><Button
      v-if="store.state.connection !== 'connected'"
      type="link"
      :disabled="store.state.connection === 'offline'"
      @click="store.reconnect()"
      >重新连接</Button
    >
  </div>
  <ProblemAlert :error="store.state.error" retry @retry="store.refresh()" />
  <div class="notification-tabs" role="group" aria-label="通知视图">
    <Button
      :type="mode === 'unread' ? 'primary' : 'default'"
      :disabled="busy"
      @click="switchMode('unread')"
      >待阅读</Button
    ><Button
      :type="mode === 'history' ? 'primary' : 'default'"
      :disabled="busy"
      @click="switchMode('history')"
      >全部记录</Button
    >
  </div>
  <Alert
    v-if="stale"
    class="form-notice"
    type="info"
    show-icon
    title="阅读进度已在其他页面或设备更新"
    description="当前展示页保持不变，请重新读取后继续确认。"
    ><template #action
      ><Button :disabled="busy || loading" @click="load">重新读取本页</Button></template
    ></Alert
  >
  <Alert v-if="newPageAvailable && !stale" class="form-notice" type="info" title="有新通知可查看"
    ><template #action
      ><Button :disabled="busy || loading" @click="load">刷新本页</Button></template
    ></Alert
  >
  <ProblemAlert :error="error" retry @retry="load" />
  <Alert
    v-if="outcomeUnknown"
    class="form-notice"
    type="warning"
    title="请先同步阅读进度"
    description="上次确认结果可能已变化；不要重复提交，重新读取后再确认。"
  />
  <Skeleton v-if="!store.state.receipt && !store.state.error" active :paragraph="{ rows: 5 }" />
  <template v-else>
    <Table
      :columns="columns"
      :data-source="page?.items || []"
      row-key="id"
      :pagination="false"
      :loading="loading"
      :scroll="{ x: 760 }"
      :locale="{
        emptyText:
          error || store.state.error
            ? '通知暂不可用'
            : mode === 'unread'
              ? newPageAvailable
                ? '有新通知，请刷新查看'
                : '当前没有待阅读通知'
              : '暂无通知记录',
      }"
      ><template #bodyCell="{ column, record }">
        <Tag
          v-if="column.key === 'type'"
          :color="record.type === 'NEW_ORDER' ? 'success' : 'warning'"
          >{{ noticeLabel(record.type) }}</Tag
        >
        <ResourceId v-if="column.key === 'order'" :value="record.orderId" />
        <template v-if="column.key === 'time'">{{ dateTime(record.occurredAt) }}</template>
        <template v-if="column.key === 'read'">{{
          !store.state.receipt
            ? '阅读进度未知'
            : record.sequence <= store.state.receipt.sequence
              ? '已确认阅读'
              : '待阅读'
        }}</template>
        <Button
          v-if="column.key === 'actions'"
          type="link"
          :disabled="busy"
          @click="router.push(`/orders/${record.orderId}`)"
          >查看订单</Button
        >
      </template></Table
    >
    <div class="notification-page-actions">
      <span class="muted"
        >{{ page?.items.length || 0 }} 条已加载<span v-if="page?.hasMore">
          · 后面还有记录</span
        ></span
      >
      <template v-if="mode === 'unread'"
        ><Button :disabled="!canAcknowledge" :loading="store.state.pendingAck" @click="acknowledge"
          >将本页标为已读</Button
        ></template
      >
      <div v-else class="row-actions">
        <Button :disabled="!historyBack.length || loading || !!error" @click="previousHistory"
          >上一页</Button
        ><Button :disabled="!page?.hasMore || loading || !!error" @click="nextHistory"
          >下一页</Button
        >
      </div>
    </div>
    <p class="muted notification-help">
      {{
        mode === 'unread'
          ? '阅读确认只覆盖当前连续页面；点击订单不会自动标记本页已读。'
          : '历史按发生记录顺序逐页加载，不代表未读总数。'
      }}
      通知是历史事实，处理订单前请确认最新状态。
    </p>
  </template>
</template>
