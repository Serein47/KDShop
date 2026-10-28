import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { addCartItem, fetchCart, mergeCart, patchCartItem, removeCartItem, type CartView } from '@/api/cart'
import { useAuthStore } from './auth'

export interface LocalCartItem {
  skuId: number
  productId: number
  name: string
  spec: Record<string, string>
  image?: string
  price: number
  quantity: number
  stock: number
  selected: boolean
}

const STORAGE_KEY = 'kdshop.cart'

function readLocal(): LocalCartItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as LocalCartItem[]) : []
  } catch {
    return []
  }
}

export const useCartStore = defineStore('cart', () => {
  const auth = useAuthStore()

  const local = ref<LocalCartItem[]>(readLocal())
  const server = ref<CartView | null>(null)

  const isServer = computed(() => !!auth.user)

  const count = computed(() => {
    if (isServer.value && server.value) {
      return server.value.summary.selectedCount
    }
    return local.value.reduce((sum, item) => sum + (item.selected ? item.quantity : 0), 0)
  })

  function persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(local.value))
  }

  async function load() {
    if (isServer.value) {
      try {
        server.value = await fetchCart()
      } catch {
        server.value = null
      }
    } else {
      local.value = readLocal()
    }
  }

  async function add(input: { skuId: number; productId: number; name: string; spec: Record<string, string>; image?: string; price: number; stock: number }, quantity = 1) {
    if (isServer.value) {
      await addCartItem(input.skuId, quantity)
      await load()
      return
    }
    const existing = local.value.find((item) => item.skuId === input.skuId)
    const target = (existing?.quantity ?? 0) + quantity
    if (target > input.stock) {
      throw new Error(`库存不足，当前可买 ${input.stock} 件`)
    }
    if (existing) {
      existing.quantity = target
    } else {
      local.value.push({ ...input, quantity, selected: true })
    }
    persist()
  }

  async function updateQuantity(item: { id?: number; skuId: number }, quantity: number) {
    if (isServer.value && item.id) {
      await patchCartItem(item.id, { quantity })
      await load()
      return
    }
    const target = local.value.find((entry) => entry.skuId === item.skuId)
    if (!target) return
    if (quantity <= 0) {
      local.value = local.value.filter((entry) => entry.skuId !== item.skuId)
    } else {
      target.quantity = Math.min(quantity, target.stock)
    }
    persist()
  }

  async function toggle(item: { id?: number; skuId: number }, selected: boolean) {
    if (isServer.value && item.id) {
      await patchCartItem(item.id, { selected })
      await load()
      return
    }
    const target = local.value.find((entry) => entry.skuId === item.skuId)
    if (target) {
      target.selected = selected
      persist()
    }
  }

  async function remove(item: { id?: number; skuId: number }) {
    if (isServer.value && item.id) {
      await removeCartItem(item.id)
      await load()
      return
    }
    local.value = local.value.filter((entry) => entry.skuId !== item.skuId)
    persist()
  }

  /** 登录后把本地购物车合并到服务端，成功后清空本地 */
  async function mergeLocal() {
    if (!local.value.length) return null
    const result = await mergeCart(local.value.map((item) => ({ skuId: item.skuId, quantity: item.quantity })))
    local.value = []
    persist()
    await load()
    return result
  }

  return { local, server, count, isServer, load, add, updateQuantity, toggle, remove, mergeLocal }
})
