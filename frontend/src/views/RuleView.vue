<template>
  <PlaceholderPanel
    title="检视规则"
    description="AI 规则与脚本规则将在这里统一管理，后续接入规则 CRUD 和 Prompt 模板编辑。"
    :rows="rows"
  />
</template>

<script setup lang="ts">
import PlaceholderPanel from '@/components/PlaceholderPanel.vue'

const rows = [
  { name: 'AI 规则列表', status: '待接入', note: '绑定 Skill、项目类型、严重等级和 Prompt 模板' },
  { name: '脚本规则入口', status: '待接入', note: '脚本类规则也会统一输出问题结构' },
  { name: 'AI 生成脚本', status: '待接入', note: '生成草稿后人工确认' }
]
</script>
