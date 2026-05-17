<template>
  <PlaceholderPanel
    title="脚本规则"
    description="脚本规则支持 Shell、Python 和 Node，适合补充确定性检查。"
    :rows="rows"
  />
</template>

<script setup lang="ts">
import PlaceholderPanel from '@/components/PlaceholderPanel.vue'

const rows = [
  { name: '脚本编辑器', status: '待接入', note: '后续引入代码编辑器和参数模板' },
  { name: '测试运行', status: '待接入', note: '显示 stdout、stderr、退出码' },
  { name: '执行安全', status: '待接入', note: '超时、目录隔离和输出限制' }
]
</script>
