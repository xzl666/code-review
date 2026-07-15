<template>
  <div class="page-stack">
    <section class="toolbar">
      <div class="toolbar-title">
        <strong>AI Skill</strong>
        <span>维护代码检视关注点，约束 AI 按统一 JSON 输出问题</span>
      </div>
      <el-input v-model="query.skillName" clearable placeholder="Skill 名称" class="toolbar-input" @keyup.enter="loadSkills">
        <template #prefix><Search :size="16" /></template>
      </el-input>
      <el-select v-model="query.projectType" clearable placeholder="项目类型" class="toolbar-select">
        <el-option label="全部" value="ALL" />
        <el-option label="前端" value="FRONTEND" />
        <el-option label="后端" value="BACKEND" />
      </el-select>
      <el-select v-model="query.status" clearable placeholder="状态" class="toolbar-select">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button :icon="Search" @click="loadSkills">查询</el-button>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增 Skill</el-button>
    </section>

    <section class="panel data-panel">
      <div class="panel-header">
        <div>
          <h2>Skill 列表</h2>
          <p>根据关注点和代码片段，让大模型返回统一 issues JSON</p>
        </div>
        <el-tag effect="plain">共 {{ total }} 条</el-tag>
      </div>
      <el-table v-loading="loading" :data="skills" stripe class="data-table">
        <el-table-column prop="skillName" label="名称" min-width="170" />
        <el-table-column prop="skillCode" label="编码" min-width="180">
          <template #default="{ row }">
            <span class="table-ellipsis">{{ row.skillCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="projectType" label="项目类型" width="110">
          <template #default="{ row }">{{ projectTypeLabel(row.projectType) }}</template>
        </el-table-column>
        <el-table-column prop="ruleMatchingEnabled" label="规则匹配" width="100">
          <template #default="{ row }">
            <el-tag :type="row.ruleMatchingEnabled === 1 ? 'success' : 'info'">
              {{ row.ruleMatchingEnabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reviewGuidelines" label="检视关注点" min-width="320">
          <template #default="{ row }">
            <span class="table-ellipsis">{{ guidelinesSummary(row.reviewGuidelines) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="100" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 1" link type="warning" :icon="Pause" @click="setStatus(row, false)">停用</el-button>
            <el-button v-else link type="success" :icon="Play" @click="setStatus(row, true)">启用</el-button>
            <el-button link type="danger" :icon="Trash2" @click="removeSkill(row)">删除</el-button>
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
          @size-change="loadSkills"
          @current-change="loadSkills"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑 Skill' : '新增 Skill'" width="860px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="Skill 名称" prop="skillName">
          <el-input v-model="form.skillName" placeholder="例如：后端 Java Web 默认检视 Skill" />
        </el-form-item>
        <el-form-item label="Skill 编码" prop="skillCode">
          <el-input v-model="form.skillCode" placeholder="例如：DEFAULT_BACKEND_JAVA_WEB_REVIEW" />
        </el-form-item>
        <el-form-item label="适用项目" prop="projectType">
          <el-segmented v-model="form.projectType" :options="projectTypeOptions" />
        </el-form-item>
        <el-form-item label="版本" prop="version">
          <el-input v-model="form.version" />
        </el-form-item>
        <el-form-item label="检视关注点" prop="reviewGuidelines">
          <el-input
            v-model="form.reviewGuidelines"
            type="textarea"
            :rows="12"
            spellcheck="false"
            placeholder="每行一条具体规则，例如：
接口必须校验鉴权和越权访问
数据库查询必须分页，避免 N+1 和全表加载
事务方法不能吞掉异常导致不回滚
日志不能输出 token、密码、身份证、手机号等敏感信息
外部输入拼接 SQL、路径、命令、URL 前必须校验或参数化"
          />
        </el-form-item>
        <el-form-item label="代码匹配范围">
          <el-switch
            v-model="form.ruleMatchingEnabled"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
        <el-form-item v-if="form.ruleMatchingEnabled === 1" label="匹配规则">
          <el-input
            v-model="form.matchRules"
            type="textarea"
            :rows="7"
            spellcheck="false"
            placeholder="每行一条：ext:java；path:**/src/main/java/**；contains:@Transactional；regex:.*Controller"
          />
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
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import { Edit, Pause, Play, Plus, Search, Trash2 } from 'lucide-vue-next'
import {
  createSkill,
  deleteSkill,
  disableSkill,
  enableSkill,
  pageSkills,
  updateSkill,
  type Skill,
  type SkillForm
} from '@/api/skill'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number>()
const formRef = ref<FormInstance>()
const skills = ref<Skill[]>([])
const total = ref(0)

const projectTypeOptions = [
  { label: '全部', value: 'ALL' },
  { label: '前端', value: 'FRONTEND' },
  { label: '后端', value: 'BACKEND' }
]

const query = reactive({
  skillName: '',
  projectType: undefined as string | undefined,
  status: undefined as number | undefined,
  pageNo: 1,
  pageSize: 10
})

const form = reactive<SkillForm>({
  skillName: '',
  skillCode: '',
  version: '1.0.0',
  projectType: 'ALL',
  ruleMatchingEnabled: 0,
  matchRules: '',
  reviewGuidelines: ''
})

const rules: FormRules = {
  skillName: [{ required: true, message: '请输入 Skill 名称', trigger: 'blur' }],
  skillCode: [{ required: true, message: '请输入 Skill 编码', trigger: 'blur' }],
  projectType: [{ required: true, message: '请选择适用项目', trigger: 'change' }],
  version: [{ required: true, message: '请输入版本', trigger: 'blur' }],
  reviewGuidelines: [{ required: true, message: '请输入检视关注点', trigger: 'blur' }]
}

async function loadSkills() {
  loading.value = true
  try {
    const page = await pageSkills({
      skillName: query.skillName || undefined,
      projectType: query.projectType,
      status: query.status,
      pageNo: query.pageNo,
      pageSize: query.pageSize
    })
    skills.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingId.value = undefined
  Object.assign(form, {
    id: undefined,
    skillName: '',
    skillCode: '',
    version: '1.0.0',
    projectType: 'ALL',
    ruleMatchingEnabled: 0,
    matchRules: '',
    reviewGuidelines: '',
    status: undefined
  })
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: Skill) {
  editingId.value = row.id
  Object.assign(form, {
    ...row,
    projectType: row.projectType || 'ALL',
    ruleMatchingEnabled: row.ruleMatchingEnabled ?? 0,
    matchRules: row.matchRules || '',
    reviewGuidelines: row.reviewGuidelines || ''
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = { ...form, matchRules: form.ruleMatchingEnabled === 1 ? form.matchRules : '' }
    if (editingId.value) {
      await updateSkill({ ...payload, id: editingId.value, status: form.status ?? 1 })
      ElMessage.success('Skill 已更新')
    } else {
      await createSkill(payload)
      ElMessage.success('Skill 已创建')
    }
    dialogVisible.value = false
    await loadSkills()
  } finally {
    saving.value = false
  }
}

async function setStatus(row: Skill, enabled: boolean) {
  if (enabled) {
    await enableSkill(row.id)
  } else {
    await disableSkill(row.id)
  }
  ElMessage.success(enabled ? 'Skill 已启用' : 'Skill 已停用')
  await loadSkills()
}

async function removeSkill(row: Skill) {
  await ElMessageBox.confirm(`确认删除 ${row.skillName}？`, '删除确认', { type: 'warning' })
  await deleteSkill(row.id)
  ElMessage.success('Skill 已删除')
  await loadSkills()
}

function guidelinesSummary(value?: string) {
  const lines = (value || '')
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean)
  if (lines.length === 0) return '-'
  return lines.slice(0, 3).join('；')
}

function projectTypeLabel(value?: string) {
  if (value === 'FRONTEND') return '前端'
  if (value === 'BACKEND') return '后端'
  return '全部'
}

onMounted(loadSkills)
</script>
