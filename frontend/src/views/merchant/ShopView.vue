<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchDashboard, fetchShopProfile, saveShopProfile, type Dashboard, type ShopProfile } from '@/api/merchantProfile'
import { uploadImage } from '@/api/merchantProduct'
import { yuan } from '@/api/catalog'
import { mediaUrl } from '@/api/media'

const profile = ref<ShopProfile | null>(null)
const dashboard = ref<Dashboard | null>(null)
const loading = ref(true)
const saving = ref(false)
const uploading = ref(false)
const form = ref({ shopName: '', description: '', logo: '', contactName: '', contactPhone: '' })

const maxAmount = computed(() => Math.max(1, ...(dashboard.value?.trend ?? []).map((t) => t.amount)))

onMounted(async () => {
  try {
    const [p, d] = await Promise.all([fetchShopProfile(), fetchDashboard()])
    profile.value = p
    dashboard.value = d
    form.value = {
      shopName: p.shopName,
      description: p.description ?? '',
      logo: p.logo ?? '',
      contactName: p.contactName,
      contactPhone: p.contactPhone,
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
})

async function onLogo(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    form.value.logo = await uploadImage(file)
    ElMessage.success('Logo 已上传')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function save() {
  if (!form.value.shopName.trim() || !form.value.contactName.trim() || !form.value.contactPhone.trim()) {
    ElMessage.warning('请填写店铺名、联系人和电话')
    return
  }
  saving.value = true
  try {
    profile.value = await saveShopProfile({ ...form.value })
    ElMessage.success('已保存')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="shop container">
    <p class="eyebrow">Merchant</p>
    <h1>店铺概览</h1>

    <div v-if="loading" class="state">加载中…</div>

    <template v-else>
      <nav class="quick">
        <RouterLink to="/merchant/products">商品管理</RouterLink>
        <RouterLink to="/merchant/orders">本店订单</RouterLink>
        <RouterLink to="/merchant/refunds">退款处理</RouterLink>
        <RouterLink to="/merchant/settlements">结算单</RouterLink>
      </nav>

      <section v-if="dashboard" class="stats">
        <div class="stat"><span>在售商品</span><strong>{{ dashboard.onSaleProducts }}</strong></div>
        <div class="stat"><span>待发货</span><strong>{{ dashboard.pendingShipCount }}</strong></div>
        <div class="stat"><span>今日订单</span><strong>{{ dashboard.todayOrders }}</strong></div>
        <div class="stat"><span>累计销售</span><strong>¥{{ yuan(dashboard.totalSales) }}</strong></div>
      </section>

      <section v-if="dashboard" class="card">
        <h2>近 7 天</h2>
        <div class="trend">
          <div v-for="point in dashboard.trend" :key="point.date" class="bar">
            <div class="bar__fill" :style="{ height: Math.max(4, (point.amount / maxAmount) * 96) + 'px' }" />
            <span class="bar__label">{{ point.date.slice(5) }}</span>
            <span class="bar__value">{{ yuan(point.amount) }}</span>
          </div>
        </div>
      </section>

      <section class="card">
        <h2>店铺资料</h2>
        <div class="form">
          <label>店铺名称<input v-model="form.shopName" /></label>
          <label>店铺介绍<textarea v-model="form.description" rows="3" /></label>
          <label>联系人<input v-model="form.contactName" /></label>
          <label>联系电话<input v-model="form.contactPhone" /></label>
          <label>Logo
            <input type="file" accept="image/jpeg,image/png,image/webp" @change="onLogo" />
          </label>
          <p v-if="uploading" class="muted">上传中…</p>
          <div v-if="form.logo" class="logo">
            <img :src="mediaUrl(form.logo)" alt="logo" />
          </div>
        </div>
        <button class="btn btn--solid" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
        <p class="muted">佣金费率 {{ profile ? (profile.commissionRate / 100).toFixed(1) : '-' }}%，由平台设定。</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.shop { padding-top: 48px; max-width: 900px; }
h1 { font-size: 34px; margin: 6px 0 22px; }
.quick { display: flex; gap: 16px; font-size: 13px; margin-bottom: 18px; }
.quick a { color: var(--ink-soft); }
.quick a:hover { color: var(--rust); }
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 18px; }
.stat { background: #fff; border-radius: 14px; padding: 18px 20px; box-shadow: var(--shadow-sm); display: grid; gap: 6px; }
.stat span { font-size: 12px; color: var(--muted); }
.stat strong { font-size: 24px; }
.card { background: #fff; border-radius: 18px; padding: 22px 24px; box-shadow: var(--shadow-sm); margin-bottom: 18px; }
.card h2 { font-size: 18px; margin: 0 0 16px; }
.trend { display: flex; gap: 12px; align-items: flex-end; height: 140px; }
.bar { flex: 1; display: grid; justify-items: center; align-content: end; gap: 6px; }
.bar__fill { width: 100%; max-width: 42px; background: var(--rust-soft); border-radius: 6px 6px 0 0; }
.bar__label { font-size: 11px; color: var(--muted); }
.bar__value { font-size: 10px; color: var(--muted); }
.form { display: grid; gap: 14px; margin-bottom: 18px; }
label { display: grid; gap: 6px; font-size: 13px; color: var(--muted); }
input, textarea { padding: 10px 12px; border: 1px solid var(--line); border-radius: 10px; font-size: 14px; font-family: inherit; }
.logo img { width: 72px; height: 72px; object-fit: cover; border-radius: 12px; }
.muted { color: var(--muted); font-size: 12px; }
.state { color: var(--muted); padding: 40px 0; }
@media (max-width: 820px) { .stats { grid-template-columns: repeat(2, 1fr); } }
</style>
