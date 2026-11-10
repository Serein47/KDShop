import http, { type ApiResponse } from './http'

export interface Coupon {
  couponId: number
  name: string
  type: 'FIXED' | 'THRESHOLD'
  value: number
  minAmount: number
  remaining: number
  claimed: boolean
  expiresAt: string
}

export interface MyCoupon {
  userCouponId: number
  couponId: number
  name: string
  type: string
  value: number
  minAmount: number
  status: 'UNUSED' | 'USED' | 'EXPIRED'
  expiresAt: string
  usedAt?: string
}

export async function fetchCoupons(): Promise<Coupon[]> {
  const { data } = await http.get<ApiResponse<Coupon[]>>('/coupons')
  return data.data
}

export async function claimCoupon(id: number): Promise<MyCoupon> {
  const { data } = await http.post<ApiResponse<MyCoupon>>(`/coupons/${id}/claim`)
  return data.data
}

export async function fetchMyCoupons(): Promise<MyCoupon[]> {
  const { data } = await http.get<ApiResponse<MyCoupon[]>>('/me/coupons')
  return data.data
}

export async function fetchAdminCoupons(): Promise<Coupon[]> {
  const { data } = await http.get<ApiResponse<Coupon[]>>('/admin/coupons')
  return data.data
}

export async function createCoupon(payload: {
  name: string
  type: string
  value: number
  minAmount: number
  total: number
  startsAt: string
  endsAt: string
}): Promise<Coupon> {
  const { data } = await http.post<ApiResponse<Coupon>>('/admin/coupons', payload)
  return data.data
}
