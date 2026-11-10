import http, { type ApiResponse } from './http'

export interface AdminUser {
  id: number
  email: string
  nickname: string
  status: string
  roles: string[]
  lastLoginAt?: string
  createdAt: string
}

export const ROLE_LABELS: Record<string, string> = {
  CUSTOMER: '顾客', MERCHANT: '商家', SUPPORT: '客服', OPERATOR: '运营', ADMIN: '管理员',
}

export async function fetchAdminUsers(params: { keyword?: string; status?: string }): Promise<AdminUser[]> {
  const { data } = await http.get<ApiResponse<AdminUser[]>>('/admin/users', { params })
  return data.data
}

export async function disableUser(id: number, reason: string): Promise<AdminUser> {
  const { data } = await http.post<ApiResponse<AdminUser>>(`/admin/users/${id}/disable`, { reason })
  return data.data
}

export async function enableUser(id: number): Promise<AdminUser> {
  const { data } = await http.post<ApiResponse<AdminUser>>(`/admin/users/${id}/enable`)
  return data.data
}

export async function assignRoles(id: number, roles: string[]): Promise<AdminUser> {
  const { data } = await http.post<ApiResponse<AdminUser>>(`/admin/users/${id}/roles`, { roles })
  return data.data
}
