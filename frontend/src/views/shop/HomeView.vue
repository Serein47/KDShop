<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchCategories, fetchProducts, fetchTags, type CategoryNode, type ProductCard, type TagGroups } from '@/api/catalog'
import ProductCardView from '@/components/ProductCardView.vue'
import ProductMedia from '@/components/ProductMedia.vue'

const categories = ref<CategoryNode[]>([])
const tags = ref<TagGroups>({ CRAFT: [], MATERIAL: [], OCCASION: [] })
const latest = ref<ProductCard[]>([])
const hot = ref<ProductCard[]>([])
const total = ref(0)

const generalCategories = computed(() => categories.value.filter((c) => !c.featured))
const featuredCategories = computed(() => categories.value.filter((c) => c.featured))

onMounted(async () => {
  const [cats, tagGroups, newest, best, all] = await Promise.all([
    fetchCategories(),
    fetchTags(),
    fetchProducts({ sort: 'created_desc', size: 4 }),
    fetchProducts({ sort: 'sales_desc', size: 4 }),
    fetchProducts({ size: 1 }),
  ])
  categories.value = cats
  tags.value = tagGroups
  latest.value = newest.items
  hot.value = best.items
  total.value = all.total
})
</script>

<template>
  <div class="home">
    <section class="hero container">
      <div class="hero__text">
        <p class="eyebrow">Everyday Goods · Curated</p>
        <h1>把日常<br />挑得讲究些</h1>
        <p class="hero__lead">
          八大类目覆盖吃穿用度，另有手作与定制专区：限量编号、瑕疵披露、定金工期，
          售后一路有人管。
        </p>
        <div class="hero__actions">
          <RouterLink to="/products" class="btn btn--solid">开始逛 →</RouterLink>
          <a href="#craft" class="btn btn--ghost">手作专区</a>
        </div>
        <div class="hero__stats">
          <div><strong>{{ total }}</strong><span>件在售</span></div>
          <div><strong>8</strong><span>大类目</span></div>
          <div><strong>3</strong><span>手作专区</span></div>
        </div>
      </div>

      <div class="hero__mosaic">
        <RouterLink
          v-for="(item, index) in latest.slice(0, 3)"
          :key="item.id"
          :to="`/products/${item.id}`"
          class="tile"
          :class="`tile--${index}`"
        >
          <ProductMedia :name="item.name" :image="item.mainImage" :seed="item.id" ratio="1 / 1" />
          <span class="tile__label">{{ item.name }}</span>
        </RouterLink>
      </div>
    </section>

    <section class="cats container">
      <div class="section-head">
        <div>
          <p class="eyebrow">Categories</p>
          <h2>全部分类</h2>
        </div>
        <RouterLink to="/products" class="more">查看全部 →</RouterLink>
      </div>
      <div class="cat-grid">
        <RouterLink
          v-for="(cat, index) in generalCategories"
          :key="cat.id"
          :to="{ path: '/products', query: { categoryId: cat.id } }"
          class="cat"
          :class="`cat--tint-${index % 4}`"
        >
          <span class="cat__name">{{ cat.name }}</span>
          <span class="cat__sub">{{ cat.children.map((c) => c.name).join(' / ') }}</span>
          <span class="cat__arrow">→</span>
        </RouterLink>
      </div>
    </section>

    <section id="craft" class="craft">
      <div class="container craft__inner">
        <div class="craft__head">
          <div>
            <p class="eyebrow">Featured Zone</p>
            <h2>手作与定制专区</h2>
          </div>
          <p class="craft__note">限量编号 · 手工痕迹披露 · 支持刻字与工期交付</p>
        </div>

        <div class="craft__grid">
          <RouterLink
            v-for="cat in featuredCategories"
            :key="cat.id"
            :to="{ path: '/products', query: { categoryId: cat.id } }"
            class="craft__card"
          >
            <span class="craft__name">{{ cat.name }}</span>
            <span class="craft__sub">{{ cat.children.map((c) => c.name).join(' / ') }}</span>
            <span class="craft__arrow">→</span>
          </RouterLink>
        </div>

        <div class="craft__tags">
          <RouterLink
            v-for="tag in tags.CRAFT"
            :key="tag.slug"
            :to="{ path: '/products', query: { craft: tag.slug } }"
            class="tag"
          >{{ tag.name }}</RouterLink>
        </div>
      </div>
    </section>

    <section class="featured container">
      <div class="section-head">
        <div>
          <p class="eyebrow">New Arrivals</p>
          <h2>新品上架</h2>
        </div>
        <RouterLink to="/products" class="more">全部商品 →</RouterLink>
      </div>
      <div class="grid">
        <ProductCardView v-for="item in latest" :key="item.id" :product="item" />
      </div>
    </section>

    <section class="hot container">
      <div class="section-head">
        <div>
          <p class="eyebrow">Best Sellers</p>
          <h2>本周热销</h2>
        </div>
      </div>
      <div class="grid">
        <ProductCardView v-for="item in hot" :key="item.id" :product="item" />
      </div>
    </section>

    <section id="about" class="about container">
      <div class="about__card">
        <p class="about__quote">“日常将就一点没关系，挑的时候别将就。”</p>
        <p class="about__text">
          KDShop 是一个多商家商城：常规商品备货发出，手作与定制按工期交付，
          平台负责交易、售后与结算。
        </p>
        <RouterLink to="/products" class="btn btn--solid">去逛逛</RouterLink>
      </div>
    </section>
  </div>
