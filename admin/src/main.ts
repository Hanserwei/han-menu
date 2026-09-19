import { createApp, watch } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import App from './App.vue'
import { router, installRouteGuards } from './app/router'
import { queryClient } from './shared/api/query-client'
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
