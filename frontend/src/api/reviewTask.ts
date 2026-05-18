import { post, type PageResponse } from './http'

export interface ReviewTask {
  id: number
  taskNo: string
  projectId: number
  projectName: string
  triggerType: string
  reviewBranch: string
  reviewDays: number
  commitCount: number
  diffFileCount: number
  issueCount: number
  blockerCount: number
  criticalCount: number
  majorCount: number
  minorCount: number
  infoCount: number
  aiCallCount: number
  status: string
  startTime?: string
  endTime?: string
  errorMessage?: string
}

export interface ReviewTaskQuery {
  pageNo: number
  pageSize: number
  projectName?: string
  status?: string
  triggerType?: string
}

export function pageReviewTasks(query: ReviewTaskQuery) {
  return post<PageResponse<ReviewTask>>('/api/review-task/page', query)
}

export function cancelReviewTask(id: number) {
  return post<void>('/api/review-task/cancel', { id })
}

export function retryReviewTask(id: number) {
  return post<void>('/api/review-task/retry', { id })
}
