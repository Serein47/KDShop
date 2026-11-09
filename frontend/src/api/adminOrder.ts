import http, { type ApiResponse } from './http'

export interface AdminOrder {
  orderNo: string
  userId: number
  nickname?: string
  status: string
  merchantCount: number
  goodsAmount: number
  payAmount: number
  createdAt: string
  paidAt?: string
}

export interface AdminOrderSub {
  subOrderNo: string
  merchantId: number
  shopName?: string
  status: string
  payAmount: number
  refundedAmount: number
  carrier?: string
  trackingNo?: string
  shippedAt?: string
}

export interface AdminOrderRefund {
  refundNo: string
  subOrderNo?: string
  amount: number
  status: string
  reason: string
  rejectReason?: string
  createdAt: string
}

export interface AdminOrderDetail {
  orderNo: string
  userId: number
  nickname?: string
  status: string
  goodsAmount: number
  discount: number
  shippingFee: number
  payAmount: number
  receiver?: string
  phone?: string
  address?: string
  createdAt: string
  paidAt?: string
  subOrders: AdminOrderSub[]
  refunds: AdminOrderRefund[]
}

export async function fetchAdminOrders(params: { orderNo?: string; status?: string }): Promise<AdminOrder[]> {
  const { data } = await http.get<ApiResponse<AdminOrder[]>>('/admin/orders', { params })
  return data.data
}

export async function fetchAdminOrder(orderNo: string): Promise<AdminOrderDetail> {
  const { data } = await http.get<ApiResponse<AdminOrderDetail>>(`/admin/orders/${orderNo}`)
  return data.data
}
