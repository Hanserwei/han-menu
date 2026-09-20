<script setup lang="ts">
import { Alert, Button } from 'antdv-next'
import ProblemAlert from './ProblemAlert.vue'
defineProps<{ error: unknown; needsReload: boolean; pending?: boolean }>()
defineEmits<{ reload: [] }>()
</script>
<template>
  <ProblemAlert :error="error" />
  <Alert
    v-if="needsReload"
    type="warning"
    show-icon
    title="请重新读取后确认"
    description="当前草稿已保留。数据可能已变化，或上次操作结果尚未确定；重新读取前不能再次提交。"
    class="form-notice"
  >
    <template #action
      ><Button :disabled="pending" @click="$emit('reload')">重新读取</Button></template
    >
  </Alert>
</template>
