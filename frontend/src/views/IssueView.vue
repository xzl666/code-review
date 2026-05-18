<template>
  <div class="page-stack">
    <section class="toolbar">
      <div class="toolbar-title">
        <strong>问题列表</strong>
        <span>查看 AI 与脚本规则发现的问题，并完成处理闭环</span>
      </div>
      <el-input-number v-model="query.taskId" :min="1" placeholder="任务 ID" class="toolbar-select" />
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
    </section>

    <section class="panel data-panel">
      <div class="panel-header">
        <div>
          <h2>问题明细</h2>
          <p>行号为空表示模型或脚本未能定位到稳定行号</p>
        </div>
        <el-tag effect="plain">Total {{ total }}</el-tag>
      </div>
      <el-table v-loading="loading" :data="issues" stripe class="data-table">
        <el-table-column prop="severity" label="严重度" width="100">
          <template #default="{ row }">
            <el-tag :type="severityType(row.severity)">{{ severityText(row.severity) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="issueSource" label="来源" width="90" />
        <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip />
        <el-table-column prop="filePath" label="文件" min-width="260" show-overflow-tooltip />
        <el-table-column label="行号" width="100">
          <template #default="{ row }">{{ lineText(row.startLine) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">{{ statusText(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from 'lucide-vue-next'
import { ignoreIssue, markIssueFixed, pageIssues, type ReviewIssue } from '@/api/issue'

const loading = ref(false)
const issues = ref<ReviewIssue[]>([])
const total = ref(0)

const query = reactive({
  taskId: undefined as number | undefined,
  severity: '',
  status: 'OPEN',
  pageNo: 1,
  pageSize: 10
})

async function loadIssues() {
  loading.value = true
  try {
    const page = await pageIssues({
      taskId: query.taskId,
      severity: query.severity || undefined,
      status: query.status || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize
    })
    issues.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
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

onMounted(loadIssues)
</script>