</template>

<style scoped>
.hero {
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  gap: 56px;
  align-items: center;
  padding: 72px 0 40px;
}
.hero h1 {
  font-size: clamp(42px, 5.6vw, 66px);
  line-height: 1.1;
  margin: 14px 0 18px;
}
.hero__lead { max-width: 460px; color: var(--ink-soft); margin: 0 0 26px; }
.hero__actions { display: flex; gap: 12px; }
.hero__stats {
  display: flex;
  gap: 36px;
  margin-top: 40px;
  padding-top: 22px;
  border-top: 1px solid var(--line);
}
.hero__stats strong {
  display: block;
  font-size: 26px;
  font-family: var(--serif);
  color: var(--ink);
}
.hero__stats span { font-size: 12px; color: var(--muted); letter-spacing: 0.06em; }

.hero__mosaic {
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  grid-template-rows: 1fr 1fr;
  grid-template-areas:
    "big side-a"
    "big side-c";
  gap: 14px;
  height: 560px;
}
.tile--0 { grid-area: big; }
.tile--1 { grid-area: side-a; }
.tile--2 { grid-area: side-c; }
.tile::after {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent 55%, rgba(25, 21, 18, 0.45));
  pointer-events: none;
}
.tile {
  position: relative;
  border-radius: 18px;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
  transition: transform .25s ease, box-shadow .25s ease;
}
.tile:hover { transform: translateY(-4px); box-shadow: var(--shadow); }
.tile :deep(.media) { border-radius: 18px; height: 100%; width: 100%; aspect-ratio: auto; }
.tile__label {
  position: absolute;
  left: 14px;
  bottom: 12px;
  color: #fff;
  font-size: 13px;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.35);
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 22px;
  gap: 16px;
}
.section-head h2 { font-size: 28px; margin-top: 6px; }
.more { font-size: 13px; color: var(--muted); }
.more:hover { color: var(--rust); }

.cats { margin-top: 56px; }
.cat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.cat {
  position: relative;
  padding: 22px 20px;
  border-radius: 14px;
  background: #fff;
  box-shadow: var(--shadow-sm);
  transition: transform .2s ease, box-shadow .2s ease;
  border: 1px solid transparent;
}
.cat:hover { transform: translateY(-3px); box-shadow: var(--shadow); border-color: rgba(217, 72, 15, 0.25); }
.cat--tint-0 { background: #fdf0e8; }
.cat--tint-1 { background: #f2f5ec; }
.cat--tint-2 { background: #fbf4e6; }
.cat--tint-3 { background: #f4f1ea; }
.cat__name { display: block; font-family: var(--serif); font-size: 19px; margin-bottom: 4px; }
.cat__sub { color: var(--muted); font-size: 12px; }
.cat__arrow { position: absolute; right: 16px; top: 20px; color: var(--rust); opacity: 0; transition: opacity .2s; }
.cat:hover .cat__arrow { opacity: 1; }

.craft {
  margin-top: 72px;
  background: var(--forest);
  color: #f4ecdd;
  padding: 54px 0 48px;
}
.craft__head { display: flex; justify-content: space-between; align-items: flex-end; gap: 24px; flex-wrap: wrap; }
.craft__head h2 { font-size: 30px; margin-top: 8px; color: #fdf8ef; }
.craft__note { color: rgba(244, 236, 221, 0.7); font-size: 13px; margin: 0; }
.craft__grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-top: 28px; }
.craft__card {
  position: relative;
  padding: 24px 22px;
  border-radius: 14px;
  border: 1px solid rgba(244, 236, 221, 0.22);
  background: rgba(255, 255, 255, 0.04);
  transition: all .2s ease;
}
.craft__card:hover { background: rgba(255, 255, 255, 0.1); border-color: rgba(244, 236, 221, 0.5); }
.craft__name { display: block; font-family: var(--serif); font-size: 20px; margin-bottom: 4px; }
.craft__sub { font-size: 12px; color: rgba(244, 236, 221, 0.65); }
.craft__arrow { position: absolute; right: 18px; bottom: 16px; color: #f0a882; }
.craft__tags { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 26px; }
.tag {
  padding: 7px 16px;
  border-radius: 999px;
  border: 1px solid rgba(244, 236, 221, 0.28);
  font-size: 13px;
  color: rgba(244, 236, 221, 0.9);
}
.tag:hover { background: rgba(255, 255, 255, 0.12); }

.featured { margin-top: 72px; }
.hot { margin-top: 64px; }
.grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 22px; }

.about { margin-top: 88px; }
.about__card {
  text-align: center;
  padding: 54px 24px;
  border-radius: 22px;
  background: linear-gradient(160deg, #fdf1e7, #f3ede1);
}
.about__quote { font-family: var(--serif); font-size: clamp(22px, 3vw, 30px); margin: 0 0 12px; }
.about__text { color: var(--muted); margin: 0 0 22px; }

@media (max-width: 900px) {
  .hero { grid-template-columns: 1fr; gap: 32px; padding-top: 40px; }
  .cat-grid { grid-template-columns: repeat(2, 1fr); }
  .craft__grid { grid-template-columns: 1fr; }
  .grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
