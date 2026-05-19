<template>
  <div class="page-stack">
    <section class="toolbar">
      <div class="toolbar-title">
        <strong>AI Skill</strong>
        <span>维护 Function Calling 函数定义与结构化输出 Schema</span>
      </div>
      <el-input v-model="query.skillName" clearable placeholder="Skill 名称" class="toolbar-input" @keyup.enter="loadSkills">
        <template #prefix><Search :size="16" /></template>
      </el-input>
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
          <p>AI 规则通过 Skill 约束模型输出结构</p>
        </div>
        <el-tag effect="plain">共 {{ total }} 条</el-tag>
      </div>
      <el-table v-loading="loading" :data="skills" stripe class="data-table">
        <el-table-column prop="skillName" label="名称" min-width="150" />
        <el-table-column prop="skillCode" label="编码" min-width="150" show-overflow-tooltip />
        <el-table-column prop="functionName" label="函数名" min-width="170" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="100" />
        <el-table-column prop="parametersSchema" label="Schema" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ schemaSummary(row.parametersSchema) }}</template>
        </el-table-column>
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑 Skill' : '新增 Skill'" width="820px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="116px">
        <el-form-item label="Skill 名称" prop="skillName">
          <el-input v-model="form.skillName" />
        </el-form-item>
        <el-form-item label="Skill 编码" prop="skillCode">
          <el-input v-model="form.skillCode" />
        </el-form-item>
        <el-form-item label="函数名" prop="functionName">
          <el-input v-model="form.functionName" />
        </el-form-item>
        <el-form-item label="版本" prop="version">
          <el-input v-model="form.version" />
        </el-form-item>
        <el-form-item label="函数描述">
          <el-input v-model="form.functionDescription" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="参数 Schema" prop="parametersSchema">
          <el-input v-model="form.parametersSchema" type="textarea" :rows="12" spellcheck="false" />
        </el-form-item>
        <el-form-item>
          <el-button :icon="CheckCircle2" :loading="validating" @click="validateSchema">校验 Schema</el-button>
          <el-tag v-if="schemaResult" :type="schemaResult.valid ? 'success' : 'danger'">{{ schemaResult.message }}</el-tag>
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
import { CheckCircle2, Edit, Pause, Play, Plus, Search, Trash2 } from 'lucide-vue-next'
import {
  createSkill,
  deleteSkill,
  disableSkill,
  enableSkill,
  pageSkills,
  updateSkill,
  validateSkillSchema,
  type Skill,
  type SkillForm
} from '@/api/skill'

const defaultSchema = JSON.stringify(
  {
    type: 'object',
    properties: {
      issues: {
        type: 'array',
        items: {
          type: 'object',
          properties: {
            severity: { type: 'string' },
            filePath: { type: 'string' },
            startLine: { type: 'integer' },
            summary: { type: 'string' },
            suggestion: { type: 'string' }
          },
          required: ['severity', 'filePath', 'summary']
        }
      }
    },
    required: ['issues']
  },
  null,
  2
)

const loading = ref(false)
const saving = ref(false)
const validating = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number>()
const formRef = ref<FormInstance>()
const skills = ref<Skill[]>([])
const total = ref(0)
const schemaResult = ref<{ valid: boolean; message: string }>()

const query = reactive({
  skillName: '',
  status: undefined as number | undefined,
  pageNo: 1,
  pageSize: 10
})

const form = reactive<SkillForm>({
  skillName: '',
  skillCode: '',
  functionName: 'submit_review_issues',
  functionDescription: '',
  parametersSchema: defaultSchema,
  version: '1.0.0'
})

const rules: FormRules = {
  skillName: [{ required: true, message: '请输入 Skill 名称', trigger: 'blur' }],
  skillCode: [{ required: true, message: '请输入 Skill 编码', trigger: 'blur' }],
  functionName: [{ required: true, message: '请输入函数名', trigger: 'blur' }],
  parametersSchema: [{ required: true, message: '请输入参数 Schema', trigger: 'blur' }],
  version: [{ required: true, message: '请输入版本', trigger: 'blur' }]
}

async function loadSkills() {
  loading.value = true
  try {
    const page = await pageSkills({
      skillName: query.skillName || undefined,
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
  schemaResult.value = undefined
  Object.assign(form, {
    id: undefined,
    skillName: '',
    skillCode: '',
    functionName: 'submit_review_issues',
    functionDescription: '',
    parametersSchema: defaultSchema,
    version: '1.0.0',
    status: undefined
  })
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: Skill) {
  editingId.value = row.id
  schemaResult.value = undefined
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function validateSchema() {
  validating.value = true
  try {
    schemaResult.value = await validateSkillSchema(form.parametersSchema)
  } finally {
    validating.value = false
  }
}

async function submitForm() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editingId.value) {
      await updateSkill({ ...form, id: editingId.value, status: form.status ?? 1 })
      ElMessage.success('Skill 已更新')
    } else {
      await createSkill(form)
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

function schemaSummary(value: string) {
  try {
    const parsed = JSON.parse(value)
    const properties = parsed?.properties ? Object.keys(parsed.properties).join(', ') : 'object'
    return `${parsed.type || 'schema'}: ${properties}`
  } catch {
    return value
  }
}

onMounted(loadSkills)
</script>
