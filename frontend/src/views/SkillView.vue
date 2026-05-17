<template>
  <PlaceholderPanel
    title="AI Skill"
    description="平台核心配置页，用于维护 Function Calling 函数定义和 JSON Schema。"
    :rows="rows"
  />
</template>

<script setup lang="ts">
import PlaceholderPanel from '@/components/PlaceholderPanel.vue'

const rows = [
  { name: 'Skill 列表', status: '待接入', note: '新增、编辑、启停和版本管理' },
  { name: 'JSON Schema 校验', status: '待接入', note: '强约束 AI 结构化输出' },
  { name: '规则引用关系', status: '待接入', note: '查看哪些规则正在使用当前 Skill' }
]
</script>
