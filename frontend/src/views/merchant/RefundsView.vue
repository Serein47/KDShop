<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approveRefund, fetchMerchantRefunds, rejectRefund, type RefundView } from '@/api/refund'
import { yuan } from '@/api/catalog'

const list = ref<RefundView[]>([])
const status = ref('PENDING')
const loading = ref(true)
const busy = ref(false)

const tabs = [
  { value: 'PENDING', label: '待处理' },
  { value: 'REFUNDED', label: '已退款' },
  { value: 'REJECTED', label: '已驳回' },
]
const label: Record<string, string> = { PENDING: '待处理', REFUNDED: '已退款', REJECTED: '已驳回' }

async function load() {
  loading.value = true
  try {
    list.value = await fetchMerchantRefunds(status.value)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function approve(item: RefundView) {
  busy.value = true
  try {
    await approveRefund(item.refundNo)
    ElMessage.success('已同意退款')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    busy.value = false
  }
}

async function reject(item: RefundView) {
  try {
    const { value } = await ElMessageBox.prompt('填写驳回原因', '驳回退款', {
      inputPlaceholder: '例如：商品已使用，不符合退款条件',
      inputValidator: (v) => (v && v.trim().length > 0) || '原因不能为空',
    })
    await rejectRefund(item.refundNo, value)
    ElMessage.success('已驳回')
    await load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}
</script>

<template>
  <div class="refunds container">
    <p class="eyebrow">Merchant</p>
    <h1>退款处理</h1>

    <div class="tabs">
      <button v-for="tab in tabs" :key="tab.value" :class="{ on: status === tab.value }"
        @click="status = tab.value; load()">{{ tab.label }}</button>
    </div>

    <div v-if="loading" class="state">加载中…</div>
    <p v-else-if="!list.length" class="state">这个状态下没有退款单。</p>

    <div v-else class="list">
      <article v-for="item in list" :key="item.refundNo" class="card">
        <header class="card__head">
          <div>
            <h2>{{ item.refundNo }}</h2>
            <p class="muted">子单 {{ item.subOrderNo }} · 订单 {{ item.orderNo }}</p>
          </div>
          <span class="tag">{{ label[item.status] }}</span>
        </header>
        <p class="line">退款金额 <strong>¥{{ yuan(item.amount) }}</strong></p>
        <p class="muted">原因：{{ item.reason }}</p>
        <p v-if="item.rejectReason" class="reject">驳回原因：{{ item.rejectReason }}</p>
        <div v-if="item.status === 'PENDING'" class="actions">
          <button class="btn btn--solid" :disabled="busy" @click="approve(item)">同意退款</button>
          <button class="btn btn--ghost" :disabled="busy" @click="reject(item)">驳回</button>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.refunds { padding-top: 48px; max-width: 820px; }
h1 { font-size: 34px; margin: 6px 0 20px; }
.tabs { display: flex; gap: 10px; margin-bottom: 18px; }
.tabs button { padding: 8px 16px; border: 1px solid var(--line); background: none; border-radius: 999px; cursor: pointer; font-size: 13px; }
.tabs button.on { border-color: var(--rust); color: var(--rust); background: var(--rust-soft); }
.list { display: grid; gap: 14px; }
.card { background: #fff; border-radius: 16px; padding: 20px 22px; box-shadow: var(--shadow-sm); }
.card__head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 6px; }
.card__head h2 { font-size: 16px; margin: 0 0 4px; }
.muted { color: var(--muted); font-size: 12px; margin: 2px 0; }
.line { margin: 8px 0 4px; font-size: 14px; }
.tag { font-size: 12px; color: var(--rust); }
.reject { color: #c0392b; font-size: 12px; margin: 4px 0; }
.actions { display: flex; gap: 10px; margin-top: 12px; }
.state { color: var(--muted); padding: 40px 0; }
</style>
