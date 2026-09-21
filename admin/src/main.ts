import { createApp, watch } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import App from './App.vue'
import { router, installRouteGuards } from './app/router'
import { queryClient } from './shared/api/query-client'
import { useNotificationsStore } from './modules/notifications'
import { useSessionStore } from './modules/auth'
import 'antdv-next/dist/reset.css'
import './app/styles/main.css'

/** 启动顺序保证路由守卫可使用 Pinia，且整棵组件树共用同一查询生命周期。 */
const app = createApp(App)
app.use(createPinia())
app.use(VueQueryPlugin, { queryClient })
installRouteGuards()
app.use(router)
const session = useSessionStore()
const notices = useNotificationsStore()
// 同步监听认证身份；清理桥的AbortSignal保证退出时Socket在下一次渲染前关闭。
const stopNotificationWatch = watch(
  () => (session.authenticated ? session.identity?.id : null),
  (id) => {
    if (id) notices.start()
    else notices.stop()
  },
  { immediate: true, flush: 'sync' },
)
if (import.meta.hot)
  import.meta.hot.dispose(() => {
    stopNotificationWatch()
    notices.stop()
  })
watch(
  () => session.status,
  (state) => {
    if (state === 'anonymous' && router.currentRoute.value.meta.requiresAuth)
      void router.replace({
        name: 'login',
        query: { redirect: router.currentRoute.value.fullPath },
      })
  },
)
void router.isReady().then(() => app.mount('#app'))
