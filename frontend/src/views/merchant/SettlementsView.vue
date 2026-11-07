<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchMySettlements, type Settlement } from '@/api/settlement'
import { yuan } from '@/api/catalog'

const list = ref<Settlement[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    list.value = await fetchMySettlements()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="settle container">
    <p class="eyebrow">Merchant</p>
    <h1>结算单</h1>

    <div v-if="loading" class="state">加载中…</div>
    <p v-else-if="!list.length" class="state">还没有结算单，平台生成后会出现在这里。</p>

    <table v-else class="table">
      <thead>
        <tr><th>结算单</th><th>周期</th><th>订单</th><th>成交</th><th>佣金</th><th>退款</th><th>应结</th><th>状态</th></tr>
      </thead>
      <tbody>
        <tr v-for="item in list" :key="item.settlementNo">
          <td>{{ item.settlementNo }}</td>
          <td>{{ item.periodStart }} ~ {{ item.periodEnd }}</td>
          <td>{{ item.orderCount }}</td>
          <td>¥{{ yuan(item.grossAmount) }}</td>
          <td>-¥{{ yuan(item.commission) }}</td>
          <td>-¥{{ yuan(item.refund) }}</td>
          <td><strong>¥{{ yuan(item.netAmount) }}</strong></td>
          <td>{{ item.status === 'PAID' ? '已打款' : '待打款' }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.settle { padding-top: 48px; max-width: 900px; }
h1 { font-size: 34px; margin: 6px 0 20px; }
.table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 14px; overflow: hidden; box-shadow: var(--shadow-sm); font-size: 13px; }
th, td { text-align: left; padding: 12px 12px; border-bottom: 1px solid var(--line); }
th { color: var(--muted); font-weight: 500; }
.state { color: var(--muted); padding: 40px 0; }
</style>
