import axios from 'axios'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { currentUserId } from '@/utils/currentUser'

export interface ApiResponse<T> {
  code: string
  message: string
  data: T
}

export interface PageResponse<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}

const http = axios.create({
  baseURL: '',
  timeout: 130000,
  headers: {
    'Content-Type': 'application/json'
  }
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body && body.code !== '0') {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return response
  },
  (error) => {
    ElMessage.error(error?.message || '网络异常')
    return Promise.reject(error)
  }
)

http.interceptors.request.use((config) => {
  config.headers['X-User-Id'] = currentUserId()
  return config
})

export async function post<T>(url: string, data?: unknown): Promise<T> {
  const response = await http.post<ApiResponse<T>>(url, data || {})
  return response.data.data
}

export async function postForm<T>(url: string, formData: FormData): Promise<T> {
  const response = await http.post<ApiResponse<T>>(url, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return response.data.data
}
