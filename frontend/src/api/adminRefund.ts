import http, { type ApiResponse } from './http'

export interface AdminRefund {
  refundNo: string
  subOrderNo?: string
  orderNo?: string
  merchantId: number
  shopName?: string
  amount: number
  reason: string
  status: 'PENDING' | 'REFUNDED' | 'REJECTED'
  rejectReason?: string
  createdAt: string
  finishedAt?: string
}

export async function fetchRefunds(status?: string): Promise<AdminRefund[]> {
  const { data } = await http.get<ApiResponse<AdminRefund[]>>('/admin/refunds', { params: { status } })
  return data.data
}

export async function interveneRefund(refundNo: string, approve: boolean, reason: string): Promise<AdminRefund> {
  const { data } = await http.post<ApiResponse<AdminRefund>>(`/admin/refunds/${refundNo}/intervene`, { approve, reason })
  return data.data
}
