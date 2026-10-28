<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { yuan, type ProductCard } from '@/api/catalog'
import ProductMedia from './ProductMedia.vue'

const props = defineProps<{ product: ProductCard }>()

const badge = computed(() => {
  if (props.product.uniquePiece) return '仅此一件'
  if (props.product.editionSize) return `限量 ${props.product.editionSize} 件`
  if (props.product.saleMode === 'CUSTOM') return `定制 · ${props.product.leadDays} 天`
  if (props.product.saleMode === 'PREORDER') return `预售 · ${props.product.leadDays} 天`
  return ''
})

const price = computed(() => {
  const min = yuan(props.product.minPrice)
  return props.product.maxPrice && props.product.maxPrice !== props.product.minPrice
    ? `¥${min} 起`
    : `¥${min}`
})
</script>

<template>
  <RouterLink :to="`/products/${product.id}`" class="card">
    <ProductMedia :name="product.name" :image="product.mainImage" :seed="product.id" />
    <div class="card__body">
      <p class="card__badge" :class="{ 'is-empty': !badge }">{{ badge || '·' }}</p>
      <h3 class="card__name">{{ product.name }}</h3>
      <p v-if="product.subtitle" class="card__sub">{{ product.subtitle }}</p>
      <div class="card__foot">
        <span class="card__price">{{ price }}</span>
        <span v-if="product.shopName" class="card__shop">{{ product.shopName }}</span>
      </div>
    </div>
  </RouterLink>
</template>

<style scoped>
.card {
  display: block;
  transition: transform 0.25s ease;
}
.card:hover { transform: translateY(-4px); }
.card__body { padding: 14px 4px 0; }
.card__badge.is-empty { visibility: hidden; }
.card__badge {
  display: inline-block;
  min-height: 20px;
  visibility: visible;
  margin: 0 0 6px;
  padding: 2px 8px;
  border: 1px solid var(--line);
  border-radius: 999px;
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--rust);
  background: rgba(255, 255, 255, 0.5);
}
.card__name {
  font-size: 17px;
  margin: 0 0 4px;
}
.card__sub {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}
.card__foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 10px;
}
.card__price {
  font-family: var(--serif);
  font-size: 16px;
  color: var(--ink);
}
.card__shop {
  font-size: 12px;
  color: var(--muted);
}
</style>
