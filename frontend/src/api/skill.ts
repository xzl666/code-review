import { post, type PageResponse } from './http'

export interface Skill {
  id: number
  skillName: string
  skillCode: string
  functionName: string
  functionDescription?: string
  parametersSchema: string
  version: string
  status: number
}

export interface SkillForm {
  id?: number
  skillName: string
  skillCode: string
  functionName: string
  functionDescription?: string
  parametersSchema: string
  version: string
  status?: number
}

export interface SkillQuery {
  skillName?: string
  skillCode?: string
  status?: number
  pageNo: number
  pageSize: number
}

export interface AiGenerateSkillRequest {
  requirement?: string
  projectType?: string
  ruleType?: string
  severity?: string
}

export interface AiGeneratedSkillDraft {
  skillName: string
  skillCode: string
  functionName: string
  functionDescription?: string
  parametersSchema: string
  version: string
  ruleName?: string
  ruleCode?: string
  ruleType?: string
  severity?: string
  projectType?: string
  promptTemplate?: string
}

export function pageSkills(query: SkillQuery) {
  return post<PageResponse<Skill>>('/api/skill/page', query)
}

export function createSkill(data: SkillForm) {
  return post<{ id: number }>('/api/skill/create', data)
}

export function updateSkill(data: SkillForm) {
  return post<void>('/api/skill/update', data)
}

export function deleteSkill(id: number) {
  return post<void>('/api/skill/delete', { id })
}

export function enableSkill(id: number) {
  return post<void>('/api/skill/enable', { id })
}

export function disableSkill(id: number) {
  return post<void>('/api/skill/disable', { id })
}

export function validateSkillSchema(parametersSchema: string) {
  return post<{ valid: boolean; message: string }>('/api/skill/validate-schema', { parametersSchema })
}

export function generateSkillDraft(data: AiGenerateSkillRequest) {
  return post<AiGeneratedSkillDraft>('/api/skill/generate', data)
}
