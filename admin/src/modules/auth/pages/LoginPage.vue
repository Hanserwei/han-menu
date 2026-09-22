<script setup lang="ts">
import { ref, reactive, computed, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Form, FormItem, Input, InputPassword, Button, Alert } from 'antdv-next'
import { ArrowRightOutlined, SafetyOutlined } from '@antdv-next/icons'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
import { ApiProblem } from '@/shared/api/problem'
import { useSessionStore } from '../model/session.store'

const session = useSessionStore()
const router = useRouter()
const route = useRoute()
const form = reactive({ username: '', password: '' })
const pending = ref(false)
const error = ref<unknown>(null)
const remaining = ref(0)
let countdown: ReturnType<typeof setInterval> | undefined
const rules = {
  username: [
    { required: true, message: '请输入用户名' },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9_]{2,31}$/,
      message: '用户名为字母开头的 3—32 位字母、数字或下划线',
    },
  ],
  password: [{ required: true, message: '请输入密码' }],
}
const reason = computed(() =>
  route.query.notice === 'local-logout'
    ? '已退出当前浏览器；服务器暂不可达，未确认服务端会话撤销。'
    : session.reason,
)

/** 登录后只接受本站安全路径；路由守卫会继续检查目标资源角色。 */
async function proceed() {
  const target = typeof route.query.redirect === 'string' ? route.query.redirect : '/workspace'
  await router.replace(
    target.startsWith('/') &&
      !target.startsWith('//') &&
      !target.includes('\\') &&
      !target.startsWith('/login')
      ? target
      : '/workspace',
  )
}
async function submit() {
  if (pending.value || remaining.value) return
  pending.value = true
  error.value = null
  try {
    await session.login(form.username, form.password)
    if (session.authenticated) await proceed()
  } catch (failure) {
    error.value = failure
    if (failure instanceof ApiProblem && failure.retryAfter > 0) {
      remaining.value = failure.retryAfter
      clearInterval(countdown)
      countdown = setInterval(() => {
        if (--remaining.value <= 0) clearInterval(countdown)
      }, 1000)
    }
  } finally {
    form.password = ''
    pending.value = false
  }
}
async function restore() {
  await session.restore(true)
  if (session.authenticated) await proceed()
}
onUnmounted(() => {
  clearInterval(countdown)
  form.password = ''
})
</script>
<template>
  <main class="login-page">
    <section class="login-brand" aria-label="HAN MENU 商家管理">
      <div class="wordmark">HAN MENU</div>
      <p>商家管理</p>
      <div class="login-intro">
        <span class="eyebrow">专注经营 · 从容管理</span>
        <h1>每一份用心，<br />都有序抵达。</h1>
        <p>从一份订单开始，<br />让日常经营井然有序。</p>
      </div>
      <span class="brand-caption">HAN MENU · 商家工作空间</span>
    </section>
    <section class="login-content">
      <div class="login-form-wrap">
        <span class="eyebrow">欢迎回来</span>
        <h2>登录商家管理</h2>
        <p class="muted">使用你的员工账号，继续今天的工作。</p>
        <Alert v-if="reason" type="info" :title="reason" show-icon class="form-notice" />
        <div v-if="session.status === 'unavailable'" class="form-notice">
          <ProblemAlert :error="session.restoreError" retry @retry="restore" />
          <Button type="link" @click="session.clear()">使用其他账号登录</Button>
        </div>
        <ProblemAlert :error="error" />
        <Form
          v-if="session.status !== 'unavailable'"
          :model="form"
          :rules="rules"
          layout="vertical"
          @finish="submit"
        >
          <FormItem label="用户名" name="username"
            ><Input
              v-model:value="form.username"
              placeholder="请输入员工用户名"
              autocomplete="username"
              :maxlength="32"
              size="large"
              :disabled="pending"
          /></FormItem>
          <FormItem label="密码" name="password"
            ><InputPassword
              v-model:value="form.password"
              placeholder="请输入密码"
              autocomplete="current-password"
              :maxlength="128"
              size="large"
              :disabled="pending"
          /></FormItem>
          <Button
            type="primary"
            html-type="submit"
            size="large"
            block
            :loading="pending"
            :disabled="remaining > 0"
            :aria-label="remaining ? `${remaining} 秒后重试` : '登录'"
            >{{ remaining ? `${remaining} 秒后重试` : '登录' }}<ArrowRightOutlined v-if="!pending"
          /></Button>
        </Form>
        <div class="login-security">
          <SafetyOutlined /><span>仅授权员工可访问商家工作空间</span>
        </div>
      </div>
      <footer>HAN MENU <span>经营有序，服务有温度。</span></footer>
    </section>
  </main>
</template>
