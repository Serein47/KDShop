<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { fetchCategories, fetchProducts, fetchTags, type CategoryNode, type ProductCard, type TagGroups } from '@/api/catalog'
import ProductCardView from '@/components/ProductCardView.vue'

const route = useRoute()
const router = useRouter()

const products = ref<ProductCard[]>([])
const total = ref(0)
const loading = ref(false)
const categories = ref<CategoryNode[]>([])
const tags = ref<TagGroups>({ CRAFT: [], MATERIAL: [], OCCASION: [] })

const generalCategories = computed(() => categories.value.filter((c) => !c.featured))
const featuredCategories = computed(() => categories.value.filter((c) => c.featured))

const page = computed(() => Number(route.query.page ?? 1))
const size = 12

const query = computed(() => ({
  keyword: (route.query.keyword as string) || undefined,
  categoryId: route.query.categoryId ? Number(route.query.categoryId) : undefined,
  craft: (route.query.craft as string) || undefined,
  material: (route.query.material as string) || undefined,
  occasion: (route.query.occasion as string) || undefined,
  sort: (route.query.sort as string) || 'sales_desc',
  page: page.value,
  size,
}))

const activeFilters = computed(() => {
  const result: { label: string; patch: Record<string, string | undefined> }[] = []
  if (query.value.keyword) result.push({ label: `关键词：${query.value.keyword}`, patch: { keyword: undefined } })
  if (query.value.categoryId) {
    const name = findCategory(query.value.categoryId)
    result.push({ label: `品类：${name}`, patch: { categoryId: undefined } })
  }
  for (const key of ['craft', 'material', 'occasion'] as const) {
    const slug = query.value[key]
    if (!slug) continue
    const hit = tags.value[key.toUpperCase() as 'CRAFT' | 'MATERIAL' | 'OCCASION']?.find((t) => t.slug === slug)
    result.push({ label: hit ? hit.name : slug, patch: { [key]: undefined } })
  }
  return result
})

