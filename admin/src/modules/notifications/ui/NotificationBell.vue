<script setup lang="ts">
import { computed, watch, onBeforeUnmount, h } from 'vue'
import { useRouter } from 'vue-router'
import { App, Badge, Button, Tooltip, Tag } from 'antdv-next'
import { BellOutlined } from '@antdv-next/icons'
import { useNotificationsStore } from '../model/notifications.store'
import { connectionView, noticeLabel } from '../model/connection-view'
const store = useNotificationsStore(),
  router = useRouter()
const { notification } = App.useApp()
const connection = computed(() => connectionView(store.state))
const key = 'merchant-order-notice'
/** 冷启动历史补查不连续弹窗；新通知合并成一个提示，点击后读取权威订单详情。 */
watch(
  () => store.state.activity?.revision,
  () => {
    const activity = store.state.activity
    if (!activity || document.visibilityState !== 'visible') return
    const notice = activity.notice
    notification.info({
      key,
      title: noticeLabel(notice.type),
      description: '收到新的订单提醒，请及时查看处理。',
      duration: 6,
      actions: h(
        Button,
        {
          type: 'link',
          onClick: () => {
            notification.destroy(key)
            void router.push(`/orders/${notice.orderId}`)
          },
        },
        () => '查看订单',
      ),
    })
  },
)
onBeforeUnmount(() => notification.destroy(key))
</script>
<template>
  <Tag class="notification-connection" :color="connection.color" :bordered="false">{{
    connection.label
  }}</Tag>
  <Tooltip :title="store.hasUnread ? '有待阅读通知' : '查看通知中心'">
    <Badge :dot="store.hasUnread"
      ><Button
        type="text"
        :aria-label="store.hasUnread ? '通知中心，有待阅读通知' : '通知中心'"
        @click="router.push('/notifications')"
        ><BellOutlined /></Button
    ></Badge>
  </Tooltip>
</template>
