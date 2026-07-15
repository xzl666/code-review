<template>
  <div class="page-stack">
    <section class="toolbar">
      <div class="toolbar-title">
        <strong>检视规则</strong>
        <span>维护 AI 规则和项目类型匹配关系</span>
      </div>
      <el-input v-model="query.ruleName" clearable placeholder="规则名称" class="toolbar-input" @keyup.enter="loadRules">
        <template #prefix><Search :size="16" /></template>
      </el-input>
      <el-select v-model="query.projectType" clearable placeholder="项目类型" class="toolbar-select">
        <el-option label="全部" value="ALL" />
        <el-option label="后端" value="BACKEND" />
        <el-option label="前端" value="FRONTEND" />
      </el-select>
      <el-button :icon="Search" @click="loadRules">查询</el-button>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增规则</el-button>
    </section>

    <section class="panel data-panel">
      <div class="panel-header">
        <div>
          <h2>规则列表</h2>
          <p>启用规则会参与 AI Skill 检视任务的执行链路</p>
        </div>
        <el-tag effect="plain">共 {{ total }} 条</el-tag>
      </div>
      <el-table v-loading="loading" :data="rulesData" stripe class="data-table">
        <el-table-column prop="ruleName" label="规则名称" min-width="150" />
        <el-table-column prop="ruleKind" label="类型" width="90">
          <template #default="{ row }">
            <el-tag>AI</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="projectType" label="项目" width="90">
          <template #default="{ row }">{{ projectTypeText(row.projectType) }}</template>
        </el-table-column>
        <el-table-column label="问题类型" width="120">
          <template #default="{ row }">{{ issueTypeText(row.ruleType) }}</template>
        </el-table-column>
        <el-table-column prop="severity" label="严重度" width="100">
          <template #default="{ row }">{{ severityText(row.severity) }}</template>
        </el-table-column>
        <el-table-column label="绑定 Skill" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ bindingText(row) }}</template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 1" link type="warning" :icon="Pause" @click="setStatus(row, false)">停用</el-button>
            <el-button v-else link type="success" :icon="Play" @click="setStatus(row, true)">启用</el-button>
            <el-button link type="danger" :icon="Trash2" @click="removeRule(row)">删除</el-button>
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
          @size-change="loadRules"
          @current-change="loadRules"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑规则' : '新增规则'" width="820px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="form.ruleName" />
        </el-form-item>
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input v-model="form.ruleCode" />
        </el-form-item>
        <el-form-item label="问题类型" prop="ruleType">
          <el-input v-model="form.ruleType" placeholder="STYLE / BUG / SECURITY / CUSTOM" />
        </el-form-item>
        <el-form-item label="严重度" prop="severity">
          <el-select v-model="form.severity">
            <el-option label="阻断" value="BLOCKER" />
            <el-option label="严重" value="CRITICAL" />
            <el-option label="主要" value="MAJOR" />
            <el-option label="次要" value="MINOR" />
            <el-option label="提示" value="INFO" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目类型" prop="projectType">
          <el-segmented v-model="form.projectType" :options="projectTypeOptions" />
        </el-form-item>
        <el-form-item label="绑定 Skill" prop="skillId">
          <el-select v-model="form.skillId" filterable placeholder="选择启用的 Skill">
            <el-option v-for="item in skillOptions" :key="item.id" :label="item.skillName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="Prompt 模板">
          <el-input v-model="form.promptTemplate" type="textarea" :rows="7" />
        </el-form-item>
        <el-form-item label="AI 生成需求">
          <el-input
            v-model="aiRequirement"
            type="textarea"
            :rows="3"
            placeholder="描述希望检视的问题，例如：检查 Java Web Controller 是否缺少参数校验，并输出中文修复建议"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :loading="generating" @click="generateDraft">AI 生成草稿</el-button>
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
  createRule,
  deleteRule,
  disableRule,
  enableRule,
  pageRules,
  updateRule,
  type Rule,
  type RuleForm
} from '@/api/rule'
import { createSkill, generateSkillDraft, pageSkills, type Skill } from '@/api/skill'
import { issueTypeText } from '@/utils/dictionaries'

const loading = ref(false)
const saving = ref(false)
const generating = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number>()
const formRef = ref<FormInstance>()
const rulesData = ref<Rule[]>([])
const total = ref(0)
const skillOptions = ref<Skill[]>([])
const aiRequirement = ref('')

const query = reactive({
  ruleName: '',
  projectType: '',
  pageNo: 1,
  pageSize: 10
})

