<script setup lang="ts">
import { Alert, Button } from 'antdv-next'
/** 图表资源失败不遮蔽已获取的经营数据，保留日账与导出作为可用入口。 */
function reload() {
  window.location.reload()
}
</script>
<template>
  <Alert
    type="warning"
    show-icon
    title="趋势图暂不可用"
    description="可继续查看经营日账或下载报表。网络恢复后重新加载页面，日期筛选将恢复默认值。"
  >
    <template #action><Button @click="reload">重新加载图表</Button></template>
  </Alert>
</template>
