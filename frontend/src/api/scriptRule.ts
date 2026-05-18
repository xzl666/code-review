import { post, type PageResponse } from './http'

export interface ScriptRule {
  id: number
  scriptName: string
  scriptCode: string
  scriptLanguage: string
  scriptContent: string
  parameterTemplate?: string
  timeoutSeconds: number
  generatedByAi: number
  status: number
}

export interface ScriptRuleForm {
  id?: number
  scriptName: string
  scriptCode: string
  scriptLanguage: string
  scriptContent: string
  parameterTemplate?: string
  timeoutSeconds: number
  generatedByAi?: number
  status?: number
}

export interface ScriptRuleQuery {
  scriptName?: string
  scriptLanguage?: string
  status?: number
  pageNo: number
  pageSize: number
}

export interface ScriptTestRunResponse {
  success: boolean
  exitCode?: number
  stdout: string
  stderr: string
  timeout: boolean
}

export function pageScriptRules(query: ScriptRuleQuery) {
  return post<PageResponse<ScriptRule>>('/api/script/page', query)
}

export function createScriptRule(data: ScriptRuleForm) {
  return post<{ id: number }>('/api/script/create', data)
}

export function updateScriptRule(data: ScriptRuleForm) {
  return post<void>('/api/script/update', data)
}

export function deleteScriptRule(id: number) {
  return post<void>('/api/script/delete', { id })
}

export function enableScriptRule(id: number) {
  return post<void>('/api/script/enable', { id })
}

export function disableScriptRule(id: number) {
  return post<void>('/api/script/disable', { id })
}

export function testRunScript(data: { scriptId?: number; scriptLanguage?: string; scriptContent: string; inputJson?: string; timeoutSeconds?: number }) {
  return post<ScriptTestRunResponse>('/api/script/test-run', data)
}
