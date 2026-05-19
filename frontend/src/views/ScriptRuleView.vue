<template>
  <div class="page-stack">
    <section class="toolbar">
      <div class="toolbar-title">
        <strong>脚本规则</strong>
        <span>维护 Shell、Python、Node 确定性检查脚本</span>
      </div>
      <el-input v-model="query.scriptName" clearable placeholder="脚本名称" class="toolbar-input" @keyup.enter="loadScripts">
        <template #prefix><Search :size="16" /></template>
      </el-input>
      <el-select v-model="query.scriptLanguage" clearable placeholder="语言" class="toolbar-select">
        <el-option label="Shell" value="SHELL" />
        <el-option label="Python" value="PYTHON" />
        <el-option label="Node" value="NODE" />
      </el-select>
      <el-button :icon="Search" @click="loadScripts">查询</el-button>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增脚本</el-button>
    </section>

    <section class="panel data-panel">
      <div class="panel-header">
        <div>
          <h2>脚本列表</h2>
          <p>脚本需输出统一 JSON 问题结构，适合补充确定性检查</p>
        </div>
        <el-tag effect="plain">共 {{ total }} 条</el-tag>
      </div>
      <el-table v-loading="loading" :data="scripts" stripe class="data-table">
        <el-table-column prop="scriptName" label="脚本名称" min-width="150" />
        <el-table-column prop="scriptCode" label="编码" min-width="150" show-overflow-tooltip />
        <el-table-column prop="scriptLanguage" label="语言" width="100" />
        <el-table-column prop="timeoutSeconds" label="超时" width="90">
          <template #default="{ row }">{{ row.timeoutSeconds }}s</template>
        </el-table-column>
        <el-table-column prop="generatedByAi" label="来源" width="100">
          <template #default="{ row }">{{ row.generatedByAi === 1 ? 'AI 生成' : '人工' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="310" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" :icon="PlayCircle" @click="openTest(row)">测试</el-button>
            <el-button v-if="row.status === 1" link type="warning" :icon="Pause" @click="setStatus(row, false)">停用</el-button>
            <el-button v-else link type="success" :icon="Play" @click="setStatus(row, true)">启用</el-button>
            <el-button link type="danger" :icon="Trash2" @click="removeScript(row)">删除</el-button>
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
          @size-change="loadScripts"
          @current-change="loadScripts"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑脚本' : '新增脚本'" width="860px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="脚本名称" prop="scriptName">
          <el-input v-model="form.scriptName" />
        </el-form-item>
        <el-form-item label="脚本编码" prop="scriptCode">
          <el-input v-model="form.scriptCode" />
        </el-form-item>
        <el-form-item label="语言" prop="scriptLanguage">
          <el-segmented v-model="form.scriptLanguage" :options="languageOptions" @change="resetContentForLanguage" />
        </el-form-item>
        <el-form-item label="超时秒数" prop="timeoutSeconds">
          <el-input-number v-model="form.timeoutSeconds" :min="1" :max="300" />
        </el-form-item>
        <el-form-item label="参数模板">
          <el-input v-model="form.parameterTemplate" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="脚本内容" prop="scriptContent">
          <el-input v-model="form.scriptContent" type="textarea" :rows="14" spellcheck="false" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button :icon="PlayCircle" @click="testCurrentForm">测试当前脚本</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="testVisible" title="脚本测试结果" width="760px">
      <el-form label-width="90px">
        <el-form-item label="输入 JSON">
          <el-input v-model="testInput" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="testing" :icon="PlayCircle" @click="runTest">运行测试</el-button>
        </el-form-item>
      </el-form>
      <div v-if="testResult" class="result-grid">
        <el-tag :type="testResult.success ? 'success' : 'danger'">{{ testResult.success ? '成功' : '失败' }}</el-tag>
        <el-tag v-if="testResult.timeout" type="danger">超时</el-tag>
        <span>退出码：{{ testResult.exitCode ?? '-' }}</span>
        <h3>stdout</h3>
        <pre>{{ testResult.stdout || '-' }}</pre>
        <h3>stderr</h3>
        <pre>{{ testResult.stderr || '-' }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import { Edit, Pause, Play, PlayCircle, Plus, Search, Trash2 } from 'lucide-vue-next'
import {
  createScriptRule,
  deleteScriptRule,
  disableScriptRule,
  enableScriptRule,
  pageScriptRules,
  testRunScript,
  updateScriptRule,
  type ScriptRule,
  type ScriptRuleForm,
  type ScriptTestRunResponse
} from '@/api/scriptRule'

const samples: Record<string, string> = {
  SHELL: 'echo "{\\"issues\\":[]}"',
  PYTHON: 'print("{\\"issues\\":[]}")',
  NODE: 'console.log(JSON.stringify({ issues: [] }))'
}

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const dialogVisible = ref(false)
const testVisible = ref(false)
const editingId = ref<number>()
const testingScriptId = ref<number>()
const formRef = ref<FormInstance>()
const scripts = ref<ScriptRule[]>([])
const total = ref(0)
const testInput = ref('{"diffContent": ""}')
const testResult = ref<ScriptTestRunResponse>()

const query = reactive({
  scriptName: '',
  scriptLanguage: '',
  pageNo: 1,
  pageSize: 10
})

const form = reactive<ScriptRuleForm>({
  scriptName: '',
  scriptCode: '',
  scriptLanguage: 'PYTHON',
  scriptContent: samples.PYTHON,
  parameterTemplate: '',
  timeoutSeconds: 30,
  generatedByAi: 0
})

const languageOptions = [
  { label: 'Shell', value: 'SHELL' },
  { label: 'Python', value: 'PYTHON' },
  { label: 'Node', value: 'NODE' }
]

const rules: FormRules = {
  scriptName: [{ required: true, message: '请输入脚本名称', trigger: 'blur' }],
  scriptCode: [{ required: true, message: '请输入脚本编码', trigger: 'blur' }],
  scriptLanguage: [{ required: true, message: '请选择脚本语言', trigger: 'change' }],
  scriptContent: [{ required: true, message: '请输入脚本内容', trigger: 'blur' }],
  timeoutSeconds: [{ required: true, message: '请输入超时秒数', trigger: 'change' }]
}

async function loadScripts() {
  loading.value = true
  try {
    const page = await pageScriptRules({
      scriptName: query.scriptName || undefined,
      scriptLanguage: query.scriptLanguage || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize
    })
    scripts.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingId.value = undefined
  Object.assign(form, {
    id: undefined,
    scriptName: '',
    scriptCode: '',
    scriptLanguage: 'PYTHON',
    scriptContent: samples.PYTHON,
    parameterTemplate: '',
    timeoutSeconds: 30,
    generatedByAi: 0,
    status: undefined
  })
}

function resetContentForLanguage() {
  form.scriptContent = samples[form.scriptLanguage] || ''
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: ScriptRule) {
  editingId.value = row.id
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editingId.value) {
      await updateScriptRule({ ...form, id: editingId.value, status: form.status ?? 1 })
      ElMessage.success('脚本已更新')
    } else {
      await createScriptRule(form)
      ElMessage.success('脚本已创建')
    }
    dialogVisible.value = false
    await loadScripts()
  } finally {
    saving.value = false
  }
}

