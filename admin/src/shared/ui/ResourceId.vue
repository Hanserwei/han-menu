<script setup lang="ts">
import { Button, Tooltip, App } from 'antdv-next'
import { CopyOutlined } from '@antdv-next/icons'
const props = defineProps<{ value?: string }>()
const { message } = App.useApp()
/** 显示可缩略，复制及所有请求始终使用完整标识。 */
async function copy() {
  try {
    await navigator.clipboard.writeText(props.value || '')
    message.success('已复制完整编号')
  } catch {
    message.error('复制失败，请从完整编号中手动复制')
  }
}
</script>
<template>
  <span class="resource-id"
    ><Tooltip :title="value"
      ><span>{{ value ? `${value.slice(0, 8)}…${value.slice(-4)}` : '—' }}</span></Tooltip
    ><Button v-if="value" type="text" size="small" aria-label="复制完整编号" @click.stop="copy"
      ><CopyOutlined /></Button
  ></span>
</template>
