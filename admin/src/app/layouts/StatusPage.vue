<script setup lang="ts">
import { Result, Button } from 'antdv-next'
import { useRouter } from 'vue-router'
const props = defineProps<{ kind: 'forbidden' | 'missing' | 'upcoming' }>()
const router = useRouter()
</script>
<template>
  <Result
    :status="kind === 'forbidden' ? '403' : kind === 'missing' ? '404' : 'info'"
    :title="
      kind === 'forbidden'
        ? '你没有访问此页面的权限'
        : kind === 'missing'
          ? '页面不存在'
          : '此功能暂未开放'
    "
    :sub-title="
      props.kind === 'forbidden'
        ? '请使用具有相应权限的员工账号。'
        : '你可以返回工作台，继续查看当前经营概况。'
    "
  >
    <template #extra
      ><Button type="primary" @click="router.push('/workspace')">返回工作台</Button></template
    >
  </Result>
</template>
