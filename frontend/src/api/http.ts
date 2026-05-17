import axios from 'axios'
import { ElMessage } from 'element-plus'

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
  timeout: 15000,
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

export async function post<T>(url: string, data?: unknown): Promise<T> {
  const response = await http.post<ApiResponse<T>>(url, data || {})
  return response.data.data
}
