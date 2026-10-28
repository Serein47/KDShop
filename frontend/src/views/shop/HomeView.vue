<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchCategories, fetchProducts, fetchTags, type CategoryNode, type ProductCard, type TagGroups } from '@/api/catalog'
import ProductCardView from '@/components/ProductCardView.vue'

const categories = ref<CategoryNode[]>([])
const tags = ref<TagGroups>({ CRAFT: [], MATERIAL: [], OCCASION: [] })
const featured = ref<ProductCard[]>([])
const hot = ref<ProductCard[]>([])

const generalCategories = computed(() => categories.value.filter((c) => !c.featured))
const featuredCategories = computed(() => categories.value.filter((c) => c.featured))

onMounted(async () => {
  const [cats, tagGroups, latest, bestSelling] = await Promise.all([
    fetchCategories(),
    fetchTags(),
    fetchProducts({ sort: 'created_desc', size: 4 }),
    fetchProducts({ sort: 'sales_desc', size: 4 }),
  ])
  categories.value = cats
  tags.value = tagGroups
  featured.value = latest.items
  hot.value = bestSelling.items
})
</script>

<template>
  <div class="home">
    <section class="hero container">
      <div class="hero__text">
        <p class="eyebrow">Everyday Goods · Handmade Finds</p>
        <h1>把日常<br />挑得讲究些</h1>
        <p class="hero__lead">
          八大类目覆盖吃穿用度，另设手作专区：陶艺、木作、金工、织造。
          支持定制与工期交付，售后一路有人管。
        </p>
        <div class="hero__actions">
          <RouterLink to="/products" class="btn btn--solid">开始逛</RouterLink>
          <a href="#craft" class="btn btn--ghost">手作专区</a>
        </div>
      </div>
      <div class="hero__aside">
        <p class="eyebrow">热销</p>
        <p class="hero__aside-title">本周卖得好</p>
        <ul>
          <li v-for="item in hot.slice(0, 3)" :key="item.id">
            <RouterLink :to="`/products/${item.id}`">{{ item.name }}</RouterLink>
          </li>
        </ul>
      </div>
    </section>

    <section class="cats container">
      <div class="section-head">
        <h2>全部分类</h2>
        <p class="eyebrow">Categories</p>
      </div>
      <div class="cat-grid">
        <RouterLink
          v-for="cat in generalCategories"
          :key="cat.id"
          :to="{ path: '/products', query: { categoryId: cat.id } }"
          class="cat"
        >
          <span class="cat__name">{{ cat.name }}</span>
          <span class="cat__sub">{{ cat.children.map((c) => c.name).join(' / ') }}</span>
        </RouterLink>
      </div>
    </section>

    <section id="craft" class="special container">
      <div class="special__head">
        <div>
          <p class="eyebrow">Featured</p>
          <h2>手作与定制专区</h2>
        </div>
        <p class="special__note">限量编号、手工痕迹披露、支持刻字与工期交付</p>
      </div>
      <div class="special__grid">
        <RouterLink
          v-for="cat in featuredCategories"
          :key="cat.id"
          :to="{ path: '/products', query: { categoryId: cat.id } }"
          class="special__card"
        >
          <span class="special__name">{{ cat.name }}</span>
          <span class="special__sub">{{ cat.children.map((c) => c.name).join(' / ') }}</span>
          <span class="special__arrow">→</span>
        </RouterLink>
      </div>
    </section>

    <section class="craft container">
      <div class="section-head">
        <h2>按工艺</h2>
        <p class="eyebrow">Craft</p>
      </div>
      <div class="chips">
        <RouterLink
          v-for="tag in tags.CRAFT"
          :key="tag.slug"
          :to="{ path: '/products', query: { craft: tag.slug } }"
          class="chip"
        >{{ tag.name }}</RouterLink>
      </div>
    </section>

    <section class="featured container">
      <div class="section-head">
        <h2>新品上架</h2>
        <RouterLink to="/products" class="more">全部 →</RouterLink>
      </div>
      <div class="grid">
        <ProductCardView v-for="item in featured" :key="item.id" :product="item" />
      </div>
    </section>

    <section id="about" class="about container">
      <p class="about__quote">“日常将就一点没关系，挑的时候别将就。”</p>
      <p class="about__text">
        KDShop 是一个多商家商城：常规商品当日发货，手作与定制按工期交付，平台负责交易、售后与结算。
      </p>
    </section>
  </div>
