<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { yuan, type ProductCard } from '@/api/catalog'
import ProductMedia from './ProductMedia.vue'

const props = defineProps<{ product: ProductCard }>()

const badge = computed(() => {
  if (props.product.uniquePiece) return '仅此一件'
  if (props.product.editionSize) return `限量 ${props.product.editionSize} 件`
  if (props.product.saleMode === 'CUSTOM') return `定制 · 工期 ${props.product.leadDays} 天`
  if (props.product.saleMode === 'PREORDER') return `预售 · 预计 ${props.product.leadDays} 天发货`
  return ''
})

const deposit = computed(() => {
  const rate = props.product.depositRate
  if (!rate || props.product.saleMode === 'IN_STOCK') return ''
  return `定金 ${rate / 100}%`
})

const price = computed(() => {
  const min = yuan(props.product.minPrice)
  return props.product.maxPrice && props.product.maxPrice !== props.product.minPrice ? `¥${min} 起` : `¥${min}`
})
</script>

<template>
  <RouterLink :to="`/products/${product.id}`" class="card">
    <div class="card__media">
      <ProductMedia :name="product.name" :image="product.mainImage" :seed="product.id" />
      <span v-if="badge" class="card__badge">{{ badge }}</span>
      <span v-if="deposit" class="card__deposit">{{ deposit }}</span>
    </div>
    <div class="card__body">
      <h3 class="card__name">{{ product.name }}</h3>
      <p class="card__sub">{{ product.subtitle || ' ' }}</p>
      <div class="card__foot">
        <span class="card__price">{{ price }}</span>
        <span class="card__sales">已售 {{ product.salesCount }}</span>
      </div>
      <p v-if="product.shopName" class="card__shop">{{ product.shopName }}</p>
    </div>
  </RouterLink>
</template>

<style scoped>
.card {
  display: block;
  background: var(--card);
  border-radius: var(--radius);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
  transition: transform .22s ease, box-shadow .22s ease;
}
.card:hover { transform: translateY(-5px); box-shadow: var(--shadow); }
.card__media { position: relative; }
.card__media :deep(.media) { border-radius: 0; }
.card__badge {
  position: absolute;
  left: 10px;
  top: 10px;
  max-width: calc(100% - 20px);
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--rust);
  color: #fff;
  font-size: 11px;
  letter-spacing: 0.04em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  box-shadow: 0 4px 10px rgba(217, 72, 15, 0.3);
}
.card__deposit {
  position: absolute;
  right: 10px;
  bottom: 10px;
  padding: 3px 9px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.94);
  color: var(--rust);
  font-size: 11px;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}
.card__body { padding: 14px 16px 16px; }
.card__name {
  font-size: 16px;
  margin: 0 0 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.card__sub {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.card__foot { display: flex; align-items: baseline; justify-content: space-between; }
.card__price {
  font-size: 19px;
  font-weight: 700;
  color: var(--rust);
  font-family: var(--sans);
}
.card__sales { font-size: 11px; color: var(--muted); }
.card__shop { margin: 6px 0 0; font-size: 11px; color: var(--muted); }
</style>
