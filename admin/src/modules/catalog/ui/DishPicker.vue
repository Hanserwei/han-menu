<script setup lang="ts">
import { ref, computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Modal, Select, Table, Button } from 'antdv-next'
import { catalogApi, type Product } from '../api/catalog'
import PagePagination from '@/shared/ui/PagePagination.vue'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
import { money } from '@/shared/lib/display'
const props = defineProps<{ open: boolean; selected: string[] }>()
defineEmits<{ close: []; choose: [product: Product] }>()
const category = ref<string>(),
  page = ref(0),
  size = ref(20)
const categories = useQuery({
  queryKey: ['catalog-categories'],
  queryFn: ({ signal }) => catalogApi.categories(signal),
})
const options = computed(() =>
  (categories.data.value || [])
    .filter((c) => c.kind === 'DISH')
    .map((c) => ({ label: c.name, value: c.id })),
)
const query = useQuery({
  queryKey: computed(() => ['catalog-picker', category.value, page.value, size.value]),
  enabled: computed(() => props.open),
  queryFn: ({ signal }) =>
    catalogApi.products('DISH', category.value, page.value, size.value, signal),
})
const columns = [
  { title: '菜品', dataIndex: 'name' },
  { title: '价格', key: 'price' },
  { title: '状态', key: 'status' },
  { title: '操作', key: 'actions' },
]
</script>
<template>
  <Modal title="选择组成菜品" :open="open" :footer="null" :width="760" @cancel="$emit('close')"
    ><Select
      v-model:value="category"
      aria-label="组成菜品分类"
      placeholder="全部分类"
      allow-clear
      :options="options"
      style="width: 240px; margin-bottom: 16px"
      @change="page = 0" /><ProblemAlert
      :error="query.error.value || categories.error.value"
      retry
      @retry="
        () => {
          query.refetch()
          categories.refetch()
        }
      " /><Table
      :columns="columns"
      :data-source="query.data.value?.items || []"
      row-key="id"
      :pagination="false"
      :loading="query.isFetching.value"
      ><template #bodyCell="{ column, record }"
        ><template v-if="column.key === 'price'">{{ money(record.price) }}</template
        ><template v-if="column.key === 'status'">{{
          record.status === 'ON_SALE' ? '在售' : '下架'
        }}</template
        ><Button
          v-if="column.key === 'actions'"
          :disabled="selected.includes(record.id || '')"
          @click="$emit('choose', record)"
          >{{ selected.includes(record.id || '') ? '已添加' : '添加' }}</Button
        ></template
      ></Table
    ><PagePagination
      :page="page"
      :size="size"
      :total="query.data.value?.totalElements || 0"
      @change="
        (p, s) => {
          page = p
          size = s
        }
      "
  /></Modal>
</template>
