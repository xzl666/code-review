import { post, type PageResponse } from './http'

export interface ReviewIssue {
  id: number
  taskId: number
  taskNo?: string
  projectId: number
  ruleId?: number
  skillId?: number
  issueSource: string
  severity: string
  issueType: string
  filePath: string
  startLine?: number
  endLine?: number
  summary: string
  detail?: string
  suggestion?: string
  codeSnippet?: string
  rawResponse?: string
  status: string
  createTime?: string
}

export interface IssueQuery {
  pageNo: number
  pageSize: number
  taskId?: number
  taskNo?: string
  projectId?: number
  severity?: string
  issueSource?: string
  status?: string
}

export function pageIssues(query: IssueQuery) {
  return post<PageResponse<ReviewIssue>>('/api/review-issue/page', query)
}

export function getIssue(id: number) {
  return post<ReviewIssue>('/api/review-issue/detail', { id })
}

export function ignoreIssue(id: number) {
  return post<void>('/api/review-issue/ignore', { id })
}

export function markIssueFixed(id: number) {
  return post<void>('/api/review-issue/mark-fixed', { id })
}

export function exportIssues(query: IssueQuery) {
  return post<string>('/api/review-issue/export', query)
}
