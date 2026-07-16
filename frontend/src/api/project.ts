import { post, postForm, type PageResponse } from './http'
import type { SystemUser } from './user'

export interface Project {
  id: number
  projectName: string
  projectType: string
  repoUrl: string
  defaultBranch: string
  ownerName?: string
  ownerUserIds?: string[]
  owners?: SystemUser[]
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
  projectType: string
  repoUrl: string
  defaultBranch: string
  ownerName?: string
  ownerUserIds?: string[]
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

export interface ProjectCommit {
  hash: string
  shortHash: string
  subject: string
  author: string
  commitTime: string
  parentHashes: string[]
}

export interface ImportProjectResult {
  successCount: number
  failureCount: number
  failureReasons: string[]
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
  timeoutSeconds?: number
}) {
  return post<{ success: boolean; message: string; branch?: string }>('/api/project/test-repo-connection', data)
}

export function listProjectCommits(data: { projectId: number; branch?: string; limit?: number }) {
  return post<ProjectCommit[]>('/api/project/commits', data)
}

export function importProjects(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return postForm<ImportProjectResult>('/api/project/import-excel', formData)
}
