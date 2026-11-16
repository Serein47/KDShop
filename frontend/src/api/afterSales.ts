import http, { type ApiResponse } from './http'

export type AfterSaleType = 'REFUND_ONLY' | 'RETURN_REFUND'
export type AfterSaleStatus =
  | 'APPLIED'
  | 'APPROVED'
  | 'REJECTED'
  | 'RETURNING'
  | 'RECEIVED'
  | 'INSPECTED'
  | 'REFUNDED'
  | 'CLOSED'
  | 'ESCALATED'

export interface AfterSaleLog {
  fromStatus?: string
  toStatus: string
  operatorType: string
  note?: string
  at: string
}

export interface AfterSale {
  asNo: string
  orderNo: string
  subOrderNo: string
  merchantId: number
  shopName?: string
  customerName?: string
  type: AfterSaleType
  reasonCode: string
  description?: string
  evidence: string[]
  status: AfterSaleStatus
  refundAmount: number
  refundNo?: string
  returnCarrier?: string
  returnTrackingNo?: string
  returnShippedAt?: string
  receivedAt?: string
  inspectResult?: 'GOOD' | 'DEFECTIVE'
  inspectNote?: string
  inspectImages: string[]
  responsibility?: 'MERCHANT' | 'LOGISTICS' | 'CUSTOMER'
  verdictNote?: string
  rejectReason?: string
  reapplyCount: number
  createdAt: string
  completedAt?: string
  logs?: AfterSaleLog[]
}

export interface ApplyAfterSalePayload {
  type: AfterSaleType
  reasonCode: string
  description?: string
  evidence?: string[]
}

export const REASON_LABELS: Record<string, string> = {
  QUALITY: '质量问题',
  NOT_AS_DESCRIBED: '与描述不符',
  DAMAGED: '运输破损',
  WRONG_ITEM: '发错商品',
  NO_LONGER_NEEDED: '不想要了',
  OTHER: '其他',
}

export const AFTER_SALE_STATUS_LABELS: Record<string, string> = {
  APPLIED: '待商家处理',
  APPROVED: '商家已同意',
  REJECTED: '已驳回',
  RETURNING: '待寄回',
  RECEIVED: '商家已收货',
  INSPECTED: '已质检',
  REFUNDED: '已退款',
  CLOSED: '已关闭',
  ESCALATED: '平台介入',
}

export async function applyAfterSale(
  orderNo: string,
  subOrderNo: string,
  payload: ApplyAfterSalePayload,
): Promise<AfterSale> {
  const { data } = await http.post<ApiResponse<AfterSale>>(
    `/orders/${encodeURIComponent(orderNo)}/sub-orders/${encodeURIComponent(subOrderNo)}/after-sales`,
    payload,
  )
  return data.data
}

export async function fetchMyAfterSales(): Promise<AfterSale[]> {
  const { data } = await http.get<ApiResponse<AfterSale[]>>('/me/after-sales')
  return data.data
}

export async function fetchMyAfterSale(asNo: string): Promise<AfterSale> {
  const { data } = await http.get<ApiResponse<AfterSale>>(`/me/after-sales/${asNo}`)
  return data.data
}

export async function reapplyAfterSale(asNo: string, payload: ApplyAfterSalePayload): Promise<AfterSale> {
  const { data } = await http.post<ApiResponse<AfterSale>>(`/me/after-sales/${asNo}/reapply`, payload)
  return data.data
}

export async function fetchMerchantAfterSales(status?: string): Promise<AfterSale[]> {
  const { data } = await http.get<ApiResponse<AfterSale[]>>('/merchant/after-sales', { params: { status } })
  return data.data
}

export async function fetchMerchantAfterSale(asNo: string): Promise<AfterSale> {
  const { data } = await http.get<ApiResponse<AfterSale>>(`/merchant/after-sales/${asNo}`)
  return data.data
}

export async function reviewAfterSale(
  asNo: string,
  approve: boolean,
  reason?: string,
): Promise<AfterSale> {
  const { data } = await http.post<ApiResponse<AfterSale>>(`/merchant/after-sales/${asNo}/review`, {
    approve,
    reason,
  })
  return data.data
}

export async function uploadEvidence(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await http.post<ApiResponse<{ key: string }>>('/uploads/image', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data.data.key
}
