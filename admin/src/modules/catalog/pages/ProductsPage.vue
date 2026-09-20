<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { Button, Table, Select, Tag } from 'antdv-next'
import { catalogApi, type Kind, type Product } from '../api/catalog'
import { queryClient } from '@/shared/api/query-client'
import { revision } from '@/shared/api/result'
import { useCommand } from '@/shared/model/use-command'
import { money } from '@/shared/lib/display'
import ProductImage from '../ui/ProductImage.vue'
import PagePagination from '@/shared/ui/PagePagination.vue'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
import WriteFeedback from '@/shared/ui/WriteFeedback.vue'
const props = defineProps<{ kind: Kind }>(),
  router = useRouter()
const category = ref<string>(),
  page = ref(0),
  size = ref(20)
const command = useCommand(),
  { pending, error, needsReload } = command
const categories = useQuery({
  queryKey: ['catalog-categories'],
  queryFn: ({ signal }) => catalogApi.categories(signal),
})
const options = computed(() =>
  (categories.data.value || [])
    .filter((c) => c.kind === props.kind)
    .map((c) => ({ label: c.name, value: c.id })),
)
const query = useQuery({
  queryKey: computed(() => [
    'catalog-products',
    props.kind,
    category.value,
    page.value,
    size.value,
  ]),
  queryFn: ({ signal }) =>
    catalogApi.products(props.kind, category.value, page.value, size.value, signal),
})
const path = computed(() => (props.kind === 'DISH' ? '/catalog/dishes' : '/catalog/meals'))
const title = computed(() => (props.kind === 'DISH' ? '菜品' : '套餐'))
const columns = [
  { title: '图片', key: 'image', width: 80 },
  { title: '名称', dataIndex: 'name' },
  { title: '分类', key: 'category' },
  { title: '售价', key: 'price' },
  { title: '状态', key: 'status' },
  { title: '操作', key: 'actions', width: 240 },
]
async function refresh() {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['catalog-products'] }),
    queryClient.invalidateQueries({ queryKey: ['catalog-picker'] }),
    queryClient.invalidateQueries({ queryKey: ['workspace'] }),
  ])
  if (page.value > 0 && !query.data.value?.items?.length) page.value--
}
async function sale(value: Product) {
  const target = value.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE'
  if (
    !(await command.confirm(
      target === 'OFF_SALE' ? '下架商品？' : '上架商品？',
      target === 'OFF_SALE'
        ? '下架后顾客不能继续购买；在售套餐引用会由服务器检查。'
        : '请确认价格和规格，分类与组成菜品必须符合可售条件。',
    ))
  )
    return
  return command.run(async () => {
    const { id, version } = revision(value)
    await catalogApi.sale(id, target, version)
    await refresh()
  }, '商品状态已更新')
}
async function edit(value: Product) {
  if (value.status === 'ON_SALE') {
    if (await sale(value)) await router.push(`${path.value}/${value.id}/edit`)
    return
  }
  void router.push(`${path.value}/${value.id}/edit`)
}
async function remove(value: Product) {
  if (
    !(await command.confirm(
      `删除“${value.name}”？`,
      '只允许删除已下架且无引用的商品；不会删除共享图片。',
    ))
  )
    return
  await command.run(async () => {
    const { id, version } = revision(value)
    await catalogApi.remove(id, version)
    await refresh()
  }, '商品已删除')
}
async function reload() {
  error.value = null
  await query.refetch()
}
</script>
<template>
  <div class="page-heading heading-inline">
    <div>
      <h1>{{ title }}管理</h1>
      <p class="muted">维护{{ title }}资料，明确控制上下架。</p>
    </div>
    <Button type="primary" :disabled="pending" @click="router.push(`${path}/new`)"
      >新增{{ title }}</Button
    >
  </div>
  <div class="filter-bar">
    <Select
      v-model:value="category"
      aria-label="商品分类筛选"
      placeholder="全部分类"
      allow-clear
      :options="options"
      @change="page = 0"
    /><Button
      @click="
        () => {
          category = undefined
          page = 0
        }
      "
      >重置</Button
    ><Button @click="reload">刷新</Button>
  </div>
  <ProblemAlert
    :error="query.error.value || categories.error.value"
    retry
    @retry="
      () => {
        query.refetch()
        categories.refetch()
      }
    "
  /><WriteFeedback :error="error" :needs-reload="needsReload" :pending="pending" @reload="reload" />
  <Table
    :columns="columns"
    :data-source="query.data.value?.items || []"
    row-key="id"
    :pagination="false"
    :loading="query.isFetching.value"
    :locale="{
      emptyText: query.isError.value
        ? '商品列表暂不可用'
        : category
          ? '该分类暂无商品'
          : '暂无商品，可新增商品',
    }"
    :scroll="{ x: 850 }"
    ><template #bodyCell="{ column, record }"
      ><ProductImage v-if="column.key === 'image'" :id="record.imageId" /><template
        v-if="column.key === 'category'"
        >{{ categories.data.value?.find((c) => c.id === record.categoryId)?.name || '—' }}</template
      ><template v-if="column.key === 'price'">{{ money(record.price) }}</template
      ><Tag
        v-if="column.key === 'status'"
        :color="record.status === 'ON_SALE' ? 'success' : 'default'"
        >{{ record.status === 'ON_SALE' ? '在售' : '下架' }}</Tag
      >
      <div v-if="column.key === 'actions'" class="row-actions">
        <Button type="link" :disabled="pending || needsReload" @click="edit(record)">{{
          record.status === 'ON_SALE' ? '下架后编辑' : '编辑'
        }}</Button
        ><Button type="link" :disabled="pending || needsReload" @click="sale(record)">{{
          record.status === 'ON_SALE' ? '下架' : '上架'
        }}</Button
        ><Button
          type="link"
          danger
          :disabled="pending || needsReload || record.status === 'ON_SALE'"
          @click="remove(record)"
          >删除</Button
        >
      </div></template
    ></Table
  >
  <PagePagination
    :page="page"
    :size="size"
    :total="query.data.value?.totalElements || 0"
    :disabled="pending"
    @change="
      (p, s) => {
        page = p
        size = s
      }
    "
  />
</template>
