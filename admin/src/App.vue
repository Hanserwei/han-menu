<script setup lang="ts">
import { ConfigProvider, App as AntApp } from 'antdv-next'
import zhCN from 'antdv-next/locale/zh_CN'
import theme from './app/theme.json'
</script>
<template>
  <ConfigProvider :locale="zhCN" :theme="theme"
    ><AntApp><RouterView /></AntApp
  ></ConfigProvider>
</template>
