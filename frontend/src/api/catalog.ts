import http, { type ApiResponse } from './http'

export interface CategoryNode {
  id: number
  name: string
  sort: number
  children: CategoryNode[]
}

export interface TagView {
  id: number
  name: string
  slug: string
}

export type TagGroups = Record<'CRAFT' | 'MATERIAL' | 'OCCASION', TagView[]>

export interface ProductCard {
  id: number
  name: string
  subtitle?: string
  mainImage?: string
  minPrice: number
  maxPrice: number
  salesCount: number
  merchantId: number
  shopName?: string
  saleMode: 'IN_STOCK' | 'PREORDER' | 'CUSTOM'
  leadDays?: number
  depositRate?: number
  uniquePiece?: boolean
  editionSize?: number
  editionNo?: number
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
  hasNext: boolean
}

export interface SkuView {
  id: number
  spec: Record<string, string>
  price: number
  originalPrice?: number
  stock: number
}

export interface OptionView {
  id: number
  name: string
  type: 'TEXT' | 'ENUM'
  required: boolean
  maxLength?: number
  choices: string[]
  priceDelta: number
}

export interface ProductDetail extends ProductCard {
  description?: string
  images: string[]
  categoryId: number
  categoryName?: string
  shop: { id: number; shopName: string; logo?: string }
  skus: SkuView[]
  tags: Partial<Record<'CRAFT' | 'MATERIAL' | 'OCCASION', TagView[]>>
  options: OptionView[]
  story?: string
  flawNote?: string
  requiresFlawAck: boolean
  rating: number
  reviewCount: number
  favorited: boolean
}

export interface ShopPage {
  id: number
  shopName: string
  logo?: string
  description?: string
  productCount: number
  salesCount: number
  createdAt: string
}

function unwrap<T>(payload: ApiResponse<T>): T {
  return payload.data
}

export async function fetchCategories(): Promise<CategoryNode[]> {
  const { data } = await http.get<ApiResponse<CategoryNode[]>>('/categories')
  return unwrap(data)
}

export async function fetchTags(): Promise<TagGroups> {
  const { data } = await http.get<ApiResponse<TagGroups>>('/tags')
  return unwrap(data)
}

export interface ProductQuery {
  keyword?: string
  categoryId?: number
  craft?: string
  material?: string
  occasion?: string
  minPrice?: number
  maxPrice?: number
  sort?: string
  page?: number
  size?: number
}

export async function fetchProducts(query: ProductQuery): Promise<PageResult<ProductCard>> {
  const { data } = await http.get<ApiResponse<PageResult<ProductCard>>>('/products', { params: query })
  return unwrap(data)
}

export async function fetchProduct(id: number | string): Promise<ProductDetail> {
  const { data } = await http.get<ApiResponse<ProductDetail>>(`/products/${id}`)
  return unwrap(data)
}

export async function fetchShop(id: number | string): Promise<ShopPage> {
  const { data } = await http.get<ApiResponse<ShopPage>>(`/shops/${id}`)
  return unwrap(data)
}

export async function fetchShopProducts(id: number | string, page = 1, size = 12): Promise<PageResult<ProductCard>> {
  const { data } = await http.get<ApiResponse<PageResult<ProductCard>>>(`/shops/${id}/products`, {
    params: { page, size },
  })
  return unwrap(data)
}

export function yuan(cent?: number): string {
  if (cent === undefined || cent === null) return '—'
  return (cent / 100).toFixed(2).replace(/\.00$/, '')
}
