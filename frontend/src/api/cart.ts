import http, { type ApiResponse } from './http'

export interface CartItemView {
  id: number
  skuId: number
  productId: number
  name: string
  spec: Record<string, string>
  image?: string
  price: number
  quantity: number
  stock: number
  selected: boolean
  subtotal: number
}

export interface CartGroup {
  merchantId: number
  shopName?: string
  items: CartItemView[]
  subtotal: number
}

export interface CartView {
  groups: CartGroup[]
  summary: { selectedCount: number; goodsAmount: number }
}

export interface MergeResult {
  merged: number
  dropped: { skuId: number; reason: string; available?: number }[]
}

export async function fetchCart(): Promise<CartView> {
  const { data } = await http.get<ApiResponse<CartView>>('/cart')
  return data.data
}

export async function addCartItem(skuId: number, quantity: number): Promise<CartItemView> {
  const { data } = await http.post<ApiResponse<CartItemView>>('/cart/items', { skuId, quantity })
  return data.data
}

export async function patchCartItem(id: number, patch: { quantity?: number; selected?: boolean }): Promise<void> {
  await http.put(`/cart/items/${id}`, patch)
}

export async function removeCartItem(id: number): Promise<void> {
  await http.delete(`/cart/items/${id}`)
}

export async function mergeCart(items: { skuId: number; quantity: number }[]): Promise<MergeResult> {
  const { data } = await http.post<ApiResponse<MergeResult>>('/cart/merge', { items })
  return data.data
}
