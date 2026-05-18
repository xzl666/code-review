<template>
  <div class="page-stack">
    <section class="toolbar project-toolbar">
      <div class="toolbar-title">
        <strong>项目资产</strong>
        <span>接入仓库、令牌和默认检视范围</span>
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
        <el-table-column prop="ownerName" label="责任人" width="120" />
        <el-table-column prop="reviewDays" label="检视天数" width="100" />
        <el-table-column label="定时检视" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.scheduleEnabled === 1" type="success" effect="plain">{{ row.scheduleCron }}</el-tag>
            <el-tag v-else type="info" effect="plain">未启用</el-tag>
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑项目' : '新增项目'" width="680px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="104px">
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="form.projectName" />
        </el-form-item>
        <el-form-item label="项目编码" prop="projectCode">
          <el-input v-model="form.projectCode" />
        </el-form-item>
        <el-form-item label="项目类型" prop="projectType">
          <el-segmented v-model="form.projectType" :options="projectTypeOptions" />
        </el-form-item>
        <el-form-item label="仓库地址" prop="repoUrl">
          <el-input v-model="form.repoUrl" />
        </el-form-item>
        <el-form-item label="默认分支" prop="defaultBranch">
          <el-input v-model="form.defaultBranch" />
        </el-form-item>
        <el-form-item label="责任人">
          <el-input v-model="form.ownerName" />
        </el-form-item>
        <el-form-item label="检视天数" prop="reviewDays">
          <el-input-number v-model="form.reviewDays" :min="1" :max="365" />
        </el-form-item>
        <el-form-item label="定时检视">
          <el-switch v-model="scheduleEnabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="Cron 表达式" prop="scheduleCron">
          <el-input v-model="form.scheduleCron" placeholder="例如：0 0 9 * * *">
            <template #append>秒 分 时 日 月 周</template>
          </el-input>
        </el-form-item>
        <el-form-item label="访问令牌">
          <el-input v-model="form.projectToken" type="password" show-password />
        </el-form-item>
        <el-form-item label="默认令牌">
          <el-switch v-model="useDefaultToken" active-text="使用" inactive-text="不用" />
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Pause, Play, Plus, Rocket, Search, Trash2 } from 'lucide-vue-next'
import {
  createProject,
  deleteProject,
  disableProject,
  enableProject,
  pageProjects,
  updateProject,
  type Project,
  type ProjectForm
} from '@/api/project'
import { startReviewTask } from '@/api/reviewTask'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number>()
const formRef = ref<FormInstance>()
const projects = ref<Project[]>([])
const total = ref(0)

const query = reactive({
  projectName: '',
  projectType: '',
  status: undefined as number | undefined,
  pageNo: 1,
  pageSize: 10
})

const form = reactive<ProjectForm>({
  projectName: '',
  projectCode: '',
  projectType: 'BACKEND',
  repoUrl: '',
  projectToken: '',
  useDefaultToken: 1,
  defaultBranch: 'master',
  ownerName: '',
  reviewDays: 7,
  scheduleCron: '',
  scheduleEnabled: 0,
  remark: ''
})

const useDefaultToken = computed({
  get: () => form.useDefaultToken === 1,
  set: (value: boolean) => {
    form.useDefaultToken = value ? 1 : 0
  }
})

const scheduleEnabled = computed({
  get: () => form.scheduleEnabled === 1,
  set: (value: boolean) => {
    form.scheduleEnabled = value ? 1 : 0
  }
})

const projectTypeOptions = [
  { label: '后端项目', value: 'BACKEND' },
  { label: '前端项目', value: 'FRONTEND' }
]

const rules: FormRules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  projectCode: [{ required: true, message: '请输入项目编码', trigger: 'blur' }],
  projectType: [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  repoUrl: [{ required: true, message: '请输入仓库地址', trigger: 'blur' }],
  reviewDays: [{ required: true, message: '请输入检视天数', trigger: 'change' }]
}

function resetForm() {
  editingId.value = undefined
  Object.assign(form, {
    projectName: '',
    projectCode: '',
    projectType: 'BACKEND',
    repoUrl: '',
    projectToken: '',
    useDefaultToken: 1,
    defaultBranch: 'master',
    ownerName: '',
    reviewDays: 7,
    scheduleCron: '',
    scheduleEnabled: 0,
    remark: ''
  })
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
  editingId.value = row.id
  Object.assign(form, {
    id: row.id,
    projectName: row.projectName,
    projectCode: row.projectCode,
    projectType: row.projectType,
    repoUrl: row.repoUrl,
    projectToken: '',
    useDefaultToken: row.useDefaultToken,
    defaultBranch: row.defaultBranch,
    ownerName: row.ownerName || '',
    reviewDays: row.reviewDays,
    scheduleCron: row.scheduleCron || '',
    scheduleEnabled: row.scheduleEnabled || 0,
    status: row.status,
    remark: row.remark || ''
  })
  dialogVisible.value = true
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
  await ElMessageBox.confirm(`确认立即触发「${row.projectName}」的一次代码检视？`, '触发检视', {
    type: 'warning',
    confirmButtonText: '触发',
    cancelButtonText: '取消'
  })
  await startReviewTask({
    projectId: row.id,
    branch: row.defaultBranch,
    reviewDays: row.reviewDays
  })
  ElMessage.success('检视任务已提交')
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

onMounted(loadProjects)
</script>
