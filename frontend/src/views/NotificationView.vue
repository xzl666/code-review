<template>
  <div class="page-stack">
    <section class="panel">
      <div class="panel-header">
        <div>
          <h2>通知配置</h2>
          <p>任务成功或失败后自动发送 Webhook 摘要</p>
        </div>
        <el-button :icon="RefreshCw" @click="refreshActive">刷新</el-button>
      </div>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="Webhook 配置" name="config">
          <div class="toolbar inline-toolbar">
            <el-input v-model="configQuery.configName" clearable placeholder="配置名称" class="toolbar-input" />
            <el-button :icon="Search" @click="loadConfigs">查询</el-button>
            <el-button type="primary" :icon="Plus" @click="openConfigCreate">新增 Webhook</el-button>
          </div>
          <el-table v-loading="configLoading" :data="configs" stripe class="data-table">
            <el-table-column prop="configName" label="名称" min-width="150" />
            <el-table-column prop="webhookUrl" label="Webhook URL" min-width="300" show-overflow-tooltip />
            <el-table-column prop="secretMasked" label="Secret" width="120" />
            <el-table-column prop="enabled" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="300" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="Edit" @click="openConfigEdit(row)">编辑</el-button>
                <el-button link type="primary" :icon="Send" @click="testConfig(row)">测试</el-button>
                <el-button v-if="row.enabled === 1" link type="warning" :icon="Pause" @click="setConfigStatus(row, false)">停用</el-button>
                <el-button v-else link type="success" :icon="Play" @click="setConfigStatus(row, true)">启用</el-button>
                <el-button link type="danger" :icon="Trash2" @click="removeConfig(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-row">
            <el-pagination
              v-model:current-page="configQuery.pageNo"
              v-model:page-size="configQuery.pageSize"
              :total="configTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadConfigs"
              @current-change="loadConfigs"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="通知模板" name="template">
          <div class="toolbar inline-toolbar">
            <el-input v-model="templateQuery.templateName" clearable placeholder="模板名称" class="toolbar-input" />
            <el-select v-model="templateQuery.eventType" clearable placeholder="事件类型" class="toolbar-select">
              <el-option label="任务成功" value="TASK_SUCCESS" />
              <el-option label="任务失败" value="TASK_FAILED" />
            </el-select>
            <el-button :icon="Search" @click="loadTemplates">查询</el-button>
            <el-button type="primary" :icon="Plus" @click="openTemplateCreate">新增模板</el-button>
          </div>
          <el-table v-loading="templateLoading" :data="templates" stripe class="data-table">
            <el-table-column prop="templateName" label="模板名称" min-width="150" />
            <el-table-column prop="templateCode" label="编码" min-width="140" show-overflow-tooltip />
            <el-table-column prop="eventType" label="事件" width="120">
              <template #default="{ row }">{{ eventText(row.eventType) }}</template>
            </el-table-column>
            <el-table-column prop="templateContent" label="内容" min-width="280" show-overflow-tooltip />
            <el-table-column prop="enabled" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="300" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="Edit" @click="openTemplateEdit(row)">编辑</el-button>
                <el-button link type="primary" :icon="Eye" @click="previewTemplate(row)">预览</el-button>
                <el-button v-if="row.enabled === 1" link type="warning" :icon="Pause" @click="setTemplateStatus(row, false)">停用</el-button>
                <el-button v-else link type="success" :icon="Play" @click="setTemplateStatus(row, true)">启用</el-button>
                <el-button link type="danger" :icon="Trash2" @click="removeTemplate(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-row">
            <el-pagination
              v-model:current-page="templateQuery.pageNo"
              v-model:page-size="templateQuery.pageSize"
              :total="templateTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadTemplates"
              @current-change="loadTemplates"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="configDialogVisible" :title="configEditingId ? '编辑 Webhook' : '新增 Webhook'" width="720px">
      <el-form ref="configFormRef" :model="configForm" :rules="configRules" label-width="110px">
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="configForm.configName" />
        </el-form-item>
        <el-form-item label="Webhook" prop="webhookUrl">
          <el-input v-model="configForm.webhookUrl" />
        </el-form-item>
        <el-form-item label="Secret">
          <el-input v-model="configForm.secret" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="configSaving" @click="submitConfig">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="templateDialogVisible" :title="templateEditingId ? '编辑模板' : '新增模板'" width="820px">
      <el-form ref="templateFormRef" :model="templateForm" :rules="templateRules" label-width="110px">
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="templateForm.templateName" />
        </el-form-item>
        <el-form-item label="模板编码" prop="templateCode">
          <el-input v-model="templateForm.templateCode" />
        </el-form-item>
        <el-form-item label="事件类型" prop="eventType">
          <el-segmented v-model="templateForm.eventType" :options="eventOptions" />
        </el-form-item>
        <el-form-item label="模板内容" prop="templateContent">
          <el-input v-model="templateForm.templateContent" type="textarea" :rows="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="previewTemplateForm">预览当前模板</el-button>
        <el-button @click="templateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="templateSaving" @click="submitTemplate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultVisible" title="执行结果" width="720px">
      <pre>{{ resultText }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Eye, Pause, Play, Plus, RefreshCw, Search, Send, Trash2 } from 'lucide-vue-next'
