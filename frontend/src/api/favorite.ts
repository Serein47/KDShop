import http, { type ApiResponse } from './http'

export interface FavoriteProduct {
  productId: number
  name: string
  subtitle?: string
  mainImage?: string
  priceMin: number
  salesCount: number
  merchantId: number
  shopName?: string
}

export interface FollowedShop {
  merchantId: number
  shopName: string
  logo?: string
  description?: string
  productCount: number
}

export async function fetchFavorites(): Promise<FavoriteProduct[]> {
  const { data } = await http.get<ApiResponse<FavoriteProduct[]>>('/me/favorites')
  return data.data
}

export async function addFavorite(productId: number): Promise<void> {
  await http.post(`/me/favorites/${productId}`)
}

export async function removeFavorite(productId: number): Promise<void> {
  await http.delete(`/me/favorites/${productId}`)
}

export async function fetchFollows(): Promise<FollowedShop[]> {
  const { data } = await http.get<ApiResponse<FollowedShop[]>>('/me/follows')
  return data.data
}

export async function followShop(merchantId: number): Promise<void> {
  await http.post(`/me/follows/${merchantId}`)
}

export async function unfollowShop(merchantId: number): Promise<void> {
  await http.delete(`/me/follows/${merchantId}`)
}
