<template>
  <div class="page-stack">
    <section class="panel">
      <div class="panel-header">
        <div>
          <h2>通知配置</h2>
          <p>任务成功或失败后自动发送 Webhook 摘要</p>
        </div>
        <div>
          <el-button :icon="Send" @click="openZhaohuTest">招乎测试</el-button>
          <el-button :icon="RefreshCw" @click="refreshActive">刷新</el-button>
        </div>
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

        <el-tab-pane label="投递日志" name="log">
          <div class="toolbar inline-toolbar">
            <el-input v-model="logQuery.taskNo" clearable placeholder="任务编号" class="toolbar-input" />
            <el-select v-model="logQuery.eventType" clearable placeholder="事件类型" class="toolbar-select">
              <el-option label="任务成功" value="TASK_SUCCESS" />
              <el-option label="任务失败" value="TASK_FAILED" />
            </el-select>
            <el-select v-model="logQuery.status" clearable placeholder="状态" class="toolbar-select">
              <el-option label="待发送" value="PENDING" />
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
            </el-select>
            <el-button :icon="Search" @click="loadLogs">查询</el-button>
          </div>
          <el-table v-loading="logLoading" :data="logs" stripe class="data-table">
            <el-table-column prop="taskNo" label="任务编号" min-width="150" show-overflow-tooltip />
            <el-table-column prop="eventType" label="事件" width="120">
              <template #default="{ row }">{{ eventText(row.eventType) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="logStatusType(row.status)">{{ logStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="retryCount" label="重试" width="80" />
            <el-table-column prop="webhookUrl" label="Webhook URL" min-width="260" show-overflow-tooltip />
            <el-table-column label="投递时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="下次重试" width="170">
              <template #default="{ row }">{{ formatTime(row.nextRetryTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="Eye" @click="showLog(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-row">
            <el-pagination
              v-model:current-page="logQuery.pageNo"
              v-model:page-size="logQuery.pageSize"
              :total="logTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadLogs"
              @current-change="loadLogs"
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
        <el-form-item label="默认占位符">
          <div class="placeholder-help">
            ${notifyAccount}、${notifyTitle}、${notifyContent}、${projectName}、${taskNo}、${status}、${issueCount}、${activeIssueCount}、${ignoredIssueCount}、${reportTitle}、${reportHtml}
          </div>
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

    <el-dialog v-model="zhaohuTestVisible" title="招乎机器人测试" width="680px">
      <el-form label-width="90px">
        <el-form-item label="接收人员" required>
          <el-select v-model="zhaohuTestForm.userIds" multiple filterable collapse-tags>
            <el-option v-for="user in userOptions" :key="user.userId" :label="`${user.userName} ${user.employeeId}`" :value="user.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="卡片标题" required><el-input v-model="zhaohuTestForm.title" /></el-form-item>
        <el-form-item label="自定义内容" required><el-input v-model="zhaohuTestForm.content" type="textarea" :rows="7" /></el-form-item>
        <el-form-item label="消息摘要"><el-input v-model="zhaohuTestForm.summary" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="zhaohuTestVisible = false">取消</el-button>
        <el-button type="primary" :loading="zhaohuTesting" @click="submitZhaohuTest">发送测试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
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
  pageNotifyDeliveryLogs,
  pageNotifyTemplates,
  previewNotifyTemplate,
  testSendNotifyConfig,
  testSendZhaohu,
  updateNotifyConfig,
  updateNotifyTemplate,
  type NotifyConfig,
  type NotifyConfigForm,
  type NotifyDeliveryLog,
  type NotifyTemplate,
  type NotifyTemplateForm
} from '@/api/notification'
import { listSystemUsers, type SystemUser } from '@/api/user'

