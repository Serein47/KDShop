<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  fetchFavorites, fetchFollows, removeFavorite, unfollowShop,
  type FavoriteProduct, type FollowedShop,
} from '@/api/favorite'
import { yuan } from '@/api/catalog'
import { mediaUrl } from '@/api/media'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const tab = ref<'products' | 'shops'>('products')
const products = ref<FavoriteProduct[]>([])
const shops = ref<FollowedShop[]>([])
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    const [fav, follows] = await Promise.all([fetchFavorites(), fetchFollows()])
    products.value = fav
    shops.value = follows
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (!auth.ready) await auth.fetchMe()
  if (!auth.user) {
    router.push({ path: '/login', query: { redirect: '/favorites' } })
    return
  }
  await load()
})

async function dropProduct(item: FavoriteProduct) {
  try {
    await removeFavorite(item.productId)
    products.value = products.value.filter((p) => p.productId !== item.productId)
    ElMessage.success('已取消收藏')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

async function dropShop(item: FollowedShop) {
  try {
    await unfollowShop(item.merchantId)
    shops.value = shops.value.filter((s) => s.merchantId !== item.merchantId)
    ElMessage.success('已取消关注')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}
</script>

<template>
  <div class="fav container">
    <p class="eyebrow">Favorites</p>
    <h1>收藏夹</h1>

    <div class="tabs">
      <button :class="{ on: tab === 'products' }" @click="tab = 'products'">商品 {{ products.length }}</button>
      <button :class="{ on: tab === 'shops' }" @click="tab = 'shops'">店铺 {{ shops.length }}</button>
    </div>

    <div v-if="loading" class="state">加载中…</div>

    <template v-else-if="tab === 'products'">
      <p v-if="!products.length" class="state">还没有收藏的商品，去商品页点「收藏」试试。</p>
      <div v-else class="grid">
        <article v-for="item in products" :key="item.productId" class="card">
          <RouterLink :to="`/products/${item.productId}`" class="thumb">
            <img v-if="item.mainImage" :src="mediaUrl(item.mainImage)" :alt="item.name" />
            <span v-else>{{ item.name.slice(0, 1) }}</span>
          </RouterLink>
          <div class="info">
            <RouterLink :to="`/products/${item.productId}`" class="name">{{ item.name }}</RouterLink>
            <p class="muted">{{ item.shopName }}</p>
            <p class="price">¥{{ yuan(item.priceMin) }}</p>
          </div>
          <button class="link" @click="dropProduct(item)">取消收藏</button>
        </article>
      </div>
    </template>

    <template v-else>
      <p v-if="!shops.length" class="state">还没有关注的店铺，去店铺页点「关注店铺」。</p>
      <div v-else class="shops">
        <article v-for="shop in shops" :key="shop.merchantId" class="shop">
          <RouterLink :to="`/shops/${shop.merchantId}`" class="shop__name">{{ shop.shopName }}</RouterLink>
          <p class="muted">{{ shop.description || '这家店还没有写介绍' }}</p>
          <p class="muted">在售 {{ shop.productCount }} 件</p>
          <button class="link" @click="dropShop(shop)">取消关注</button>
        </article>
      </div>
    </template>
  </div>
</template>

<style scoped>
.fav { padding-top: 48px; max-width: 960px; }
h1 { font-size: 34px; margin: 6px 0 20px; }
.tabs { display: flex; gap: 10px; margin-bottom: 20px; }
.tabs button { padding: 8px 16px; border: 1px solid var(--line); background: none; border-radius: 999px; cursor: pointer; font-size: 13px; }
.tabs button.on { border-color: var(--rust); color: var(--rust); background: var(--rust-soft); }
.grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.card { background: #fff; border-radius: 16px; padding: 14px; box-shadow: var(--shadow-sm); display: grid; gap: 10px; }
.thumb { display: block; aspect-ratio: 1 / 1; border-radius: 12px; overflow: hidden; background: var(--rust-soft); display: grid; place-items: center; font-size: 28px; color: var(--rust); }
.thumb img { width: 100%; height: 100%; object-fit: cover; }
.name { font-family: var(--serif); font-size: 16px; color: var(--ink); }
.muted { color: var(--muted); font-size: 12px; margin: 0; }
.price { color: var(--rust); font-weight: 700; margin: 2px 0 0; }
.link { border: 0; background: none; color: var(--muted); font-size: 12px; cursor: pointer; justify-self: start; }
.link:hover { color: var(--rust); }
.shops { display: grid; gap: 14px; }
.shop { background: #fff; border-radius: 16px; padding: 18px 20px; box-shadow: var(--shadow-sm); display: grid; gap: 6px; }
.shop__name { font-family: var(--serif); font-size: 18px; color: var(--ink); }
.state { color: var(--muted); padding: 40px 0; }
@media (max-width: 860px) { .grid { grid-template-columns: repeat(2, 1fr); } }
</style>
