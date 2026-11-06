import http, { type ApiResponse } from './http'

export interface ShopProfile {
  id: number
  shopName: string
  description?: string
  logo?: string
  contactName: string
  contactPhone: string
  status: string
  commissionRate: number
  createdAt: string
}

export interface Dashboard {
  shopName: string
  status: string
  onSaleProducts: number
  pendingShipCount: number
  shippedCount: number
  totalSales: number
  todayOrders: number
  todaySales: number
  trend: { date: string; orders: number; amount: number }[]
}

export async function fetchShopProfile(): Promise<ShopProfile> {
  const { data } = await http.get<ApiResponse<ShopProfile>>('/merchant/profile')
  return data.data
}

export async function saveShopProfile(payload: {
  shopName: string
  description?: string
  logo?: string
  contactName: string
  contactPhone: string
}): Promise<ShopProfile> {
  const { data } = await http.put<ApiResponse<ShopProfile>>('/merchant/profile', payload)
  return data.data
}

export async function fetchDashboard(): Promise<Dashboard> {
  const { data } = await http.get<ApiResponse<Dashboard>>('/merchant/dashboard')
  return data.data
}
