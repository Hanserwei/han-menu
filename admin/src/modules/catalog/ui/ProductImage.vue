<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Button } from 'antdv-next'
import { PictureOutlined } from '@antdv-next/icons'
import { catalogApi } from '../api/catalog'
const props = defineProps<{ id?: string; large?: boolean }>()
const failed = ref(false)
const query = useQuery({
  queryKey: computed(() => ['catalog-image', props.id]),
  enabled: computed(() => !!props.id),
  queryFn: ({ signal }) => catalogApi.image(props.id!, signal),
  staleTime: 240_000,
})
watch(
  () => props.id,
  () => (failed.value = false),
)
async function retry() {
  failed.value = false
  await query.refetch()
}
</script>
<template>
  <div class="product-image" :class="{ 'large-image': large }">
    <img
      v-if="id && query.data.value?.url && !failed"
      :src="query.data.value.url"
      alt="商品图片"
      @error="failed = true"
    /><template v-else
      ><PictureOutlined /><span v-if="large">{{
        id ? '图片暂不可用' : '未设置图片'
      }}</span></template
    >
  </div>
  <Button v-if="id && (failed || query.isError.value)" type="link" size="small" @click="retry"
    >刷新图片</Button
  >
</template>
