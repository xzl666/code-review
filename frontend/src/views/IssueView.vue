<template>
  <PlaceholderPanel
    title="问题列表"
    description="统一展示 AI 和脚本发现的问题，并支持忽略和标记已修复。"
    :rows="rows"
  />
</template>

<script setup lang="ts">
import PlaceholderPanel from '@/components/PlaceholderPanel.vue'

const rows = [
  { name: '问题分页', status: '待接入', note: '按项目、任务、严重度、状态过滤' },
  { name: '问题详情', status: '待接入', note: '展示代码片段、详情和修复建议' },
  { name: '状态变更', status: '待接入', note: '忽略问题或标记已修复' }
]
</script>
