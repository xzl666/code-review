import { post, type PageResponse } from './http'

export interface ReviewTask {
  id: number
  taskNo: string
  projectId: number
  projectName: string
  triggerType: string
  reviewBranch: string
  reviewMode: 'RANGE' | 'YESTERDAY' | 'COMMIT' | 'WORKSPACE' | 'SCAN'
  baseRef?: string
  targetRef?: string
  commitRef?: string
  scanPath?: string
  scanExclude?: string
  scanNoPlan?: number
  maxTokensBudget?: number
  reviewBackground?: string
  reviewStartTime?: string
  reviewEndTime?: string
  notifyEnabled?: number
  commitCount: number
  diffFileCount: number
  issueCount: number
  criticalCount: number
  highCount: number
  mediumCount: number
  lowCount: number
  aiCallCount: number
  aiSuccessCount: number
  aiFailureCount: number
  inputTokenCount: number
  outputTokenCount: number
  totalTokenCount: number
  cacheReadTokenCount: number
  cacheWriteTokenCount: number
  skippedCommitCount?: number
  skippedFileCount?: number
  status: string
  startTime?: string
  endTime?: string
  warningMessage?: string
  errorMessage?: string
}

export interface ReviewTaskQuery {
  pageNo: number
  pageSize: number
  projectId?: number
  projectName?: string
  status?: string
  triggerType?: string
}

export interface ManualReviewStartRequest {
  projectId: number
  branch?: string
  reviewMode: 'RANGE' | 'YESTERDAY' | 'COMMIT' | 'WORKSPACE' | 'SCAN'
  baseRef?: string
  targetRef?: string
  commitRef?: string
  scanPath?: string
  scanExclude?: string
  scanNoPlan?: boolean
  maxTokensBudget?: number
  background?: string
  sendNotification?: boolean
}

export function pageReviewTasks(query: ReviewTaskQuery) {
  return post<PageResponse<ReviewTask>>('/api/review-task/page', query)
}

export function startReviewTask(data: ManualReviewStartRequest) {
  return post<ReviewTask>('/api/review-task/manual-start', data)
}

export function getReviewTask(id: number) {
  return post<ReviewTask>('/api/review-task/detail', { id })
}

export function cancelReviewTask(id: number) {
  return post<void>('/api/review-task/cancel', { id })
}

export function retryReviewTask(id: number) {
  return post<void>('/api/review-task/retry', { id })
}
