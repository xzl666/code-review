import { post, type PageResponse } from './http'

export interface ZhaohuConfig {
  enabled: number
  apiHost: string
  clientId: string
  clientSecretConfigured: boolean
  clientSecretMasked: string
  robotId: string
  appBaseUrl: string
  tokenExpireSeconds: number
  tokenBufferSeconds: number
  timeoutSeconds: number
}

export interface ZhaohuConfigForm {
  enabled: number
  apiHost: string
  clientId: string
  clientSecret?: string
  robotId: string
  appBaseUrl: string
  tokenExpireSeconds: number
  tokenBufferSeconds: number
  timeoutSeconds: number
}

export interface ZhaohuTestResult {
  successCount: number
  failureCount: number
  failureReasons: string[]
}

export interface NotifyDeliveryLog {
  id: number
  taskId?: number
  taskNo?: string
  eventType: string
  channelType: 'ZHAOHU'
  webhookUrl: string
  requestContent?: string
  responseContent?: string
  status: string
  retryCount: number
  nextRetryTime?: string
  lastError?: string
  createTime?: string
}

export function getZhaohuConfig() {
  return post<ZhaohuConfig>('/api/zhaohu/config/detail')
}

export function updateZhaohuConfig(data: ZhaohuConfigForm) {
  return post<void>('/api/zhaohu/config/update', data)
}

export function testSendZhaohu(data: { userIds: string[]; title: string; content: string; summary?: string }) {
  return post<ZhaohuTestResult>('/api/zhaohu/test-send', data)
}

export function pageNotifyDeliveryLogs(query: { taskNo?: string; eventType?: string; status?: string; pageNo: number; pageSize: number }) {
  return post<PageResponse<NotifyDeliveryLog>>('/api/notify-delivery-log/page', query)
}
