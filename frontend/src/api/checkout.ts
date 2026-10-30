import http, { type ApiResponse } from './http'

export interface Address {
  id: number
  receiver: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  isDefault: number
}

export interface AddressPayload {
  receiver: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  isDefault?: boolean
}

export interface PreviewLine {
  skuId: number
  productId: number
  name: string
  spec: Record<string, string>
  price: number
  quantity: number
  subtotal: number
}

export interface PreviewSubOrder {
  merchantId: number
  shopName: string
  items: PreviewLine[]
  goodsAmount: number
  shippingFee: number
  discount: number
  payAmount: number
}

export interface PreviewCoupon {
  userCouponId: number
  couponId: number
  name: string
  type: string
  value: number
  minAmount: number
  expiresAt: string
}

export interface PreviewUnusable {
  couponId: number
  name: string
  reason: string
}

export interface CheckoutPreview {
  subOrders: PreviewSubOrder[]
  summary: { goodsAmount: number; discount: number; shippingFee: number; payAmount: number }
  coupons: { usable: PreviewCoupon[]; unusable: PreviewUnusable[] }
  address: { receiver: string; phone: string; province: string; city: string; district: string; detail: string } | null
}

export async function fetchPreview(payload: { addressId?: number; couponId?: number; items?: { skuId: number; quantity: number }[] }): Promise<CheckoutPreview> {
  const { data } = await http.post<ApiResponse<CheckoutPreview>>('/checkout/preview', payload)
  return data.data
}

export async function fetchAddresses(): Promise<Address[]> {
  const { data } = await http.get<ApiResponse<Address[]>>('/me/addresses')
  return data.data
}

export async function createAddress(payload: AddressPayload): Promise<Address> {
  const { data } = await http.post<ApiResponse<Address>>('/me/addresses', payload)
  return data.data
}
