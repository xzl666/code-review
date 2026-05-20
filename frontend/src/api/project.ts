import { post, type PageResponse } from './http'

export interface Project {
  id: number
  projectName: string
  projectCode: string
  projectType: string
  repoUrl: string
  useDefaultToken: number
  defaultBranch: string
  ownerName?: string
  reviewDays: number
  scheduleCron?: string
  scheduleEnabled?: number
  notifyEnabled?: number
  notifyWebhookUrl?: string
  notifyExtraParams?: string
  status: number
  remark?: string
}

export interface ProjectForm {
  id?: number
  projectName: string
  projectCode: string
  projectType: string
  repoUrl: string
  projectToken?: string
  useDefaultToken: number
  defaultBranch: string
  ownerName?: string
  reviewDays: number
  scheduleCron?: string
  scheduleEnabled?: number
  notifyEnabled?: number
  notifyWebhookUrl?: string
  notifyExtraParams?: string
  status?: number
  remark?: string
}

export interface ProjectPageQuery {
  projectName?: string
  projectType?: string
  status?: number
  pageNo: number
  pageSize: number
}

export function pageProjects(query: ProjectPageQuery) {
  return post<PageResponse<Project>>('/api/project/page', query)
}

export function createProject(data: ProjectForm) {
  return post<{ id: number }>('/api/project/create', data)
}

export function updateProject(data: ProjectForm) {
  return post<void>('/api/project/update', data)
}

export function deleteProject(id: number) {
  return post<void>('/api/project/delete', { id })
}

export function enableProject(id: number) {
  return post<void>('/api/project/enable', { id })
}

export function disableProject(id: number) {
  return post<void>('/api/project/disable', { id })
}

export function testRepoConnection(data: {
  projectId?: number
  repoUrl?: string
  branch?: string
  projectToken?: string
  useDefaultToken?: number
  timeoutSeconds?: number
}) {
  return post<{ success: boolean; message: string; branch?: string }>('/api/project/test-repo-connection', data)
}
