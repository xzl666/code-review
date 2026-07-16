<template>
  <div class="page-stack">
    <section class="toolbar project-toolbar">
      <div class="toolbar-title">
        <strong>项目资产</strong>
        <span>接入仓库和默认检视范围</span>
      </div>
      <el-input v-model="query.projectName" clearable placeholder="搜索项目名称" class="toolbar-input" @keyup.enter="loadProjects">
        <template #prefix><Search :size="16" /></template>
      </el-input>
      <el-select v-model="query.projectType" clearable placeholder="项目类型" class="toolbar-select">
        <el-option label="后端项目" value="BACKEND" />
        <el-option label="前端项目" value="FRONTEND" />
      </el-select>
      <el-select v-model="query.status" clearable placeholder="状态" class="toolbar-select">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button :icon="Search" @click="loadProjects">查询</el-button>
      <el-button :icon="Upload" @click="importDialogVisible = true">Excel 导入</el-button>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增项目</el-button>
    </section>

    <section class="panel data-panel">
      <div class="panel-header">
        <div>
          <h2>项目列表</h2>
          <p>所有接入代码检视平台的 Gitee 仓库</p>
        </div>
        <el-tag effect="plain">共 {{ total }} 条</el-tag>
      </div>
      <el-table v-loading="loading" :data="projects" stripe class="data-table">
        <el-table-column prop="projectName" label="项目名称" min-width="150" />
        <el-table-column prop="projectType" label="类型" width="110">
          <template #default="{ row }">
            <el-tag>{{ row.projectType === 'BACKEND' ? '后端' : '前端' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="repoUrl" label="仓库地址" min-width="280" show-overflow-tooltip />
        <el-table-column prop="defaultBranch" label="默认分支" width="120" />
        <el-table-column label="项目负责人" min-width="180">
          <template #default="{ row }">{{ row.owners?.map((user: any) => user.userName).join('、') || '-' }}</template>
        </el-table-column>
        <el-table-column label="定时检视" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="plain">{{ row.status === 1 ? '每日 01:00' : '不执行' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结果通知" width="110">
          <template #default="{ row }">
            <el-tag :type="row.notifyEnabled === 0 ? 'info' : 'success'" effect="plain">{{ row.notifyEnabled === 0 ? '关闭' : '开启' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Rocket" @click="triggerReview(row)">触发检视</el-button>
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 1" link type="warning" :icon="Pause" @click="setStatus(row, false)">停用</el-button>
            <el-button v-else link type="success" :icon="Play" @click="setStatus(row, true)">启用</el-button>
            <el-button link type="danger" :icon="Trash2" @click="removeProject(row)">删除</el-button>
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
          @size-change="loadProjects"
          @current-change="loadProjects"
        />
      </div>
    </section>

    <el-dialog v-model="importDialogVisible" title="Excel 导入项目" width="620px">
      <div class="import-actions">
        <a href="/api/project/import-template" download><el-button :icon="Download">下载导入模板</el-button></a>
      </div>
      <el-upload drag :auto-upload="false" :limit="1" accept=".xlsx" :on-change="selectImportFile" :on-remove="removeImportFile">
        <Upload :size="28" />
        <div>选择或拖入 .xlsx 文件</div>
      </el-upload>
      <el-alert v-if="importResult" :type="importResult.failureCount ? 'warning' : 'success'" :closable="false" show-icon>
        <template #title>成功 {{ importResult.successCount }} 条，失败 {{ importResult.failureCount }} 条</template>
        <div v-for="reason in importResult.failureReasons" :key="reason">{{ reason }}</div>
      </el-alert>
      <template #footer>
        <el-button @click="importDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importFile" @click="submitImport">开始导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑项目' : '新增项目'" width="680px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="104px">
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="form.projectName" />
        </el-form-item>
        <el-form-item label="项目类型" prop="projectType">
          <el-segmented v-model="form.projectType" :options="projectTypeOptions" />
        </el-form-item>
        <el-form-item label="仓库地址" prop="repoUrl">
          <el-input v-model="form.repoUrl" placeholder="owner/repo.git、https://... 或 git@..." @blur="validateRepo">
            <template #append>
              <el-tooltip :content="repoCheckTooltip" placement="top" :disabled="!repoCheckMessage">
                <el-button :loading="repoChecking" @click="validateRepo">
                  <CheckCircle2 v-if="repoCheckMessage && repoCheckSuccess" :size="15" class="repo-check-icon is-success" />
                  <XCircle v-else-if="repoCheckMessage" :size="15" class="repo-check-icon is-error" />
                  校验
                </el-button>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="默认分支" prop="defaultBranch">
          <el-input v-model="form.defaultBranch" />
        </el-form-item>
        <el-form-item label="项目负责人">
          <el-select v-model="form.ownerUserIds" multiple filterable collapse-tags placeholder="可选择多个负责人">
            <el-option v-for="user in userOptions" :key="user.userId" :label="`${user.userName} ${user.employeeId}`" :value="user.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果通知">
          <el-switch v-model="notifyEnabled" active-text="开启" inactive-text="关闭" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reviewDialogVisible" :title="`触发检视 - ${reviewProject?.projectName || ''}`" width="680px">
      <el-form label-width="112px">
        <el-form-item label="检视方式">
          <el-segmented v-model="reviewForm.reviewMode" :options="reviewModeOptions" />
        </el-form-item>
        <el-form-item label="检出分支" required>
          <el-input v-model="reviewForm.branch" @change="loadReviewCommits">
            <template #append>
              <el-button :icon="RefreshCw" :loading="commitsLoading" @click="loadReviewCommits" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item v-if="reviewForm.reviewMode === 'RANGE'" label="起始版本" required>
          <el-select v-model="reviewForm.baseRef" filterable :loading="commitsLoading" placeholder="选择起始提交">
            <el-option v-for="commit in reviewCommits" :key="`base-${commit.hash}`" :label="commitOptionLabel(commit)" :value="commit.hash" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="reviewForm.reviewMode === 'RANGE'" label="目标版本" required>
          <el-select v-model="reviewForm.targetRef" filterable :loading="commitsLoading" placeholder="选择结束提交">
            <el-option v-for="commit in reviewCommits" :key="`target-${commit.hash}`" :label="commitOptionLabel(commit)" :value="commit.hash" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="reviewForm.reviewMode === 'COMMIT'" label="提交版本" required>
          <el-select v-model="reviewForm.commitRef" filterable :loading="commitsLoading" placeholder="选择提交">
            <el-option v-for="commit in reviewCommits" :key="`commit-${commit.hash}`" :label="commitOptionLabel(commit)" :value="commit.hash" />
          </el-select>
        </el-form-item>
        <template v-if="reviewForm.reviewMode === 'SCAN'">
          <el-form-item label="扫描路径">
            <el-input v-model="reviewForm.scanPath" placeholder="多个路径使用逗号分隔" />
          </el-form-item>
          <el-form-item label="排除规则">
            <el-input v-model="reviewForm.scanExclude" placeholder="多个模式使用逗号分隔" />
          </el-form-item>
          <el-form-item label="Token 预算">
            <el-input-number v-model="reviewForm.maxTokensBudget" :min="0" :step="10000" />
          </el-form-item>
          <el-form-item label="跳过规划">
            <el-switch v-model="reviewForm.scanNoPlan" />
          </el-form-item>
        </template>
        <el-form-item v-if="reviewForm.reviewMode === 'YESTERDAY'" label="机器人通知">
          <el-switch v-model="reviewForm.sendNotification" active-text="发送" inactive-text="不发送" />
        </el-form-item>
        <el-form-item label="业务背景">
          <el-input v-model="reviewForm.background" type="textarea" :rows="3" maxlength="8000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewStarting" @click="submitReview">触发检视</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import { CheckCircle2, Download, Edit, Pause, Play, Plus, RefreshCw, Rocket, Search, Trash2, Upload, XCircle } from 'lucide-vue-next'
import {
  createProject,
  deleteProject,
  disableProject,
  enableProject,
  importProjects,
  listProjectCommits,
  pageProjects,
  testRepoConnection,
  updateProject,
  type Project,
  type ProjectForm,
  type ProjectCommit,
  type ImportProjectResult
} from '@/api/project'
import { startReviewTask } from '@/api/reviewTask'
import { listSystemUsers, type SystemUser } from '@/api/user'

const loading = ref(false)
const saving = ref(false)
const repoChecking = ref(false)
const dialogVisible = ref(false)
const reviewDialogVisible = ref(false)
const reviewStarting = ref(false)
const commitsLoading = ref(false)
const reviewCommits = ref<ProjectCommit[]>([])
const reviewProject = ref<Project>()
const editingId = ref<number>()
const formRef = ref<FormInstance>()
const projects = ref<Project[]>([])
const total = ref(0)
const repoCheckMessage = ref('')
const repoCheckSuccess = ref(false)
const userOptions = ref<SystemUser[]>([])
const importDialogVisible = ref(false)
const importing = ref(false)
const importFile = ref<File>()
const importResult = ref<ImportProjectResult>()

const repoCheckTooltip = computed(() => repoCheckMessage.value || '点击校验仓库地址')

const query = reactive({
  projectName: '',
  projectType: '',
  status: undefined as number | undefined,
  pageNo: 1,
  pageSize: 10
})

const form = reactive<ProjectForm>({
  projectName: '',
  projectType: 'BACKEND',
  repoUrl: '',
  defaultBranch: 'dev',
  ownerUserIds: [],
  scheduleCron: '0 0 7 * * *',
  scheduleEnabled: 1,
  notifyEnabled: 1,
  remark: ''
})

const notifyEnabled = computed({
  get: () => form.notifyEnabled === 1,
  set: (value: boolean) => {
    form.notifyEnabled = value ? 1 : 0
  }
})

const projectTypeOptions = [
  { label: '后端项目', value: 'BACKEND' },
  { label: '前端项目', value: 'FRONTEND' }
]

const reviewModeOptions = [
  { label: '分支区间', value: 'RANGE' },
  { label: '昨天提交', value: 'YESTERDAY' },
  { label: '单个提交', value: 'COMMIT' },
  { label: '工作区', value: 'WORKSPACE' },
  { label: '全量扫描', value: 'SCAN' }
]

const reviewForm = reactive({
  reviewMode: 'RANGE' as 'RANGE' | 'YESTERDAY' | 'COMMIT' | 'WORKSPACE' | 'SCAN',
  branch: '',
  baseRef: 'HEAD~1',
  targetRef: '',
  commitRef: 'HEAD',
  scanPath: '',
  scanExclude: '',
  scanNoPlan: false,
  maxTokensBudget: 500000,
  background: '',
  sendNotification: false
})

const rules: FormRules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  projectType: [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  repoUrl: [{ required: true, message: '请输入仓库地址', trigger: 'blur' }],
}

function resetForm() {
  editingId.value = undefined
  Object.assign(form, {
    projectName: '',
    projectType: 'BACKEND',
    repoUrl: '',
    defaultBranch: 'dev',
    ownerUserIds: [],
    scheduleCron: '0 0 7 * * *',
    scheduleEnabled: 1,
    notifyEnabled: 1,
    remark: ''
  })
  repoCheckMessage.value = ''
  repoCheckSuccess.value = false
}

async function loadProjects() {
  loading.value = true
  try {
    const page = await pageProjects({
      projectName: query.projectName || undefined,
      projectType: query.projectType || undefined,
      status: query.status,
      pageNo: query.pageNo,
      pageSize: query.pageSize
    })
    projects.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: Project) {
  repoCheckMessage.value = ''
  repoCheckSuccess.value = false
  editingId.value = row.id
  Object.assign(form, {
    id: row.id,
    projectName: row.projectName,
    projectType: row.projectType,
    repoUrl: row.repoUrl,
    defaultBranch: row.defaultBranch,
    ownerUserIds: row.ownerUserIds || [],
    scheduleCron: row.scheduleCron || '0 0 7 * * *',
    scheduleEnabled: row.scheduleEnabled ?? 1,
    notifyEnabled: row.notifyEnabled ?? 1,
    status: row.status,
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

async function validateRepo() {
  if (!form.repoUrl?.trim()) {
    return
  }
  repoChecking.value = true
  repoCheckMessage.value = ''
  try {
    const result = await testRepoConnection({
      repoUrl: form.repoUrl,
      branch: form.defaultBranch || 'dev',
      timeoutSeconds: 15
    })
    repoCheckSuccess.value = result.success
    repoCheckMessage.value = result.message
  } catch (error) {
    repoCheckSuccess.value = false
    repoCheckMessage.value = error instanceof Error ? error.message : '仓库校验失败'
  } finally {
    repoChecking.value = false
  }
}

function selectImportFile(uploadFile: any) {
  importFile.value = uploadFile.raw
  importResult.value = undefined
}

function removeImportFile() { importFile.value = undefined }

async function submitImport() {
  if (!importFile.value) return
  importing.value = true
  try {
    importResult.value = await importProjects(importFile.value)
    ElMessage.success(`导入完成：成功 ${importResult.value.successCount} 条`)
    await loadProjects()
  } finally {
    importing.value = false
  }
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    if (editingId.value) {
      await updateProject({ ...form, id: editingId.value, status: form.status ?? 1 })
      ElMessage.success('项目已更新')
    } else {
      await createProject(form)
      ElMessage.success('项目已创建')
    }
    dialogVisible.value = false
    await loadProjects()
  } finally {
    saving.value = false
  }
}

async function setStatus(row: Project, enabled: boolean) {
  if (enabled) {
    await enableProject(row.id)
  } else {
    await disableProject(row.id)
  }
  ElMessage.success(enabled ? '项目已启用' : '项目已停用')
  await loadProjects()
}

async function triggerReview(row: Project) {
  reviewProject.value = row
  Object.assign(reviewForm, {
    reviewMode: 'RANGE', branch: row.defaultBranch, baseRef: '', targetRef: '',
    commitRef: '', scanPath: '', scanExclude: '', scanNoPlan: false,
    maxTokensBudget: 500000, background: '', sendNotification: false
  })
  reviewDialogVisible.value = true
  await loadReviewCommits()
}

async function loadReviewCommits() {
  if (!reviewProject.value || !reviewForm.branch.trim()) return
  commitsLoading.value = true
  try {
    reviewCommits.value = await listProjectCommits({
      projectId: reviewProject.value.id,
      branch: reviewForm.branch,
      limit: 100
    })
    const latest = reviewCommits.value[0]
    reviewForm.targetRef = latest?.hash || ''
    reviewForm.commitRef = latest?.hash || ''
    reviewForm.baseRef = reviewCommits.value[1]?.hash || latest?.parentHashes?.[0] || ''
    if (!latest) ElMessage.warning('该分支暂无可选择的提交')
  } finally {
    commitsLoading.value = false
  }
}

function commitOptionLabel(commit: ProjectCommit) {
  const time = commit.commitTime ? commit.commitTime.replace('T', ' ').slice(0, 19) : ''
  return `${commit.shortHash}  ${commit.subject}  ${commit.author}  ${time}`
}

async function submitReview() {
  if (!reviewProject.value) return
  if (reviewForm.reviewMode === 'RANGE' && (!reviewForm.baseRef.trim() || !reviewForm.targetRef.trim())) {
    ElMessage.warning('请选择起始提交和结束提交')
    return
  }
  if (reviewForm.reviewMode === 'COMMIT' && !reviewForm.commitRef.trim()) {
    ElMessage.warning('请选择提交版本')
    return
  }
  reviewStarting.value = true
  try {
    await startReviewTask({
      projectId: reviewProject.value.id,
      branch: reviewForm.branch,
      reviewMode: reviewForm.reviewMode,
      baseRef: reviewForm.baseRef || undefined,
      targetRef: reviewForm.targetRef || undefined,
      commitRef: reviewForm.commitRef || undefined,
      scanPath: reviewForm.scanPath || undefined,
      scanExclude: reviewForm.scanExclude || undefined,
      scanNoPlan: reviewForm.scanNoPlan,
      maxTokensBudget: reviewForm.maxTokensBudget,
      background: reviewForm.background || undefined,
      sendNotification: reviewForm.reviewMode === 'YESTERDAY' && reviewForm.sendNotification
    })
    reviewDialogVisible.value = false
    ElMessage.success('检视任务已提交')
  } finally {
    reviewStarting.value = false
  }
}

async function removeProject(row: Project) {
  await ElMessageBox.confirm(`确认删除项目「${row.projectName}」？`, '删除项目', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await deleteProject(row.id)
  ElMessage.success('项目已删除')
  await loadProjects()
}

onMounted(async () => {
  await Promise.all([loadProjects(), listSystemUsers().then(users => { userOptions.value = users })])
})
</script>

<style scoped>
.repo-check-icon {
  margin-right: 4px;
}

.repo-check-icon.is-success {
  color: var(--el-color-success);
}

.repo-check-icon.is-error {
  color: var(--el-color-danger);
}

.import-actions { display: flex; justify-content: flex-end; margin-bottom: 12px; }
</style>