</template>

<style scoped>
.hero {
  display: grid;
  grid-template-columns: 1.4fr 0.6fr;
  gap: 48px;
  padding: 88px 0 56px;
  align-items: end;
}
.hero h1 {
  font-size: clamp(40px, 6.4vw, 72px);
  line-height: 1.1;
  margin: 18px 0 22px;
}
.hero__lead { max-width: 480px; color: var(--ink-soft); margin: 0 0 30px; }
.hero__actions { display: flex; gap: 14px; }
.hero__aside { border-left: 1px solid var(--line); padding-left: 24px; }
.hero__aside-title { font-family: var(--serif); font-size: 22px; margin: 6px 0 14px; }
.hero__aside ul { list-style: none; padding: 0; margin: 0; }
.hero__aside li { padding: 8px 0; border-bottom: 1px dashed var(--line); font-size: 14px; }
.hero__aside li a:hover { color: var(--rust); }

.btn { display: inline-block; padding: 12px 26px; border-radius: 999px; font-size: 14px; transition: all 0.2s ease; }
.btn--solid { background: var(--ink); color: var(--paper); }
.btn--solid:hover { background: var(--rust); }
.btn--ghost { border: 1px solid var(--ink); }
.btn--ghost:hover { background: var(--ink); color: var(--paper); }

.section-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 18px; }
.section-head h2 { font-size: 28px; }
.more { font-size: 13px; color: var(--muted); }

.cats { margin: 20px auto 0; }
.cat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.cat {
  padding: 22px 20px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.42);
  transition: all 0.2s ease;
}
.cat:hover { border-color: var(--rust); transform: translateY(-2px); }
.cat__name { display: block; font-family: var(--serif); font-size: 19px; margin-bottom: 4px; }
.cat__sub { color: var(--muted); font-size: 12px; }

.special {
  margin-top: 72px;
  padding: 34px 36px;
  border: 1px solid var(--line);
  border-radius: 20px;
  background: linear-gradient(150deg, rgba(180, 82, 47, 0.06), rgba(108, 122, 92, 0.06));
}
.special__head { display: flex; justify-content: space-between; align-items: flex-end; gap: 24px; flex-wrap: wrap; }
.special__head h2 { font-size: 28px; margin-top: 6px; }
.special__note { color: var(--muted); font-size: 13px; margin: 0; }
.special__grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-top: 24px; }
.special__card {
  position: relative;
  padding: 22px 20px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid rgba(180, 82, 47, 0.25);
  transition: all 0.2s ease;
}
.special__card:hover { background: #fff; transform: translateY(-2px); }
.special__name { display: block; font-family: var(--serif); font-size: 20px; margin-bottom: 4px; }
.special__sub { color: var(--muted); font-size: 12px; }
.special__arrow { position: absolute; right: 18px; bottom: 16px; color: var(--rust); }

.craft { margin-top: 64px; }
.chips { display: flex; flex-wrap: wrap; gap: 10px; }
.chip {
  padding: 8px 18px;
  border: 1px solid var(--line);
  border-radius: 999px;
  font-size: 14px;
  background: rgba(255, 255, 255, 0.35);
  transition: all 0.2s ease;
}
.chip:hover { border-color: var(--rust); color: var(--rust); }

.featured { margin-top: 72px; }
.grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 26px; }

.about { margin-top: 96px; padding: 56px 0; border-top: 1px solid var(--line); text-align: center; }
.about__quote { font-family: var(--serif); font-size: clamp(22px, 3vw, 30px); margin: 0 0 14px; }
.about__text { color: var(--muted); margin: 0; }

@media (max-width: 900px) {
  .hero { grid-template-columns: 1fr; padding-top: 56px; }
  .hero__aside { border-left: 0; border-top: 1px solid var(--line); padding: 20px 0 0; }
  .cat-grid { grid-template-columns: repeat(2, 1fr); }
  .special__grid { grid-template-columns: 1fr; }
  .grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