function openTest(row: ScriptRule) {
  testingScriptId.value = row.id
  testResult.value = undefined
  testVisible.value = true
}

async function testCurrentForm() {
  await formRef.value?.validate()
  testingScriptId.value = undefined
  testVisible.value = true
  await runTest()
}

async function runTest() {
  testing.value = true
  try {
    testResult.value = await testRunScript({
      scriptId: testingScriptId.value,
      scriptLanguage: testingScriptId.value ? undefined : form.scriptLanguage,
      scriptContent: testingScriptId.value ? 'from-saved-script' : form.scriptContent,
      inputJson: testInput.value,
      timeoutSeconds: form.timeoutSeconds
    })
  } finally {
    testing.value = false
  }
}

async function setStatus(row: ScriptRule, enabled: boolean) {
  if (enabled) {
    await enableScriptRule(row.id)
  } else {
    await disableScriptRule(row.id)
  }
  ElMessage.success(enabled ? '脚本已启用' : '脚本已停用')
  await loadScripts()
}

async function removeScript(row: ScriptRule) {
  await ElMessageBox.confirm(`确认删除 ${row.scriptName}？`, '删除确认', { type: 'warning' })
  await deleteScriptRule(row.id)
  ElMessage.success('脚本已删除')
  await loadScripts()
}

onMounted(loadScripts)
</script>