import {
  createNotifyConfig,
  createNotifyTemplate,
  deleteNotifyConfig,
  deleteNotifyTemplate,
  disableNotifyConfig,
  disableNotifyTemplate,
  enableNotifyConfig,
  enableNotifyTemplate,
  pageNotifyConfigs,
  pageNotifyTemplates,
  previewNotifyTemplate,
  testSendNotifyConfig,
  updateNotifyConfig,
  updateNotifyTemplate,
  type NotifyConfig,
  type NotifyConfigForm,
  type NotifyTemplate,
  type NotifyTemplateForm
} from '@/api/notification'

const activeTab = ref('config')
const configLoading = ref(false)
const templateLoading = ref(false)
const configSaving = ref(false)
const templateSaving = ref(false)
const configDialogVisible = ref(false)
const templateDialogVisible = ref(false)
const resultVisible = ref(false)
const resultText = ref('')
const configEditingId = ref<number>()
const templateEditingId = ref<number>()
const configFormRef = ref<FormInstance>()
const templateFormRef = ref<FormInstance>()
const configs = ref<NotifyConfig[]>([])
const templates = ref<NotifyTemplate[]>([])
const configTotal = ref(0)
const templateTotal = ref(0)

const configQuery = reactive({ configName: '', pageNo: 1, pageSize: 10 })
const templateQuery = reactive({ templateName: '', eventType: '', pageNo: 1, pageSize: 10 })

const configForm = reactive<NotifyConfigForm>({
  configName: '',
  channelType: 'WEBHOOK',
  webhookUrl: '',
  secret: ''
})

const templateForm = reactive<NotifyTemplateForm>({
  templateName: '',
  templateCode: '',
  channelType: 'WEBHOOK',
  eventType: 'TASK_SUCCESS',
  templateContent: '代码检视任务 {{taskNo}} 已完成，项目：{{projectName}}，问题数：{{issueCount}}。'
})

const eventOptions = [
  { label: '任务成功', value: 'TASK_SUCCESS' },
  { label: '任务失败', value: 'TASK_FAILED' }
]

const configRules: FormRules = {
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  webhookUrl: [{ required: true, message: '请输入 Webhook URL', trigger: 'blur' }]
}

const templateRules: FormRules = {
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  templateCode: [{ required: true, message: '请输入模板编码', trigger: 'blur' }],
  eventType: [{ required: true, message: '请选择事件类型', trigger: 'change' }],
  templateContent: [{ required: true, message: '请输入模板内容', trigger: 'blur' }]
}

async function loadConfigs() {
  configLoading.value = true
  try {
    const page = await pageNotifyConfigs({
      configName: configQuery.configName || undefined,
      pageNo: configQuery.pageNo,
      pageSize: configQuery.pageSize
    })
    configs.value = page.records
    configTotal.value = page.total
  } finally {
    configLoading.value = false
  }
}

async function loadTemplates() {
  templateLoading.value = true
  try {
    const page = await pageNotifyTemplates({
      templateName: templateQuery.templateName || undefined,
      eventType: templateQuery.eventType || undefined,
      pageNo: templateQuery.pageNo,
      pageSize: templateQuery.pageSize
    })
    templates.value = page.records
    templateTotal.value = page.total
  } finally {
    templateLoading.value = false
  }
}

function refreshActive() {
  activeTab.value === 'config' ? loadConfigs() : loadTemplates()
}

function openConfigCreate() {
  configEditingId.value = undefined
  Object.assign(configForm, { id: undefined, configName: '', channelType: 'WEBHOOK', webhookUrl: '', secret: '', enabled: undefined })
  configDialogVisible.value = true
}

