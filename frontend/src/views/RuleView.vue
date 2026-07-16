<template>
  <div class="page-stack">
    <section class="toolbar">
      <div class="toolbar-title">
        <strong>OpenCodeReview 规则</strong>
        <span>按文件路径为 OpenCodeReview 补充项目检视规则</span>
      </div>
      <el-input v-model="query.ruleName" clearable placeholder="规则名称" class="toolbar-input" @keyup.enter="loadRules">
        <template #prefix><Search :size="16" /></template>
      </el-input>
      <el-button :icon="Search" @click="loadRules">查询</el-button>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增规则</el-button>
    </section>

    <section class="panel data-panel">
      <div class="panel-header">
        <div>
          <h2>规则列表</h2>
          <p>规则按排序首次匹配；可选择与 OpenCodeReview 内置规则合并</p>
        </div>
        <el-tag effect="plain">共 {{ total }} 条</el-tag>
      </div>
      <el-table v-loading="loading" :data="rulesData" stripe class="data-table">
        <el-table-column prop="ruleName" label="规则名称" min-width="180" />
        <el-table-column prop="ruleCode" label="规则编码" min-width="180" show-overflow-tooltip />
        <el-table-column prop="pathPattern" label="路径模式" min-width="230" show-overflow-tooltip />
        <el-table-column label="内置规则" width="110">
          <template #default="{ row }">{{ row.mergeSystemRule === 1 ? '合并' : '替换' }}</template>
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑规则' : '新增规则'" width="760px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="form.ruleName" />
        </el-form-item>
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input v-model="form.ruleCode" />
        </el-form-item>
        <el-form-item label="路径模式" prop="pathPattern">
          <el-input v-model="form.pathPattern" placeholder="例如：src/main/**/*.java" />
        </el-form-item>
        <el-form-item label="合并内置规则">
          <el-switch v-model="mergeSystemRule" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="规则内容" prop="promptTemplate">
          <el-input v-model="form.promptTemplate" type="textarea" :rows="10" placeholder="描述该路径下代码需要重点检查的问题" />
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

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number>()
const formRef = ref<FormInstance>()
const rulesData = ref<Rule[]>([])
const total = ref(0)

const query = reactive({ ruleName: '', pageNo: 1, pageSize: 10 })
const form = reactive<RuleForm>({
  ruleName: '',
  ruleCode: '',
  pathPattern: '**/*',
  mergeSystemRule: 1,
  promptTemplate: '',
  sortOrder: 0
})
const mergeSystemRule = computed({
  get: () => form.mergeSystemRule === 1,
  set: value => { form.mergeSystemRule = value ? 1 : 0 }
})
const formRules: FormRules = {
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  ruleCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }],
  pathPattern: [{ required: true, message: '请输入路径模式', trigger: 'blur' }],
  promptTemplate: [{ required: true, message: '请输入规则内容', trigger: 'blur' }]
}

async function loadRules() {
  loading.value = true
  try {
    const page = await pageRules({ ruleName: query.ruleName || undefined, pageNo: query.pageNo, pageSize: query.pageSize })
    rulesData.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = undefined
  Object.assign(form, { ruleName: '', ruleCode: '', pathPattern: '**/*', mergeSystemRule: 1, promptTemplate: '', sortOrder: 0 })
  dialogVisible.value = true
}

function openEdit(row: Rule) {
  editingId.value = row.id
  Object.assign(form, row)
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = { ...form, id: editingId.value, status: editingId.value ? form.status : 1 }
    if (editingId.value) await updateRule(payload)
    else await createRule(payload)
    ElMessage.success(editingId.value ? '规则已更新' : '规则已创建')
    dialogVisible.value = false
    await loadRules()
  } finally {
    saving.value = false
  }
}

async function setStatus(row: Rule, enabled: boolean) {
  if (enabled) await enableRule(row.id)
  else await disableRule(row.id)
  ElMessage.success(enabled ? '规则已启用' : '规则已停用')
  await loadRules()
}

async function removeRule(row: Rule) {
  await ElMessageBox.confirm(`确认删除「${row.ruleName}」？`, '删除确认', { type: 'warning' })
  await deleteRule(row.id)
  ElMessage.success('规则已删除')
  await loadRules()
}

onMounted(loadRules)
</script>
