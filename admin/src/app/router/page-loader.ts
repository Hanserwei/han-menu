import type { Component } from 'vue'
import PageLoadFailure from '../layouts/PageLoadFailure.vue'
/** 路由保持懒加载；失败返回已随主入口加载的恢复页，使初始深链接也能结束启动等待。 */
export function recoverPage(loader: () => Promise<{ default: Component }>) {
  return async () => {
    try {
      return (await loader()).default
    } catch {
      return PageLoadFailure
    }
  }
}
