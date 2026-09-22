<script setup lang="ts">
import { ref, watch } from 'vue'
import dayjs, { type Dayjs } from 'dayjs'
import { Button, DatePicker } from 'antdv-next'
import { period, recentDays, type Period } from '../model/reporting'
import ProblemAlert from '@/shared/ui/ProblemAlert.vue'
const props = defineProps<{ value: Period; disabled?: boolean }>()
const emit = defineEmits<{ change: [value: Period] }>()
const dates = ref<[Dayjs, Dayjs]>(),
  error = ref<unknown>(null)
watch(
  () => props.value,
  (value) => {
    dates.value = [dayjs(value.from), dayjs(value.to)]
  },
  { immediate: true },
)
function apply() {
  try {
    const value = period(
      dates.value?.[0]?.format('YYYY-MM-DD') || '',
      dates.value?.[1]?.format('YYYY-MM-DD') || '',
    )
    error.value = null
    emit('change', value)
  } catch (failure) {
    error.value = failure
  }
}
function recent(days: number) {
  error.value = null
  emit('change', recentDays(days))
}
</script>
<template>
  <div class="filter-bar">
    <span>经营日期</span><Button :disabled="disabled" @click="recent(7)">最近7天</Button
    ><Button :disabled="disabled" @click="recent(30)">最近30天</Button
    ><DatePicker.RangePicker
      v-model:value="dates"
      :disabled="disabled"
      :placeholder="['开始日期', '结束日期']"
    /><Button :disabled="disabled" @click="apply">查询</Button
    ><span class="muted">北京时间 · 包含首尾日期</span>
  </div>
  <ProblemAlert :error="error" />
</template>
