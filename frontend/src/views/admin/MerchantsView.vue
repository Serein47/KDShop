<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approveMerchant, fetchMerchants, rejectMerchant, type Merchant } from '@/api/merchant'

const list = ref<Merchant[]>([])
const status = ref<string>('PENDING')
const loading = ref(true)

const tabs = [
  { value: 'PENDING', label: '待审核' },
  { value: 'ACTIVE', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
]

async function load() {
  loading.value = true
  try {
    list.value = await fetchMerchants(status.value)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function approve(item: Merchant) {
  try {
    await approveMerchant(item.id)
    ElMessage.success('已通过：' + item.shopName)
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

async function reject(item: Merchant) {
  try {
    const { value } = await ElMessageBox.prompt('填写驳回原因', '驳回入驻申请', {
      inputPlaceholder: '例如：资质照片不清晰',
      inputValidator: (v) => (v && v.trim().length > 0) || '原因不能为空',
    })
    await rejectMerchant(item.id, value)
    ElMessage.success('已驳回')
    await load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}
</script>

<template>
  <div class="admin container">
    <p class="eyebrow">Platform</p>
    <h1>商家审核</h1>

    <div class="tabs">
      <button v-for="tab in tabs" :key="tab.value" :class="{ on: status === tab.value }"
        @click="status = tab.value; load()">{{ tab.label }}</button>
    </div>

    <div v-if="loading" class="state">加载中…</div>
    <p v-else-if="!list.length" class="state">没有需要处理的申请。</p>

    <div v-else class="list">
      <article v-for="item in list" :key="item.id" class="row">
        <div class="row__main">
          <h2>{{ item.shopName }}</h2>
          <p class="muted">{{ item.contactName }} · {{ item.contactPhone }}</p>
          <p class="desc">{{ item.description || '（未填写店铺介绍）' }}</p>
          <p class="muted">申请于 {{ item.createdAt?.slice(0, 10) }} · 佣金 {{ (item.commissionRate / 100).toFixed(1) }}%</p>
          <p v-if="item.rejectReason" class="reject">驳回原因：{{ item.rejectReason }}</p>
        </div>
        <div class="row__actions">
          <template v-if="item.status === 'PENDING'">
            <button class="btn btn--solid" @click="approve(item)">通过</button>
            <button class="btn btn--ghost" @click="reject(item)">驳回</button>
          </template>
          <span v-else class="tag">{{ item.status === 'ACTIVE' ? '已通过' : '已驳回' }}</span>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.admin { padding-top: 48px; max-width: 900px; }
h1 { font-size: 34px; margin: 6px 0 20px; }
.tabs { display: flex; gap: 10px; margin-bottom: 20px; }
.tabs button { padding: 8px 16px; border: 1px solid var(--line); background: none; border-radius: 999px; cursor: pointer; font-size: 13px; }
.tabs button.on { border-color: var(--rust); color: var(--rust); background: var(--rust-soft); }
.list { display: grid; gap: 14px; }
.row { display: flex; justify-content: space-between; gap: 24px; background: #fff; border-radius: 16px; padding: 20px 22px; box-shadow: var(--shadow-sm); }
.row h2 { font-size: 18px; margin: 0 0 6px; }
.muted { color: var(--muted); font-size: 12px; margin: 2px 0; }
.desc { font-size: 13px; color: var(--ink-soft); margin: 8px 0; }
.reject { color: #c0392b; font-size: 12px; margin: 6px 0 0; }
.row__actions { display: flex; flex-direction: column; gap: 10px; justify-content: center; min-width: 96px; }
.tag { font-size: 12px; color: var(--muted); text-align: center; }
.state { color: var(--muted); padding: 40px 0; }
</style>
