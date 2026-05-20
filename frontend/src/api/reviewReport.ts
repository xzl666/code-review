import { post } from './http'

export interface ReviewReport {
  id: number
  taskId: number
  taskNo: string
  projectId: number
  reportTitle: string
  reportContent: string
  activeIssueCount: number
  ignoredIssueCount: number
  createTime?: string
}

export function getReviewReportByTask(taskId: number) {
  return post<ReviewReport>('/api/review-report/detail-by-task', { id: taskId })
}