function openConfigEdit(row: NotifyConfig) {
  configEditingId.value = row.id
  Object.assign(configForm, { id: row.id, configName: row.configName, channelType: row.channelType, webhookUrl: row.webhookUrl, secret: '', enabled: row.enabled })
  configDialogVisible.value = true
}

async function submitConfig() {
  await configFormRef.value?.validate()
  configSaving.value = true
  try {
    if (configEditingId.value) {
      await updateNotifyConfig({ ...configForm, id: configEditingId.value, enabled: configForm.enabled ?? 1 })
      ElMessage.success('Webhook 已更新')
    } else {
      await createNotifyConfig(configForm)
      ElMessage.success('Webhook 已创建')
    }
    configDialogVisible.value = false
    await loadConfigs()
  } finally {
    configSaving.value = false
  }
}

async function testConfig(row: NotifyConfig) {
  const result = await testSendNotifyConfig({
    configId: row.id,
    title: 'Code Review 通知测试',
    content: 'Webhook 配置可达性测试'
  })
  resultText.value = JSON.stringify(result, null, 2)
  resultVisible.value = true
}

async function setConfigStatus(row: NotifyConfig, enabled: boolean) {
  enabled ? await enableNotifyConfig(row.id) : await disableNotifyConfig(row.id)
  ElMessage.success(enabled ? 'Webhook 已启用' : 'Webhook 已停用')
  await loadConfigs()
}

async function removeConfig(row: NotifyConfig) {
  await ElMessageBox.confirm(`确认删除 ${row.configName}？`, '删除确认', { type: 'warning' })
  await deleteNotifyConfig(row.id)
  ElMessage.success('Webhook 已删除')
  await loadConfigs()
}

function openTemplateCreate() {
  templateEditingId.value = undefined
  Object.assign(templateForm, {
    id: undefined,
    templateName: '',
    templateCode: '',
    channelType: 'WEBHOOK',
    eventType: 'TASK_SUCCESS',
    templateContent: '代码检视任务 {{taskNo}} 已完成，项目：{{projectName}}，问题数：{{issueCount}}。',
    enabled: undefined
  })
  templateDialogVisible.value = true
}

function openTemplateEdit(row: NotifyTemplate) {
  templateEditingId.value = row.id
  Object.assign(templateForm, { ...row })
  templateDialogVisible.value = true
}

async function submitTemplate() {
  await templateFormRef.value?.validate()
  templateSaving.value = true
  try {
    if (templateEditingId.value) {
      await updateNotifyTemplate({ ...templateForm, id: templateEditingId.value, enabled: templateForm.enabled ?? 1 })
      ElMessage.success('模板已更新')
    } else {
      await createNotifyTemplate(templateForm)
      ElMessage.success('模板已创建')
    }
    templateDialogVisible.value = false
    await loadTemplates()
  } finally {
    templateSaving.value = false
  }
}

async function previewTemplate(row: NotifyTemplate) {
  const result = await previewNotifyTemplate({ templateId: row.id, variables: previewVariables() })
  resultText.value = result.content
  resultVisible.value = true
}

async function previewTemplateForm() {
  const result = await previewNotifyTemplate({ templateContent: templateForm.templateContent, variables: previewVariables() })
  resultText.value = result.content
  resultVisible.value = true
}

async function setTemplateStatus(row: NotifyTemplate, enabled: boolean) {
  enabled ? await enableNotifyTemplate(row.id) : await disableNotifyTemplate(row.id)
  ElMessage.success(enabled ? '模板已启用' : '模板已停用')
  await loadTemplates()
}

async function removeTemplate(row: NotifyTemplate) {
  await ElMessageBox.confirm(`确认删除 ${row.templateName}？`, '删除确认', { type: 'warning' })
  await deleteNotifyTemplate(row.id)
  ElMessage.success('模板已删除')
  await loadTemplates()
}

function previewVariables() {
  return {
    taskNo: 'CR202605180001',
    projectName: 'code-review',
    status: 'SUCCESS',
    issueCount: 3,
    errorMessage: ''
  }
}

function eventText(value: string) {
  return ({ TASK_SUCCESS: '任务成功', TASK_FAILED: '任务失败' } as Record<string, string>)[value] || value
}

onMounted(() => {
  loadConfigs()
  loadTemplates()
})
</script>
