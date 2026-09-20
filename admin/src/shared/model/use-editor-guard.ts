import { onBeforeUnmount, type Ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { App } from 'antdv-next'
import { sessionBridge } from '../api/session-bridge'

/** 草稿仅留当前页面；离开、关闭抽屉和重新读取共用明确的放弃提示。 */
export function useEditorGuard(dirty: Ref<boolean>, pending: Ref<boolean>) {
  const { modal } = App.useApp()
  function confirmDiscard(): Promise<boolean> {
    if (!sessionBridge.snapshot().token) return Promise.resolve(true)
    if (pending.value) return Promise.resolve(false)
    if (!dirty.value) return Promise.resolve(true)
    return new Promise((resolve) =>
      modal.confirm({
        title: '放弃尚未保存的修改？',
        content: '重新读取或离开会清空当前草稿。',
        okText: '放弃修改',
        cancelText: '继续编辑',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      }),
    )
  }
  onBeforeRouteLeave(confirmDiscard)
  onBeforeRouteUpdate(confirmDiscard)
  const beforeUnload = (event: BeforeUnloadEvent) => {
    if (dirty.value || pending.value) event.preventDefault()
  }
  window.addEventListener('beforeunload', beforeUnload)
  onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
  return { confirmDiscard }
}
