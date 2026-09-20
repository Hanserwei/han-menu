import { ref, computed } from 'vue'
import { App } from 'antdv-next'
import { ApiProblem } from '../api/problem'
import { sessionBridge } from '../api/session-bridge'

/** 写操作不重试也不乐观成功；保留409与结果未知状态供页面要求显式重读。 */
export function useCommand() {
  const pending = ref(false)
  const error = ref<unknown>(null)
  const { message, modal } = App.useApp()
  const needsReload = computed(
    () =>
      error.value instanceof ApiProblem &&
      ([0, 409].includes(error.value.status) || error.value.status >= 500),
  )
  async function run(action: () => Promise<void>, success = '已保存'): Promise<boolean> {
    if (pending.value || needsReload.value) return false
    pending.value = true
    error.value = null
    const epoch = sessionBridge.snapshot().generation
    try {
      await action()
      if (!sessionBridge.isCurrent(epoch)) return false
      message.success(success)
      return true
    } catch (failure) {
      if (
        sessionBridge.isCurrent(epoch) &&
        !(failure instanceof DOMException && failure.name === 'AbortError')
      )
        error.value = failure
      return false
    } finally {
      pending.value = false
    }
  }
  function confirm(title: string, content: string): Promise<boolean> {
    return new Promise((resolve) =>
      modal.confirm({
        title,
        content,
        okText: '确认',
        cancelText: '返回',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      }),
    )
  }
  return { pending, error, needsReload, run, confirm }
}
