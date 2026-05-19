<template>
  <div class="page-stack">
    <section class="toolbar">
      <div class="toolbar-title">
        <strong>问题列表</strong>
        <span>查看 AI 与脚本规则发现的问题，并完成处理闭环</span>
      </div>
      <el-select v-model="query.projectId" clearable filterable placeholder="项目" class="toolbar-select">
        <el-option v-for="project in projectOptions" :key="project.id" :label="project.projectName" :value="project.id" />
      </el-select>
      <el-select v-model="query.issueSource" clearable placeholder="来源" class="toolbar-select">
        <el-option label="AI" value="AI" />
        <el-option label="脚本" value="SCRIPT" />
      </el-select>
      <el-select v-model="query.severity" clearable placeholder="严重度" class="toolbar-select">
        <el-option label="阻断" value="BLOCKER" />
        <el-option label="严重" value="CRITICAL" />
        <el-option label="主要" value="MAJOR" />
        <el-option label="次要" value="MINOR" />
        <el-option label="提示" value="INFO" />
      </el-select>
      <el-select v-model="query.status" clearable placeholder="状态" class="toolbar-select">
        <el-option label="打开" value="OPEN" />
        <el-option label="已忽略" value="IGNORED" />
        <el-option label="已修复" value="FIXED" />
      </el-select>
      <el-button :icon="Search" @click="loadIssues">查询</el-button>
      <el-button :icon="Download" @click="downloadExport">导出</el-button>
      <el-tag v-if="query.taskId" closable effect="plain" @close="clearTaskFilter">任务 #{{ query.taskId }}</el-tag>
    </section>

    <section class="panel data-panel">
      <div class="panel-header">
        <div>
          <h2>问题明细</h2>
          <p>行号为空表示模型或脚本未能定位到稳定行号</p>
        </div>
        <el-tag effect="plain">共 {{ total }} 条</el-tag>
      </div>
      <el-table v-loading="loading" :data="issues" stripe class="data-table">
        <el-table-column prop="severity" label="严重度" width="100">
          <template #default="{ row }">
            <el-tag :type="severityType(row.severity)">{{ severityText(row.severity) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="issueSource" label="来源" width="90" />
        <el-table-column label="检视任务" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ taskText(row) }}</template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip />
        <el-table-column prop="filePath" label="文件" min-width="260" show-overflow-tooltip />
        <el-table-column label="起始行" width="90">
          <template #default="{ row }">{{ lineText(row.startLine) }}</template>
        </el-table-column>
        <el-table-column label="结束行" width="90">
          <template #default="{ row }">{{ lineText(row.endLine) }}</template>
        </el-table-column>
        <el-table-column label="检查时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">{{ statusText(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Eye" @click="openDetail(row.id)">详情</el-button>
            <el-button v-if="row.status === 'OPEN'" link type="warning" @click="ignore(row.id)">忽略</el-button>
            <el-button v-if="row.status === 'OPEN'" link type="success" @click="fixed(row.id)">修复</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadIssues"
          @current-change="loadIssues"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" title="问题详情" size="620px">
      <div v-if="detail" class="detail-grid">
        <div><span>问题 ID</span><strong>#{{ detail.id }}</strong></div>
        <div><span>检视任务</span><strong>{{ taskText(detail) }}</strong></div>
        <div><span>来源</span><strong>{{ detail.issueSource }}</strong></div>
        <div><span>严重度</span><el-tag :type="severityType(detail.severity)">{{ severityText(detail.severity) }}</el-tag></div>
        <div><span>状态</span><strong>{{ statusText(detail.status) }}</strong></div>
        <div><span>问题类型</span><strong>{{ detail.issueType || '-' }}</strong></div>
        <div><span>检查时间</span><strong>{{ formatTime(detail.createTime) }}</strong></div>
        <div class="detail-full"><span>文件</span><strong>{{ detail.filePath }}</strong></div>
        <div><span>起始行</span><strong>{{ lineText(detail.startLine) }}</strong></div>
        <div><span>结束行</span><strong>{{ lineText(detail.endLine) }}</strong></div>
        <div class="detail-full"><span>摘要</span><pre>{{ detail.summary || '-' }}</pre></div>
        <div class="detail-full"><span>详情</span><pre>{{ detail.detail || '-' }}</pre></div>
        <div class="detail-full"><span>修复建议</span><pre>{{ detail.suggestion || '-' }}</pre></div>
        <div class="detail-full"><span>代码片段</span><pre>{{ detail.codeSnippet || '-' }}</pre></div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download, Eye, Search } from 'lucide-vue-next'
import { pageProjects, type Project } from '@/api/project'
import { exportIssues, getIssue, ignoreIssue, markIssueFixed, pageIssues, type ReviewIssue } from '@/api/issue'

const loading = ref(false)
const detailVisible = ref(false)
const issues = ref<ReviewIssue[]>([])
const detail = ref<ReviewIssue>()
const projectOptions = ref<Project[]>([])
const total = ref(0)
const route = useRoute()
const router = useRouter()

const query = reactive({
  taskId: undefined as number | undefined,
  projectId: undefined as number | undefined,
  issueSource: '',
  severity: '',
  status: 'OPEN',
  pageNo: 1,
  pageSize: 10
})

async function loadIssues() {
  loading.value = true
  try {
    const page = await pageIssues(currentQuery())
    issues.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function loadProjects() {
  const page = await pageProjects({ status: 1, pageNo: 1, pageSize: 200 })
  projectOptions.value = page.records
}

async function openDetail(id: number) {
  detail.value = await getIssue(id)
  detailVisible.value = true
}

async function ignore(id: number) {
  await ignoreIssue(id)
  ElMessage.success('问题已忽略')
  await loadIssues()
}

async function fixed(id: number) {
  await markIssueFixed(id)
  ElMessage.success('问题已标记修复')
  await loadIssues()
}

async function downloadExport() {
  const content = await exportIssues(currentQuery())
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `review-issues-${Date.now()}.csv`
  link.click()
  URL.revokeObjectURL(url)
}

function currentQuery() {
  return {
    taskId: query.taskId,
    projectId: query.projectId,
    issueSource: query.issueSource || undefined,
    severity: query.severity || undefined,
    status: query.status || undefined,
    pageNo: query.pageNo,
    pageSize: query.pageSize
  }
}

function severityText(severity: string) {
  return ({
    BLOCKER: '阻断',
    CRITICAL: '严重',
    ERROR: '主要',
    HIGH: '主要',
    MAJOR: '主要',
    WARNING: '次要',
    WARN: '次要',
    MEDIUM: '次要',
    MINOR: '次要',
    LOW: '提示',
    NOTICE: '提示',
    INFO: '提示'
  } as Record<string, string>)[severity] || severity
}

function severityType(severity: string) {
  return ({
    BLOCKER: 'danger',
    CRITICAL: 'danger',
    ERROR: 'warning',
    HIGH: 'warning',
    MAJOR: 'warning',
    WARNING: 'primary',
    WARN: 'primary',
    MEDIUM: 'primary',
    MINOR: 'primary',
    LOW: 'info',
    NOTICE: 'info',
    INFO: 'info'
  } as Record<string, string>)[severity] || 'info'
}

function statusText(status: string) {
  return ({ OPEN: '打开', IGNORED: '已忽略', FIXED: '已修复' } as Record<string, string>)[status] || status
}

function lineText(line?: number) {
  return line && line > 0 ? line : '-'
}

function applyRouteQuery() {
  const taskId = Number(route.query.taskId)
  query.taskId = Number.isFinite(taskId) && taskId > 0 ? taskId : undefined
  if (query.taskId) {
    query.projectId = undefined
    query.issueSource = ''
    query.severity = ''
    query.status = typeof route.query.status === 'string' ? route.query.status : ''
  }
}

function clearTaskFilter() {
  query.taskId = undefined
  query.pageNo = 1
  router.replace({ path: '/issues', query: {} })
  loadIssues()
}

function taskText(issue: ReviewIssue) {
  return issue.taskNo ? `${issue.taskNo} (#${issue.taskId})` : `#${issue.taskId}`
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ') : '-'
}

onMounted(() => {
  applyRouteQuery()
  loadProjects()
  loadIssues()
})

watch(
  () => route.query.taskId,
  () => {
    applyRouteQuery()
    query.pageNo = 1
    loadIssues()
  }
)
</script>
