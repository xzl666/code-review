import { post } from './http'

export interface TokenDetail {
  configured: boolean
  maskedToken?: string
}

export interface DeepSeekConfig {
  configured: boolean
  maskedApiKey?: string
  url: string
  model: string
}

export interface ModelConfig {
  id: number
  configName: string
  providerType: string
  baseUrl: string
  modelName: string
  configured: boolean
  maskedApiKey?: string
  enabled: number
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface ModelConfigForm {
  id?: number
  configName: string
  providerType: string
  baseUrl: string
  modelName: string
  apiKey?: string
  enabled?: number
  remark?: string
}

export interface ConfigValidationResult {
  success: boolean
  statusCode?: number
  message: string
  responseBody?: string
}

export function getDefaultGiteeToken() {
  return post<TokenDetail>('/api/system-config/default-gitee-token/detail')
}

export function updateDefaultGiteeToken(token: string) {
  return post<void>('/api/system-config/default-gitee-token/update', { token })
}

export function validateDefaultGiteeToken() {
  return post<ConfigValidationResult>('/api/system-config/default-gitee-token/validate')
}

export function getDeepSeekConfig() {
  return post<DeepSeekConfig>('/api/system-config/deepseek/detail')
}

export function updateDeepSeekConfig(data: { apiKey: string; url?: string; model?: string }) {
  return post<void>('/api/system-config/deepseek/update', data)
}

export function validateDeepSeekConfig() {
  return post<ConfigValidationResult>('/api/system-config/deepseek/validate')
}

export function listModelConfigs() {
  return post<ModelConfig[]>('/api/system-config/model-config/list')
}

export function saveModelConfig(data: ModelConfigForm) {
  return post<void>('/api/system-config/model-config/save', data)
}

export function enableModelConfig(id: number) {
  return post<void>('/api/system-config/model-config/enable', { id })
}

export function deleteModelConfig(id: number) {
  return post<void>('/api/system-config/model-config/delete', { id })
}

export function validateModelConfig(data?: Partial<ModelConfigForm>) {
  return post<ConfigValidationResult>('/api/system-config/model-config/validate', data || {})
}
