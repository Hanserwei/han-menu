<script setup lang="ts">
import { reactive, ref, onBeforeUnmount, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import {
  App,
  Button,
  Descriptions,
  DescriptionsItem,
  Form,
  FormItem,
  InputPassword,
} from 'antdv-next'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
import { useSessionStore } from '../model/session.store'

const session = useSessionStore()
const router = useRouter()
const { modal } = App.useApp()
const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const pending = ref(false)
const error = ref<unknown>(null)
const dirty = ref(false)
watch(form, () => {
  dirty.value = Object.values(form).some(Boolean)
})
const rules = {
  currentPassword: [{ required: true, message: '请输入当前密码' }],
  newPassword: [
    { required: true, message: '请输入新密码' },
    { min: 12, max: 64, message: '新密码长度为 12—64 个字符' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码' },
    {
      validator: async (_rule: unknown, value: string) => {
        if (value !== form.newPassword) throw new Error('两次输入的新密码不一致')
      },
    },
  ],
}
async function submit() {
  if (pending.value) return
  pending.value = true
  error.value = null
  try {
    await session.updatePassword(form.currentPassword, form.newPassword)
    dirty.value = false
    await router.replace('/login')
  } catch (failure) {
    error.value = failure
  } finally {
    pending.value = false
  }
}
/** 表单离开确认不持久化任何密码；登录失效时允许安全退出。 */
onBeforeRouteLeave(() => {
  if (!dirty.value || !session.authenticated) return true
  return new Promise<boolean>((resolve) =>
    modal.confirm({
      title: '放弃尚未提交的修改？',
      content: '离开后输入的密码将被清空。',
      okText: '放弃修改',
      cancelText: '继续编辑',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    }),
  )
})
function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value) event.preventDefault()
}
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnload)
  form.currentPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
})
</script>
<template>
  <div class="page-heading">
    <h1>我的账号</h1>
    <p class="muted">查看当前身份，维护账号安全。</p>
  </div>
  <section class="account-profile surface-section">
    <h2>账号信息</h2>
    <Descriptions :column="2" :colon="false">
      <DescriptionsItem label="用户名">{{ session.identity?.username }}</DescriptionsItem>
      <DescriptionsItem label="显示名称">{{ session.identity?.displayName }}</DescriptionsItem>
      <DescriptionsItem label="角色">{{
        session.identity?.role === 'ADMIN' ? '管理员' : '普通员工'
      }}</DescriptionsItem>
      <DescriptionsItem label="员工编号"
        ><span class="uuid">{{ session.identity?.id }}</span></DescriptionsItem
      >
    </Descriptions>
  </section>
  <section class="password-section surface-section">
    <h2>修改密码</h2>
    <p class="muted">修改成功后，所有已登录设备都需要重新登录。</p>
    <ProblemAlert :error="error" />
    <Form :model="form" :rules="rules" layout="vertical" @finish="submit">
      <FormItem label="当前密码" name="currentPassword"
        ><InputPassword
          v-model:value="form.currentPassword"
          autocomplete="current-password"
          :maxlength="128"
          :disabled="pending"
      /></FormItem>
      <FormItem label="新密码" name="newPassword"
        ><InputPassword
          v-model:value="form.newPassword"
          autocomplete="new-password"
          placeholder="12—64 个字符"
          :maxlength="64"
          :disabled="pending"
      /></FormItem>
      <FormItem label="确认新密码" name="confirmPassword"
        ><InputPassword
          v-model:value="form.confirmPassword"
          autocomplete="new-password"
          :maxlength="64"
          :disabled="pending"
      /></FormItem>
      <Button type="primary" html-type="submit" :loading="pending">更新密码</Button>
    </Form>
  </section>
</template>
