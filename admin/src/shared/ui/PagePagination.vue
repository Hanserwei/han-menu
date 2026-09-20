<script setup lang="ts">
import { Pagination } from 'antdv-next'
defineProps<{ page: number; size: number; total: number; disabled?: boolean }>()
defineEmits<{ change: [page: number, size: number] }>()
</script>
<template>
  <div class="list-pagination">
    <Pagination
      :current="page + 1"
      :page-size="size"
      :total="total"
      :page-size-options="[20, 50]"
      show-size-changer
      :disabled="disabled"
      :show-total="(count: number) => `共 ${count} 条`"
      @change="(page: number, size: number) => $emit('change', page - 1, size)"
    />
  </div>
</template>
