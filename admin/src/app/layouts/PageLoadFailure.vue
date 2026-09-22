<script setup lang="ts">
import { Result, Button } from 'antdv-next'
/** 分包请求失败后由本地已加载组件兜底，只允许员工主动刷新，不自动重载或丢失输入。 */
function reload() {
  window.location.reload()
}
</script>
<template>
  <main class="page-load-failure" aria-label="页面加载恢复">
    <Result
      status="warning"
      title="页面未能加载"
      sub-title="网络可能暂时中断，或页面版本已更新。检查网络后重新加载此页；刷新会清除尚未保存的输入。"
    >
      <template #extra><Button type="primary" @click="reload">重新加载此页</Button></template>
    </Result>
  </main>
</template>
