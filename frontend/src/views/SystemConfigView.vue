<template>
  <div class="page-stack">
    <section class="panel">
      <div class="panel-header">
        <div>
          <h2>系统配置</h2>
          <p>管理访问令牌和 OpenAI 兼容模型服务配置</p>
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
          <div class="setting-actions">
            <el-button type="primary" :loading="savingGitee" @click="saveGiteeToken">保存 Token</el-button>
            <el-button :loading="validatingGitee" @click="validateGiteeToken">验证配置</el-button>
          </div>
        </div>

        <div class="setting-card">
          <div class="setting-title">
            <strong>当前模型</strong>
            <el-tag :type="activeModel?.configured ? 'success' : 'info'">{{ activeModel?.configured ? '已配置' : '未配置' }}</el-tag>
          </div>
          <p>{{ activeModel ? `${activeModel.configName} / ${activeModel.modelName}` : '当前没有启用的模型配置' }}</p>
          <p>{{ activeModel?.baseUrl || '支持 OpenAI 兼容 /v1/chat/completions 服务' }}</p>
          <div class="setting-actions">
            <el-button type="primary" :icon="Plus" @click="openCreateModel">新增模型配置</el-button>
            <el-button :loading="validatingActiveModel" @click="validateActiveModel">验证当前配置</el-button>
          </div>
        </div>
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <div>
          <h2>模型服务配置</h2>
          <p>可保存多个模型服务，每次仅启用一个配置供 AI 检视和规则生成使用</p>
        </div>
      </div>

      <el-table :data="modelConfigs" v-loading="loadingModels" border>
        <el-table-column prop="configName" label="配置名称" min-width="160" />
        <el-table-column prop="providerType" label="类型" width="170" />
        <el-table-column prop="modelName" label="模型" min-width="160" />
        <el-table-column prop="baseUrl" label="Base URL" min-width="260" show-overflow-tooltip />
        <el-table-column label="API Key" width="150">
          <template #default="{ row }">
            <span>{{ row.maskedApiKey || '未配置' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用中' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEditModel(row)">编辑</el-button>
            <el-button size="small" :loading="validatingModelId === row.id" @click="validateModel(row)">验证</el-button>
            <el-button size="small" type="primary" :disabled="row.enabled === 1" @click="enableModel(row)">启用</el-button>
            <el-button size="small" type="danger" :disabled="row.enabled === 1" @click="removeModel(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="modelDialogVisible" :title="modelForm.id ? '编辑模型配置' : '新增模型配置'" width="640px">
      <el-form label-width="100px">
        <el-form-item label="配置名称" required>
          <el-input v-model="modelForm.configName" placeholder="例如：DeepSeek、OpenAI、Qwen" />
        </el-form-item>
        <el-form-item label="服务类型">
          <el-select v-model="modelForm.providerType">
            <el-option label="OpenAI Compatible" value="OPENAI_COMPATIBLE" />
          </el-select>
        </el-form-item>
        <el-form-item label="Base URL" required>
          <el-input v-model="modelForm.baseUrl" placeholder="https://api.example.com 或完整 /v1/chat/completions 地址" />
        </el-form-item>
        <el-form-item label="模型名称" required>
          <el-input v-model="modelForm.modelName" placeholder="例如：gpt-4o-mini、deepseek-chat、qwen-plus" />
        </el-form-item>
        <el-form-item label="API Key" :required="!modelForm.id">
          <el-input v-model="modelForm.apiKey" type="password" show-password placeholder="编辑时留空表示保留原 Key" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="modelEnabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="modelForm.remark" type="textarea" :rows="3" placeholder="用途、额度、服务商说明等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modelDialogVisible = false">取消</el-button>
        <el-button :loading="validatingDraft" @click="validateDraftModel">验证</el-button>
        <el-button type="primary" :loading="savingModel" @click="saveModel">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, RefreshCw } from 'lucide-vue-next'
import {
  deleteModelConfig,
  enableModelConfig,
  getDefaultGiteeToken,
  listModelConfigs,
  saveModelConfig,
  updateDefaultGiteeToken,
  validateDefaultGiteeToken,
  validateModelConfig,
  type ConfigValidationResult,
  type ModelConfig,
  type ModelConfigForm,
  type TokenDetail
} from '@/api/systemConfig'

const gitee = ref<TokenDetail>()
const giteeToken = ref('')
const modelConfigs = ref<ModelConfig[]>([])
const loadingModels = ref(false)
const savingGitee = ref(false)
const validatingGitee = ref(false)
const validatingActiveModel = ref(false)
const validatingModelId = ref<number>()
const modelDialogVisible = ref(false)
const savingModel = ref(false)
const validatingDraft = ref(false)

const modelForm = reactive<ModelConfigForm>({
  configName: '',
  providerType: 'OPENAI_COMPATIBLE',
  baseUrl: '',
  modelName: '',
  apiKey: '',
  enabled: 0,
  remark: ''
})

const activeModel = computed(() => modelConfigs.value.find(item => item.enabled === 1))
const modelEnabled = computed({
  get: () => modelForm.enabled === 1,
  set: value => {
    modelForm.enabled = value ? 1 : 0
  }
})

async function loadConfig() {
  const [giteeConfig] = await Promise.all([getDefaultGiteeToken(), loadModelConfigs()])
  gitee.value = giteeConfig
}

async function loadModelConfigs() {
  loadingModels.value = true
  try {
    modelConfigs.value = await listModelConfigs()
  } finally {
    loadingModels.value = false
  }
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

async function validateGiteeToken() {
  validatingGitee.value = true
  try {
    showValidationMessage(await validateDefaultGiteeToken())
  } finally {
    validatingGitee.value = false
  }
}

function openCreateModel() {
  Object.assign(modelForm, {
    id: undefined,
    configName: '',
    providerType: 'OPENAI_COMPATIBLE',
    baseUrl: '',
    modelName: '',
    apiKey: '',
    enabled: modelConfigs.value.length === 0 ? 1 : 0,
    remark: ''
  })
  modelDialogVisible.value = true
}

function openEditModel(row: ModelConfig) {
  Object.assign(modelForm, {
    id: row.id,
    configName: row.configName,
    providerType: row.providerType,
    baseUrl: row.baseUrl,
    modelName: row.modelName,
    apiKey: '',
    enabled: row.enabled,
    remark: row.remark || ''
  })
  modelDialogVisible.value = true
}

async function saveModel() {
  if (!modelForm.configName || !modelForm.baseUrl || !modelForm.modelName || (!modelForm.id && !modelForm.apiKey)) {
    ElMessage.warning('请填写完整模型配置')
    return
  }
  savingModel.value = true
  try {
    await saveModelConfig({ ...modelForm })
    ElMessage.success('模型配置已保存')
    modelDialogVisible.value = false
    await loadModelConfigs()
  } finally {
    savingModel.value = false
  }
}

async function enableModel(row: ModelConfig) {
  await enableModelConfig(row.id)
  ElMessage.success('模型配置已启用')
  await loadModelConfigs()
}

async function removeModel(row: ModelConfig) {
  await ElMessageBox.confirm(`确认删除模型配置「${row.configName}」？`, '删除确认', { type: 'warning' })
  await deleteModelConfig(row.id)
  ElMessage.success('模型配置已删除')
  await loadModelConfigs()
}

async function validateModel(row: ModelConfig) {
  validatingModelId.value = row.id
  try {
    showValidationMessage(await validateModelConfig({ id: row.id }))
  } finally {
    validatingModelId.value = undefined
  }
}

async function validateDraftModel() {
  validatingDraft.value = true
  try {
    showValidationMessage(await validateModelConfig({ ...modelForm }))
  } finally {
    validatingDraft.value = false
  }
}

async function validateActiveModel() {
  validatingActiveModel.value = true
  try {
    showValidationMessage(await validateModelConfig(activeModel.value ? { id: activeModel.value.id } : undefined))
  } finally {
    validatingActiveModel.value = false
  }
}

function showValidationMessage(result: ConfigValidationResult) {
  const status = result.statusCode ? `HTTP ${result.statusCode}` : ''
  const message = [result.message, status].filter(Boolean).join('，')
  if (result.success) {
    ElMessage.success(message)
    return
  }
  ElMessage.error(message)
}

onMounted(loadConfig)
</script>
