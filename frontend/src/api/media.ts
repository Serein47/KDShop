/** 商品图存的是 MinIO key（如 products/21/1.jpg），渲染时拼公共地址。 */
const BASE = ((import.meta.env.VITE_MEDIA_BASE as string | undefined) ?? 'http://localhost:9000/kdshop-media').replace(/\/+$/, '')

export function mediaUrl(key?: string | null): string {
  if (!key) return ''
  if (/^https?:\/\//i.test(key)) return key
  return `${BASE}/${key.replace(/^\/+/, '')}`
}
