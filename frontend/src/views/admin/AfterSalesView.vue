<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  AFTER_SALE_STATUS_LABELS, REASON_LABELS, fetchAdminAfterSales, verdictAfterSale, type AfterSale,
} from '@/api/afterSales'
import { formatDateTime } from '@/utils/order'
import { yuan } from '@/api/catalog'

const list = ref<AfterSale[]>([])
const loading = ref(true)
const status = ref('ESCALATED')
const busy = ref(false)
const verdictOpen = ref(false)
const current = ref<AfterSale | null>(null)
const form = ref({
  decision: 'APPROVE' as 'APPROVE' | 'REJECT',
  responsibility: 'MERCHANT' as 'MERCHANT' | 'LOGISTICS' | 'CUSTOMER',
  note: '',
})

const tabs = [
  { value: 'ESCALATED', label: '平台介入' },
  { value: 'APPLIED', label: '商家待审' },
  { value: '', label: '全部' },
]

async function load() {
  loading.value = true
  try {
    list.value = await fetchAdminAfterSales(status.value || undefined)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openVerdict(item: AfterSale) {
  current.value = item
  form.value = { decision: 'APPROVE', responsibility: 'MERCHANT', note: '' }
  verdictOpen.value = true
}

async function submitVerdict() {
  if (!current.value) return
  if (!form.value.note.trim()) {
    ElMessage.warning('平台裁决必须填写原因')
    return
  }
  busy.value = true
  try {
    const result = await verdictAfterSale(
      current.value.asNo, form.value.decision, form.value.responsibility, form.value.note.trim(),
    )
    ElMessage.success(`已裁决：${AFTER_SALE_STATUS_LABELS[result.status] || result.status}`)
    verdictOpen.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '裁决失败')
  } finally {
    busy.value = false
  }
}

function typeLabel(type: string) {
  return type === 'RETURN_REFUND' ? '退货退款' : '仅退款'
}
</script>

<template>
  <div class="admin container">
    <header class="head">
      <div>
        <p class="eyebrow">Platform</p>
        <h1>售后裁决</h1>
      </div>
      <nav class="links">
        <RouterLink to="/admin">平台看板</RouterLink>
        <RouterLink to="/admin/refunds">旧退款</RouterLink>
        <RouterLink to="/admin/settlements">结算</RouterLink>
      </nav>
    </header>

    <div class="tabs">
      <button v-for="tab in tabs" :key="tab.value" :class="{ on: status === tab.value }"
        @click="status = tab.value; load()">{{ tab.label }}</button>
    </div>

    <div v-if="loading" class="state">加载中…</div>
    <p v-else-if="!list.length" class="state">当前没有需要处理的售后单。</p>

    <table v-else class="table">
      <thead>
        <tr>
          <th>售后单</th><th>子单 / 店铺</th><th>类型 / 原因</th><th>退款</th>
          <th>状态</th><th>申请时间</th><th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in list" :key="item.asNo">
          <td>{{ item.asNo }}</td>
          <td>
            <p class="strong">{{ item.subOrderNo }}</p>
            <p class="muted">{{ item.shopName }}</p>
          </td>
          <td>
            <p class="strong">{{ typeLabel(item.type) }}</p>
            <p class="muted">{{ REASON_LABELS[item.reasonCode] || item.reasonCode }}</p>
          </td>
          <td>¥{{ yuan(item.refundAmount) }}</td>
          <td>
            <span class="tag">{{ AFTER_SALE_STATUS_LABELS[item.status] || item.status }}</span>
            <p v-if="item.verdictNote" class="muted">{{ item.verdictNote }}</p>
          </td>
          <td>{{ formatDateTime(item.createdAt) }}</td>
          <td>
            <button v-if="['APPLIED', 'ESCALATED'].includes(item.status)" class="link"
              @click="openVerdict(item)">裁决</button>
          </td>
        </tr>
      </tbody>
    </table>

    <el-dialog v-model="verdictOpen" title="平台裁决" width="520px">
      <p class="muted">{{ current?.asNo }} · {{ current?.subOrderNo }} · 退款 ¥{{ yuan(current?.refundAmount ?? 0) }}</p>
      <div class="form">
        <label>裁决结果
          <select v-model="form.decision">
            <option value="APPROVE">同意（仅退款直接退，退货退款进入待寄回）</option>
            <option value="REJECT">驳回</option>
          </select>
        </label>
        <label>责任归属
          <select v-model="form.responsibility">
            <option value="MERCHANT">商家</option>
            <option value="LOGISTICS">物流</option>
            <option value="CUSTOMER">顾客</option>
          </select>
        </label>
        <label>裁决原因（必填，写入审计与状态流水）
          <textarea v-model="form.note" rows="3" maxlength="300" placeholder="例如：商品与描述不符，商家承担退货运费" />
        </label>
        <p class="hint">责任归属决定退货运费由谁承担；自动裁决与人工裁决都会留痕。</p>
      </div>
      <template #footer>
        <button class="btn btn--ghost" @click="verdictOpen = false">取消</button>
        <button class="btn btn--solid" :disabled="busy" @click="submitVerdict">
          {{ busy ? '提交中…' : '提交裁决' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin { padding-top: 48px; max-width: 1100px; }
.head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 18px; }
h1 { font-size: 34px; margin: 6px 0 0; }
.links { display: flex; gap: 16px; font-size: 13px; }
.links a { color: var(--ink-soft); }
.links a:hover { color: var(--rust); }
.tabs { display: flex; gap: 10px; margin-bottom: 18px; }
.tabs button { padding: 8px 16px; border: 1px solid var(--line); background: none; border-radius: 999px; cursor: pointer; font-size: 13px; }
.tabs button.on { border-color: var(--rust); color: var(--rust); background: var(--rust-soft); }
.state { color: var(--muted); padding: 40px 0; }
.table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 14px; overflow: hidden; box-shadow: var(--shadow-sm); font-size: 13px; }
th, td { text-align: left; padding: 12px 12px; border-bottom: 1px solid var(--line); vertical-align: top; }
th { color: var(--muted); font-weight: 500; }
.strong { margin: 0; font-weight: 500; }
.muted { margin: 2px 0 0; color: var(--muted); font-size: 12px; }
.tag { font-size: 11px; color: var(--rust); background: var(--rust-soft); border-radius: 999px; padding: 2px 8px; }
.link { border: 0; background: none; color: var(--rust); cursor: pointer; font-size: 13px; }
.form { display: grid; gap: 12px; margin-top: 10px; }
label { display: grid; gap: 6px; font-size: 13px; color: var(--muted); }
select, textarea { padding: 10px 12px; border: 1px solid var(--line); border-radius: 10px; font: inherit; font-size: 14px; resize: vertical; }
.hint { font-size: 12px; color: var(--muted); margin: 0; }
</style>
