import http, { type ApiResponse } from './http'

export interface AdminDashboard {
  gmv: number
  commission: number
  orderCount: number
  paidOrderCount: number
  merchantActive: number
  merchantPending: number
  customerCount: number
  productOnSale: number
  trend: { date: string; orders: number; amount: number }[]
}

export interface AuditLog {
  id: number
  actorType: string
  actorId?: number
  action: string
  targetType?: string
  targetId?: string
  beforeJson?: string
  afterJson?: string
  ip?: string
  createdAt: string
}

export async function fetchAdminDashboard(): Promise<AdminDashboard> {
  const { data } = await http.get<ApiResponse<AdminDashboard>>('/admin/dashboard')
  return data.data
}

export async function fetchAuditLogs(params: { action?: string; actorType?: string }): Promise<AuditLog[]> {
  const { data } = await http.get<ApiResponse<AuditLog[]>>('/admin/audit-logs', { params })
  return data.data
}
