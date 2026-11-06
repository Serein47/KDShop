import http, { type ApiResponse } from './http'

export interface MerchantProduct {
  id: number
  name: string
  subtitle?: string
  categoryId: number
  categoryName?: string
  status: 'ON' | 'OFF'
  mainImage?: string
  priceMin: number
  priceMax: number
  stock: number
  salesCount: number
  skus: { id: number; price: number; stock: number; spec: string }[]
}

export interface ProductPayload {
  name: string
  subtitle?: string
  description?: string
  categoryId: number
  price: number
  stock: number
  mainImage?: string
}

export async function fetchProducts(status?: string): Promise<MerchantProduct[]> {
  const { data } = await http.get<ApiResponse<MerchantProduct[]>>('/merchant/products', { params: { status } })
  return data.data
}

export async function createProduct(payload: ProductPayload): Promise<MerchantProduct> {
  const { data } = await http.post<ApiResponse<MerchantProduct>>('/merchant/products', payload)
  return data.data
}

export async function updateProduct(id: number, payload: ProductPayload): Promise<MerchantProduct> {
  const { data } = await http.put<ApiResponse<MerchantProduct>>(`/merchant/products/${id}`, payload)
  return data.data
}

export async function setProductStatus(id: number, status: 'ON' | 'OFF'): Promise<MerchantProduct> {
  const { data } = await http.post<ApiResponse<MerchantProduct>>(`/merchant/products/${id}/status`, { status })
  return data.data
}

export async function uploadImage(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await http.post<ApiResponse<{ key: string }>>('/merchant/uploads/image', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data.data.key
}
