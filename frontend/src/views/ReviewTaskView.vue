<template>
  <PlaceholderPanel
    title="检视任务"
    description="任务页用于查看手动和定时检视任务的状态、统计与日志。"
    :rows="rows"
  />
</template>

<script setup lang="ts">
import PlaceholderPanel from '@/components/PlaceholderPanel.vue'

const rows = [
  { name: '任务列表', status: '待接入', note: '展示任务编号、项目、状态和问题数量' },
  { name: '任务详情', status: '待接入', note: '展示 Git、AI、脚本执行摘要' },
  { name: '重试与取消', status: '待接入', note: '失败重试和待执行任务取消' }
]
</script>
