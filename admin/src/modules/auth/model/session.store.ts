import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { ApiProblem } from '@/shared/api/problem'
import { sessionBridge } from '@/shared/api/session-bridge'
import { queryClient } from '@/shared/api/query-client'
import {
  createSession,
  currentIdentity,
  revokeSession,
  changePassword,
  type StaffIdentity,
} from '../api/session'
import { readSession, writeSession, type StoredSession } from './session-storage'

/** 认证状态机：先验证会话再开放路由，恢复失败时绝不把旧角色当作当前权限。 */
export const useSessionStore = defineStore('staff-session', () => {
  const identity = ref<StaffIdentity | null>(null)
  const status = ref<'anonymous' | 'restoring' | 'authenticated' | 'unavailable'>('anonymous')
  const reason = ref('')
  const restoreError = ref<unknown>(null)
  let initialized = false
  let restoring: Promise<void> | null = null
  let timer: ReturnType<typeof setTimeout> | undefined
  const authenticated = computed(() => status.value === 'authenticated' && identity.value !== null)

  /** 先切断旧请求再清缓存；清理不依赖任何服务器请求成功。 */
  function clear(message = '') {
    clearTimeout(timer)
    sessionBridge.replace(null)
    void queryClient.cancelQueries()
    queryClient.clear()
    identity.value = null
    status.value = 'anonymous'
    restoreError.value = null
    writeSession(null)
    reason.value = message
    initialized = true
  }
  sessionBridge.onExpired(() => clear('登录状态已失效，请重新登录'))

  function schedule(session: StoredSession) {
    clearTimeout(timer)
    timer = setTimeout(
      () => clear('登录已到期，请重新登录'),
      Math.min(Date.parse(session.expiresAt) - Date.now(), 2_147_483_647),
    )
  }

  async function verify(session: StoredSession) {
    sessionBridge.replace(session.accessToken)
    const epoch = sessionBridge.snapshot().generation
    status.value = 'restoring'
    try {
      const employee = await currentIdentity()
      if (!sessionBridge.isCurrent(epoch)) return
      if (!parseFuture(session)) {
        clear('登录已到期，请重新登录')
        return
      }
      identity.value = employee
      status.value = 'authenticated'
      restoreError.value = null
      writeSession(session)
      schedule(session)
    } catch (error) {
      if (!sessionBridge.isCurrent(epoch)) return
      if (error instanceof ApiProblem && [401, 403].includes(error.status)) {
        clear(error.message)
        return
      }
      identity.value = null
      status.value = 'unavailable'
      restoreError.value = error
      // 暂时故障仅保留用于再次验证的凭证，不能允许进入受保护页面。
      writeSession(session)
    }
  }
  function parseFuture(session: StoredSession) {
    return Date.parse(session.expiresAt) > Date.now()
  }

  /** 多个路由同时恢复时只发送一次 /me。 */
  async function restore(force = false): Promise<void> {
    if (restoring) return restoring
    if (initialized && !force) return
    initialized = true
    const stored = readSession()
    if (!stored) {
      clear()
      return
    }
    restoring = verify(stored).finally(() => {
      restoring = null
    })
    return restoring
  }

  async function login(username: string, password: string) {
    clear()
    const epoch = sessionBridge.snapshot().generation
    const session = await createSession(username.trim(), password)
    if (!sessionBridge.isCurrent(epoch)) return
    await verify(session)
    if (status.value === 'unavailable') throw restoreError.value
  }

  /** 撤销失败也清理本地，但调用方需如实告知用户服务器撤销尚未确认。 */
  async function logout() {
    try {
      await revokeSession()
    } finally {
      clear()
    }
  }
  async function updatePassword(current: string, replacement: string) {
    await changePassword(current, replacement)
    clear('密码已修改，请重新登录')
  }
  return {
    identity,
    status,
    reason,
    restoreError,
    authenticated,
    restore,
    login,
    logout,
    clear,
    updatePassword,
  }
})