watch(
  () => route.query,
  async () => {
    loading.value = true
    try {
      const result = await fetchProducts(query.value)
      products.value = result.items
      total.value = result.total
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

fetchCategories().then((value) => (categories.value = value))
fetchTags().then((value) => (tags.value = value))

function findCategory(id: number): string {
  for (const top of categories.value) {
    if (top.id === id) return top.name
    const child = top.children.find((c) => c.id === id)
    if (child) return `${top.name} / ${child.name}`
  }
  return String(id)
}

function apply(patch: Record<string, string | number | undefined>) {
  const next: Record<string, string> = {}
  for (const [k, v] of Object.entries({ ...route.query, ...patch })) {
    if (v !== undefined && v !== null && v !== '') next[k] = String(v)
  }
  delete next.page
  router.push({ path: '/products', query: next })
}

function toggle(key: 'craft' | 'material' | 'occasion', slug: string) {
  apply({ [key]: route.query[key] === slug ? undefined : slug })
}

function goPage(target: number) {
  router.push({ path: '/products', query: { ...route.query, page: String(target) } })
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))
</script>

<template>
  <div class="list container">
    <aside class="rail">
      <section class="rail__block">
        <h3>全部分类</h3>
        <ul>
          <li v-for="top in generalCategories" :key="top.id">
            <button class="link" :class="{ on: query.categoryId === top.id }" @click="apply({ categoryId: top.id })">
              {{ top.name }}
            </button>
            <ul class="sub">
              <li v-for="child in top.children" :key="child.id">
                <button class="link link--sm" :class="{ on: query.categoryId === child.id }" @click="apply({ categoryId: child.id })">
                  {{ child.name }}
                </button>
              </li>
            </ul>
          </li>
        </ul>
      </section>

      <section class="rail__block rail__block--special">
        <h3>手作专区</h3>
        <ul>
          <li v-for="top in featuredCategories" :key="top.id">
            <button class="link" :class="{ on: query.categoryId === top.id }" @click="apply({ categoryId: top.id })">
              {{ top.name }}
            </button>
            <ul class="sub">
              <li v-for="child in top.children" :key="child.id">
                <button class="link link--sm" :class="{ on: query.categoryId === child.id }" @click="apply({ categoryId: child.id })">
                  {{ child.name }}
                </button>
              </li>
            </ul>
          </li>
        </ul>
      </section>

      <section v-for="group in (['CRAFT', 'MATERIAL', 'OCCASION'] as const)" :key="group" class="rail__block">
        <h3>{{ group === 'CRAFT' ? '工艺' : group === 'MATERIAL' ? '材质' : '场景' }}</h3>
        <div class="chips">
          <button
            v-for="tag in tags[group]"
            :key="tag.slug"
            class="chip"
            :class="{ on: route.query[group.toLowerCase()] === tag.slug }"
            @click="toggle(group.toLowerCase() as 'craft' | 'material' | 'occasion', tag.slug)"
          >{{ tag.name }}</button>
        </div>
      </section>
    </aside>

    <section class="main">
      <div class="toolbar">
        <p class="count">
          共 <strong>{{ total }}</strong> 件商品
          <span v-if="activeFilters.length" class="active">
            <button v-for="f in activeFilters" :key="f.label" class="chip chip--active" @click="apply(f.patch)">
              {{ f.label }} ×
            </button>
            <RouterLink to="/products" class="clear">清空</RouterLink>
          </span>
        </p>
        <label class="sort">
          排序
          <select :value="query.sort" @change="apply({ sort: ($event.target as HTMLSelectElement).value })">
            <option value="sales_desc">人气</option>
            <option value="created_desc">最新</option>
            <option value="price_asc">价格从低到高</option>
            <option value="price_desc">价格从高到低</option>
          </select>
        </label>
      </div>

      <div v-if="loading" class="state">正在翻找…</div>
      <div v-else-if="!products.length" class="state">
        没有找到商品，换个分类或筛选试试。
      </div>
      <div v-else class="grid">
        <ProductCardView v-for="item in products" :key="item.id" :product="item" />
      </div>

      <div v-if="totalPages > 1" class="pager">
        <button class="btn" :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
        <span>{{ page }} / {{ totalPages }}</span>
        <button class="btn" :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.list {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 48px;
  padding: 48px 0 0;
}
.rail__block { margin-bottom: 34px; }
.rail__block--special h3 { color: var(--rust); }
.rail__block h3 {
  font-size: 15px;
  margin-bottom: 12px;
  letter-spacing: 0.08em;
}
.rail ul { list-style: none; margin: 0; padding: 0; }
.rail .sub { margin: 4px 0 0 12px; }
.link {
  background: none;
  border: 0;
  padding: 3px 0;
  color: var(--ink-soft);
  font-size: 14px;
  cursor: pointer;
  text-align: left;
}
.link:hover, .link.on { color: var(--rust); }
.link--sm { font-size: 13px; color: var(--muted); }
.chips { display: flex; flex-wrap: wrap; gap: 8px; }
.chip {
  padding: 5px 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: none;
  font-size: 13px;
  cursor: pointer;
  color: var(--ink-soft);
  transition: all 0.15s;
}
.chip:hover { border-color: var(--rust); }
.chip.on, .chip--active {
  background: var(--ink);
  border-color: var(--ink);
  color: var(--paper);
}
.chip--active { cursor: pointer; }

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 26px;
  gap: 16px;
  flex-wrap: wrap;
}
.count { margin: 0; color: var(--muted); font-size: 14px; }
.count strong { color: var(--ink); }
.active { display: inline-flex; gap: 8px; margin-left: 12px; flex-wrap: wrap; }
.clear { font-size: 12px; color: var(--muted); text-decoration: underline; }
.sort { font-size: 13px; color: var(--muted); display: flex; align-items: center; gap: 8px; }
.sort select {
  padding: 6px 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: transparent;
  font-size: 13px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 26px;
}
.state {
  padding: 80px 0;
  text-align: center;
  color: var(--muted);
  font-family: var(--serif);
  font-size: 17px;
}
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  margin-top: 46px;
  font-size: 13px;
  color: var(--muted);
}
.btn {
  padding: 8px 18px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: none;
  cursor: pointer;
  font-size: 13px;
}
.btn:disabled { opacity: 0.4; cursor: not-allowed; }
@media (max-width: 900px) {
  .list { grid-template-columns: 1fr; }
  .grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
