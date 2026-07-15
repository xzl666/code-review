<template>
  <div class="page-stack">
    <section class="toolbar">
      <div class="toolbar-title">
        <strong>脚本规则</strong>
        <span>维护独立运行的 Python diff 检查规则</span>
      </div>
      <el-input v-model="query.scriptName" clearable placeholder="脚本名称" class="toolbar-input" @keyup.enter="loadScripts">
        <template #prefix><Search :size="16" /></template>
      </el-input>
      <el-input v-model="query.scriptCode" clearable placeholder="脚本编码" class="toolbar-input" @keyup.enter="loadScripts" />
      <el-select v-model="query.projectType" clearable placeholder="项目类型" class="toolbar-select">
        <el-option label="全部" value="ALL" />
        <el-option label="后端" value="BACKEND" />
        <el-option label="前端" value="FRONTEND" />
      </el-select>
      <el-button :icon="Search" @click="loadScripts">查询</el-button>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增规则</el-button>
    </section>

    <section class="panel data-panel">
      <div class="panel-header">
        <div>
          <h2>Python 规则列表</h2>
          <p>启用规则会在检视任务中自动接收 git diff 并输出标准 JSON 问题</p>
        </div>
        <el-tag effect="plain">共 {{ total }} 条</el-tag>
      </div>
      <el-table v-loading="loading" :data="scripts" stripe class="data-table">
        <el-table-column prop="scriptName" label="规则名称" min-width="160" />
        <el-table-column prop="scriptCode" label="编码" min-width="170">
          <template #default="{ row }">
            <span
              class="table-ellipsis"
              @mouseenter="showCodeTooltip($event, row.scriptCode)"
              @mousemove="moveCodeTooltip"
              @mouseleave="hideCodeTooltip"
            >
              {{ row.scriptCode }}
            </span>
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
        <el-table-column prop="timeoutSeconds" label="超时" width="90">
          <template #default="{ row }">{{ row.timeoutSeconds }}s</template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" />
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑 Python 规则' : '新增 Python 规则'" width="900px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="规则名称" prop="scriptName">
          <el-input v-model="form.scriptName" />
        </el-form-item>
        <el-form-item label="规则编码" prop="scriptCode">
          <el-input v-model="form.scriptCode" />
        </el-form-item>
        <el-form-item label="项目类型" prop="projectType">
          <el-segmented v-model="form.projectType" :options="projectTypeOptions" />
        </el-form-item>
        <el-form-item label="问题类型" prop="ruleType">
          <el-input v-model="form.ruleType" placeholder="NAMING / SECURITY / CUSTOM" />
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
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="超时秒数" prop="timeoutSeconds">
          <el-input-number v-model="form.timeoutSeconds" :min="1" :max="60" />
        </el-form-item>
        <el-form-item label="规则说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="Python 脚本" prop="scriptContent">
          <el-input v-model="form.scriptContent" type="textarea" :rows="18" spellcheck="false" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button :icon="PlayCircle" @click="testCurrentForm">测试当前规则</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="testVisible" title="脚本测试结果" width="820px">
      <el-form label-width="90px">
        <el-form-item label="输入 JSON">
          <el-input v-model="testInput" type="textarea" :rows="10" spellcheck="false" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="testing" :icon="PlayCircle" @click="runTest">运行测试</el-button>
        </el-form-item>
      </el-form>
      <div v-if="testResult" class="result-grid">
        <el-tag :type="testResult.success ? 'success' : 'danger'">{{ testResult.success ? '成功' : '失败' }}</el-tag>
        <el-tag v-if="testResult.timeout" type="danger">超时</el-tag>
        <el-tag v-if="testResult.securityBlocked" type="warning">沙箱拦截</el-tag>
        <span>退出码：{{ testResult.exitCode ?? '-' }}</span>
        <h3>stdout</h3>
        <pre>{{ testResult.stdout || '-' }}</pre>
        <h3>stderr</h3>
        <pre>{{ testResult.stderr || '-' }}</pre>
      </div>
    </el-dialog>

    <teleport to="body">
      <div
        v-if="codeTooltip.visible"
        class="cursor-tooltip"
        :style="{ left: `${codeTooltip.x}px`, top: `${codeTooltip.y}px` }"
      >
        {{ codeTooltip.content }}
      </div>
    </teleport>
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
import { issueTypeText } from '@/utils/dictionaries'

const samplePython = `import json
import sys

data = json.load(sys.stdin)
issues = []

print(json.dumps({"issues": issues}, ensure_ascii=False))
`

