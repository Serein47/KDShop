import http, { type ApiResponse } from './http'

export interface CreatedOrder {
  orderNo: string
  status: string
  payAmount: number
  expireAt: string
  subOrders: { subOrderNo: string; merchantId: number; shopName?: string; payAmount: number }[]
}

export async function createOrder(payload: {
  addressId?: number
  couponId?: number
  items?: { skuId: number; quantity: number }[]
  remark?: string
}, idempotencyKey: string): Promise<CreatedOrder> {
  const { data } = await http.post<ApiResponse<CreatedOrder>>('/orders', payload, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
  return data.data
}

export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'PARTIALLY_SHIPPED'
  | 'SHIPPED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REFUNDED'

export interface OrderListItem {
  orderNo: string
  status: OrderStatus
  payAmount: number
  createdAt: string
  subOrderCount: number
  shippedCount: number
  summary: string
  coverImage?: string | null
  expireAt?: string | null
}

export interface OrderPage {
  items: OrderListItem[]
  total: number
  page: number
  size: number
  hasNext: boolean
}

export interface OrderAddress {
  receiver: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
}

export interface OrderTimelineEntry {
  status: string
  at: string
  actor: string
}

export interface OrderItemView {
  image?: string
  name: string
  spec: Record<string, string>
  price: number
  quantity: number
  subtotal: number
}

export interface SubOrderView {
  subOrderNo: string
  merchantId: number
  shopName: string
  status: string
  payAmount: number
  shippedAt?: string | null
  shipment?: { carrier: string; trackingNo: string } | null
  items: OrderItemView[]
  actions: string[]
}

export interface OrderDetail {
  orderNo: string
  status: OrderStatus
  goodsAmount: number
  discount: number
  shippingFee: number
  payAmount: number
  address?: OrderAddress | null
  remark?: string | null
  createdAt: string
  paidAt?: string | null
  expireAt?: string | null
  timeline: OrderTimelineEntry[]
  subOrders: SubOrderView[]
}

export interface OrderCancelResult {
  orderNo: string
  status: string
}

export interface SubOrderConfirmResult {
  subOrderNo: string
  status: string
  orderStatus: string
}

export async function fetchOrders(params: {
  status?: string
  page?: number
  size?: number
} = {}): Promise<OrderPage> {
  const { data } = await http.get<ApiResponse<OrderPage>>('/orders', { params })
  return data.data
}

export async function fetchOrder(orderNo: string): Promise<OrderDetail> {
  const { data } = await http.get<ApiResponse<OrderDetail>>(`/orders/${encodeURIComponent(orderNo)}`)
  return data.data
}

export async function cancelOrder(orderNo: string): Promise<OrderCancelResult> {
  const { data } = await http.post<ApiResponse<OrderCancelResult>>(
    `/orders/${encodeURIComponent(orderNo)}/cancel`,
  )
  return data.data
}

export async function confirmSubOrder(
  orderNo: string,
  subOrderNo: string,
): Promise<SubOrderConfirmResult> {
  const { data } = await http.post<ApiResponse<SubOrderConfirmResult>>(
    `/orders/${encodeURIComponent(orderNo)}/sub-orders/${encodeURIComponent(subOrderNo)}/confirm`,
  )
  return data.data
}
