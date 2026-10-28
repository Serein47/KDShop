<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchCategories, fetchProducts, fetchTags, type CategoryNode, type ProductCard, type TagGroups } from '@/api/catalog'
import ProductCardView from '@/components/ProductCardView.vue'

const categories = ref<CategoryNode[]>([])
const tags = ref<TagGroups>({ CRAFT: [], MATERIAL: [], OCCASION: [] })
const featured = ref<ProductCard[]>([])

onMounted(async () => {
  const [cats, tagGroups, page] = await Promise.all([
    fetchCategories(),
    fetchTags(),
    fetchProducts({ sort: 'created_desc', size: 4 }),
  ])
  categories.value = cats
  tags.value = tagGroups
  featured.value = page.items
})
</script>

<template>
  <div class="home">
    <section class="hero container">
      <div class="hero__text">
        <p class="eyebrow">Handmade · Independent Design</p>
        <h1>把日子<br />过成手作</h1>
        <p class="hero__lead">
          陶瓷、木作、金工、织造。每一件都带着匠人的时间与手上的痕迹，
          我们把这些不完美，认真交到你手上。
        </p>
        <div class="hero__actions">
          <RouterLink to="/products" class="btn btn--solid">浏览全部作品</RouterLink>
          <a href="#craft" class="btn btn--ghost">按工艺逛</a>
        </div>
      </div>
      <div class="hero__aside">
        <p class="eyebrow">本期</p>
        <p class="hero__aside-title">限量与定制</p>
        <ul>
          <li v-for="item in featured.slice(0, 3)" :key="item.id">
            <RouterLink :to="`/products/${item.id}`">{{ item.name }}</RouterLink>
          </li>
        </ul>
      </div>
    </section>

    <section class="cats container">
      <RouterLink
        v-for="cat in categories"
        :key="cat.id"
        :to="{ path: '/products', query: { categoryId: cat.id } }"
        class="cat"
      >
        <span class="cat__name">{{ cat.name }}</span>
        <span class="cat__sub">{{ cat.children.map((c) => c.name).join(' / ') }}</span>
      </RouterLink>
    </section>

    <section id="craft" class="craft container">
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
      <div class="section-head section-head--mt">
        <h2>按场景</h2>
        <p class="eyebrow">Occasion</p>
      </div>
      <div class="chips">
        <RouterLink
          v-for="tag in tags.OCCASION"
          :key="tag.slug"
          :to="{ path: '/products', query: { occasion: tag.slug } }"
          class="chip chip--soft"
        >{{ tag.name }}</RouterLink>
      </div>
    </section>

    <section class="featured container">
      <div class="section-head">
        <h2>本期新作</h2>
        <RouterLink to="/products" class="more">全部 →</RouterLink>
      </div>
      <div class="grid">
        <ProductCardView v-for="item in featured" :key="item.id" :product="item" />
      </div>
    </section>

    <section id="about" class="about container">
      <p class="about__quote">“手作不是慢，是把时间放进去了。”</p>
      <p class="about__text">
        KDShop 是一个多商家市集：工作室可以接单定制、按工期交付，平台负责交易、售后与结算。
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
  font-size: clamp(44px, 7vw, 76px);
  line-height: 1.08;
  margin: 18px 0 22px;
}
.hero__lead {
  max-width: 460px;
  color: var(--ink-soft);
  margin: 0 0 30px;
}
.hero__actions { display: flex; gap: 14px; }
.hero__aside {
  border-left: 1px solid var(--line);
  padding-left: 24px;
}
.hero__aside-title {
  font-family: var(--serif);
  font-size: 22px;
  margin: 6px 0 14px;
}
.hero__aside ul { list-style: none; padding: 0; margin: 0; }
.hero__aside li { padding: 8px 0; border-bottom: 1px dashed var(--line); font-size: 14px; }
.hero__aside li a:hover { color: var(--rust); }

.btn {
  display: inline-block;
  padding: 12px 26px;
  border-radius: 999px;
  font-size: 14px;
  transition: all 0.2s ease;
}
.btn--solid { background: var(--ink); color: var(--paper); }
.btn--solid:hover { background: var(--rust); }
.btn--ghost { border: 1px solid var(--ink); }
.btn--ghost:hover { background: var(--ink); color: var(--paper); }

.cats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
  margin: 24px auto 72px;
}
.cat {
  padding: 26px 24px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.4);
  transition: all 0.2s ease;
}
.cat:hover { border-color: var(--rust); transform: translateY(-2px); }
.cat__name {
  display: block;
  font-family: var(--serif);
  font-size: 22px;
  margin-bottom: 6px;
}
.cat__sub { color: var(--muted); font-size: 12px; letter-spacing: 0.06em; }

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 18px;
}
.section-head--mt { margin-top: 44px; }
.section-head h2 { font-size: 28px; }
.more { font-size: 13px; color: var(--muted); }

.chips { display: flex; flex-wrap: wrap; gap: 10px; }
.chip {
  padding: 8px 18px;
  border: 1px solid var(--line);
  border-radius: 999px;
  font-size: 14px;
  transition: all 0.2s ease;
  background: rgba(255, 255, 255, 0.35);
}
.chip:hover { border-color: var(--rust); color: var(--rust); }
.chip--soft { border-style: dashed; }

.featured { margin-top: 76px; }
.grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 26px;
}
.about {
  margin-top: 96px;
  padding: 56px 0;
  border-top: 1px solid var(--line);
  text-align: center;
}
.about__quote {
  font-family: var(--serif);
  font-size: clamp(22px, 3vw, 32px);
  margin: 0 0 14px;
}
.about__text { color: var(--muted); margin: 0; }

@media (max-width: 900px) {
  .hero { grid-template-columns: 1fr; padding-top: 56px; }
  .hero__aside { border-left: 0; border-top: 1px solid var(--line); padding: 20px 0 0; }
  .cats { grid-template-columns: 1fr; }
  .grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
