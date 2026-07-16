import { post, type PageResponse } from './http'

export interface Rule {
  id: number
  ruleName: string
  promptTemplate: string
  pathPattern: string
  mergeSystemRule: number
  status: number
  sortOrder: number
}

export interface RuleForm {
  id?: number
  ruleName: string
  promptTemplate: string
  pathPattern: string
  mergeSystemRule: number
  status?: number
  sortOrder: number
}

export interface RuleQuery {
  ruleName?: string
  status?: number
  pageNo: number
  pageSize: number
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
