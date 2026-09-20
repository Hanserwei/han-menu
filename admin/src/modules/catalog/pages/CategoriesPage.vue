<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import {
  Button,
  Table,
  Tag,
  Modal,
  Form,
  FormItem,
  Input,
  InputNumber,
  Select,
  Switch,
} from 'antdv-next'
import { catalogApi, type Category, type Kind } from '../api/catalog'
import { revision } from '@/shared/api/result'
import { queryClient } from '@/shared/api/query-client'
import { useCommand } from '@/shared/model/use-command'
import { useEditorGuard } from '@/shared/model/use-editor-guard'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
import WriteFeedback from '@/shared/ui/WriteFeedback.vue'
const query = useQuery({
  queryKey: ['catalog-categories'],
  queryFn: ({ signal }) => catalogApi.categories(signal),
})
const command = useCommand(),
  { pending, error, needsReload } = command
const current = ref<Category>(),
  open = ref(false),
  baseline = ref('')
const form = reactive({ kind: 'DISH' as Kind, name: '', sortOrder: 0, enabled: true })
const dirty = computed(() => open.value && baseline.value !== JSON.stringify(form)),
  { confirmDiscard } = useEditorGuard(dirty, pending)
const columns = [
  { title: '分类名称', dataIndex: 'name' },
  { title: '种类', key: 'kind' },
  { title: '排序号', dataIndex: 'sortOrder' },
  { title: '状态', key: 'enabled' },
  { title: '操作', key: 'actions' },
]
function edit(value?: Category) {
  current.value = value
  Object.assign(form, {
    kind: value?.kind || 'DISH',
    name: value?.name || '',
    sortOrder: value?.sortOrder || 0,
    enabled: value?.enabled ?? true,
  })
  baseline.value = JSON.stringify(form)
  error.value = null
  open.value = true
}
async function close() {
  if (await confirmDiscard()) open.value = false
}
async function reload() {
  if (!(await confirmDiscard())) return
  if (open.value && current.value?.id) {
    try {
      edit(await catalogApi.category(current.value.id))
    } catch (failure) {
      error.value = failure
    }
  } else {
    error.value = null
    await query.refetch()
  }
}
async function refresh() {
  await queryClient.invalidateQueries({ queryKey: ['catalog-categories'] })
}
async function save() {
  await command.run(async () => {
    if (current.value) {
      const { id, version } = revision(current.value)
      await catalogApi.updateCategory(id, {
        name: form.name.trim(),
        sortOrder: form.sortOrder,
        enabled: form.enabled,
        version,
      })
    } else
      await catalogApi.createCategory({
        kind: form.kind,
        name: form.name.trim(),
        sortOrder: form.sortOrder,
      })
    baseline.value = JSON.stringify(form)
    open.value = false
    await refresh()
  })
}
async function remove(value: Category) {
  if (
    !(await command.confirm(
      `删除分类“${value.name}”？`,
      '仅无商品引用的分类可删除，操作不可恢复。',
    ))
  )
    return
  await command.run(async () => {
    const { id, version } = revision(value)
    await catalogApi.deleteCategory(id, version)
    await refresh()
  }, '分类已删除')
}
</script>
<template>
  <div class="page-heading heading-inline">
    <div>
      <h1>分类管理</h1>
      <p class="muted">分别维护菜品与套餐分类，排序号越小越靠前。</p>
    </div>
    <Button type="primary" :disabled="pending" @click="edit()">新增分类</Button>
  </div>
  <ProblemAlert :error="query.error.value" retry @retry="query.refetch()" /><WriteFeedback
    v-if="!open"
    :error="error"
    :needs-reload="needsReload"
    :pending="pending"
    @reload="reload"
  />
  <Table
    :columns="columns"
    :data-source="query.data.value || []"
    row-key="id"
    :pagination="false"
    :loading="query.isFetching.value"
    :locale="{ emptyText: query.isError.value ? '分类列表暂不可用' : '暂无分类，可新增分类' }"
    :scroll="{ x: 760 }"
    ><template #bodyCell="{ column, record }"
      ><template v-if="column.key === 'kind'">{{
        record.kind === 'DISH' ? '菜品' : '套餐'
      }}</template
      ><Tag v-if="column.key === 'enabled'" :color="record.enabled ? 'success' : 'default'">{{
        record.enabled ? '启用' : '停用'
      }}</Tag>
      <div v-if="column.key === 'actions'" class="row-actions">
        <Button type="link" :disabled="pending" @click="edit(record)">编辑</Button
        ><Button type="link" danger :disabled="pending || needsReload" @click="remove(record)"
          >删除</Button
        >
      </div></template
    ></Table
  >
  <Modal :title="current ? '编辑分类' : '新增分类'" :open="open" :footer="null" @cancel="close"
    ><WriteFeedback
      :error="error"
      :needs-reload="needsReload"
      :pending="pending"
      @reload="reload"
    /><Form :model="form" layout="vertical" :disabled="pending" @finish="save"
      ><FormItem label="分类种类" name="kind"
        ><Select
          v-model:value="form.kind"
          :disabled="!!current"
          :options="[
            { label: '菜品', value: 'DISH' },
            { label: '套餐', value: 'SET_MEAL' },
          ]" /></FormItem
      ><FormItem
        label="分类名称"
        name="name"
        :rules="[
          { required: true, whitespace: true, max: 50, message: '请输入分类名称，最多50字' },
        ]"
        ><Input v-model:value="form.name" :maxlength="50" /></FormItem
      ><FormItem
        label="排序号"
        name="sortOrder"
        :rules="[
          { required: true, type: 'integer', min: 0, max: 10000, message: '排序号为0—10000整数' },
        ]"
        ><InputNumber
          v-model:value="form.sortOrder"
          :min="0"
          :max="10000"
          :precision="0" /></FormItem
      ><FormItem v-if="current" label="启用分类"><Switch v-model:checked="form.enabled" /></FormItem
      ><Button type="primary" html-type="submit" :loading="pending" :disabled="needsReload"
        >保存分类</Button
      ></Form
    ></Modal
  >
</template>
