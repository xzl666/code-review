<template>
  <div class="page-stack">
    <section class="toolbar">
      <div class="toolbar-title">
        <strong>问题列表</strong>
        <span>查看 OpenCodeReview 发现的问题，并完成处理闭环</span>
      </div>
      <el-select v-model="query.projectId" clearable filterable placeholder="项目" class="toolbar-select">
        <el-option v-for="project in projectOptions" :key="project.id" :label="project.projectName" :value="project.id" />
      </el-select>
      <el-input
        v-model="taskKeyword"
        clearable
        placeholder="检视任务编号"
        class="toolbar-input"
        @input="handleTaskKeywordInput"
        @keyup.enter="searchIssues"
      />
      <el-select v-model="query.issueSource" clearable placeholder="来源" class="toolbar-select">
        <el-option label="OpenCodeReview" value="OCR" />
      </el-select>
      <el-select v-model="query.severity" clearable placeholder="严重度" class="toolbar-select">
        <el-option label="严重" value="CRITICAL" />
        <el-option label="高" value="HIGH" />
        <el-option label="中" value="MEDIUM" />
        <el-option label="低" value="LOW" />
      </el-select>
      <el-select v-model="query.status" clearable placeholder="状态" class="toolbar-select">
        <el-option label="打开" value="OPEN" />
        <el-option label="已忽略" value="IGNORED" />
        <el-option label="已修复" value="FIXED" />
      </el-select>
      <el-button :icon="Search" @click="searchIssues">查询</el-button>
      <el-button :icon="Download" @click="downloadExport">导出</el-button>
      <el-tag v-if="query.taskId" closable effect="plain" @close="clearTaskFilter">任务 #{{ query.taskId }}</el-tag>
    </section>

    <section class="panel data-panel">
      <div class="panel-header">
        <div>
          <h2>问题明细</h2>
          <p>行号为空表示 OpenCodeReview 未能定位到稳定行号</p>
        </div>
        <el-tag effect="plain">共 {{ total }} 条</el-tag>
      </div>
      <el-table v-loading="loading" :data="issues" stripe class="data-table">
        <el-table-column prop="severity" label="严重度" width="100">
          <template #default="{ row }">
            <el-tag :type="severityType(row.severity)">{{ severityText(row.severity) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="150">
          <template #default="{ row }">{{ sourceText(row.issueSource) }}</template>
        </el-table-column>
        <el-table-column label="命中规则" min-width="190">
          <template #default="{ row }">
            <span
              class="table-ellipsis"
              @mouseenter="showIssueTooltip($event, sourceTraceText(row))"
              @mousemove="moveIssueTooltip"
              @mouseleave="hideIssueTooltip"
            >
              {{ sourceTraceText(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="检视任务" width="180">
          <template #default="{ row }">
            <span
              class="table-ellipsis"
              @mouseenter="showIssueTooltip($event, taskText(row))"
              @mousemove="moveIssueTooltip"
              @mouseleave="hideIssueTooltip"
            >
              {{ taskText(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="问题类型" width="120">
          <template #default="{ row }">{{ issueTypeText(row.issueType) }}</template>
        </el-table-column>
        <el-table-column label="摘要" min-width="220">
          <template #default="{ row }">
            <span
              class="table-ellipsis"
              @mouseenter="showIssueTooltip($event, row.summary || '')"
              @mousemove="moveIssueTooltip"
              @mouseleave="hideIssueTooltip"
            >
              {{ row.summary }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="问题责任人" width="150">
          <template #default="{ row }">{{ row.assigneeName ? `${row.assigneeName} ${row.assigneeEmployeeId || ''}` : '未匹配' }}</template>
        </el-table-column>
        <el-table-column label="文件" min-width="260">
          <template #default="{ row }">
            <span
              class="table-ellipsis"
              @mouseenter="showIssueTooltip($event, row.filePath || '')"
              @mousemove="moveIssueTooltip"
              @mouseleave="hideIssueTooltip"
            >
              {{ row.filePath }}
            </span>
          </template>
        </el-table-column>
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

    <el-dialog v-model="detailVisible" title="问题详情" width="880px" class="issue-detail-dialog" destroy-on-close>
      <div v-if="detail" class="issue-detail">
        <section class="detail-section detail-section-soft">
          <div class="detail-section-title">
            <strong>{{ detail.summary || '未命名问题' }}</strong>
            <el-tag :type="severityType(detail.severity)">{{ severityText(detail.severity) }}</el-tag>
          </div>
          <div class="detail-meta-grid">
            <div><span>问题 ID</span><strong>#{{ detail.id }}</strong></div>
            <div><span>检视任务</span><strong>{{ taskText(detail) }}</strong></div>
            <div><span>来源</span><strong>{{ sourceText(detail.issueSource) }}</strong></div>
            <div><span>状态</span><strong>{{ statusText(detail.status) }}</strong></div>
            <div><span>问题类型</span><strong>{{ issueTypeText(detail.issueType) }}</strong></div>
            <div><span>检查时间</span><strong>{{ formatTime(detail.createTime) }}</strong></div>
            <div><span>命中规则</span><strong>{{ detail.ruleName || idText('规则', detail.ruleId) }}</strong></div>
            <div><span>检视引擎</span><strong>alibaba/open-code-review</strong></div>
            <div><span>问题责任人</span><strong>{{ detail.assigneeName ? `${detail.assigneeName} ${detail.assigneeEmployeeId || ''}` : '未匹配' }}</strong></div>
            <div><span>Git 提交人</span><strong>{{ detail.commitAuthor || '-' }}</strong></div>
          </div>
        </section>

        <section class="detail-section detail-section-file">
          <div class="detail-section-title">
            <strong>定位信息</strong>
            <el-button
              v-if="detail.codeDetailUrl"
              class="source-link-button"
              type="primary"
              :icon="ExternalLink"
              @click="openCodeDetail(detail.codeDetailUrl)"
            >
              查看源码
            </el-button>
          </div>
          <div class="detail-location">
            <div><span>文件</span><strong>{{ detail.filePath || '-' }}</strong></div>
            <div><span>起始行</span><strong>{{ lineText(detail.startLine) }}</strong></div>
            <div><span>结束行</span><strong>{{ lineText(detail.endLine) }}</strong></div>
          </div>
        </section>

        <section class="detail-section detail-section-info">
          <div class="detail-section-title">
            <strong>问题说明</strong>
          </div>
          <pre class="detail-text">{{ detail.detail || '-' }}</pre>
        </section>

        <section class="detail-section detail-section-fix">
          <div class="detail-section-title">
            <strong>修复建议</strong>
          </div>
          <pre class="detail-text">{{ detail.suggestion || '-' }}</pre>
        </section>

        <section class="detail-section detail-section-code">
          <div class="detail-section-title">
            <strong>代码片段</strong>
            <span>{{ codeSnippetSource(detail) }}</span>
          </div>
          <pre v-if="displayCodeSnippet(detail)" class="detail-code">{{ displayCodeSnippet(detail) }}</pre>
          <div v-else class="detail-empty">暂无代码片段，后续新生成的问题会优先从 diff 中补充代码上下文。</div>
        </section>
      </div>
    </el-dialog>

    <teleport to="body">
      <div
        v-if="issueTooltip.visible"
        class="cursor-tooltip"
        :style="{ left: `${issueTooltip.x}px`, top: `${issueTooltip.y}px` }"
      >
        {{ issueTooltip.content }}
      </div>
    </teleport>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { Download, ExternalLink, Eye, Search } from 'lucide-vue-next'
import { pageProjects, type Project } from '@/api/project'
import { exportIssues, getIssue, ignoreIssue, markIssueFixed, pageIssues, type ReviewIssue } from '@/api/issue'
import { issueTypeText } from '@/utils/dictionaries'

const loading = ref(false)
const detailVisible = ref(false)
const issues = ref<ReviewIssue[]>([])
const detail = ref<ReviewIssue>()
const projectOptions = ref<Project[]>([])
const total = ref(0)
const taskKeyword = ref('')
const route = useRoute()
const router = useRouter()
const issueTooltip = reactive({
  visible: false,
  content: '',
  x: 0,
  y: 0
})

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

function searchIssues() {
  query.pageNo = 1
  loadIssues()
}

function handleTaskKeywordInput() {
  if (!query.taskId || taskKeyword.value.trim() === String(query.taskId)) {
    return
  }
  query.taskId = undefined
  router.replace({ path: '/issues', query: {} })
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
  const keyword = taskKeyword.value.trim()
  const keywordTaskId = /^\d+$/.test(keyword) ? Number(keyword) : undefined
  return {
    taskId: query.taskId || keywordTaskId,
    taskNo: !keywordTaskId && keyword ? keyword : undefined,
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
    BLOCKER: '严重',
    CRITICAL: '严重',
    ERROR: '高',
    HIGH: '高',
    MAJOR: '高',
    WARNING: '中',
    WARN: '中',
    MEDIUM: '中',
    MINOR: '中',
    LOW: '低',
    NOTICE: '低',
    INFO: '低'
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
  taskKeyword.value = query.taskId ? String(query.taskId) : ''
  if (query.taskId) {
    query.projectId = undefined
    query.issueSource = ''
    query.severity = ''
    query.status = typeof route.query.status === 'string' ? route.query.status : ''
  }
}

function clearTaskFilter() {
  query.taskId = undefined
  taskKeyword.value = ''
  query.pageNo = 1
  router.replace({ path: '/issues', query: {} })
  loadIssues()
}

function taskText(issue: ReviewIssue) {
  return issue.taskNo ? `${issue.taskNo} (#${issue.taskId})` : `#${issue.taskId}`
}

function sourceTraceText(issue: ReviewIssue) {
  const rule = issue.ruleName || idText('规则', issue.ruleId)
  const binding = sourceBindingName(issue)
  return binding === '-' ? rule : `${rule} / ${binding}`
}

function sourceBindingName(issue: ReviewIssue) {
  if (issue.issueSource === 'OCR') {
    return '-'
  }
  if (issue.issueSource === 'SCRIPT') {
    return issue.scriptName || idText('脚本', issue.scriptId)
  }
  return issue.skillName || idText('Skill', issue.skillId)
}

function sourceText(value?: string) {
  return value === 'OCR' ? 'OpenCodeReview' : (value || '-')
}

function idText(label: string, id?: number) {
  return id ? `${label} #${id}` : '-'
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ') : '-'
}

function openCodeDetail(url: string) {
  window.open(url, '_blank', 'noopener,noreferrer')
}

function displayCodeSnippet(issue: ReviewIssue) {
  if (issue.codeSnippet?.trim()) {
    return issue.codeSnippet.trim()
  }
  return snippetFromRawResponse(issue.rawResponse)
}

function codeSnippetSource(issue: ReviewIssue) {
  if (issue.codeSnippet?.trim()) {
    return '来自代码上下文'
  }
  if (snippetFromRawResponse(issue.rawResponse)) {
    return '来自模型原始返回'
  }
  return '暂无可用片段'
}

function snippetFromRawResponse(rawResponse?: string) {
  if (!rawResponse?.trim()) {
    return ''
  }
  try {
    const payload = JSON.parse(rawResponse)
    const issuesPayload = Array.isArray(payload) ? payload : payload.issues
    if (!Array.isArray(issuesPayload)) {
      return ''
    }
    const snippet = issuesPayload.find((item) => typeof item?.codeSnippet === 'string' && item.codeSnippet.trim())?.codeSnippet
    return typeof snippet === 'string' ? snippet.trim() : ''
  } catch {
    return ''
  }
}

function showIssueTooltip(event: MouseEvent, content: string) {
  issueTooltip.content = content
  issueTooltip.visible = true
  updateIssueTooltipPosition(event)
}

function moveIssueTooltip(event: MouseEvent) {
  if (issueTooltip.visible) {
    updateIssueTooltipPosition(event)
  }
}

function hideIssueTooltip() {
  issueTooltip.visible = false
}

function updateIssueTooltipPosition(event: MouseEvent) {
  const offset = 12
  const maxTooltipWidth = Math.min(520, window.innerWidth - 32)
  issueTooltip.x = Math.max(16, Math.min(event.clientX + offset, window.innerWidth - maxTooltipWidth - 16))
  issueTooltip.y = Math.max(16, Math.min(event.clientY + offset, window.innerHeight - 48))
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