const sampleInput = JSON.stringify(
  {
    project: { id: 1, name: '示例后端项目', code: 'demo-backend', type: 'BACKEND' },
    branch: 'master',
    reviewDays: 7,
    commitCount: 1,
    diffFileCount: 1,
    filePaths: ['src/main/java/demo/userService.java'],
    diffContent:
      'diff --git a/src/main/java/demo/userService.java b/src/main/java/demo/userService.java\\n+++ b/src/main/java/demo/userService.java\\n@@ -0,0 +1,4 @@\\n+package Demo.Service;\\n+public class userService {\\n+  static final String retryCount = \"3\";\\n+}\\n',
    files: [
      {
        filePath: 'src/main/java/demo/userService.java',
        diffContent:
          'diff --git a/src/main/java/demo/userService.java b/src/main/java/demo/userService.java\\n+++ b/src/main/java/demo/userService.java\\n@@ -0,0 +1,4 @@\\n+package Demo.Service;\\n+public class userService {\\n+  static final String retryCount = \"3\";\\n+}\\n'
      }
    ]
  },
  null,
  2
)

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
const testInput = ref(sampleInput)
const testResult = ref<ScriptTestRunResponse>()
const codeTooltip = reactive({
  visible: false,
  content: '',
  x: 0,
  y: 0
})

const query = reactive({
  scriptName: '',
  scriptCode: '',
  projectType: '',
  pageNo: 1,
  pageSize: 10
})

const form = reactive<ScriptRuleForm>({
  scriptName: '',
  scriptCode: '',
  projectType: 'BACKEND',
  ruleType: 'CUSTOM',
  severity: 'MAJOR',
  description: '',
  scriptContent: samplePython,
  timeoutSeconds: 20,
  sortOrder: 0
})

const projectTypeOptions = [
  { label: '全部', value: 'ALL' },
  { label: '后端', value: 'BACKEND' },
  { label: '前端', value: 'FRONTEND' }
]

const rules: FormRules = {
  scriptName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  scriptCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }],
  projectType: [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  ruleType: [{ required: true, message: '请输入问题类型', trigger: 'blur' }],
  severity: [{ required: true, message: '请选择严重度', trigger: 'change' }],
  scriptContent: [{ required: true, message: '请输入 Python 脚本', trigger: 'blur' }],
  timeoutSeconds: [{ required: true, message: '请输入超时秒数', trigger: 'change' }]
}

async function loadScripts() {
  loading.value = true
  try {
    const page = await pageScriptRules({
      scriptName: query.scriptName || undefined,
      scriptCode: query.scriptCode || undefined,
      projectType: query.projectType || undefined,
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
    projectType: 'BACKEND',
    ruleType: 'CUSTOM',
    severity: 'MAJOR',
    description: '',
    scriptContent: samplePython,
    timeoutSeconds: 20,
    sortOrder: 0,
    status: undefined
  })
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
      ElMessage.success('脚本规则已更新')
    } else {
      await createScriptRule(form)
      ElMessage.success('脚本规则已创建')
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
  testInput.value = sampleInput
  testVisible.value = true
}

async function testCurrentForm() {
  await formRef.value?.validate()
  testingScriptId.value = undefined
  testResult.value = undefined
  testInput.value = sampleInput
  testVisible.value = true
}

async function runTest() {
  testing.value = true
  try {
    testResult.value = await testRunScript({
      scriptId: testingScriptId.value,
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
  ElMessage.success(enabled ? '脚本规则已启用' : '脚本规则已停用')
  await loadScripts()
}

async function removeScript(row: ScriptRule) {
  await ElMessageBox.confirm(`确认删除 ${row.scriptName}？`, '删除确认', { type: 'warning' })
  await deleteScriptRule(row.id)
  ElMessage.success('脚本规则已删除')
  await loadScripts()
}

function severityText(value: string) {
  return ({ BLOCKER: '阻断', CRITICAL: '严重', MAJOR: '主要', MINOR: '次要', INFO: '提示' } as Record<string, string>)[value] || value
}

function projectTypeText(value: string) {
  return ({ ALL: '全部', BACKEND: '后端', FRONTEND: '前端' } as Record<string, string>)[value] || value
}

function showCodeTooltip(event: MouseEvent, content: string) {
  codeTooltip.content = content
  codeTooltip.visible = true
  updateCodeTooltipPosition(event)
}

function moveCodeTooltip(event: MouseEvent) {
  if (codeTooltip.visible) {
    updateCodeTooltipPosition(event)
  }
}

function hideCodeTooltip() {
  codeTooltip.visible = false
}

function updateCodeTooltipPosition(event: MouseEvent) {
  const offset = 12
  const maxTooltipWidth = Math.min(520, window.innerWidth - 32)
  codeTooltip.x = Math.max(16, Math.min(event.clientX + offset, window.innerWidth - maxTooltipWidth - 16))
  codeTooltip.y = Math.max(16, Math.min(event.clientY + offset, window.innerHeight - 48))
}

onMounted(loadScripts)
</script>
