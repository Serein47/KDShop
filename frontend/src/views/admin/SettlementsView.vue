<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { confirmSettlement, fetchSettlements, generateSettlements, type Settlement } from '@/api/settlement'
import { yuan } from '@/api/catalog'

const list = ref<Settlement[]>([])
const loading = ref(true)
const busy = ref(false)
const start = ref('2026-09-01')
const end = ref('2026-12-31')

async function load() {
  loading.value = true
  try {
    list.value = await fetchSettlements()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function generate() {
  if (!start.value || !end.value) {
    ElMessage.warning('请选择结算周期')
    return
  }
  busy.value = true
  try {
    const created = await generateSettlements(start.value, end.value)
    ElMessage.success(created.length ? `已生成 ${created.length} 张结算单` : '该周期没有可结算的子单')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '生成失败')
  } finally {
    busy.value = false
  }
}

async function confirm(item: Settlement) {
  try {
    await ElMessageBox.confirm(`确认向「${item.shopName}」打款 ¥${(item.netAmount / 100).toFixed(2)}？`, '确认打款', { type: 'warning' })
    await confirmSettlement(item.settlementNo)
    ElMessage.success('已确认打款')
    await load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}
</script>

<template>
  <div class="settle container">
    <header class="head">
      <div>
        <p class="eyebrow">Platform</p>
        <h1>结算管理</h1>
      </div>
      <nav class="links">
        <RouterLink to="/admin">平台看板</RouterLink>
        <RouterLink to="/admin/merchants">商家审核</RouterLink>
        <RouterLink to="/admin/audit">审计日志</RouterLink>
      </nav>
    </header>

    <section class="gen">
      <label>开始<input v-model="start" type="date" /></label>
      <label>结束<input v-model="end" type="date" /></label>
      <button class="btn btn--solid" :disabled="busy" @click="generate">{{ busy ? '生成中…' : '生成结算单' }}</button>
    </section>

    <div v-if="loading" class="state">加载中…</div>
    <p v-else-if="!list.length" class="state">还没有结算单。选择周期后点「生成结算单」。</p>

    <table v-else class="table">
      <thead>
        <tr>
          <th>结算单</th><th>店铺</th><th>周期</th><th>订单</th>
          <th>成交</th><th>佣金</th><th>退款</th><th>应结</th><th>状态</th><th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in list" :key="item.settlementNo">
          <td>{{ item.settlementNo }}</td>
          <td>{{ item.shopName }}</td>
          <td>{{ item.periodStart }} ~ {{ item.periodEnd }}</td>
          <td>{{ item.orderCount }}</td>
          <td>¥{{ yuan(item.grossAmount) }}</td>
          <td>-¥{{ yuan(item.commission) }}</td>
          <td>-¥{{ yuan(item.refund) }}</td>
          <td><strong>¥{{ yuan(item.netAmount) }}</strong></td>
          <td>{{ item.status === 'PAID' ? '已打款' : '待打款' }}</td>
          <td>
            <button v-if="item.status === 'PENDING'" class="link" @click="confirm(item)">确认打款</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.settle { padding-top: 48px; max-width: 1100px; }
.head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 18px; }
h1 { font-size: 34px; margin: 6px 0 0; }
.links { display: flex; gap: 16px; font-size: 13px; }
.links a { color: var(--ink-soft); }
.links a:hover { color: var(--rust); }
.gen { display: flex; gap: 12px; align-items: flex-end; background: #fff; border-radius: 14px; padding: 16px 18px; box-shadow: var(--shadow-sm); margin-bottom: 18px; }
label { display: grid; gap: 6px; font-size: 12px; color: var(--muted); }
input { padding: 8px 10px; border: 1px solid var(--line); border-radius: 8px; font-size: 13px; }
.table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 14px; overflow: hidden; box-shadow: var(--shadow-sm); font-size: 13px; }
th, td { text-align: left; padding: 12px 12px; border-bottom: 1px solid var(--line); }
th { color: var(--muted); font-weight: 500; }
.link { border: 0; background: none; color: var(--rust); cursor: pointer; font-size: 13px; }
.state { color: var(--muted); padding: 40px 0; }
</style>
