<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useCartStore } from '@/stores/cart'
import { useAuthStore } from '@/stores/auth'
import { yuan } from '@/api/catalog'
import ProductMedia from '@/components/ProductMedia.vue'

const cart = useCartStore()
const router = useRouter()
const auth = useAuthStore()

onMounted(async () => {
  if (!auth.ready) await auth.fetchMe()
  await cart.load()
})

interface Row {
  id?: number
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

const groups = computed(() => {
  if (cart.isServer && cart.server) {
    return cart.server.groups.map((group) => ({ key: String(group.merchantId), title: group.shopName || '店铺', items: group.items as Row[] }))
  }
  return [{ key: 'local', title: auth.user ? '购物车' : '本地购物车（登录后可同步）', items: cart.local as Row[] }]
})

const selectedRows = computed(() => groups.value.flatMap((g) => g.items).filter((i) => i.selected))
const totalAmount = computed(() => selectedRows.value.reduce((sum, item) => sum + item.price * item.quantity, 0))
const totalCount = computed(() => selectedRows.value.reduce((sum, item) => sum + item.quantity, 0))

async function changeQty(item: Row, delta: number) {
  const next = item.quantity + delta
  if (next < 1) return
  if (next > item.stock) {
    ElMessage.warning(`最多可买 ${item.stock} 件`)
    return
  }
  await cart.updateQuantity(item, next)
}

async function toggle(item: Row, value: boolean) {
  await cart.toggle(item, value)
}

async function remove(item: Row) {
  await cart.remove(item)
  ElMessage.success('已移出购物车')
}

function checkout() {
  if (!selectedRows.value.length) return
  if (!cart.isServer) {
    ElMessage.warning('请先登录再结算')
    router.push({ path: '/login', query: { redirect: '/cart' } })
    return
  }
  router.push('/checkout')
}
</script>

<template>
  <div class="cart container">
    <header class="head">
      <div>
        <p class="eyebrow">Cart</p>
        <h1>购物车</h1>
      </div>
      <RouterLink to="/products" class="more">继续购物 →</RouterLink>
    </header>

    <div v-if="!groups.some((g) => g.items.length)" class="empty">
      <p class="empty__title">购物车还是空的</p>
      <p class="empty__text">挑几件喜欢的，或者先去看看手作专区。</p>
      <RouterLink to="/products" class="btn btn--solid">去逛逛</RouterLink>
    </div>

    <template v-else>
      <section v-for="group in groups" :key="group.key" class="group">
        <h2 class="group__title">{{ group.title }}</h2>
        <div v-for="item in group.items" :key="item.id ?? item.skuId" class="row">
          <label class="check">
            <input type="checkbox" :checked="item.selected" @change="toggle(item, ($event.target as HTMLInputElement).checked)" />
          </label>
          <RouterLink :to="`/products/${item.productId}`" class="thumb">
            <ProductMedia :name="item.name" :image="item.image" :seed="item.skuId" ratio="1 / 1" />
          </RouterLink>
          <div class="info">
            <RouterLink :to="`/products/${item.productId}`" class="name">{{ item.name }}</RouterLink>
            <p class="spec">{{ Object.values(item.spec).join(' · ') || '标准规格' }}</p>
            <p class="stock">库存 {{ item.stock }}</p>
          </div>
          <div class="qty">
            <button :disabled="item.quantity <= 1" @click="changeQty(item, -1)">−</button>
            <span>{{ item.quantity }}</span>
            <button :disabled="item.quantity >= item.stock" @click="changeQty(item, 1)">+</button>
          </div>
          <div class="price">
            <strong>¥{{ yuan(item.price * item.quantity) }}</strong>
            <span>¥{{ yuan(item.price) }} / 件</span>
          </div>
          <button class="remove" @click="remove(item)">移除</button>
        </div>
      </section>

      <div class="summary">
        <div class="summary__left">
          <span>已选 <strong>{{ totalCount }}</strong> 件</span>
          <span v-if="!cart.isServer" class="hint">登录后可跨设备同步</span>
        </div>
        <div class="summary__right">
          <span class="total">合计 <strong>¥{{ yuan(totalAmount) }}</strong></span>
          <button class="btn btn--solid" :disabled="!selectedRows.length" @click="checkout">去结算</button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.cart { padding-top: 48px; }
.head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 28px; }
.head h1 { font-size: 34px; margin-top: 4px; }
.more { font-size: 13px; color: var(--muted); }
.more:hover { color: var(--rust); }

.empty { text-align: center; padding: 90px 0 120px; }
.empty__title { font-family: var(--serif); font-size: 24px; margin: 0 0 8px; }
.empty__text { color: var(--muted); margin: 0 0 22px; }

.group { background: #fff; border-radius: 18px; box-shadow: var(--shadow-sm); padding: 18px 20px; margin-bottom: 18px; }
.group__title { font-size: 16px; margin: 0 0 8px; color: var(--ink-soft); }
.row {
  display: grid;
  grid-template-columns: 24px 84px 1fr 120px 120px 56px;
  align-items: center;
  gap: 16px;
  padding: 16px 0;
  border-top: 1px solid var(--line);
}
.row:first-of-type { border-top: 0; }
.check input { width: 16px; height: 16px; accent-color: var(--rust); }
.thumb { width: 84px; }
.thumb :deep(.media) { border-radius: 10px; aspect-ratio: 1 / 1; }
.thumb :deep(.media__glyph) { font-size: 30px; }
.info .name { font-family: var(--serif); font-size: 16px; }
.info .spec { margin: 2px 0 0; font-size: 12px; color: var(--muted); }
.info .stock { margin: 2px 0 0; font-size: 11px; color: var(--muted); }
.qty { display: flex; align-items: center; border: 1px solid var(--line); border-radius: 999px; overflow: hidden; width: fit-content; }
.qty button { width: 32px; height: 32px; border: 0; background: none; cursor: pointer; font-size: 15px; }
.qty button:disabled { opacity: .35; cursor: not-allowed; }
.qty span { width: 30px; text-align: center; font-size: 13px; }
.price { text-align: right; }
.price strong { display: block; color: var(--rust); font-size: 16px; }
.price span { font-size: 11px; color: var(--muted); }
.remove { border: 0; background: none; color: var(--muted); font-size: 12px; cursor: pointer; }
.remove:hover { color: var(--rust); }

.summary {
  position: sticky;
  bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-radius: 18px;
  padding: 16px 22px;
  box-shadow: var(--shadow);
  margin-top: 24px;
}
.summary__left { color: var(--muted); font-size: 14px; display: flex; gap: 14px; align-items: baseline; }
.summary__left strong { color: var(--ink); font-size: 18px; }
.hint { font-size: 12px; color: var(--gold); }
.summary__right { display: flex; align-items: center; gap: 18px; }
.total { font-size: 14px; color: var(--muted); }
.total strong { font-size: 24px; color: var(--rust); }
@media (max-width: 900px) {
  .row { grid-template-columns: 24px 64px 1fr; grid-template-areas: "c t i" ". q p"; row-gap: 10px; }
  .summary { flex-direction: column; align-items: flex-start; gap: 12px; }
}
</style>
