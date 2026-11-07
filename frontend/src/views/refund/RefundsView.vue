<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchMyRefunds, type RefundView } from '@/api/refund'
import { yuan } from '@/api/catalog'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const list = ref<RefundView[]>([])
const loading = ref(true)

const label: Record<string, string> = { PENDING: '等待商家处理', REFUNDED: '已退款', REJECTED: '已驳回' }

onMounted(async () => {
  if (!auth.ready) await auth.fetchMe()
  try {
    list.value = await fetchMyRefunds()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="refunds container">
    <p class="eyebrow">Refunds</p>
    <h1>退款记录</h1>

    <div v-if="loading" class="state">加载中…</div>
    <p v-else-if="!list.length" class="state">还没有退款申请。在订单详情里可以对子单发起退款。</p>

    <div v-else class="list">
      <article v-for="item in list" :key="item.refundNo" class="card">
        <header class="card__head">
          <div>
            <h2>{{ item.refundNo }}</h2>
            <p class="muted">订单 {{ item.orderNo }} · {{ item.shopName }}</p>
          </div>
          <span class="tag" :class="item.status.toLowerCase()">{{ label[item.status] }}</span>
        </header>
        <p class="line">退款金额 ¥{{ yuan(item.amount) }}</p>
        <p class="muted">原因：{{ item.reason }}</p>
        <p v-if="item.rejectReason" class="reject">驳回原因：{{ item.rejectReason }}</p>
        <RouterLink :to="`/orders/${item.orderNo}`" class="link">查看订单 →</RouterLink>
      </article>
    </div>
  </div>
</template>

<style scoped>
.refunds { padding-top: 48px; max-width: 760px; }
h1 { font-size: 34px; margin: 6px 0 22px; }
.list { display: grid; gap: 14px; }
.card { background: #fff; border-radius: 16px; padding: 20px 22px; box-shadow: var(--shadow-sm); }
.card__head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 8px; }
.card__head h2 { font-size: 16px; margin: 0 0 4px; }
.muted { color: var(--muted); font-size: 12px; margin: 2px 0; }
.line { margin: 8px 0 4px; font-size: 14px; }
.tag { font-size: 12px; padding: 2px 10px; border-radius: 999px; background: var(--rust-soft); color: var(--rust); }
.tag.refunded { background: #eaf5ee; color: #2f7d4f; }
.tag.rejected { background: #fdf1ef; color: #c0392b; }
.reject { color: #c0392b; font-size: 12px; margin: 4px 0; }
.link { display: inline-block; margin-top: 8px; font-size: 13px; color: var(--rust); }
.state { color: var(--muted); padding: 40px 0; }
</style>
