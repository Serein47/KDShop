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
