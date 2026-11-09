<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchAdminDashboard, type AdminDashboard } from '@/api/admin'
import { yuan } from '@/api/catalog'

const data = ref<AdminDashboard | null>(null)
const loading = ref(true)

const maxAmount = computed(() => Math.max(1, ...(data.value?.trend ?? []).map((t) => t.amount)))

onMounted(async () => {
  try {
    data.value = await fetchAdminDashboard()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="admin container">
    <header class="head">
      <div>
        <p class="eyebrow">Platform</p>
        <h1>平台看板</h1>
      </div>
      <nav class="links">
        <RouterLink to="/admin/merchants">商家审核</RouterLink>
        <RouterLink to="/admin/categories">类目</RouterLink>
        <RouterLink to="/admin/products">商品</RouterLink>
        <RouterLink to="/admin/settlements">结算</RouterLink>
        <RouterLink to="/admin/settlements">结算</RouterLink>
        <RouterLink to="/admin/audit">审计日志</RouterLink>
      </nav>
    </header>

    <div v-if="loading" class="state">加载中…</div>

    <template v-else-if="data">
      <section class="stats">
        <div class="stat"><span>成交额 GMV</span><strong>¥{{ yuan(data.gmv) }}</strong></div>
        <div class="stat"><span>佣金收入（5%）</span><strong>¥{{ yuan(data.commission) }}</strong></div>
        <div class="stat"><span>订单数</span><strong>{{ data.orderCount }}</strong></div>
        <div class="stat"><span>已付款订单</span><strong>{{ data.paidOrderCount }}</strong></div>
        <div class="stat"><span>营业店铺</span><strong>{{ data.merchantActive }}</strong></div>
        <div class="stat"><span>待审核店铺</span><strong class="warn">{{ data.merchantPending }}</strong></div>
        <div class="stat"><span>在售商品</span><strong>{{ data.productOnSale }}</strong></div>
        <div class="stat"><span>用户数</span><strong>{{ data.customerCount }}</strong></div>
      </section>

      <section class="card">
        <h2>近 7 天成交</h2>
        <div class="trend">
          <div v-for="point in data.trend" :key="point.date" class="bar">
            <div class="bar__fill" :style="{ height: Math.max(4, (point.amount / maxAmount) * 110) + 'px' }" />
            <span class="bar__label">{{ point.date.slice(5) }}</span>
            <span class="bar__value">{{ yuan(point.amount) }}</span>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.admin { padding-top: 48px; max-width: 1000px; }
.head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 20px; }
h1 { font-size: 34px; margin: 6px 0 0; }
.links { display: flex; gap: 16px; font-size: 13px; }
.links a { color: var(--ink-soft); }
.links a:hover { color: var(--rust); }
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 18px; }
.stat { background: #fff; border-radius: 14px; padding: 16px 18px; box-shadow: var(--shadow-sm); display: grid; gap: 6px; }
.stat span { font-size: 12px; color: var(--muted); }
.stat strong { font-size: 22px; }
.stat strong.warn { color: var(--rust); }
.card { background: #fff; border-radius: 18px; padding: 22px 24px; box-shadow: var(--shadow-sm); }
.card h2 { font-size: 18px; margin: 0 0 16px; }
.trend { display: flex; gap: 12px; align-items: flex-end; height: 160px; }
.bar { flex: 1; display: grid; justify-items: center; align-content: end; gap: 6px; }
.bar__fill { width: 100%; max-width: 46px; background: var(--rust-soft); border-radius: 6px 6px 0 0; }
.bar__label { font-size: 11px; color: var(--muted); }
.bar__value { font-size: 10px; color: var(--muted); }
.state { color: var(--muted); padding: 40px 0; }
@media (max-width: 820px) { .stats { grid-template-columns: repeat(2, 1fr); } }
</style>
