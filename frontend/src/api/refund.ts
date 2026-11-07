import http, { type ApiResponse } from './http'

export interface RefundView {
  refundNo: string
  subOrderNo: string
  orderNo: string
  merchantId: number
  shopName?: string
  amount: number
  reason: string
  status: 'PENDING' | 'REFUNDED' | 'REJECTED'
  rejectReason?: string
  createdAt: string
  finishedAt?: string
}

export async function applyRefund(subOrderNo: string, reason: string): Promise<RefundView> {
  const { data } = await http.post<ApiResponse<RefundView>>('/refunds', { subOrderNo, reason })
  return data.data
}

export async function fetchMyRefunds(): Promise<RefundView[]> {
  const { data } = await http.get<ApiResponse<RefundView[]>>('/refunds')
  return data.data
}

export async function fetchMerchantRefunds(status?: string): Promise<RefundView[]> {
  const { data } = await http.get<ApiResponse<RefundView[]>>('/merchant/refunds', { params: { status } })
  return data.data
}

export async function approveRefund(refundNo: string): Promise<RefundView> {
  const { data } = await http.post<ApiResponse<RefundView>>(`/merchant/refunds/${refundNo}/approve`)
  return data.data
}

export async function rejectRefund(refundNo: string, reason: string): Promise<RefundView> {
  const { data } = await http.post<ApiResponse<RefundView>>(`/merchant/refunds/${refundNo}/reject`, { reason })
  return data.data
}