const form = reactive<RuleForm>({
  ruleName: '',
  ruleCode: '',
  ruleKind: 'AI',
  ruleType: 'CUSTOM',
  severity: 'MAJOR',
  projectType: 'ALL',
  promptTemplate: '',
  skillId: undefined,
  scriptId: undefined,
  sortOrder: 0
})

const projectTypeOptions = [
  { label: '全部', value: 'ALL' },
  { label: '后端', value: 'BACKEND' },
  { label: '前端', value: 'FRONTEND' }
]

const formRules: FormRules = {
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  ruleCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }],
  ruleType: [{ required: true, message: '请输入问题类型', trigger: 'blur' }],
  severity: [{ required: true, message: '请选择严重度', trigger: 'change' }],
  projectType: [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  skillId: [{ required: true, message: '请选择 Skill', trigger: 'change' }]
}

async function loadRules() {
  loading.value = true
  try {
    const page = await pageRules({
      ruleName: query.ruleName || undefined,
      ruleKind: 'AI',
      projectType: query.projectType || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize
    })
    rulesData.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function loadBindings() {
  const skills = await pageSkills({ status: 1, pageNo: 1, pageSize: 200 })
  skillOptions.value = skills.records
}

function resetForm() {
  editingId.value = undefined
  Object.assign(form, {
    id: undefined,
    ruleName: '',
    ruleCode: '',
    ruleKind: 'AI',
    ruleType: 'CUSTOM',
    severity: 'MAJOR',
    projectType: 'ALL',
    promptTemplate: '',
    skillId: undefined,
    scriptId: undefined,
    status: undefined,
    sortOrder: 0
  })
  aiRequirement.value = ''
}

async function openCreate() {
  resetForm()
  await loadBindings()
  dialogVisible.value = true
}

async function openEdit(row: Rule) {
  await loadBindings()
  editingId.value = row.id
  Object.assign(form, { ...row })
  aiRequirement.value = row.promptTemplate || row.ruleName
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = { ...form, id: editingId.value, status: form.status ?? 1 }
    payload.ruleKind = 'AI'
    payload.scriptId = undefined
    if (editingId.value) {
      await updateRule(payload)
      ElMessage.success('规则已更新')
    } else {
      await createRule(payload)
      ElMessage.success('规则已创建')
    }
    dialogVisible.value = false
    await loadRules()
  } finally {
    saving.value = false
  }
}

async function setStatus(row: Rule, enabled: boolean) {
  if (enabled) {
    await enableRule(row.id)
  } else {
    await disableRule(row.id)
  }
  ElMessage.success(enabled ? '规则已启用' : '规则已停用')
  await loadRules()
}

async function removeRule(row: Rule) {
  await ElMessageBox.confirm(`确认删除 ${row.ruleName}？`, '删除确认', { type: 'warning' })
  await deleteRule(row.id)
  ElMessage.success('规则已删除')
  await loadRules()
}

async function generateDraft() {
  generating.value = true
  try {
    const requirement = aiRequirement.value || form.promptTemplate || form.ruleName
    const draft = await generateSkillDraft({
      requirement,
      projectType: form.projectType,
      ruleType: form.ruleType,
      severity: form.severity
    })
    const created = await createSkill({
      skillName: draft.skillName,
      skillCode: draft.skillCode,
      version: draft.version,
      projectType: draft.projectType || form.projectType || 'ALL',
      ruleMatchingEnabled: draft.ruleMatchingEnabled ?? 1,
      matchRules: draft.matchRules || '',
      reviewGuidelines: draft.reviewGuidelines || draft.promptTemplate || draft.functionDescription || requirement,
      status: 1
    })
    await loadBindings()
    Object.assign(form, {
      ruleName: draft.ruleName || draft.skillName,
      ruleCode: draft.ruleCode || draft.skillCode,
      ruleType: draft.ruleType || form.ruleType,
      severity: draft.severity || form.severity,
      projectType: draft.projectType || form.projectType,
      promptTemplate: draft.promptTemplate,
      skillId: created.id,
      scriptId: undefined
    })
    ElMessage.success('AI 已生成 Skill 并填充规则草稿')
  } finally {
    generating.value = false
  }
}

function bindingText(row: Rule) {
  return skillOptions.value.find((item) => item.id === row.skillId)?.skillName || `Skill #${row.skillId || '-'}`
}

function severityText(value: string) {
  return ({ BLOCKER: '阻断', CRITICAL: '严重', MAJOR: '主要', MINOR: '次要', INFO: '提示' } as Record<string, string>)[value] || value
}

function projectTypeText(value: string) {
  return ({ ALL: '全部', BACKEND: '后端', FRONTEND: '前端' } as Record<string, string>)[value] || value
}

onMounted(async () => {
  await loadBindings()
  await loadRules()
})
</script>
