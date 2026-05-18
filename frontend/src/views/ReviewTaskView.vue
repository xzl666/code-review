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
        <el-table-column prop="errorMessage" label="提示/错误" min-width="260" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshCw, Search } from 'lucide-vue-next'
import { cancelReviewTask, pageReviewTasks, retryReviewTask, type ReviewTask } from '@/api/reviewTask'

const loading = ref(false)
const tasks = ref<ReviewTask[]>([])
const total = ref(0)

const query = reactive({
  projectName: '',
  status: '',
  pageNo: 1,
  pageSize: 10
})

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

function statusText(status: string) {
  return ({ PENDING: '待执行', RUNNING: '执行中', SUCCESS: '成功', FAILED: '失败', CANCELED: '已取消' } as Record<string, string>)[status] || status
}

function statusType(status: string) {
  return ({ SUCCESS: 'success', FAILED: 'danger', RUNNING: 'warning', CANCELED: 'info' } as Record<string, string>)[status] || 'info'
}

onMounted(loadTasks)
</script>
