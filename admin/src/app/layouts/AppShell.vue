<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, h, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { Menu, Button, Dropdown, Avatar, Tag, Drawer } from 'antdv-next'
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
  LogoutOutlined,
  DownOutlined,
} from '@antdv-next/icons'
import { useQuery } from '@tanstack/vue-query'
import { NotificationBell } from '@/modules/notifications'
import { useSessionStore } from '@/modules/auth'
import { getStorefront } from '@/modules/workspace'
import { navigationItems } from '../router/navigation'

const session = useSessionStore()
const route = useRoute()
const router = useRouter()
const viewport = ref(window.innerWidth)
const collapsed = ref(viewport.value < 1366)
const mobileMenu = ref(false)
const loggingOut = ref(false)
const mobile = computed(() => viewport.value < 1024)
const selected = computed(() =>
  route.path.startsWith('/orders')
    ? '/orders'
    : route.path.startsWith('/customers')
      ? '/customers'
      : route.path.replace(/\/(?:new|[^/]+\/edit)$/, ''),
)
const openKeys = ref<string[]>([])
watch(
  () => [route.path, collapsed.value, mobile.value] as const,
  ([path, isCollapsed, isMobile]) => {
    if (isCollapsed && !isMobile) {
      openKeys.value = []
      return
    }
    if (path.startsWith('/catalog/')) openKeys.value = ['catalog']
    else if (path.startsWith('/settings/')) openKeys.value = ['settings']
  },
  { immediate: true },
)
const items = computed(() => navigationItems(session.identity?.role === 'ADMIN'))
const storefront = useQuery({
  queryKey: ['storefront'],
  queryFn: ({ signal }) => getStorefront(signal),
})
const profileMenu = [
  { key: 'account', label: '我的账号', icon: () => h(UserOutlined) },
  { key: 'logout', label: '退出登录' },
]
function resize() {
  viewport.value = window.innerWidth
  if (viewport.value < 1366) collapsed.value = true
}
onMounted(() => window.addEventListener('resize', resize))
onUnmounted(() => window.removeEventListener('resize', resize))
function navigate(key: string) {
  mobileMenu.value = false
  void router.push(key)
}
async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  let notice: string | undefined
  try {
    await session.logout()
  } catch {
    notice = 'local-logout'
  } finally {
    loggingOut.value = false
    await router.replace({ path: '/login', query: notice ? { notice } : {} })
  }
}
function profile(key: string) {
  if (key === 'account') void router.push('/account')
  else void logout()
}
</script>
<template>
  <div class="app-shell" :class="{ 'is-collapsed': collapsed, 'is-mobile': mobile }">
    <aside v-if="!mobile" class="app-sidebar">
      <RouterLink to="/workspace" class="brand-link" aria-label="HAN MENU 工作台"
        ><div class="wordmark">{{ collapsed ? 'HM' : 'HAN MENU' }}</div>
        <span v-if="!collapsed">商家管理</span></RouterLink
      >
      <nav class="sidebar-nav" aria-label="主导航">
        <Menu
          v-model:open-keys="openKeys"
          mode="inline"
          :inline-collapsed="collapsed"
          :items="items"
          :selected-keys="[selected]"
          @click="({ key }) => navigate(String(key))"
        />
      </nav>
      <Dropdown
        :menu="{ items: profileMenu, onClick: ({ key }) => profile(String(key)) }"
        placement="topRight"
        :trigger="['click']"
      >
        <button class="sidebar-profile" aria-label="打开账号菜单">
          <Avatar :size="36"
            ><template #icon><UserOutlined /></template></Avatar
          ><span v-if="!collapsed" class="profile-text"
            ><strong>{{ session.identity?.displayName }}</strong
            ><small>{{ session.identity?.role === 'ADMIN' ? '管理员' : '普通员工' }}</small></span
          ><DownOutlined v-if="!collapsed" />
        </button>
      </Dropdown>
    </aside>
    <Drawer
      title="HAN MENU · 商家管理"
      placement="left"
      :size="248"
      :open="mobileMenu"
      @close="mobileMenu = false"
      ><Menu
        v-model:open-keys="openKeys"
        mode="inline"
        :items="items"
        :selected-keys="[selected]"
        @click="({ key }) => navigate(String(key))"
    /></Drawer>
    <div class="app-main">
      <header class="app-header">
        <div class="header-location">
          <Button
            type="text"
            :aria-label="mobile ? '打开导航' : collapsed ? '展开导航' : '折叠导航'"
            @click="mobile ? (mobileMenu = true) : (collapsed = !collapsed)"
            ><MenuUnfoldOutlined v-if="collapsed || mobile" /><MenuFoldOutlined v-else /></Button
          ><span class="breadcrumb-group">{{ route.meta.group || '商家管理' }}</span
          ><span class="breadcrumb-separator">/</span><strong>{{ route.meta.title }}</strong>
        </div>
        <div class="header-actions">
          <span class="store-name">{{ storefront.data.value?.name || 'HAN MENU' }}</span
          ><Tag
            v-if="storefront.data.value"
            :color="storefront.data.value.status === 'OPEN' ? 'success' : 'default'"
            :bordered="false"
            >{{ storefront.data.value.status === 'OPEN' ? '营业中' : '已打烊' }}</Tag
          ><span v-else class="muted">门店状态暂不可用</span><NotificationBell /><Dropdown
            :menu="{ items: profileMenu, onClick: ({ key }) => profile(String(key)) }"
            :trigger="['click']"
            ><Button type="text" aria-label="账号菜单"><UserOutlined /></Button></Dropdown
          ><Button
            class="quick-logout"
            type="text"
            :loading="loggingOut"
            aria-label="退出登录"
            @click="logout"
            ><LogoutOutlined
          /></Button>
        </div>
      </header>
      <main id="main-content" class="page-content">
        <RouterView :key="route.path.startsWith('/catalog/') ? route.path : undefined" />
      </main>
    </div>
  </div>
</template>
