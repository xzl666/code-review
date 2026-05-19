import { post, type PageResponse } from './http'

export interface Rule {
  id: number
  ruleName: string
  ruleCode: string
  ruleKind: string
  ruleType: string
  severity: string
  projectType: string
  promptTemplate?: string
  skillId?: number
  scriptId?: number
  status: number
  sortOrder: number
}

export interface RuleForm {
  id?: number
  ruleName: string
  ruleCode: string
  ruleKind: string
  ruleType: string
  severity: string
  projectType: string
  promptTemplate?: string
  skillId?: number
  scriptId?: number
  status?: number
  sortOrder: number
}

export interface RuleQuery {
  ruleName?: string
  ruleKind?: string
  ruleType?: string
  projectType?: string
  status?: number
  pageNo: number
  pageSize: number
}

export interface AiGenerateScriptRequest {
  requirement?: string
  projectType?: string
  ruleType?: string
  severity?: string
  scriptLanguage?: string
}

export interface AiGeneratedScriptDraft {
  scriptName: string
  scriptCode: string
  scriptLanguage: string
  scriptContent: string
  parameterTemplate?: string
  timeoutSeconds: number
  ruleName: string
  ruleCode: string
  ruleType: string
  severity: string
  projectType: string
  promptTemplate?: string
}

export function pageRules(query: RuleQuery) {
  return post<PageResponse<Rule>>('/api/rule/page', query)
}

export function createRule(data: RuleForm) {
  return post<{ id: number }>('/api/rule/create', data)
}

export function updateRule(data: RuleForm) {
  return post<void>('/api/rule/update', data)
}

export function deleteRule(id: number) {
  return post<void>('/api/rule/delete', { id })
}

export function enableRule(id: number) {
  return post<void>('/api/rule/enable', { id })
}

export function disableRule(id: number) {
  return post<void>('/api/rule/disable', { id })
}

export function generateScriptDraft(data: AiGenerateScriptRequest) {
  return post<AiGeneratedScriptDraft>('/api/rule/generate-script', data)
}
