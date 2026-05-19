import { post, type PageResponse } from './http'

export interface NotifyConfig {
  id: number
  configName: string
  channelType: string
  webhookUrl: string
  secretMasked?: string
  enabled: number
}

export interface NotifyConfigForm {
  id?: number
  configName: string
  channelType: string
  webhookUrl: string
  secret?: string
  enabled?: number
}

export interface NotifyTemplate {
  id: number
  templateName: string
  templateCode: string
  channelType: string
  eventType: string
  templateContent: string
  enabled: number
}

export interface NotifyTemplateForm {
  id?: number
  templateName: string
  templateCode: string
  channelType: string
  eventType: string
  templateContent: string
  enabled?: number
}

export interface NotifyTestResult {
  success: boolean
  statusCode?: number
  message: string
  responseBody: string
}

export interface NotifyDeliveryLog {
  id: number
  configId?: number
  taskId?: number
  taskNo?: string
  eventType: string
  channelType: string
  webhookUrl: string
  requestContent?: string
  responseContent?: string
  status: string
  retryCount: number
  nextRetryTime?: string
  lastError?: string
  createTime?: string
}

export function pageNotifyConfigs(query: { configName?: string; channelType?: string; enabled?: number; pageNo: number; pageSize: number }) {
  return post<PageResponse<NotifyConfig>>('/api/notify-config/page', query)
}

export function createNotifyConfig(data: NotifyConfigForm) {
  return post<{ id: number }>('/api/notify-config/create', data)
}

export function updateNotifyConfig(data: NotifyConfigForm) {
  return post<void>('/api/notify-config/update', data)
}

export function deleteNotifyConfig(id: number) {
  return post<void>('/api/notify-config/delete', { id })
}

export function enableNotifyConfig(id: number) {
  return post<void>('/api/notify-config/enable', { id })
}

export function disableNotifyConfig(id: number) {
  return post<void>('/api/notify-config/disable', { id })
}

export function testSendNotifyConfig(data: { configId?: number; webhookUrl?: string; secret?: string; title?: string; content?: string }) {
  return post<NotifyTestResult>('/api/notify-config/test-send', data)
}

export function pageNotifyTemplates(query: { templateName?: string; templateCode?: string; channelType?: string; eventType?: string; enabled?: number; pageNo: number; pageSize: number }) {
  return post<PageResponse<NotifyTemplate>>('/api/notify-template/page', query)
}

export function createNotifyTemplate(data: NotifyTemplateForm) {
  return post<{ id: number }>('/api/notify-template/create', data)
}

export function updateNotifyTemplate(data: NotifyTemplateForm) {
  return post<void>('/api/notify-template/update', data)
}

export function deleteNotifyTemplate(id: number) {
  return post<void>('/api/notify-template/delete', { id })
}

export function enableNotifyTemplate(id: number) {
  return post<void>('/api/notify-template/enable', { id })
}

export function disableNotifyTemplate(id: number) {
  return post<void>('/api/notify-template/disable', { id })
}

export function previewNotifyTemplate(data: { templateId?: number; templateContent?: string; variables?: Record<string, unknown> }) {
  return post<{ content: string }>('/api/notify-template/preview', data)
}

export function pageNotifyDeliveryLogs(query: { taskNo?: string; eventType?: string; status?: string; pageNo: number; pageSize: number }) {
  return post<PageResponse<NotifyDeliveryLog>>('/api/notify-delivery-log/page', query)
}
