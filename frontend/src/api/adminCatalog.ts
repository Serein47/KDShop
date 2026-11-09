import http, { type ApiResponse } from './http'

export interface AdminCategory {
  id: number
  parentId?: number
  name: string
  sort: number
  status: string
  featured: boolean
  productCount: number
}

export interface AdminProduct {
  id: number
  name: string
  merchantId: number
  shopName?: string
  categoryId: number
  categoryName?: string
  status: string
  offlineReason?: string
  mainImage?: string
  priceMin: number
  stock: number
  salesCount: number
}

export async function fetchCategories(): Promise<AdminCategory[]> {
  const { data } = await http.get<ApiResponse<AdminCategory[]>>('/admin/categories')
  return data.data
}

export async function createCategory(payload: { parentId?: number; name: string; sort?: number; featured?: boolean }): Promise<AdminCategory> {
  const { data } = await http.post<ApiResponse<AdminCategory>>('/admin/categories', payload)
  return data.data
}

export async function updateCategory(id: number, payload: { name: string; sort?: number; featured?: boolean; status?: string }): Promise<AdminCategory> {
  const { data } = await http.put<ApiResponse<AdminCategory>>(`/admin/categories/${id}`, payload)
  return data.data
}

export async function fetchAdminProducts(params: { status?: string; keyword?: string; merchantId?: number }): Promise<AdminProduct[]> {
  const { data } = await http.get<ApiResponse<AdminProduct[]>>('/admin/products', { params })
  return data.data
}

export async function forceOffline(id: number, reason: string): Promise<void> {
  await http.post(`/admin/products/${id}/offline`, { reason })
}

export async function forceOnline(id: number): Promise<void> {
  await http.post(`/admin/products/${id}/online`)
}
