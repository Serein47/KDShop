<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { fetchProduct, yuan, type ProductDetail } from '@/api/catalog'
import ProductMedia from '@/components/ProductMedia.vue'

const route = useRoute()
const product = ref<ProductDetail | null>(null)
const failed = ref(false)
const skuId = ref<number | null>(null)
const optionValues = ref<Record<number, string>>({})
const flawAck = ref(false)
const qty = ref(1)

const sku = computed(() => product.value?.skus.find((s) => s.id === skuId.value) ?? product.value?.skus[0])

const optionDelta = computed(() =>
  (product.value?.options ?? []).reduce((sum, o) => {
    if (!optionValues.value[o.id]) return sum
    return sum + (o.priceDelta || 0)
  }, 0),
)

const price = computed(() => (sku.value ? sku.value.price + optionDelta.value : 0))

const canBuy = computed(() => {
  if (!product.value) return false
  if (product.value.requiresFlawAck && !flawAck.value) return false
  const missing = product.value.options.filter((o) => o.required && !optionValues.value[o.id])
  if (missing.length) return false
  return (sku.value?.stock ?? 0) > 0
})

const flatTags = computed(() => Object.values(product.value?.tags ?? {}).flat())

const saleText = computed(() => {
  if (!product.value) return ''
  if (product.value.saleMode === 'CUSTOM') return `接单定制 · 工期约 ${product.value.leadDays} 天`
  if (product.value.saleMode === 'PREORDER') return `预售 · 付定金后 ${product.value.leadDays} 天发货`
  return '现货 · 48 小时内发出'
})

onMounted(async () => {
  try {
    product.value = await fetchProduct(route.params.id as string)
    skuId.value = product.value.skus[0]?.id ?? null
  } catch {
    failed.value = true
  }
})
</script>

<template>
  <div class="detail container">
    <div v-if="failed" class="state">这件作品可能已经下架了。<RouterLink to="/products">去看看别的</RouterLink></div>

    <template v-else-if="product">
      <nav class="crumb">
        <RouterLink to="/products">全部商品</RouterLink>
        <span>/</span>
        <RouterLink :to="{ path: '/products', query: { categoryId: product.categoryId } }">
          {{ product.categoryName }}
        </RouterLink>
      </nav>

      <div class="body">
        <div class="gallery">
          <ProductMedia :name="product.name" :image="product.mainImage" :seed="product.id" ratio="4 / 5" />
          <div class="meta-strip">
            <span>{{ saleText }}</span>
            <span v-if="product.editionSize">限量 {{ product.editionSize }} 件 · 第 {{ product.editionNo }} 件</span>
            <span v-if="product.uniquePiece">仅此一件</span>
          </div>
        </div>

        <div class="info">
          <p class="eyebrow">{{ product.shop.shopName }}</p>
          <h1>{{ product.name }}</h1>
          <p class="sub">{{ product.subtitle }}</p>

          <div class="price">
            <span class="price__now">¥{{ yuan(price) }}</span>
            <span v-if="sku?.originalPrice && sku.originalPrice > sku.price" class="price__old">
              ¥{{ yuan(sku.originalPrice) }}
            </span>
          </div>

          <section class="block">
            <h3>选择</h3>
            <div class="skus">
              <button
                v-for="item in product.skus"
                :key="item.id"
                class="sku"
                :class="{ on: sku?.id === item.id, off: item.stock <= 0 }"
                :disabled="item.stock <= 0"
                @click="skuId = item.id"
              >
                <span>{{ Object.values(item.spec).join(' · ') || '标准' }}</span>
                <small v-if="item.stock <= 0">售罄</small>
                <small v-else>库存 {{ item.stock }}</small>
              </button>
            </div>
          </section>

          <section v-if="product.options.length" class="block">
            <h3>定制</h3>
            <div v-for="opt in product.options" :key="opt.id" class="opt">
              <label :for="`opt-${opt.id}`">
                {{ opt.name }}
                <em v-if="opt.required">必填</em>
                <em v-if="opt.priceDelta">+¥{{ yuan(opt.priceDelta) }}</em>
              </label>
              <input
                v-if="opt.type === 'TEXT'"
                :id="`opt-${opt.id}`"
                v-model="optionValues[opt.id]"
                :maxlength="opt.maxLength || 12"
                :placeholder="`最多 ${opt.maxLength || 12} 字`"
              />
              <select v-else :id="`opt-${opt.id}`" v-model="optionValues[opt.id]">
                <option value="">请选择</option>
                <option v-for="choice in opt.choices" :key="choice" :value="choice">{{ choice }}</option>
              </select>
            </div>
          </section>

          <section v-if="product.requiresFlawAck" class="flaw">
            <p class="flaw__note">{{ product.flawNote }}</p>
            <label class="ack">
              <input v-model="flawAck" type="checkbox" />
              我已了解手作痕迹，接受它可能与图片略有不同
            </label>
          </section>

          <div class="buy">
            <div class="qty">
              <button :disabled="qty <= 1" @click="qty--">−</button>
              <span>{{ qty }}</span>
              <button :disabled="qty >= (sku?.stock ?? 1)" @click="qty++">+</button>
            </div>
            <button class="cta" :disabled="!canBuy">加入购物车</button>
          </div>
          <p class="hint">购物车与下单将在下一版开放，当前可先浏览与筛选。</p>
        </div>
      </div>

      <section v-if="product.story" class="story">
        <p class="eyebrow">About the piece</p>
        <h2>关于这件作品</h2>
        <p class="story__text">{{ product.story }}</p>
      </section>

      <section class="extra">
        <div class="tags">
          <RouterLink
            v-for="tag in flatTags"
            :key="tag.slug"
            :to="{ path: '/products', query: { craft: tag.slug } }"
            class="chip"
          >{{ tag.name }}</RouterLink>
        </div>
        <RouterLink :to="`/shops/${product.shop.id}`" class="shop-card">
          <span class="eyebrow">工作室</span>
          <strong>{{ product.shop.shopName }}</strong>
          <span>进入店铺 →</span>
        </RouterLink>
      </section>

      <section v-if="product.description" class="desc" v-html="product.description" />
    </template>
  </div>
