<template>
  <div class="page-stack">
    <section class="panel">
      <div class="panel-header">
        <div>
          <h2>招乎机器人</h2>
          <p>代码检视完成后，按项目负责人和问题归属人员分别发送通知</p>
        </div>
        <div>
          <el-button :icon="Send" @click="openTest">发送测试</el-button>
          <el-button :icon="RefreshCw" @click="loadConfig">刷新</el-button>
        </div>
      </div>

      <el-form v-loading="configLoading" :model="configForm" label-width="140px" class="config-form">
        <el-form-item label="启用机器人">
          <el-switch v-model="enabled" />
        </el-form-item>
        <el-form-item label="API 地址" required>
          <el-input v-model="configForm.apiHost" placeholder="http://gatewayoazh.cmbchina.cn" />
        </el-form-item>
        <el-form-item label="Client ID" required>
          <el-input v-model="configForm.clientId" />
        </el-form-item>
        <el-form-item label="Client Secret" :required="!secretConfigured">
          <el-input
            v-model="configForm.clientSecret"
            type="password"
            show-password
            :placeholder="secretConfigured ? '已配置 ' + secretMasked + '，留空表示保留' : '请输入 Client Secret'"
          />
        </el-form-item>
        <el-form-item label="机器人 ID" required>
          <el-input v-model="configForm.robotId" />
        </el-form-item>
        <el-form-item label="平台访问地址" required>
          <el-input v-model="configForm.appBaseUrl" placeholder="http://localhost:5173" />
        </el-form-item>
        <el-form-item label="请求超时">
          <el-input-number v-model="configForm.timeoutSeconds" :min="1" :max="120" />
          <span class="unit-text">秒</span>
        </el-form-item>
        <el-form-item label="Token 有效期">
          <el-input-number v-model="configForm.tokenExpireSeconds" :min="60" />
          <span class="unit-text">秒</span>
        </el-form-item>
        <el-form-item label="Token 刷新缓冲">
          <el-input-number v-model="configForm.tokenBufferSeconds" :min="0" />
          <span class="unit-text">秒</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Save" :loading="configSaving" @click="saveConfig">保存配置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="panel data-panel">
      <div class="panel-header">
        <div>
          <h2>招乎投递日志</h2>
          <p>查看机器人消息发送状态、失败原因和重试记录</p>
        </div>
      </div>
      <div class="toolbar inline-toolbar">
        <el-input v-model="logQuery.taskNo" clearable placeholder="任务编号" class="toolbar-input" />
        <el-select v-model="logQuery.eventType" clearable placeholder="事件类型" class="toolbar-select">
          <el-option label="检视成功" value="DAILY_REVIEW_SUCCESS" />
          <el-option label="检视失败" value="DAILY_REVIEW_FAILED" />
        </el-select>
        <el-select v-model="logQuery.status" clearable placeholder="投递状态" class="toolbar-select">
          <el-option label="待发送" value="PENDING" />
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
        </el-select>
        <el-button :icon="Search" @click="loadLogs">查询</el-button>
      </div>
      <el-table v-loading="logLoading" :data="logs" stripe class="data-table">
        <el-table-column prop="taskNo" label="任务编号" min-width="170" />
        <el-table-column label="事件" width="120">
          <template #default="{ row }">{{ eventText(row.eventType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试次数" width="100" />
        <el-table-column label="发送时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
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
    </section>

    <el-dialog v-model="testVisible" title="发送招乎测试消息" width="680px">
      <el-form label-width="90px">
        <el-form-item label="接收人员" required>
          <el-select v-model="testForm.userIds" multiple filterable collapse-tags>
            <el-option v-for="user in userOptions" :key="user.userId" :label="user.userName + ' ' + user.employeeId" :value="user.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="卡片标题" required><el-input v-model="testForm.title" /></el-form-item>
        <el-form-item label="自定义内容" required><el-input v-model="testForm.content" type="textarea" :rows="7" /></el-form-item>
        <el-form-item label="消息摘要"><el-input v-model="testForm.summary" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="testVisible = false">取消</el-button>
        <el-button type="primary" :loading="testing" @click="submitTest">发送测试</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultVisible" title="执行结果" width="760px">
      <pre>{{ resultText }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Eye, RefreshCw, Save, Search, Send } from 'lucide-vue-next'
