import { post } from './http'

export interface DashboardOverview {
  projectCount: number
  enabledProjectCount: number
  todayTaskCount: number
  todayIssueCount: number
  openIssueCount: number
  todayAiCallCount: number
  totalTokenCount: number
  todayTokenCount: number
  criticalCount: number
  highCount: number
}

export interface NameValue {
  name: string
  value: number
}

export function getDashboardOverview() {
  return post<DashboardOverview>('/api/dashboard/overview')
}

export function getIssueTrend(days = 7) {
  return post<NameValue[]>('/api/dashboard/issue-trend', { days })
}

export function getSeverityDistribution() {
  return post<NameValue[]>('/api/dashboard/severity-distribution')
}
