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
