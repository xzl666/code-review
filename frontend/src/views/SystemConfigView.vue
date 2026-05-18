<template>
  <div class="page-stack">
    <section class="panel">
      <div class="panel-header">
        <div>
          <h2>系统配置</h2>
          <p>密钥仅展示脱敏状态；本地环境变量仍是推荐方式</p>
        </div>
        <el-button :icon="RefreshCw" @click="loadConfig">刷新</el-button>
      </div>

      <div class="settings-grid">
        <div class="setting-card">
          <div class="setting-title">
            <strong>Gitee 默认 Token</strong>
            <el-tag :type="gitee?.configured ? 'success' : 'info'">{{ gitee?.configured ? '已配置' : '未配置' }}</el-tag>
          </div>
          <p>{{ gitee?.maskedToken || '优先读取 CODE_REVIEW_GITEE_TOKEN 环境变量' }}</p>
          <el-input v-model="giteeToken" type="password" show-password placeholder="写入数据库配置的 Gitee Token" />
          <el-button type="primary" :loading="savingGitee" @click="saveGiteeToken">保存 Token</el-button>
        </div>

        <div class="setting-card">
          <div class="setting-title">
            <strong>DeepSeek</strong>
            <el-tag :type="deepSeek?.configured ? 'success' : 'info'">{{ deepSeek?.configured ? '已配置' : '未配置' }}</el-tag>
          </div>
          <p>{{ deepSeek?.maskedApiKey || '优先读取 CODE_REVIEW_DEEPSEEK_API_KEY 环境变量' }}</p>
          <el-input v-model="deepSeekForm.apiKey" type="password" show-password placeholder="API Key" />
          <el-input v-model="deepSeekForm.url" placeholder="Base URL 或 chat completions URL" />
          <el-input v-model="deepSeekForm.model" placeholder="模型名称" />
          <el-button type="primary" :loading="savingDeepSeek" @click="saveDeepSeek">保存 DeepSeek 配置</el-button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshCw } from 'lucide-vue-next'
import {
  getDeepSeekConfig,
  getDefaultGiteeToken,
  updateDeepSeekConfig,
  updateDefaultGiteeToken,
  type DeepSeekConfig,
  type TokenDetail
} from '@/api/systemConfig'

const gitee = ref<TokenDetail>()
const deepSeek = ref<DeepSeekConfig>()
const giteeToken = ref('')
const savingGitee = ref(false)
const savingDeepSeek = ref(false)

const deepSeekForm = reactive({
  apiKey: '',
  url: 'https://zhenze-huhehaote.cmecloud.cn',
  model: 'deepseek-v4-flash'
})

async function loadConfig() {
  const [giteeConfig, deepSeekConfig] = await Promise.all([getDefaultGiteeToken(), getDeepSeekConfig()])
  gitee.value = giteeConfig
  deepSeek.value = deepSeekConfig
  deepSeekForm.url = deepSeekConfig.url || deepSeekForm.url
  deepSeekForm.model = deepSeekConfig.model || deepSeekForm.model
}

async function saveGiteeToken() {
  if (!giteeToken.value) {
    ElMessage.warning('请输入 Gitee Token')
    return
  }
  savingGitee.value = true
  try {
    await updateDefaultGiteeToken(giteeToken.value)
    giteeToken.value = ''
    ElMessage.success('Gitee Token 已保存')
    await loadConfig()
  } finally {
    savingGitee.value = false
  }
}

async function saveDeepSeek() {
  if (!deepSeekForm.apiKey) {
    ElMessage.warning('请输入 DeepSeek API Key')
    return
  }
  savingDeepSeek.value = true
  try {
    await updateDeepSeekConfig({ ...deepSeekForm })
    deepSeekForm.apiKey = ''
    ElMessage.success('DeepSeek 配置已保存')
    await loadConfig()
  } finally {
    savingDeepSeek.value = false
  }
}

onMounted(loadConfig)
</script>
