import { post } from './http'

export interface SystemUser {
  userName: string
  userId: string
  employeeId: string
}

export function listSystemUsers() {
  return post<SystemUser[]>('/api/system-user/list')
}
