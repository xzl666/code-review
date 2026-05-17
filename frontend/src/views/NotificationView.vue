<template>
  <PlaceholderPanel
    title="通知配置"
    description="第一期支持 Webhook 通知，任务完成或失败后发送摘要。"
    :rows="rows"
  />
</template>

<script setup lang="ts">
import PlaceholderPanel from '@/components/PlaceholderPanel.vue'

const rows = [
  { name: 'Webhook 配置', status: '待接入', note: '新增、编辑、删除、测试发送' },
  { name: '通知模板', status: '待接入', note: '任务变量渲染和模板预览' },
  { name: '失败重试', status: '待接入', note: '记录通知失败原因' }
]
</script>
