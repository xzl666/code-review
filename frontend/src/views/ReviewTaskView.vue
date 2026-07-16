<template>
  <div class="page-stack">
    <section class="toolbar">
      <div class="toolbar-title">
        <strong>检视任务</strong>
        <span>跟踪 Git 与 OpenCodeReview 引擎的执行状态</span>
      </div>
      <el-input v-model="query.projectName" clearable placeholder="项目名称" class="toolbar-input" @keyup.enter="loadTasks">
        <template #prefix><Search :size="16" /></template>
      </el-input>
      <el-select v-model="query.status" clearable placeholder="任务状态" class="toolbar-select">
        <el-option label="待执行" value="PENDING" />
        <el-option label="执行中" value="RUNNING" />
        <el-option label="成功" value="SUCCESS" />
        <el-option label="失败" value="FAILED" />
        <el-option label="已取消" value="CANCELED" />
      </el-select>
      <el-button :icon="Search" @click="loadTasks">查询</el-button>
      <el-button :icon="RefreshCw" @click="loadTasks">刷新</el-button>
      <el-button type="primary" :icon="Plus" @click="openStart">手动发起</el-button>
    </section>

    <section class="panel data-panel">
      <div class="panel-header">
        <div>
          <h2>任务列表</h2>
          <p>成功任务中的提示信息表示本次检视使用了限流或跳过策略</p>
        </div>
        <el-tag effect="plain">共 {{ total }} 条</el-tag>
      </div>
      <el-table v-loading="loading" :data="tasks" stripe class="data-table">
        <el-table-column label="任务编号" min-width="180">
          <template #default="{ row }">
            <span
              class="task-no-cell"
              @mouseenter="showTaskNoTooltip($event, row.taskNo)"
              @mousemove="moveTaskNoTooltip"
              @mouseleave="hideTaskNoTooltip"
            >
              {{ row.taskNo }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="projectName" label="项目" min-width="150" />
        <el-table-column label="触发方式" width="95">
          <template #default="{ row }">{{ triggerText(row.triggerType) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="plain">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="方式" width="100">
          <template #default="{ row }">{{ reviewModeText(row.reviewMode) }}</template>
        </el-table-column>
        <el-table-column prop="reviewBranch" label="分支" width="110" />
        <el-table-column prop="commitCount" label="提交" width="80" />
        <el-table-column prop="diffFileCount" label="文件" width="80" />
        <el-table-column prop="issueCount" label="问题" width="80" />
        <el-table-column prop="aiCallCount" label="模型调用" width="95" />
        <el-table-column label="成功/失败" width="105">
          <template #default="{ row }">{{ row.aiSuccessCount || 0 }} / {{ row.aiFailureCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="Token" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.totalTokenCount) }}</template>
        </el-table-column>
        <el-table-column label="裁剪" width="100">
          <template #default="{ row }">
            <el-tag v-if="hasSkipped(row)" type="warning" effect="plain">{{ (row.skippedCommitCount || 0) + (row.skippedFileCount || 0) }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="提示/错误" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.warningMessage || row.errorMessage || '' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Eye" @click="openDetail(row.id)">详情</el-button>
            <el-button v-if="isFinished(row)" link type="primary" :icon="FileText" @click="openReport(row.id)">报告</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="warning" @click="cancelTask(row.id)">取消</el-button>
            <el-button v-if="row.status === 'FAILED'" link type="primary" @click="retryTask(row.id)">重试</el-button>
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
          @size-change="loadTasks"
          @current-change="loadTasks"
        />
      </div>
    </section>

    <el-dialog v-model="startDialogVisible" title="手动发起检视" width="680px">
      <el-form ref="startFormRef" :model="startForm" :rules="startRules" label-width="112px">
        <el-form-item label="项目" prop="projectId">
          <el-select v-model="startForm.projectId" filterable placeholder="选择启用项目" @change="fillProjectDefaults">
            <el-option v-for="project in projectOptions" :key="project.id" :label="project.projectName" :value="project.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="检视方式">
          <el-segmented v-model="startForm.reviewMode" :options="reviewModeOptions" />
        </el-form-item>
        <el-form-item label="检出分支" required>
          <el-input v-model="startForm.branch" @change="loadStartCommits">
            <template #append>
              <el-button :icon="RefreshCw" :loading="commitsLoading" @click="loadStartCommits" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item v-if="startForm.reviewMode === 'RANGE'" label="起始版本" required>
          <el-select v-model="startForm.baseRef" filterable :loading="commitsLoading" placeholder="选择起始提交">
            <el-option v-for="commit in startCommits" :key="`base-${commit.hash}`" :label="commitOptionLabel(commit)" :value="commit.hash" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="startForm.reviewMode === 'RANGE'" label="目标版本" required>
          <el-select v-model="startForm.targetRef" filterable :loading="commitsLoading" placeholder="选择结束提交">
            <el-option v-for="commit in startCommits" :key="`target-${commit.hash}`" :label="commitOptionLabel(commit)" :value="commit.hash" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="startForm.reviewMode === 'COMMIT'" label="提交版本" required>
          <el-select v-model="startForm.commitRef" filterable :loading="commitsLoading" placeholder="选择提交">
            <el-option v-for="commit in startCommits" :key="`commit-${commit.hash}`" :label="commitOptionLabel(commit)" :value="commit.hash" />
          </el-select>
        </el-form-item>
        <template v-if="startForm.reviewMode === 'SCAN'">
          <el-form-item label="扫描路径">
            <el-input v-model="startForm.scanPath" placeholder="多个路径使用逗号分隔" />
          </el-form-item>
          <el-form-item label="排除规则">
            <el-input v-model="startForm.scanExclude" placeholder="多个模式使用逗号分隔" />
          </el-form-item>
          <el-form-item label="Token 预算">
            <el-input-number v-model="startForm.maxTokensBudget" :min="0" :step="10000" />
          </el-form-item>
          <el-form-item label="跳过规划">
            <el-switch v-model="startForm.scanNoPlan" />
          </el-form-item>
        </template>
        <el-form-item v-if="startForm.reviewMode === 'YESTERDAY'" label="机器人通知">
          <el-switch v-model="startForm.sendNotification" active-text="发送" inactive-text="不发送" />
        </el-form-item>
        <el-form-item label="业务背景">
          <el-input v-model="startForm.background" type="textarea" :rows="3" maxlength="8000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="starting" @click="submitStart">发起检视</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="任务详情" size="560px">
      <div v-if="detail && isFinished(detail)" class="drawer-actions">
        <el-button type="primary" :icon="ListChecks" @click="viewTaskIssues(detail)">查看本次问题</el-button>
      </div>
      <div v-if="detail" class="detail-grid">
        <div><span>任务编号</span><strong>{{ detail.taskNo }}</strong></div>
        <div><span>项目</span><strong>{{ detail.projectName }}</strong></div>
        <div>
          <span>状态</span>
          <strong class="status-inline" :class="statusClass(detail.status)">
            <i class="status-dot" />
            {{ statusText(detail.status) }}
          </strong>
        </div>
        <div><span>分支</span><strong>{{ detail.reviewBranch }}</strong></div>
        <div><span>触发方式</span><strong>{{ triggerText(detail.triggerType) }}</strong></div>
        <div><span>检视方式</span><strong>{{ reviewModeText(detail.reviewMode) }}</strong></div>
        <div v-if="detail.reviewMode === 'YESTERDAY'"><span>机器人通知</span><strong>{{ detail.notifyEnabled === 1 ? '发送' : '不发送' }}</strong></div>
        <div v-if="detail.reviewStartTime" class="detail-full"><span>提交时间范围</span><strong>{{ detail.reviewStartTime }} 至 {{ detail.reviewEndTime }}</strong></div>
        <div v-if="detail.reviewMode === 'RANGE'"><span>版本区间</span><strong>{{ detail.baseRef }} → {{ detail.targetRef }}</strong></div>
        <div v-if="detail.reviewMode === 'COMMIT'"><span>提交版本</span><strong>{{ detail.commitRef }}</strong></div>
        <div v-if="detail.reviewMode === 'SCAN'"><span>扫描路径</span><strong>{{ detail.scanPath || '全部' }}</strong></div>
        <div><span>提交/文件/问题</span><strong>{{ detail.commitCount }} / {{ detail.diffFileCount }} / {{ detail.issueCount }}</strong></div>
        <div><span>跳过提交/文件</span><strong>{{ detail.skippedCommitCount || 0 }} / {{ detail.skippedFileCount || 0 }}</strong></div>
        <div><span>严重度分布</span><strong>严重 {{ detail.criticalCount }}，高 {{ detail.highCount }}，中 {{ detail.mediumCount }}，低 {{ detail.lowCount }}</strong></div>
        <div><span>模型调用</span><strong>{{ detail.aiCallCount }} 次（成功 {{ detail.aiSuccessCount || 0 }} / 失败 {{ detail.aiFailureCount || 0 }}）</strong></div>
        <div><span>Token 总量</span><strong>{{ formatNumber(detail.totalTokenCount) }}</strong></div>
        <div><span>输入/输出 Token</span><strong>{{ formatNumber(detail.inputTokenCount) }} / {{ formatNumber(detail.outputTokenCount) }}</strong></div>
        <div><span>缓存读/写 Token</span><strong>{{ formatNumber(detail.cacheReadTokenCount) }} / {{ formatNumber(detail.cacheWriteTokenCount) }}</strong></div>
        <div><span>开始时间</span><strong>{{ detail.startTime || '-' }}</strong></div>
        <div><span>结束时间</span><strong>{{ detail.endTime || '-' }}</strong></div>
        <div class="detail-full"><span>提示</span><pre>{{ detail.warningMessage || '-' }}</pre></div>
        <div class="detail-full"><span>错误</span><pre>{{ detail.errorMessage || '-' }}</pre></div>
      </div>
    </el-drawer>

    <el-dialog v-model="reportVisible" :title="report?.reportTitle || '检视报告'" width="980px" class="report-dialog">
      <div v-if="report" class="report-content" v-html="report.reportContent" />
    </el-dialog>

    <teleport to="body">
      <div
        v-if="taskNoTooltip.visible"
        class="cursor-tooltip"
        :style="{ left: `${taskNoTooltip.x}px`, top: `${taskNoTooltip.y}px` }"
      >
        {{ taskNoTooltip.content }}
      </div>
    </teleport>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { Eye, FileText, ListChecks, Plus, RefreshCw, Search } from 'lucide-vue-next'
import { listProjectCommits, pageProjects, type Project, type ProjectCommit } from '@/api/project'
import {
  cancelReviewTask,
  getReviewTask,
  pageReviewTasks,
  retryReviewTask,
  startReviewTask,
  type ReviewTask
} from '@/api/reviewTask'
import { getReviewReportByTask, type ReviewReport } from '@/api/reviewReport'

const loading = ref(false)
const starting = ref(false)
const commitsLoading = ref(false)
const startCommits = ref<ProjectCommit[]>([])
const startDialogVisible = ref(false)
const detailVisible = ref(false)
const reportVisible = ref(false)
const startFormRef = ref<FormInstance>()
const tasks = ref<ReviewTask[]>([])
const detail = ref<ReviewTask>()
const report = ref<ReviewReport>()
const total = ref(0)
const projectOptions = ref<Project[]>([])
const router = useRouter()
const route = useRoute()
let refreshTimer: number | undefined
const taskNoTooltip = reactive({
  visible: false,
  content: '',
  x: 0,
  y: 0
})

const query = reactive({
  projectName: '',
  status: '',
  pageNo: 1,
  pageSize: 10
})

const startForm = reactive({
  projectId: undefined as number | undefined,
  branch: '',
  reviewMode: 'RANGE' as 'RANGE' | 'YESTERDAY' | 'COMMIT' | 'WORKSPACE' | 'SCAN',
  baseRef: '',
  targetRef: '',
  commitRef: '',
  scanPath: '',
  scanExclude: '',
  scanNoPlan: false,
  maxTokensBudget: 500000,
  background: '',
  sendNotification: false
})

const reviewModeOptions = [
  { label: '分支区间', value: 'RANGE' },
  { label: '昨天提交', value: 'YESTERDAY' },
  { label: '单个提交', value: 'COMMIT' },
  { label: '工作区', value: 'WORKSPACE' },
  { label: '全量扫描', value: 'SCAN' }
]

const startRules: FormRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }]
}

