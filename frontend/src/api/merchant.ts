import http, { type ApiResponse } from './http'

export interface Merchant {
  id: number
  userId: number
  shopName: string
  description?: string
  contactName: string
  contactPhone: string
  status: 'PENDING' | 'ACTIVE' | 'REJECTED'
  rejectReason?: string
  commissionRate: number
  createdAt: string
  approvedAt?: string
}

export interface ApplyPayload {
  shopName: string
  contactName: string
  contactPhone: string
  description?: string
  licenseImage?: string
}

export async function applyMerchant(payload: ApplyPayload): Promise<Merchant> {
  const { data } = await http.post<ApiResponse<Merchant>>('/merchant/applications', payload)
  return data.data
}

export async function fetchMyMerchant(): Promise<Merchant> {
  const { data } = await http.get<ApiResponse<Merchant>>('/merchant/applications/me')
  return data.data
}

export async function fetchMerchants(status?: string): Promise<Merchant[]> {
  const { data } = await http.get<ApiResponse<Merchant[]>>('/admin/merchants', { params: { status } })
  return data.data
}

export async function approveMerchant(id: number): Promise<Merchant> {
  const { data } = await http.post<ApiResponse<Merchant>>(`/admin/merchants/${id}/approve`)
  return data.data
}

export async function rejectMerchant(id: number, reason: string): Promise<Merchant> {
  const { data } = await http.post<ApiResponse<Merchant>>(`/admin/merchants/${id}/reject`, { reason })
  return data.data
}
