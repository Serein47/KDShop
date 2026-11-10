<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { claimCoupon, fetchCoupons, fetchMyCoupons, type Coupon, type MyCoupon } from '@/api/coupon'
import { yuan } from '@/api/catalog'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const tab = ref<'center' | 'mine'>('center')
const coupons = ref<Coupon[]>([])
const mine = ref<MyCoupon[]>([])
const loading = ref(true)

function rule(c: Coupon | MyCoupon) {
  return c.minAmount > 0 ? `满 ¥${(c.minAmount / 100).toFixed(0)} 可用` : '无门槛'
}

async function load() {
  loading.value = true
  try {
    const [all, owned] = await Promise.all([fetchCoupons(), fetchMyCoupons()])
    coupons.value = all
    mine.value = owned
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (!auth.ready) await auth.fetchMe()
  await load()
})

async function claim(c: Coupon) {
  try {
    await claimCoupon(c.couponId)
    ElMessage.success('领取成功')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '领取失败')
  }
}
</script>

<template>
  <div class="coupons container">
    <p class="eyebrow">Coupons</p>
    <h1>优惠券</h1>

    <div class="tabs">
      <button :class="{ on: tab === 'center' }" @click="tab = 'center'">领券中心</button>
      <button :class="{ on: tab === 'mine' }" @click="tab = 'mine'">我的券 {{ mine.length }}</button>
    </div>

    <div v-if="loading" class="state">加载中…</div>

    <template v-else-if="tab === 'center'">
      <p v-if="!coupons.length" class="state">暂时没有可领的优惠券。</p>
      <div v-else class="grid">
        <article v-for="c in coupons" :key="c.couponId" class="ticket" :class="{ done: c.claimed || c.remaining === 0 }">
          <div class="ticket__value"><strong>¥{{ yuan(c.value) }}</strong><span>{{ rule(c) }}</span></div>
          <div class="ticket__info">
            <h2>{{ c.name }}</h2>
            <p class="muted">有效期至 {{ c.expiresAt?.slice(0, 10) }} · 剩余 {{ c.remaining }}</p>
          </div>
          <button class="btn btn--solid" :disabled="c.claimed || c.remaining === 0" @click="claim(c)">
            {{ c.claimed ? '已领取' : c.remaining === 0 ? '已领完' : '领取' }}
          </button>
        </article>
      </div>
    </template>

    <template v-else>
      <p v-if="!mine.length" class="state">还没有券，去领券中心看看。</p>
      <div v-else class="grid">
        <article v-for="c in mine" :key="c.userCouponId" class="ticket" :class="{ done: c.status !== 'UNUSED' }">
          <div class="ticket__value"><strong>¥{{ yuan(c.value) }}</strong><span>{{ rule(c) }}</span></div>
          <div class="ticket__info">
            <h2>{{ c.name }}</h2>
            <p class="muted">有效期至 {{ c.expiresAt?.slice(0, 10) }}</p>
            <p class="muted">{{ c.status === 'UNUSED' ? '未使用' : c.status === 'USED' ? '已使用' : '已过期' }}</p>
          </div>
        </article>
      </div>
    </template>
  </div>
</template>

<style scoped>
.coupons { padding-top: 48px; max-width: 900px; }
h1 { font-size: 34px; margin: 6px 0 20px; }
.tabs { display: flex; gap: 10px; margin-bottom: 18px; }
.tabs button { padding: 8px 16px; border: 1px solid var(--line); background: none; border-radius: 999px; cursor: pointer; font-size: 13px; }
.tabs button.on { border-color: var(--rust); color: var(--rust); background: var(--rust-soft); }
.grid { display: grid; gap: 14px; }
.ticket { display: flex; align-items: center; gap: 20px; background: #fff; border-radius: 16px; padding: 18px 22px; box-shadow: var(--shadow-sm); border-left: 4px solid var(--rust); }
.ticket.done { border-left-color: var(--line); opacity: 0.7; }
.ticket__value { display: grid; gap: 4px; min-width: 120px; }
.ticket__value strong { font-size: 28px; color: var(--rust); }
.ticket__value span { font-size: 12px; color: var(--muted); }
.ticket__info { flex: 1; }
.ticket__info h2 { font-size: 16px; margin: 0 0 4px; }
.muted { color: var(--muted); font-size: 12px; margin: 2px 0; }
.state { color: var(--muted); padding: 40px 0; }
</style>
