<script setup lang="ts">
import { computed } from 'vue'
import { Alert, Button } from 'antdv-next'
import { ApiProblem, errorMessage } from '../api/problem'
const props = defineProps<{ error: unknown; retry?: boolean }>()
defineEmits<{ retry: [] }>()
const traceId = computed(() =>
  props.error instanceof ApiProblem ? props.error.traceId : undefined,
)
</script>
<template>
  <Alert v-if="error" type="error" show-icon class="problem-alert" role="alert">
    <template #title>{{ errorMessage(error) }}</template>
    <template #description>
      <details v-if="traceId">
        <summary>查看追踪编号</summary>
        <code>{{ traceId }}</code>
      </details>
    </template>
    <template v-if="retry" #action
      ><Button aria-label="重试" size="small" @click="$emit('retry')">重试</Button></template
    >
  </Alert>
</template>