async function loadTasks() {
  loading.value = true
  try {
    const page = await pageReviewTasks({
      projectName: query.projectName || undefined,
      status: query.status || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize
    })
    tasks.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function loadProjects() {
  const page = await pageProjects({ status: 1, pageNo: 1, pageSize: 200 })
  projectOptions.value = page.records
}

async function openStart() {
  if (!projectOptions.value.length) {
    await loadProjects()
  }
  startForm.projectId = undefined
  Object.assign(startForm, {
    branch: '', reviewMode: 'RANGE', baseRef: '', targetRef: '',
    commitRef: '', scanPath: '', scanExclude: '', scanNoPlan: false,
    maxTokensBudget: 500000, background: '', sendNotification: false
  })
  startDialogVisible.value = true
}

async function fillProjectDefaults(projectId: number) {
  const project = projectOptions.value.find((item) => item.id === projectId)
  startForm.branch = project?.defaultBranch || ''
  startCommits.value = []
  startForm.baseRef = ''
  startForm.targetRef = ''
  startForm.commitRef = ''
  await loadStartCommits()
}

async function loadStartCommits() {
  if (!startForm.projectId || !startForm.branch.trim()) return
  commitsLoading.value = true
  try {
    startCommits.value = await listProjectCommits({
      projectId: startForm.projectId,
      branch: startForm.branch,
      limit: 100
    })
    const latest = startCommits.value[0]
    startForm.targetRef = latest?.hash || ''
    startForm.commitRef = latest?.hash || ''
    startForm.baseRef = startCommits.value[1]?.hash || latest?.parentHashes?.[0] || ''
    if (!latest) ElMessage.warning('该分支暂无可选择的提交')
  } finally {
    commitsLoading.value = false
  }
}

function commitOptionLabel(commit: ProjectCommit) {
  const time = commit.commitTime ? commit.commitTime.replace('T', ' ').slice(0, 19) : ''
  return `${commit.shortHash}  ${commit.subject}  ${commit.author}  ${time}`
}

async function submitStart() {
  await startFormRef.value?.validate()
  if (startForm.reviewMode === 'RANGE' && (!startForm.baseRef.trim() || !startForm.targetRef.trim())) {
    ElMessage.warning('请选择起始提交和结束提交')
    return
  }
  if (startForm.reviewMode === 'COMMIT' && !startForm.commitRef.trim()) {
    ElMessage.warning('请选择提交版本')
    return
  }
  starting.value = true
  try {
    await startReviewTask({
      projectId: startForm.projectId as number,
      branch: startForm.branch || undefined,
      reviewMode: startForm.reviewMode,
      baseRef: startForm.baseRef || undefined,
      targetRef: startForm.targetRef || undefined,
      commitRef: startForm.commitRef || undefined,
      scanPath: startForm.scanPath || undefined,
      scanExclude: startForm.scanExclude || undefined,
      scanNoPlan: startForm.scanNoPlan,
      maxTokensBudget: startForm.maxTokensBudget,
      background: startForm.background || undefined,
      sendNotification: startForm.reviewMode === 'YESTERDAY' && startForm.sendNotification
    })
    ElMessage.success('检视任务已提交')
    startDialogVisible.value = false
    await loadTasks()
  } finally {
    starting.value = false
  }
}

async function openDetail(id: number) {
  detail.value = await getReviewTask(id)
  detailVisible.value = true
}

async function openReport(taskId: number) {
  report.value = await getReviewReportByTask(taskId)
  reportVisible.value = true
}

function viewTaskIssues(task: ReviewTask) {
  detailVisible.value = false
  router.push({ path: '/issues', query: { taskId: String(task.id) } })
}

async function cancelTask(id: number) {
  await cancelReviewTask(id)
  ElMessage.success('任务已取消')
  await loadTasks()
}

async function retryTask(id: number) {
  await retryReviewTask(id)
  ElMessage.success('任务已重新提交')
  await loadTasks()
}

function setupAutoRefresh() {
  refreshTimer = window.setInterval(() => {
    if (tasks.value.some((task) => task.status === 'PENDING' || task.status === 'RUNNING')) {
      loadTasks()
    }
  }, 10000)
}

function statusText(status: string) {
  return ({ PENDING: '待执行', RUNNING: '执行中', SUCCESS: '成功', FAILED: '失败', CANCELED: '已取消' } as Record<string, string>)[status] || status
}

function statusType(status: string) {
  return ({ SUCCESS: 'success', FAILED: 'danger', RUNNING: 'warning', PENDING: 'primary', CANCELED: 'info' } as Record<string, string>)[status] || 'info'
}

function triggerText(triggerType?: string) {
  return triggerType === 'SCHEDULE' ? '每日定时' : '手动触发'
}

function reviewModeText(mode?: string) {
  return ({ RANGE: '分支区间', YESTERDAY: '昨天提交', COMMIT: '单个提交', WORKSPACE: '工作区', SCAN: '全量扫描' } as Record<string, string>)[mode || 'RANGE'] || mode || '分支区间'
}

function statusClass(status: string) {
  return `status-${String(status || '').toLowerCase()}`
}

function hasSkipped(task: ReviewTask) {
  return (task.skippedCommitCount || 0) > 0 || (task.skippedFileCount || 0) > 0
}

function isFinished(task: ReviewTask) {
  return ['SUCCESS', 'FAILED', 'CANCELED'].includes(task.status)
}

function formatNumber(value?: number) {
  return new Intl.NumberFormat('zh-CN').format(value || 0)
}

function showTaskNoTooltip(event: MouseEvent, content: string) {
  taskNoTooltip.content = content
  taskNoTooltip.visible = true
  updateTaskNoTooltipPosition(event)
}

function moveTaskNoTooltip(event: MouseEvent) {
  if (taskNoTooltip.visible) {
    updateTaskNoTooltipPosition(event)
  }
}

function hideTaskNoTooltip() {
  taskNoTooltip.visible = false
}

function updateTaskNoTooltipPosition(event: MouseEvent) {
  const offset = 12
  const maxTooltipWidth = Math.min(520, window.innerWidth - 32)
  taskNoTooltip.x = Math.max(16, Math.min(event.clientX + offset, window.innerWidth - maxTooltipWidth - 16))
  taskNoTooltip.y = Math.max(16, Math.min(event.clientY + offset, window.innerHeight - 48))
}

onMounted(async () => {
  await Promise.all([loadTasks(), loadProjects()])
  setupAutoRefresh()
  const taskIdValue = Array.isArray(route.query.taskId) ? route.query.taskId[0] : route.query.taskId
  const taskId = Number(taskIdValue)
  if (route.query.report === '1' && Number.isFinite(taskId) && taskId > 0) {
    await openReport(taskId)
  }
})

onBeforeUnmount(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
  }
})
</script>

<style scoped>
.task-no-cell {
  display: block;
  max-width: 100%;
  overflow: hidden;
  position: relative;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}

.status-inline {
  align-items: center;
  color: #475569;
  display: inline-flex;
  gap: 6px;
  line-height: 20px;
}

.status-dot {
  border-radius: 999px;
  display: inline-block;
  height: 7px;
  width: 7px;
}

.status-success {
  color: #047857;
}

.status-success .status-dot {
  background: #10b981;
}

.status-failed {
  color: #b91c1c;
}

.status-failed .status-dot {
  background: #ef4444;
}

.status-running {
  color: #b45309;
}

.status-running .status-dot {
  background: #f59e0b;
}

.status-pending {
  color: #1d4ed8;
}

.status-pending .status-dot {
  background: #3b82f6;
}

.status-canceled {
  color: #64748b;
}

.status-canceled .status-dot {
  background: #94a3b8;
}
</style>