import {
  getZhaohuConfig,
  pageNotifyDeliveryLogs,
  testSendZhaohu,
  updateZhaohuConfig,
  type NotifyDeliveryLog,
  type ZhaohuConfigForm
} from '@/api/notification'
import { listSystemUsers, type SystemUser } from '@/api/user'

const configLoading = ref(false)
const configSaving = ref(false)
const secretConfigured = ref(false)
const secretMasked = ref('')
const configForm = reactive<ZhaohuConfigForm>({
  enabled: 1,
  apiHost: 'http://gatewayoazh.cmbchina.cn',
  clientId: '',
  clientSecret: '',
  robotId: '',
  appBaseUrl: 'http://localhost:5173',
  tokenExpireSeconds: 86400,
  tokenBufferSeconds: 300,
  timeoutSeconds: 10
})
const enabled = computed({
  get: () => configForm.enabled === 1,
  set: value => { configForm.enabled = value ? 1 : 0 }
})

const logLoading = ref(false)
const logs = ref<NotifyDeliveryLog[]>([])
const logTotal = ref(0)
const logQuery = reactive({ taskNo: '', eventType: '', status: '', pageNo: 1, pageSize: 10 })

const testVisible = ref(false)
const testing = ref(false)
const userOptions = ref<SystemUser[]>([])
const testForm = reactive({
  userIds: [] as string[],
  title: '代码检视通知测试',
  content: '这是一条招乎机器人自定义测试消息。',
  summary: '代码检视平台测试'
})
const resultVisible = ref(false)
const resultText = ref('')

async function loadConfig() {
  configLoading.value = true
  try {
    const config = await getZhaohuConfig()
    Object.assign(configForm, config, { clientSecret: '' })
    secretConfigured.value = config.clientSecretConfigured
    secretMasked.value = config.clientSecretMasked
  } finally {
    configLoading.value = false
  }
}

async function saveConfig() {
  if (!configForm.apiHost.trim() || !configForm.clientId.trim() || !configForm.robotId.trim() || !configForm.appBaseUrl.trim()) {
    ElMessage.warning('请填写完整的招乎机器人配置')
    return
  }
  if (!secretConfigured.value && !configForm.clientSecret?.trim()) {
    ElMessage.warning('请输入 Client Secret')
    return
  }
  configSaving.value = true
  try {
    await updateZhaohuConfig(configForm)
    ElMessage.success('招乎机器人配置已保存')
    await loadConfig()
  } finally {
    configSaving.value = false
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

async function openTest() {
  if (!userOptions.value.length) userOptions.value = await listSystemUsers()
  testVisible.value = true
}

async function submitTest() {
  if (!testForm.userIds.length || !testForm.title.trim() || !testForm.content.trim()) {
    ElMessage.warning('请选择接收人员并填写标题和内容')
    return
  }
  testing.value = true
  try {
    const result = await testSendZhaohu(testForm)
    resultText.value = JSON.stringify(result, null, 2)
    resultVisible.value = true
    if (!result.failureCount) testVisible.value = false
  } finally {
    testing.value = false
  }
}

function showLog(row: NotifyDeliveryLog) {
  resultText.value = JSON.stringify({
    taskNo: row.taskNo,
    event: eventText(row.eventType),
    status: statusText(row.status),
    retryCount: row.retryCount,
    requestContent: formatJson(row.requestContent),
    responseContent: row.responseContent || '',
    lastError: row.lastError || ''
  }, null, 2)
  resultVisible.value = true
}

function eventText(value: string) {
  return value === 'DAILY_REVIEW_SUCCESS' ? '检视成功' : value === 'DAILY_REVIEW_FAILED' ? '检视失败' : value
}

function statusText(value: string) {
  return ({ PENDING: '待发送', SUCCESS: '成功', FAILED: '失败' } as Record<string, string>)[value] || value
}

function statusType(value: string) {
  return ({ PENDING: 'warning', SUCCESS: 'success', FAILED: 'danger' } as Record<string, string>)[value] || 'info'
}

function formatJson(value?: string) {
  if (!value) return ''
  try { return JSON.parse(value) } catch { return value }
}

function formatTime(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

onMounted(() => {
  loadConfig()
  loadLogs()
})
</script>

<style scoped>
.config-form {
  max-width: 820px;
}

.unit-text {
  margin-left: 10px;
  color: #64748b;
}
</style>
