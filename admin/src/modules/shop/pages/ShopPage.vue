<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { Button, Form, FormItem, Input, TextArea, Tag, Skeleton } from 'antdv-next'
import { shopApi, type Shop } from '../api/shop'
import { queryClient } from '@/shared/api/query-client'
import { ApiProblem } from '@/shared/api/problem'
import { useCommand } from '@/shared/model/use-command'
import { useEditorGuard } from '@/shared/model/use-editor-guard'
import WriteFeedback from '@/shared/ui/WriteFeedback.vue'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
const current = ref<Shop>(),
  loading = ref(false),
  loadError = ref<unknown>(null),
  baseline = ref('')
const form = reactive({ name: '', phone: '', address: '' })
const command = useCommand(),
  { pending, error, needsReload } = command
const dirty = computed(() => !!current.value && JSON.stringify(form) !== baseline.value)
const { confirmDiscard } = useEditorGuard(dirty, pending)
let generation = 0
function apply(value: Shop) {
  current.value = value
  Object.assign(form, {
    name: value.name || '',
    phone: value.phone || '',
    address: value.address || '',
  })
  baseline.value = JSON.stringify(form)
}
async function load() {
  if (!(await confirmDiscard())) return
  const epoch = ++generation
  loading.value = true
  loadError.value = null
  try {
    const value = await shopApi.get()
    if (epoch === generation) {
      apply(value)
      error.value = null
    }
  } catch (failure) {
    if (epoch === generation) loadError.value = failure
  } finally {
    if (epoch === generation) loading.value = false
  }
}
function version() {
  if (!Number.isSafeInteger(current.value?.version))
    throw new ApiProblem(502, '门店版本缺失，请重新读取')
  return current.value!.version!
}
async function refreshRelated() {
  await Promise.all(
    ['storefront', 'workspace'].map((key) => queryClient.invalidateQueries({ queryKey: [key] })),
  )
}
async function save() {
  await command.run(async () => {
    apply(
      await shopApi.update({
        ...form,
        name: form.name.trim(),
        phone: form.phone.trim(),
        address: form.address.trim(),
        version: version(),
      }),
    )
    await refreshRelated()
  })
}
async function toggle() {
  if (
    dirty.value ||
    !(await command.confirm(
      current.value?.status === 'OPEN' ? '确认打烊？' : '确认开始营业？',
      '仅修改营业状态，已提交订单继续按既有流程处理。',
    ))
  )
    return
  await command.run(async () => {
    apply(await shopApi.status(current.value?.status === 'OPEN' ? 'CLOSED' : 'OPEN', version()))
    await refreshRelated()
  }, '营业状态已更新')
}
const rules = {
  name: [{ required: true, whitespace: true, max: 100, message: '请输入门店名称' }],
  phone: [{ pattern: /^(\+?[1-9][0-9]{6,14})?$/, message: '请输入完整联系电话' }],
  address: [{ max: 300, message: '地址最多300个字符' }],
}
onMounted(load)
onBeforeUnmount(() => generation++)
</script>
<template>
  <div class="page-heading">
    <h1>门店设置</h1>
    <p class="muted">维护单店资料与营业状态。</p>
  </div>
  <ProblemAlert :error="loadError" retry @retry="load" /><WriteFeedback
    :error="error"
    :needs-reload="needsReload"
    :pending="pending"
    @reload="load"
  />
  <Skeleton v-if="loading" active />
  <template v-else-if="current">
    <section class="surface-section shop-form">
      <h2>基本资料</h2>
      <Form :model="form" :rules="rules" layout="vertical" :disabled="pending" @finish="save">
        <FormItem label="门店名称" name="name"
          ><Input v-model:value="form.name" :maxlength="100" /></FormItem
        ><FormItem label="联系电话" name="phone"
          ><Input v-model:value="form.phone" :maxlength="16" /></FormItem
        ><FormItem label="门店地址" name="address"
          ><TextArea v-model:value="form.address" :maxlength="300" :rows="3"
        /></FormItem>
        <Button type="primary" html-type="submit" :loading="pending" :disabled="needsReload"
          >保存资料</Button
        ><Button class="inline-space" :disabled="pending" @click="load">重新读取</Button>
      </Form>
    </section>
    <section class="surface-section shop-form">
      <h2>营业状态</h2>
      <Tag :color="current.status === 'OPEN' ? 'success' : 'default'">{{
        current.status === 'OPEN' ? '营业中' : '已打烊'
      }}</Tag>
      <p class="muted">开店前请完善联系电话和地址。营业期间不可清空联系资料。</p>
      <Button
        :danger="current.status === 'OPEN'"
        :disabled="pending || dirty || needsReload"
        @click="toggle"
        >{{ current.status === 'OPEN' ? '打烊' : '开始营业' }}</Button
      >
      <p v-if="dirty" class="muted">请先保存资料或放弃修改，再变更营业状态。</p>
    </section>
  </template>
</template>
