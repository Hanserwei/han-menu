import { ref, computed, watch, onBeforeUnmount, type Ref } from 'vue'
import { App } from 'antdv-next'
import { ordersApi, type OrderDetail, type OrderAction } from '../api/orders'
import { availableActions, actions, shouldPoll } from './order-state'
import { uuidPattern } from './order-filter'
import { ApiProblem } from '@/shared/api/problem'
import { revision } from '@/shared/api/result'
import { queryClient } from '@/shared/api/query-client'
import { useVisiblePoll } from '@/shared/lib/use-visible-poll'
import { sessionBridge } from '@/shared/api/session-bridge'

/** 详情读取、确认与提交共用生命周期。确认框冻结版本，写后保留新状态，不受列表筛选变化影响。 */
export function useOrderDetail(id: Ref<string>) {
  const order = ref<OrderDetail>(),
    loading = ref(false),
    loadError = ref<unknown>(null),
    writeError = ref<unknown>(null),
    pending = ref(false),
    confirming = ref(false),
    blocked = ref(false)
  const { modal, message } = App.useApp()
  let generation = 0,
    controller: AbortController | undefined,
    disposed = false
  const busy = computed(() => pending.value || confirming.value)
  const valid = computed(
    () =>
      !!order.value?.id &&
      Number.isSafeInteger(order.value.version) &&
      (order.value.version ?? -1) >= 0,
  )
  async function load(explicit = false) {
    if (busy.value || !id.value || (blocked.value && !explicit)) return
    if (!uuidPattern.test(id.value)) {
      order.value = undefined
      loadError.value = new ApiProblem(400, '订单编号不合法')
      return
    }
    controller?.abort()
    controller = new AbortController()
    const epoch = ++generation
    loading.value = true
    loadError.value = null
    try {
      const value = await ordersApi.detail(id.value, controller.signal)
      if (epoch !== generation || disposed) return
      if (value.id !== id.value || !Number.isSafeInteger(value.version))
        throw new ApiProblem(502, '订单详情或版本不完整，请重新读取')
      order.value = value
      if (explicit) {
        writeError.value = null
        blocked.value = false
      }
    } catch (error) {
      if (
        epoch === generation &&
        !disposed &&
        !(error instanceof DOMException && error.name === 'AbortError')
      ) {
        loadError.value = error
        if (error instanceof ApiProblem && [403, 404].includes(error.status))
          order.value = undefined
      }
    } finally {
      if (epoch === generation && !disposed) loading.value = false
    }
  }
  watch(
    id,
    () => {
      ++generation
      controller?.abort()
      order.value = undefined
      loadError.value = null
      writeError.value = null
      blocked.value = false
      loading.value = false
      void load(true)
    },
    { immediate: true },
  )
  async function act(action: OrderAction) {
    if (
      busy.value ||
      loading.value ||
      loadError.value ||
      blocked.value ||
      !valid.value ||
      !availableActions(order.value?.status).includes(action)
    )
      return
    const snapshot = revision(order.value!),
      epoch = generation,
      session = sessionBridge.snapshot().generation
    confirming.value = true
    const confirmed = await new Promise<boolean>((resolve) =>
      modal.confirm({
        title: actions[action].label,
        content: `订单 ${snapshot.id}。${actions[action].description}`,
        okText: actions[action].label,
        cancelText: '返回',
        okButtonProps: { danger: !!actions[action].danger },
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      }),
    )
    confirming.value = false
    if (!confirmed || disposed || generation !== epoch || !sessionBridge.isCurrent(session)) return
    pending.value = true
    writeError.value = null
    try {
      const updated = await ordersApi.act(snapshot.id, action, snapshot.version)
      if (disposed || generation !== epoch || !sessionBridge.isCurrent(session)) return
      if (updated.id !== snapshot.id || !Number.isSafeInteger(updated.version))
        throw new ApiProblem(502, '操作已提交，但响应不完整，请重新读取')
      order.value = updated
      message.success(
        updated.status === 'REFUNDING' || updated.status === 'CANCELLING'
          ? '已提交，等待服务端确认'
          : `${actions[action].label}成功`,
      )
      await Promise.all(
        ['orders-list', 'workspace', 'finance-payments', 'finance-refunds'].map((key) =>
          queryClient.invalidateQueries({ queryKey: [key] }),
        ),
      )
    } catch (error) {
      if (!disposed && generation === epoch && sessionBridge.isCurrent(session)) {
        writeError.value = error
        blocked.value = true
      }
    } finally {
      pending.value = false
    }
  }
  useVisiblePoll(
    () => load(),
    () =>
      !!order.value && shouldPoll(order.value) && !busy.value && !loading.value && !blocked.value,
  )
  onBeforeUnmount(() => {
    disposed = true
    ++generation
    controller?.abort()
  })
  return {
    order,
    loading,
    loadError,
    writeError,
    pending,
    confirming,
    blocked,
    busy,
    valid,
    load,
    act,
  }
}
