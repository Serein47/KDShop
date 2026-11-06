import http, { type ApiResponse } from './http'

export interface MerchantSubOrderItem {
  name: string
  spec: Record<string, string>
  price: number
  quantity: number
  subtotal: number
  image?: string
}

export interface MerchantSubOrder {
  subOrderNo: string
  orderNo: string
  status: string
  goodsAmount: number
  discount: number
  shippingFee: number
  payAmount: number
  createdAt: string
  shippedAt?: string
  carrier?: string
  trackingNo?: string
  receiver?: string
  phone?: string
  address?: string
  items: MerchantSubOrderItem[]
}

export async function fetchSubOrders(status?: string): Promise<MerchantSubOrder[]> {
  const { data } = await http.get<ApiResponse<MerchantSubOrder[]>>('/merchant/sub-orders', { params: { status } })
  return data.data
}

export async function shipSubOrder(subOrderNo: string, carrier: string, trackingNo: string): Promise<MerchantSubOrder> {
  const { data } = await http.post<ApiResponse<MerchantSubOrder>>(`/merchant/sub-orders/${subOrderNo}/ship`, { carrier, trackingNo })
  return data.data
}
