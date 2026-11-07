import http, { type ApiResponse } from './http'

export interface Settlement {
  settlementNo: string
  merchantId: number
  shopName?: string
  periodStart: string
  periodEnd: string
  grossAmount: number
  commission: number
  refund: number
  netAmount: number
  status: 'PENDING' | 'PAID'
  orderCount: number
  settledAt?: string
  createdAt: string
}

export async function fetchSettlements(): Promise<Settlement[]> {
  const { data } = await http.get<ApiResponse<Settlement[]>>('/admin/settlements')
  return data.data
}

export async function generateSettlements(periodStart: string, periodEnd: string): Promise<Settlement[]> {
  const { data } = await http.post<ApiResponse<Settlement[]>>('/admin/settlements/generate', { periodStart, periodEnd })
  return data.data
}

export async function confirmSettlement(settlementNo: string): Promise<Settlement> {
  const { data } = await http.post<ApiResponse<Settlement>>(`/admin/settlements/${settlementNo}/confirm`)
  return data.data
}

export async function fetchMySettlements(): Promise<Settlement[]> {
  const { data } = await http.get<ApiResponse<Settlement[]>>('/merchant/settlements')
  return data.data
}
