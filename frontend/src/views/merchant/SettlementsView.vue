<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchMySettlementDetail, fetchMySettlements, type Settlement, type SettlementDetail,
} from '@/api/settlement'
import { yuan } from '@/api/catalog'

const list = ref<Settlement[]>([])
const loading = ref(true)
const detail = ref<SettlementDetail | null>(null)
const dialog = ref(false)

onMounted(async () => {
  try {
    list.value = await fetchMySettlements()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
})

async function openDetail(item: Settlement) {
  try {
    detail.value = await fetchMySettlementDetail(item.settlementNo)
    dialog.value = true
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '明细加载失败')
  }
}
</script>

<template>
  <div class="settle container">
    <p class="eyebrow">Merchant</p>
    <h1>结算单</h1>
    <p class="hint">点任意一行查看每个子单的成交、佣金、退款与工期赔付明细。</p>

    <div v-if="loading" class="state">加载中…</div>
    <p v-else-if="!list.length" class="state">还没有结算单，平台生成后会出现在这里。</p>

    <table v-else class="table">
      <thead>
        <tr><th>结算单</th><th>周期</th><th>订单</th><th>成交</th><th>佣金</th><th>退款</th><th>赔付</th><th>应结</th><th>状态</th></tr>
      </thead>
      <tbody>
        <tr v-for="item in list" :key="item.settlementNo" class="row" @click="openDetail(item)">
          <td>{{ item.settlementNo }}</td>
          <td>{{ item.periodStart }} ~ {{ item.periodEnd }}</td>
          <td>{{ item.orderCount }}</td>
          <td>¥{{ yuan(item.grossAmount) }}</td>
          <td>-¥{{ yuan(item.commission) }}</td>
          <td>-¥{{ yuan(item.refund) }}</td>
          <td :class="{ penalty: item.penalty }">-¥{{ yuan(item.penalty) }}</td>
          <td><strong>¥{{ yuan(item.netAmount) }}</strong></td>
          <td>{{ item.status === 'PAID' ? '已打款' : '待打款' }}</td>
        </tr>
      </tbody>
    </table>

    <el-dialog v-model="dialog" title="结算明细" width="760px">
      <template v-if="detail">
        <p class="summary">
          {{ detail.settlement.settlementNo }} · {{ detail.settlement.periodStart }} ~ {{ detail.settlement.periodEnd }}
          · 应结 ¥{{ yuan(detail.settlement.netAmount) }}
        </p>
        <table class="table detail">
          <thead>
            <tr><th>子单</th><th>成交</th><th>佣金</th><th>退款</th><th>赔付</th><th>应结</th></tr>
          </thead>
          <tbody>
            <tr v-for="line in detail.items" :key="line.subOrderNo">
              <td>{{ line.subOrderNo }}</td>
              <td>¥{{ yuan(line.grossAmount) }}</td>
              <td>-¥{{ yuan(line.commission) }}</td>
              <td>-¥{{ yuan(line.refund) }}</td>
              <td :class="{ penalty: line.penalty }">
                -¥{{ yuan(line.penalty) }}
                <div v-if="line.penalties.length || line.refunds.length" class="drill">
                  <p v-for="p in line.penalties" :key="p.penaltyNo">
                    {{ p.penaltyNo }} · 第 {{ p.daysOverdue }} 天 · ¥{{ yuan(p.amount) }}
                  </p>
                  <p v-for="r in line.refunds" :key="r.refundNo">
                    {{ r.refundNo }} · 退款 ¥{{ yuan(r.amount) }}
                  </p>
                </div>
              </td>
              <td><strong>¥{{ yuan(line.netAmount) }}</strong></td>
            </tr>
          </tbody>
        </table>
        <p class="hint">应结 = 成交 − 佣金 − 退款 − 赔付；赔付为工期超时按天累计（每天 5% 定金，上限 50%）。</p>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.settle { padding-top: 48px; max-width: 980px; }
h1 { font-size: 34px; margin: 6px 0 10px; }
.hint { color: var(--muted); font-size: 12px; margin: 0 0 16px; }
.table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 14px; overflow: hidden; box-shadow: var(--shadow-sm); font-size: 13px; }
th, td { text-align: left; padding: 12px 12px; border-bottom: 1px solid var(--line); }
th { color: var(--muted); font-weight: 500; }
.row { cursor: pointer; }
.row:hover { background: #fbf7f1; }
td.penalty { color: var(--rust); }
.drill { margin-top: 4px; font-size: 11px; color: var(--muted); line-height: 1.5; }
.drill p { margin: 0; }
.summary { font-size: 13px; color: var(--ink-soft); margin: 0 0 12px; }
.detail { box-shadow: none; border: 1px solid var(--line); }
.state { color: var(--muted); padding: 40px 0; }
</style>
