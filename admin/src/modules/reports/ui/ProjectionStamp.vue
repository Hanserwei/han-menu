<script setup lang="ts">
import type { Metadata } from '../model/reporting'
import { dateTime } from '@/shared/lib/time'
defineProps<{ value?: Metadata }>()
</script>
<template>
  <div class="data-timestamp">
    投影更新于 {{ dateTime(value?.updatedAt) }}
    <details>
      <summary>投影诊断信息</summary>
      代际 {{ value?.generation ?? '—' }} · 修订 {{ value?.revision ?? '—' }} · 控制版本
      {{ value?.version ?? '—' }}
    </details>
  </div>
</template>