const activeTab = ref('config')
const configLoading = ref(false)
const templateLoading = ref(false)
const logLoading = ref(false)
const configSaving = ref(false)
const templateSaving = ref(false)
const configDialogVisible = ref(false)
const templateDialogVisible = ref(false)
const zhaohuTestVisible = ref(false)
const zhaohuTesting = ref(false)
const userOptions = ref<SystemUser[]>([])
const zhaohuTestForm = reactive({ userIds: [] as string[], title: '代码检视通知测试', content: '这是一条招乎机器人自定义测试消息。', summary: '代码检视平台测试' })
const resultVisible = ref(false)
const resultText = ref('')
const configEditingId = ref<number>()
const templateEditingId = ref<number>()
const configFormRef = ref<FormInstance>()
const templateFormRef = ref<FormInstance>()
const configs = ref<NotifyConfig[]>([])
const templates = ref<NotifyTemplate[]>([])
const logs = ref<NotifyDeliveryLog[]>([])
const configTotal = ref(0)
const templateTotal = ref(0)
const logTotal = ref(0)

const configQuery = reactive({ configName: '', pageNo: 1, pageSize: 10 })
const templateQuery = reactive({ templateName: '', eventType: '', pageNo: 1, pageSize: 10 })
const logQuery = reactive({ taskNo: '', eventType: '', status: '', pageNo: 1, pageSize: 10 })

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
  templateContent: '${notifyTitle}\n通知账号：${notifyAccount}\n${notifyContent}'
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

async function loadLogs() {
  logLoading.value = true
  try {
    const page = await pageNotifyDeliveryLogs({
      taskNo: logQuery.taskNo || undefined,
      eventType: logQuery.eventType || undefined,
      status: logQuery.status || undefined,
      pageNo: logQuery.pageNo,
      pageSize: logQuery.pageSize
    })
    logs.value = page.records
    logTotal.value = page.total
  } finally {
    logLoading.value = false
  }
}

function refreshActive() {
  if (activeTab.value === 'config') {
    loadConfigs()
  } else if (activeTab.value === 'template') {
    loadTemplates()
  } else {
    loadLogs()
  }
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

async function openZhaohuTest() {
  if (!userOptions.value.length) userOptions.value = await listSystemUsers()
  zhaohuTestVisible.value = true
}

async function submitZhaohuTest() {
  if (!zhaohuTestForm.userIds.length || !zhaohuTestForm.title.trim() || !zhaohuTestForm.content.trim()) {
    ElMessage.warning('请选择接收人员并填写标题和内容')
    return
  }
  zhaohuTesting.value = true
  try {
    const result = await testSendZhaohu(zhaohuTestForm)
    resultText.value = JSON.stringify(result, null, 2)
    resultVisible.value = true
    if (!result.failureCount) zhaohuTestVisible.value = false
  } finally {
    zhaohuTesting.value = false
  }
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
    templateContent: '${notifyTitle}\n通知账号：${notifyAccount}\n${notifyContent}',
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

function showLog(row: NotifyDeliveryLog) {
  resultText.value = JSON.stringify(
    {
      id: row.id,
      taskNo: row.taskNo,
      event: eventText(row.eventType),
      status: logStatusText(row.status),
      retryCount: row.retryCount,
      webhookUrl: row.webhookUrl,
      requestContent: tryFormatJson(row.requestContent),
      responseContent: row.responseContent || '',
      lastError: row.lastError || ''
    },
    null,
    2
  )
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
    notifyAccount: 'zhangsan',
    notifyTitle: '代码检视完成：code-review',
    notifyContent: '任务 CR202605180001 发现 3 个问题，有效 2 个，已忽略 1 个。',
    status: 'SUCCESS',
    issueCount: 3,
    activeIssueCount: 2,
    ignoredIssueCount: 1,
    reportTitle: '代码检视报告 - code-review',
    reportHtml: '<article>报告内容</article>',
    errorMessage: ''
  }
}

function eventText(value: string) {
  return ({ TASK_SUCCESS: '任务成功', TASK_FAILED: '任务失败' } as Record<string, string>)[value] || value
}

function logStatusText(value: string) {
  return ({ PENDING: '待发送', SUCCESS: '成功', FAILED: '失败' } as Record<string, string>)[value] || value
}

function logStatusType(value: string) {
  return ({ PENDING: 'info', SUCCESS: 'success', FAILED: 'danger' } as Record<string, string>)[value] || 'info'
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ') : '-'
}

function tryFormatJson(value?: string) {
  if (!value) return ''
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

onMounted(() => {
  loadConfigs()
  loadTemplates()
  loadLogs()
  listSystemUsers().then(users => { userOptions.value = users })
})
</script>
