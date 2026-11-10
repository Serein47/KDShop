import http, { type ApiResponse } from './http'

export interface ReviewView {
  id: number
  nickname: string
  rating: number
  content: string
  createdAt: string
}

export interface ProductReview {
  average: number
  count: number
  items: ReviewView[]
}

export interface MyReview {
  orderItemId: number
  productId: number
  rating: number
  content: string
  createdAt: string
}

export async function fetchProductReviews(productId: number): Promise<ProductReview> {
  const { data } = await http.get<ApiResponse<ProductReview>>(`/products/${productId}/reviews`)
  return data.data
}

export async function createReview(orderItemId: number, payload: { rating: number; content: string }): Promise<ReviewView> {
  const { data } = await http.post<ApiResponse<ReviewView>>(`/order-items/${orderItemId}/reviews`, payload)
  return data.data
}

export async function fetchMyReviews(): Promise<MyReview[]> {
  const { data } = await http.get<ApiResponse<MyReview[]>>('/me/reviews')
  return data.data
}
