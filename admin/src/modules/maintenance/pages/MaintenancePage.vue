<script setup lang="ts">
import { ref, computed, onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import {
  App,
  Button,
  Table,
  Tag,
  Drawer,
  Input,
  Form,
  FormItem,
  Alert,
  Descriptions,
  DescriptionsItem,
  Skeleton,
} from 'antdv-next'
import { maintenanceApi, projectionVersion, type Notice } from '../api/maintenance'
import { useCommand } from '@/shared/model/use-command'
import { useEditorGuard } from '@/shared/model/use-editor-guard'
import { sessionBridge } from '@/shared/api/session-bridge'
import { optionalId } from '@/shared/lib/query-filters'
import { ApiProblem } from '@/shared/api/problem'
import { dateTime } from '@/shared/lib/time'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
import ResourceId from '@/shared/ui/ResourceId.vue'
const route = useRoute(),
  cache = useQueryClient(),
  { modal } = App.useApp()
const command = useCommand(),
  { pending, error: writeError, needsReload } = command
const noticeCommand = useCommand(),
  {
    pending: noticePending,
    error: noticeWriteError,
    needsReload: noticeNeedsReload,
  } = noticeCommand
// 两种维护命令各自保留冲突与未知结果，读取通知不能清除投影的写入保护。
const confirming = ref(false),
  busy = computed(() => pending.value || noticePending.value || confirming.value)
useEditorGuard(
  computed(() => false),
  busy,
)
const unknownVersion = ref<number>(),
  unknownSince = ref(0)
const projection = useQuery({
  queryKey: ['reporting-projection'],
  queryFn: ({ signal }) => maintenanceApi.projection(signal),
  refetchOnWindowFocus: false,
})
const after = ref(0),
  back = ref<number[]>([])
const feed = useQuery({
  queryKey: computed(() => ['maintenance-feed', after.value]),
  queryFn: ({ signal }) => maintenanceApi.feed(after.value, signal),
  refetchOnWindowFocus: false,
})
const noticeId = ref(''),
  lookup = ref(''),
  selected = ref<Notice>(),
  lookupError = ref<unknown>(null)
const attempts = useQuery({
  queryKey: computed(() => ['maintenance-attempts', noticeId.value]),
  enabled: computed(() => !!noticeId.value),
  queryFn: ({ signal }) => maintenanceApi.attempts(noticeId.value, signal),
  refetchOnWindowFocus: false,
})
const statuses: Record<string, string> = {
  PENDING: '待投递',
  IN_FLIGHT: '投递中',
  DELIVERED: '已投递',
  EXHAUSTED: '重试耗尽',
}
let alive = true,
  dismiss: (() => void) | undefined
onBeforeUnmount(() => {
  alive = false
  dismiss?.()
})
/** 确认期间也防重复；离开或会话撤销时销毁对话框，不让旧确认作用于新账号。 */
async function confirm(title: string, content: string) {
  confirming.value = true
  const epoch = sessionBridge.snapshot().generation
  try {
    const accepted = await new Promise<boolean>((resolve) => {
      const dialog = modal.confirm({
        title,
        content,
        okText: '确认',
        cancelText: '返回',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      })
      dismiss = () => {
        dialog.destroy()
        resolve(false)
      }
    })
    return accepted && alive && sessionBridge.isCurrent(epoch)
  } finally {
    confirming.value = false
    dismiss = undefined
  }
}
const canRebuild = computed(
  () =>
    !!projection.data.value &&
    Number.isSafeInteger(projection.data.value.version) &&
    projection.data.value.version! >= 0 &&
    !projection.isFetching.value &&
    !projection.error.value &&
    !busy.value &&
    !needsReload.value &&
    unknownVersion.value === undefined,
)
async function rebuild() {
  if (!canRebuild.value) return
  const version = projectionVersion(projection.data.value)
  if (
    !(await confirm(
      '重建统计投影？',
      '将从订单、顾客与支付的公开事实重建统计。期间旧报表仍可读取，新事件随后追平；可能持续180秒。此操作不修改订单、资金、通知历史或阅读进度。',
    ))
  )
    return
  const started = Date.now()
  await command.run(async () => {
    const result = await maintenanceApi.rebuild(version)
    cache.setQueryData(['reporting-projection'], result)
    await cache.invalidateQueries({
      predicate: (query) =>
        String(query.queryKey[0]).startsWith('reports-') || query.queryKey[0] === 'workspace',
    })
  }, '统计投影已重建')
  if (writeError.value instanceof ApiProblem && writeError.value.status === 0) {
    unknownVersion.value = version
    unknownSince.value = started
  }
}
/** 未知结果先读状态；同一版本可能仍处于事务中，超过事务与网络上限才允许重新确认。 */
async function reloadProjection() {
  if (busy.value) return
  const result = await projection.refetch()
  if (result.error || !result.data) return
  if (
    unknownVersion.value !== undefined &&
    result.data.version === unknownVersion.value &&
    Date.now() - unknownSince.value < 195_000
  )
    return
  unknownVersion.value = undefined
  writeError.value = null
}
function select(notice: Notice) {
  if (busy.value) return
  selected.value = { ...notice }
  noticeId.value = notice.id || ''
  noticeWriteError.value = null
}
function find() {
  if (busy.value) return
  try {
    const id = optionalId(lookup.value)
    if (!id) throw new ApiProblem(400, '请输入通知 UUID')
    noticeId.value = id
    selected.value = undefined
    lookupError.value = null
    noticeWriteError.value = null
  } catch (failure) {
    lookupError.value = failure
  }
}
const reloading = ref(false)
/** 详情重读从已知sequence之前补查一条，核对UUID后才能采用当前状态及版本。 */
async function reloadNotice() {
  if (busy.value || reloading.value) return
  const old = selected.value,
    epoch = sessionBridge.snapshot().generation
  reloading.value = true
  try {
    if (old?.sequence) {
      const result = await maintenanceApi.feed(old.sequence - 1, undefined, 1)
      if (!alive || !sessionBridge.isCurrent(epoch) || noticeId.value !== old.id) return
      const current = result.items?.[0]
      if (current?.id !== old.id) throw new ApiProblem(502, '无法确认当前通知，请重新选择')
      selected.value = current
    }
    const result = await attempts.refetch()
    if (!result.error) noticeWriteError.value = null
  } catch (failure) {
    if (alive && sessionBridge.isCurrent(epoch)) noticeWriteError.value = failure
  } finally {
    reloading.value = false
  }
}
async function redeliver() {
  if (
    !selected.value ||
    selected.value.deliveryStatus !== 'EXHAUSTED' ||
    busy.value ||
    noticeNeedsReload.value ||
    reloading.value
  )
    return
  const notice = { ...selected.value }
  if (
    !(await confirm(
      '重新投递这条通知？',
      '将重新尝试向当前在线员工推送，可能出现重复提醒。原通知与投递轨迹保留，不改变任何员工的已读进度。',
    ))
  )
    return
  await noticeCommand.run(async () => {
    selected.value = await maintenanceApi.redeliver(notice)
    await feed.refetch()
    await attempts.refetch()
  }, '已重新登记投递，请刷新查看实际结果')
}
function closeNotice() {
  if (busy.value) return
  noticeId.value = ''
  selected.value = undefined
  noticeWriteError.value = null
}
function next() {
  const cursor = feed.data.value?.nextCursor
  if (feed.data.value?.hasMore && cursor && cursor > after.value) {
    back.value.push(after.value)
    after.value = cursor
  }
}
function previous() {
  const cursor = back.value.pop()
  if (cursor !== undefined) after.value = cursor
}
watch(
  () => route.query,
  async (query) => {
    if (typeof query.noticeId !== 'string') return
    lookup.value = query.noticeId
    find()
    const sequence = Number(query.sequence)
    if (!Number.isSafeInteger(sequence) || sequence < 1) return
    const epoch = sessionBridge.snapshot().generation
    try {
      const result = await maintenanceApi.feed(sequence - 1, undefined, 1)
      if (alive && sessionBridge.isCurrent(epoch) && result.items?.[0]?.id === noticeId.value)
        selected.value = result.items[0]
    } catch (failure) {
      if (alive && sessionBridge.isCurrent(epoch)) lookupError.value = failure
    }
  },
  { immediate: true },
)
</script>
<template>
  <div class="page-heading">
    <h1>系统维护</h1>
    <p class="muted">核查通知投递与统计投影；维护操作需确认影响后执行。</p>
  </div>
  <section class="surface-section section-gap">
    <div class="section-title">
      <h2>统计投影</h2>
      <div class="row-actions">
        <Button :disabled="busy" :loading="projection.isFetching.value" @click="reloadProjection"
          >刷新投影状态</Button
        ><Button :disabled="!canRebuild" :loading="pending" @click="rebuild">重建统计投影</Button>
      </div>
    </div>
    <ProblemAlert :error="projection.error.value" retry @retry="reloadProjection" /><ProblemAlert
      :error="writeError"
    />
    <Alert
      v-if="unknownVersion !== undefined"
      type="warning"
      show-icon
      title="重建结果尚未确认"
      description="请稍后刷新投影状态。系统不会自动重发；同一版本可能仍在重建，最长等待195秒后重新核查。"
    />
    <Alert
      v-else-if="needsReload"
      type="warning"
      show-icon
      title="数据可能已变化，请刷新投影状态后重新确认"
    />
    <Skeleton v-if="projection.isPending.value" active /><template v-else-if="projection.data.value"
      ><Descriptions :column="2" class="section-gap"
        ><DescriptionsItem label="状态"
          ><Tag :color="projection.data.value.initialized ? 'success' : 'warning'">{{
            projection.data.value.initialized ? '已就绪' : '尚未初始化'
          }}</Tag></DescriptionsItem
        ><DescriptionsItem label="更新时间">{{
          dateTime(projection.data.value.updatedAt)
        }}</DescriptionsItem
        ><DescriptionsItem label="上次重建">{{
          dateTime(projection.data.value.rebuiltAt)
        }}</DescriptionsItem
        ><DescriptionsItem label="订单数">{{ projection.data.value.orders }}</DescriptionsItem
        ><DescriptionsItem label="顾客数">{{ projection.data.value.customers }}</DescriptionsItem
        ><DescriptionsItem label="收款 / 退款事实"
          >{{ projection.data.value.receipts }} /
          {{ projection.data.value.refunds }}</DescriptionsItem
        ></Descriptions
      >
      <details>
        <summary>投影诊断信息</summary>
        控制版本 {{ projection.data.value.version }} · 代际 {{ projection.data.value.generation }} ·
        修订 {{ projection.data.value.revision }}
      </details></template
    >
    <p class="muted">报表异步更新。重建只恢复统计事实，不执行资金补单，也不修改真实订单。</p>
  </section>
  <section class="surface-section section-gap">
    <div class="section-title">
      <h2>通知投递诊断</h2>
      <Button :disabled="busy" :loading="feed.isFetching.value" @click="feed.refetch()"
        >刷新通知记录</Button
      >
    </div>
    <p class="muted">
      按持久化通知顺序浏览。已投递仅表示写入在线连接，不等于员工已读；尝试记录最多显示100条。
    </p>
    <Form layout="inline" class="filter-bar" @finish="find"
      ><FormItem label="通知 UUID"
        ><Input v-model:value="lookup" aria-label="通知UUID" :disabled="busy" /></FormItem
      ><Button html-type="submit" :disabled="busy">查询投递轨迹</Button></Form
    ><ProblemAlert :error="lookupError" /><ProblemAlert
      :error="feed.error.value"
      retry
      @retry="feed.refetch()"
    />
    <Table
      :columns="[
        { title: '通知编号', key: 'id', width: 180 },
        { title: '类型', key: 'type', width: 100 },
        { title: '发生时间', key: 'time', width: 180 },
        { title: '投递状态', key: 'status', width: 120 },
        { title: '尝试次数', dataIndex: 'attempts', width: 90 },
        { title: '操作', key: 'actions', width: 120 },
      ]"
      :data-source="feed.data.value?.items || []"
      row-key="id"
      :pagination="false"
      :loading="feed.isFetching.value"
      :scroll="{ x: 790 }"
      :locale="{ emptyText: feed.isError.value ? '通知暂不可用' : '暂无通知记录' }"
      ><template #bodyCell="{ column, record }"
        ><ResourceId v-if="column.key === 'id'" :value="record.id" /><template
          v-if="column.key === 'type'"
          >{{ record.type === 'NEW_ORDER' ? '新订单' : '顾客催单' }}</template
        ><template v-if="column.key === 'time'">{{ dateTime(record.occurredAt) }}</template
        ><Tag
          v-if="column.key === 'status'"
          :color="record.deliveryStatus === 'EXHAUSTED' ? 'error' : 'default'"
          >{{ statuses[record.deliveryStatus || ''] || '未知状态' }}</Tag
        ><Button
          v-if="column.key === 'actions'"
          type="link"
          :disabled="busy"
          @click="select(record)"
          >投递轨迹</Button
        ></template
      ></Table
    >
    <div class="row-actions section-gap">
      <Button
        :disabled="!back.length || feed.isFetching.value || busy || !!feed.error.value"
        @click="previous"
        >上一页</Button
      ><Button
        :disabled="!feed.data.value?.hasMore || feed.isFetching.value || busy || !!feed.error.value"
        @click="next"
        >下一页</Button
      ><span class="muted">只浏览通知流，不改变阅读进度。</span>
    </div>
  </section>
  <Drawer
    title="通知投递轨迹"
    :open="!!noticeId"
    :size="760"
    :closable="!busy"
    :mask-closable="!busy"
    :keyboard="!busy"
    @close="closeNotice"
  >
    <ResourceId :value="noticeId" />
    <p v-if="selected">
      {{ statuses[selected.deliveryStatus || ''] }} · 累计尝试 {{ selected.attempts }} 次 · 最近失败
      {{ selected.lastFailure || '—' }}
    </p>
    <p v-else class="muted">按 UUID 查询仅查看轨迹。重投需从通知流选择记录并读取当前版本。</p>
    <div class="row-actions section-gap">
      <Button
        :disabled="busy"
        :loading="reloading || attempts.isFetching.value"
        @click="reloadNotice"
        >重新读取通知</Button
      ><Button
        :disabled="
          selected?.deliveryStatus !== 'EXHAUSTED' ||
          busy ||
          noticeNeedsReload ||
          reloading ||
          !!attempts.error.value
        "
        :loading="noticePending"
        @click="redeliver"
        >重新投递</Button
      >
    </div>
    <ProblemAlert :error="noticeWriteError" /><Alert
      v-if="noticeNeedsReload"
      type="warning"
      title="请重新读取通知后确认，不能直接重复提交"
    /><ProblemAlert :error="attempts.error.value" retry @retry="reloadNotice" />
    <Table
      class="section-gap"
      :columns="[
        { title: '尝试编号', key: 'id', width: 180 },
        { title: '开始 / 结束', key: 'time', width: 175 },
        { title: '状态', dataIndex: 'status', width: 100 },
        { title: '成功连接', dataIndex: 'sent', width: 85 },
        { title: '失败连接', dataIndex: 'failed', width: 85 },
        { title: '失败分类', dataIndex: 'failure', width: 150 },
      ]"
      :data-source="attempts.data.value || []"
      row-key="id"
      :pagination="false"
      :loading="attempts.isFetching.value"
      :scroll="{ x: 790 }"
      :locale="{ emptyText: attempts.isError.value ? '轨迹暂不可用' : '暂无投递尝试' }"
      ><template #bodyCell="{ column, record }"
        ><ResourceId v-if="column.key === 'id'" :value="record.id" />
        <template v-if="column.key === 'time'"
          >{{ dateTime(record.startedAt) }}<br />{{
            record.finishedAt ? dateTime(record.finishedAt) : '尚未结束'
          }}</template
        ></template
      ></Table
    >
  </Drawer>
</template>
