<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { init, use, type EChartsType } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, AriaComponent } from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import type { components } from '@/shared/api/schema'
use([LineChart, GridComponent, TooltipComponent, AriaComponent, SVGRenderer])
const props = defineProps<{
  days: components['schemas']['Day'][]
  metric: 'turnover' | 'submittedOrders' | 'newCustomers'
  label: string
}>()
const container = ref<HTMLElement>()
let chart: EChartsType | undefined, observer: ResizeObserver | undefined
let resizeFrame = 0
function render() {
  chart?.setOption(
    {
      animation: false,
      color: ['#466957'],
      aria: { enabled: true },
      tooltip: { trigger: 'axis', renderMode: 'richText' },
      grid: { left: 65, right: 24, top: 35, bottom: 35 },
      xAxis: {
        type: 'category',
        data: props.days.map((day) => day.date),
        boundaryGap: false,
        axisLabel: { color: '#68736b' },
      },
      yAxis: {
        type: 'value',
        name: props.metric === 'turnover' ? '元' : props.metric === 'newCustomers' ? '人' : '单',
        minInterval: props.metric === 'turnover' ? undefined : 1,
        splitLine: { lineStyle: { color: '#e8ece6' } },
      },
      series: [
        {
          type: 'line',
          name: props.label,
          data: props.days.map((day) => day[props.metric] ?? null),
          smooth: false,
          symbolSize: 5,
          lineStyle: { width: 2 },
          areaStyle: { color: '#edf3ed' },
        },
      ],
    },
    true,
  )
}
onMounted(() => {
  chart = init(container.value!, undefined, { renderer: 'svg' })
  render()
  // 在下一帧调整画布，避免观察回调中同步改变SVG尺寸造成布局循环。
  observer = new ResizeObserver(() => {
    cancelAnimationFrame(resizeFrame)
    resizeFrame = requestAnimationFrame(() => chart?.resize())
  })
  observer.observe(container.value!)
})
watch(() => [props.days, props.metric], render)
onBeforeUnmount(() => {
  observer?.disconnect()
  cancelAnimationFrame(resizeFrame)
  chart?.dispose()
})
</script>
<template>
  <div
    ref="container"
    class="report-chart"
    role="img"
    :aria-label="`${label}趋势，详细数值见经营日账`"
  />
</template>
<style scoped>
.report-chart {
  height: 300px;
  width: 100%;
}
</style>
