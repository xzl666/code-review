<template>
  <div class="page-stack">
    <section class="toolbar">
      <div class="toolbar-title">
        <strong>检视任务</strong>
        <span>跟踪 Git、AI 与脚本规则的执行状态</span>
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
        <el-tag effect="plain">Total {{ total }}</el-tag>
      </div>
      <el-table v-loading="loading" :data="tasks" stripe class="data-table">
        <el-table-column prop="taskNo" label="任务编号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="projectName" label="项目" min-width="150" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reviewBranch" label="分支" width="110" />
        <el-table-column prop="commitCount" label="提交" width="80" />
        <el-table-column prop="diffFileCount" label="文件" width="80" />
        <el-table-column prop="issueCount" label="问题" width="80" />
        <el-table-column prop="aiCallCount" label="AI 调用" width="95" />
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

    <el-dialog v-model="startDialogVisible" title="手动发起检视" width="620px">
      <el-form ref="startFormRef" :model="startForm" :rules="startRules" label-width="96px">
        <el-form-item label="项目" prop="projectId">
          <el-select v-model="startForm.projectId" filterable placeholder="选择启用项目" @change="fillProjectDefaults">
            <el-option v-for="project in projectOptions" :key="project.id" :label="project.projectName" :value="project.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分支">
          <el-input v-model="startForm.branch" placeholder="为空时使用项目默认分支" />
        </el-form-item>
        <el-form-item label="检视天数">
          <el-input-number v-model="startForm.reviewDays" :min="1" :max="365" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="starting" @click="submitStart">发起检视</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="任务详情" size="560px">
      <div v-if="detail" class="detail-grid">
        <div><span>任务编号</span><strong>{{ detail.taskNo }}</strong></div>
        <div><span>项目</span><strong>{{ detail.projectName }}</strong></div>
        <div><span>状态</span><el-tag :type="statusType(detail.status)">{{ statusText(detail.status) }}</el-tag></div>
        <div><span>分支</span><strong>{{ detail.reviewBranch }}</strong></div>
        <div><span>检视天数</span><strong>{{ detail.reviewDays }}</strong></div>
        <div><span>提交/文件/问题</span><strong>{{ detail.commitCount }} / {{ detail.diffFileCount }} / {{ detail.issueCount }}</strong></div>
        <div><span>跳过提交/文件</span><strong>{{ detail.skippedCommitCount || 0 }} / {{ detail.skippedFileCount || 0 }}</strong></div>
        <div><span>严重度分布</span><strong>阻断 {{ detail.blockerCount }}，严重 {{ detail.criticalCount }}，主要 {{ detail.majorCount }}，次要 {{ detail.minorCount }}，提示 {{ detail.infoCount }}</strong></div>
        <div><span>AI 调用</span><strong>{{ detail.aiCallCount }}</strong></div>
        <div><span>开始时间</span><strong>{{ detail.startTime || '-' }}</strong></div>
        <div><span>结束时间</span><strong>{{ detail.endTime || '-' }}</strong></div>
        <div class="detail-full"><span>提示</span><pre>{{ detail.warningMessage || '-' }}</pre></div>
        <div class="detail-full"><span>错误</span><pre>{{ detail.errorMessage || '-' }}</pre></div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Eye, Plus, RefreshCw, Search } from 'lucide-vue-next'
import { pageProjects, type Project } from '@/api/project'
import {
  cancelReviewTask,
  getReviewTask,
  pageReviewTasks,
  retryReviewTask,
  startReviewTask,
  type ReviewTask
} from '@/api/reviewTask'

const loading = ref(false)
const starting = ref(false)
const startDialogVisible = ref(false)
const detailVisible = ref(false)
const startFormRef = ref<FormInstance>()
const tasks = ref<ReviewTask[]>([])
const detail = ref<ReviewTask>()
const total = ref(0)
const projectOptions = ref<Project[]>([])
let refreshTimer: number | undefined

const query = reactive({
  projectName: '',
  status: '',
  pageNo: 1,
  pageSize: 10
})

const startForm = reactive({
  projectId: undefined as number | undefined,
  branch: '',
  reviewDays: 7
})

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
  startForm.branch = ''
  startForm.reviewDays = 7
  startDialogVisible.value = true
}

function fillProjectDefaults(projectId: number) {
  const project = projectOptions.value.find((item) => item.id === projectId)
  startForm.branch = project?.defaultBranch || ''
  startForm.reviewDays = project?.reviewDays || 7
}

async function submitStart() {
  await startFormRef.value?.validate()
  starting.value = true
  try {
    await startReviewTask({
      projectId: startForm.projectId as number,
      branch: startForm.branch || undefined,
      reviewDays: startForm.reviewDays
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

function hasSkipped(task: ReviewTask) {
  return (task.skippedCommitCount || 0) > 0 || (task.skippedFileCount || 0) > 0
}

onMounted(() => {
  loadTasks()
  loadProjects()
  setupAutoRefresh()
})

onBeforeUnmount(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
  }
})
</script>
