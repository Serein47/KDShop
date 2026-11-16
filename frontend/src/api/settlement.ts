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
  /** 工期超时赔付扣减，分 */
  penalty: number
  netAmount: number
  status: 'PENDING' | 'PAID'
  orderCount: number
  settledAt?: string
  createdAt: string
}

export interface SettlementItem {
  subOrderNo: string
  grossAmount: number
  commission: number
  refund: number
  penalty: number
  netAmount: number
}

export interface SettlementDetail {
  settlement: Settlement
  items: SettlementItem[]
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

export async function fetchMySettlementDetail(settlementNo: string): Promise<SettlementDetail> {
  const { data } = await http.get<ApiResponse<SettlementDetail>>(`/merchant/settlements/${settlementNo}`)
  return data.data
}

export async function fetchSettlementDetail(settlementNo: string): Promise<SettlementDetail> {
  const { data } = await http.get<ApiResponse<SettlementDetail>>(`/admin/settlements/${settlementNo}`)
  return data.data
}
