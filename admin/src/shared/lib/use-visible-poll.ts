import { onMounted, onBeforeUnmount } from 'vue'
/** 轮询只在可见页面运行，等待当前请求结束；离页清除计时，不在失焦标签持续打接口。 */
export function useVisiblePoll(
  task: () => Promise<unknown>,
  enabled: () => boolean,
  interval = 15000,
) {
  let timer: ReturnType<typeof setTimeout> | undefined,
    disposed = false,
    running = false
  function schedule() {
    clearTimeout(timer)
    if (!disposed && document.visibilityState === 'visible') timer = setTimeout(tick, interval)
  }
  async function tick() {
    if (disposed || running || document.visibilityState !== 'visible') return
    schedule()
    if (!enabled()) return
    running = true
    try {
      await task()
    } finally {
      running = false
      schedule()
    }
  }
  function visible() {
    clearTimeout(timer)
    if (document.visibilityState === 'visible') void tick()
  }
  onMounted(() => {
    schedule()
    document.addEventListener('visibilitychange', visible)
  })
  onBeforeUnmount(() => {
    disposed = true
    clearTimeout(timer)
    document.removeEventListener('visibilitychange', visible)
  })
}
