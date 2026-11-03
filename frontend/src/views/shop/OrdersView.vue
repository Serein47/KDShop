<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchOrders, type OrderPage } from '@/api/order'
import { useAuthStore } from '@/stores/auth'
import { yuan } from '@/api/catalog'
import ProductMedia from '@/components/ProductMedia.vue'
import { formatDateTime, orderStatusLabel, orderStatusTone } from '@/utils/order'

const auth = useAuthStore()
const router = useRouter()

const tabs = [
  { label: '全部', value: '' },
  { label: '待付款', value: 'PENDING_PAYMENT' },
  { label: '待发货', value: 'PAID' },
  { label: '已发货', value: 'SHIPPED' },
  { label: '已完成', value: 'COMPLETED' },
]

const active = ref('')
const page = ref(1)
const size = 10
const data = ref<OrderPage | null>(null)
const loading = ref(false)
const failed = ref(false)
const orders = computed(() => data.value?.items ?? [])
const total = computed(() => data.value?.total ?? 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

onMounted(async () => {
  if (!auth.ready) await auth.fetchMe()
  if (!auth.user) {
    router.replace({ path: '/login', query: { redirect: '/orders' } })
    return
  }
  await load()
})

async function load() {
  loading.value = true
  failed.value = false
  try {
    data.value = await fetchOrders({
      status: active.value || undefined,
      page: page.value,
      size,
    })
  } catch (e: any) {
    if (e?.response?.status === 401) {
      router.replace({ path: '/login', query: { redirect: '/orders' } })
      return
    }
    failed.value = true
    ElMessage.error(e?.response?.data?.message || '订单加载失败')
  } finally {
    loading.value = false
  }
}

async function selectTab(value: string) {
  if (active.value === value) return
  active.value = value
  page.value = 1
  await load()
}

async function goPage(next: number) {
  if (next < 1 || next > totalPages.value) return
  page.value = next
  await load()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<template>
  <div class="orders container">
    <header class="head">
      <div>
        <p class="eyebrow">My orders</p>
        <h1>我的订单</h1>
      </div>
      <RouterLink to="/products" class="more">继续购物 →</RouterLink>
    </header>

    <nav class="tabs" aria-label="按订单状态筛选">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        class="tab"
        :class="{ on: active === tab.value }"
        @click="selectTab(tab.value)"
      >
        {{ tab.label }}
      </button>
    </nav>

    <div v-if="loading" class="state">正在加载订单…</div>

    <div v-else-if="failed" class="state">
      <p>订单没有加载出来。</p>
      <button class="btn btn--ghost" @click="load">重试</button>
    </div>

    <div v-else-if="!orders.length" class="empty">
      <p class="empty__mark">空</p>
      <h2>{{ active ? '这个状态下还没有订单' : '还没有下过单' }}</h2>
      <p class="empty__hint">挑一件喜欢的东西，下一单试试。</p>
      <RouterLink to="/products" class="btn btn--solid">去逛逛</RouterLink>
    </div>

    <template v-else>
      <p class="count">共 {{ total }} 笔订单</p>

      <div class="list">
        <RouterLink
          v-for="order in orders"
          :key="order.orderNo"
          :to="`/orders/${order.orderNo}`"
          class="order"
        >
          <ProductMedia
            class="cover"
            :name="order.summary"
            :image="order.coverImage"
            :seed="order.orderNo.length"
            ratio="1 / 1"
          />

          <div class="info">
            <div class="info__top">
              <span class="no">{{ order.orderNo }}</span>
              <span class="pill" :class="`pill--${orderStatusTone(order.status)}`">
                {{ orderStatusLabel(order.status) }}
              </span>
            </div>
            <p class="summary">{{ order.summary }}</p>
            <p class="meta">
              {{ order.subOrderCount }} 个包裹
              <template v-if="order.shippedCount"> · 已发 {{ order.shippedCount }} 个</template>
              · {{ formatDateTime(order.createdAt) }}
            </p>
          </div>

          <div class="side">
            <p class="amount">¥{{ yuan(order.payAmount) }}</p>
            <span class="detail">查看详情 →</span>
          </div>
        </RouterLink>
      </div>

      <div v-if="totalPages > 1" class="pager">
        <button class="page" :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
        <span>{{ page }} / {{ totalPages }}</span>
        <button class="page" :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.orders { padding: 46px 0 96px; }
.head { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; }
.head h1 { font-size: 36px; margin-top: 6px; }
.more { color: var(--muted); font-size: 13px; }
.more:hover { color: var(--rust); }

.tabs { display: flex; gap: 22px; margin: 30px 0 18px; border-bottom: 1px solid var(--line); }
.tab {
  position: relative;
  padding: 10px 0 12px;
  border: 0;
  background: none;
  color: var(--muted);
  font-size: 14px;
  cursor: pointer;
}
.tab.on { color: var(--ink); }
.tab.on::after {
  content: "";
  position: absolute;
  left: 0; right: 0; bottom: -1px;
  height: 2px;
  background: var(--rust);
}

.count { color: var(--muted); font-size: 12px; margin: 0 0 10px; }
.list { display: grid; gap: 12px; }
.order {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  padding: 16px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  transition: border-color .18s, box-shadow .18s, transform .18s;
}
.order:hover {
  border-color: #dfc9b4;
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}
.cover { width: 84px; border-radius: 12px; }
.info { min-width: 0; }
.info__top { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.no { font-size: 12px; letter-spacing: 0.04em; color: var(--muted); }
.pill {
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 20px;
  white-space: nowrap;
}
.pill--wait { background: var(--rust-soft); color: var(--rust); }
.pill--moving { background: #eef2e8; color: var(--sage); }
.pill--done { background: #e8efe5; color: #3f5b34; }
.pill--muted { background: #f1ede6; color: var(--muted); }
.summary {
  margin: 6px 0 2px;
  font-family: var(--serif);
  font-size: 17px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta { margin: 0; color: var(--muted); font-size: 12px; }
.side { text-align: right; padding-left: 12px; }
.amount { font-size: 18px; font-weight: 600; margin: 0 0 4px; }
.detail { color: var(--rust); font-size: 12px; }

.state, .empty {
  padding: 60px 20px;
  text-align: center;
  color: var(--muted);
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius);
}
.empty__mark {
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  margin: 0 auto 14px;
  border-radius: 50%;
  background: var(--paper-2);
  color: var(--muted);
  font-family: var(--serif);
}
.empty h2 { color: var(--ink); font-size: 22px; margin-bottom: 6px; }
.empty__hint { margin: 0 0 20px; font-size: 13px; }

.pager { display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 24px; font-size: 13px; color: var(--muted); }
.page { border: 1px solid var(--line); background: #fff; border-radius: 999px; padding: 6px 14px; color: var(--ink-soft); cursor: pointer; }
.page:disabled { opacity: .4; cursor: not-allowed; }

@media (max-width: 720px) {
  .head h1 { font-size: 28px; }
  .tabs { gap: 16px; overflow-x: auto; }
  .tab { flex: none; }
  .order { grid-template-columns: 64px minmax(0, 1fr); }
  .cover { width: 64px; }
  .side { grid-column: 2; display: flex; align-items: baseline; justify-content: space-between; padding-left: 0; }
}
</style>
