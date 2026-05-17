<template>
  <PlaceholderPanel
    title="系统配置"
    description="系统级默认令牌、AI Key 和执行限制将统一放在这里维护。"
    :rows="rows"
  />
</template>

<script setup lang="ts">
import PlaceholderPanel from '@/components/PlaceholderPanel.vue'

const rows = [
  { name: '默认 Gitee 令牌', status: '待接入', note: '项目未配置单独令牌时使用' },
  { name: 'DeepSeek 配置', status: '待接入', note: 'API Key 使用加密存储' },
  { name: '执行限制', status: '待接入', note: 'AI 并发、diff 长度、脚本超时' }
]
</script>
