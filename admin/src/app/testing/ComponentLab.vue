<script setup lang="ts">
import { ref, reactive } from 'vue'
import { App, Table, Form, FormItem, Input, DatePicker, Upload, Drawer, Button } from 'antdv-next'

/** 仅开发模式挂载的组件兼容性验收页，不进入生产导航或发送业务写请求。 */
const { message } = App.useApp()
const draft = reactive({ name: '' })
const open = ref(false)
const selectedFile = ref('')
const columns = [
  { title: '组件', dataIndex: 'name' },
  { title: '状态', dataIndex: 'status' },
]
const rows = [{ key: 'integration', name: 'antdv-next', status: '等待验证' }]
function fileSelected(file: File) {
  selectedFile.value = file.name
  return false
}
function submit() {
  message.success('表单校验通过')
  open.value = true
}
</script>
<template>
  <h1>组件兼容性验收</h1>
  <p class="muted">开发环境使用，不包含业务数据。</p>
  <Form :model="draft" layout="vertical" style="max-width: 420px" @finish="submit">
    <FormItem label="名称" name="name" :rules="[{ required: true, message: '请输入名称' }]"
      ><Input v-model:value="draft.name"
    /></FormItem>
    <FormItem label="日期"><DatePicker placeholder="选择日期" /></FormItem>
    <Upload accept="image/png,image/jpeg" :before-upload="fileSelected" :show-upload-list="false"
      ><Button>选择测试图片</Button></Upload
    >
    <p>{{ selectedFile }}</p>
    <Button type="primary" html-type="submit">验证表单与抽屉</Button>
  </Form>
  <Table :columns="columns" :data-source="rows" :pagination="false" style="margin-top: 24px" />
  <Drawer title="抽屉验证" :open="open" @close="open = false"
    ><p>名称：{{ draft.name }}</p>
    <Button @click="open = false">完成验证</Button></Drawer
  >
</template>
