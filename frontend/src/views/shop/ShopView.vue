<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchShop, fetchShopProducts, type ProductCard, type ShopPage } from '@/api/catalog'
import ProductCardView from '@/components/ProductCardView.vue'

const route = useRoute()
const router = useRouter()
const shop = ref<ShopPage | null>(null)
const products = ref<ProductCard[]>([])
const total = ref(0)

const page = computed(() => Number(route.query.page ?? 1))
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / 12)))

watch(
  () => route.params.id,
  async (id) => {
    shop.value = await fetchShop(id as string)
    const result = await fetchShopProducts(id as string, page.value, 12)
    products.value = result.items
    total.value = result.total
  },
  { immediate: true },
)

function goPage(target: number) {
  router.push({ path: route.path, query: { page: String(target) } })
}
</script>

<template>
  <div class="shop container">
    <header v-if="shop" class="banner">
      <p class="eyebrow">Workshop</p>
      <h1>{{ shop.shopName }}</h1>
      <p class="desc">{{ shop.description }}</p>
      <p class="meta">{{ shop.productCount }} 件在售 · 入场于 {{ shop.createdAt.slice(0, 10) }}</p>
    </header>

    <div class="grid">
      <ProductCardView v-for="item in products" :key="item.id" :product="item" />
    </div>

    <div v-if="totalPages > 1" class="pager">
      <button :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
      <span>{{ page }} / {{ totalPages }}</span>
      <button :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
    </div>
  </div>
</template>

<style scoped>
.shop { padding-top: 56px; }
.banner {
  padding-bottom: 36px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 40px;
}
.banner h1 { font-size: clamp(30px, 4vw, 44px); margin: 8px 0 10px; }
.desc { color: var(--ink-soft); margin: 0 0 10px; }
.meta { color: var(--muted); font-size: 12px; letter-spacing: 0.06em; margin: 0; }
.grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 26px; }
.pager {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 18px;
  margin-top: 48px;
  font-size: 13px;
  color: var(--muted);
}
.pager button {
  padding: 8px 18px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: none;
  cursor: pointer;
}
.pager button:disabled { opacity: 0.4; cursor: not-allowed; }
@media (max-width: 900px) {
  .grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
