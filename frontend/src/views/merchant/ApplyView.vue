<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { applyMerchant, fetchMyMerchant, type Merchant } from '@/api/merchant'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const shop = ref<Merchant | null>(null)
const loading = ref(true)
const submitting = ref(false)
const form = ref({ shopName: '', contactName: '', contactPhone: '', description: '' })

const canApply = computed(() => !shop.value || shop.value.status === 'REJECTED')

onMounted(async () => {
  if (!auth.ready) await auth.fetchMe()
  try {
    shop.value = await fetchMyMerchant()
  } catch {
    shop.value = null
  } finally {
    loading.value = false
  }
})

async function submit() {
  if (!form.value.shopName || !form.value.contactName || !form.value.contactPhone) {
    ElMessage.warning('请填写店铺名、联系人和手机号')
    return
  }
  submitting.value = true
  try {
    shop.value = await applyMerchant({ ...form.value })
    ElMessage.success('申请已提交，等待平台审核')
    await auth.fetchMe()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="apply container">
    <p class="eyebrow">Merchant</p>
    <h1>开一家店</h1>

    <div v-if="loading" class="state">加载中…</div>

    <template v-else>
      <section v-if="shop && shop.status === 'PENDING'" class="card">
        <h2>申请审核中</h2>
        <p class="muted">{{ shop.shopName }} · 提交于 {{ shop.createdAt?.slice(0, 10) }}</p>
        <p>平台会在审核通过后给你开通商家后台，届时这里会显示店铺入口。</p>
      </section>

      <section v-else-if="shop && shop.status === 'ACTIVE'" class="card">
        <h2>店铺已开通</h2>
        <p class="muted">{{ shop.shopName }} · 佣金 {{ (shop.commissionRate / 100).toFixed(1) }}%</p>
        <p>商家后台（商品管理、发货）在后续版本开放。</p>
      </section>

      <section v-else class="card">
        <p v-if="shop?.status === 'REJECTED'" class="reject">
          上次申请被驳回：{{ shop.rejectReason }}
        </p>
        <h2>填写入驻信息</h2>
        <div class="form">
          <label>店铺名称<input v-model="form.shopName" placeholder="例如：山野杂货" /></label>
          <label>联系人<input v-model="form.contactName" placeholder="真实姓名" /></label>
          <label>手机号<input v-model="form.contactPhone" placeholder="11 位手机号" /></label>
          <label>店铺介绍<textarea v-model="form.description" rows="3" placeholder="卖什么、有什么特色" /></label>
        </div>
        <button class="btn btn--solid" :disabled="submitting || !canApply" @click="submit">
          {{ submitting ? '提交中…' : '提交申请' }}
        </button>
        <p class="hint">提交后由平台审核；通过后账号会获得商家权限。</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.apply { max-width: 720px; padding-top: 56px; }
h1 { font-size: 34px; margin: 6px 0 24px; }
.card { background: #fff; border-radius: 18px; padding: 28px 26px; box-shadow: var(--shadow-sm); }
.card h2 { font-size: 20px; margin: 0 0 8px; }
.muted { color: var(--muted); font-size: 13px; }
.form { display: grid; gap: 14px; margin: 18px 0 20px; }
label { display: grid; gap: 6px; font-size: 13px; color: var(--muted); }
input, textarea { padding: 10px 12px; border: 1px solid var(--line); border-radius: 10px; font-size: 14px; font-family: inherit; }
input:focus, textarea:focus { outline: none; border-color: var(--rust); box-shadow: 0 0 0 3px var(--rust-soft); }
.reject { color: #c0392b; font-size: 13px; background: #fdf1ef; border-radius: 10px; padding: 10px 12px; margin-bottom: 16px; }
.hint { color: var(--muted); font-size: 12px; margin-top: 12px; }
.state { padding: 60px 0; color: var(--muted); }
</style>