</template>

<style scoped>
.detail { padding-top: 40px; }
.crumb { display: flex; gap: 8px; font-size: 13px; color: var(--muted); margin-bottom: 26px; }
.body { display: grid; grid-template-columns: 1fr 1fr; gap: 64px; align-items: start; }
.meta-strip {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 14px;
  font-size: 12px;
  color: var(--muted);
  letter-spacing: 0.04em;
}
.info h1 { font-size: clamp(30px, 4vw, 42px); margin: 8px 0 6px; }
.sub { color: var(--muted); margin: 0 0 18px; }
.price { display: flex; align-items: baseline; gap: 12px; margin-bottom: 30px; }
.price__now { font-family: var(--serif); font-size: 28px; }
.price__old { color: var(--muted); text-decoration: line-through; font-size: 14px; }
.block { margin-bottom: 26px; }
.block h3 { font-size: 14px; letter-spacing: 0.1em; color: var(--muted); margin-bottom: 10px; }
.skus { display: flex; gap: 10px; flex-wrap: wrap; }
.sku {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 10px 16px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: none;
  cursor: pointer;
  font-size: 14px;
}
.sku small { color: var(--muted); font-size: 11px; }
.sku.on { border-color: var(--ink); background: rgba(33, 27, 20, 0.05); }
.sku.off { opacity: 0.45; cursor: not-allowed; }
.opt { margin-bottom: 12px; }
.opt label { display: block; font-size: 13px; margin-bottom: 6px; }
.opt em { font-style: normal; color: var(--rust); font-size: 12px; margin-left: 6px; }
.opt input, .opt select {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.6);
  font-size: 14px;
}
.flaw {
  border: 1px dashed var(--line);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 24px;
  background: rgba(255, 255, 255, 0.35);
}
.flaw__note { margin: 0 0 10px; color: var(--ink-soft); font-size: 14px; }
.ack { display: flex; gap: 8px; align-items: flex-start; font-size: 13px; color: var(--muted); }
.buy { display: flex; gap: 14px; align-items: center; }
.qty {
  display: flex;
  align-items: center;
  border: 1px solid var(--line);
  border-radius: 999px;
  overflow: hidden;
}
.qty button {
  width: 36px;
  height: 40px;
  border: 0;
  background: none;
  font-size: 16px;
  cursor: pointer;
}
.qty span { width: 34px; text-align: center; font-size: 14px; }
.cta {
  flex: 1;
  height: 44px;
  border: 0;
  border-radius: 999px;
  background: var(--ink);
  color: var(--paper);
  font-size: 15px;
  cursor: pointer;
  transition: background 0.2s;
}
.cta:hover:not(:disabled) { background: var(--rust-dark); transform: translateY(-1px); }
.cta:disabled { opacity: 0.55; cursor: not-allowed; box-shadow: none; }
.hint { font-size: 12px; color: var(--muted); margin-top: 10px; }
.story { margin-top: 88px; border-top: 1px solid var(--line); padding-top: 40px; max-width: 760px; }
.story h2 { font-size: 26px; margin: 6px 0 16px; }
.story__text { color: var(--ink-soft); line-height: 2; }
.extra {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  margin-top: 48px;
  flex-wrap: wrap;
}
.tags { display: flex; gap: 8px; flex-wrap: wrap; }
.chip {
  padding: 6px 14px;
  border: 1px solid var(--line);
  border-radius: 999px;
  font-size: 13px;
}
.chip:hover { border-color: var(--rust); color: var(--rust); }
.shop-card {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 16px 22px;
  border: 1px solid var(--line);
  border-radius: 12px;
  min-width: 180px;
}
.shop-card strong { font-family: var(--serif); font-size: 17px; }
.shop-card span:last-child { font-size: 12px; color: var(--muted); }
.desc { margin-top: 40px; color: var(--ink-soft); max-width: 760px; }
.state { padding: 120px 0; text-align: center; color: var(--muted); }
@media (max-width: 900px) {
  .body { grid-template-columns: 1fr; gap: 32px; }
}
</style>
