<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  AFTER_SALE_STATUS_LABELS, REASON_LABELS, fetchMerchantAfterSale, fetchMerchantAfterSales,
  reviewAfterSale, type AfterSale,
} from '@/api/afterSales'
import { mediaUrl } from '@/api/media'
import { formatDateTime } from '@/utils/order'
import { yuan } from '@/api/catalog'

const list = ref<AfterSale[]>([])
const loading = ref(true)
const status = ref('APPLIED')
const detail = ref<AfterSale | null>(null)
const dialog = ref(false)
const busy = ref(false)
const rejectOpen = ref(false)
const rejectReason = ref('')

const tabs = [
  { value: 'APPLIED', label: '待处理' },
  { value: '', label: '全部' },
]

async function load() {
  loading.value = true
  try {
    list.value = await fetchMerchantAfterSales(status.value || undefined)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function openDetail(item: AfterSale) {
  try {
    detail.value = await fetchMerchantAfterSale(item.asNo)
    dialog.value = true
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '详情加载失败')
  }
}

async function approve(item: AfterSale) {
  const label = item.type === 'REFUND_ONLY' ? '同意仅退款（将立即退款）' : '同意退货退款（等待顾客寄回）'
  try {
    await ElMessageBox.confirm(`${label}，确认处理售后单 ${item.asNo}？`, '审核售后', {
      confirmButtonText: '同意',
      cancelButtonText: '再想想',
      type: 'warning',
    })
  } catch {
    return
  }
  busy.value = true
  try {
    await reviewAfterSale(item.asNo, true)
    ElMessage.success('已同意售后')
    dialog.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    busy.value = false
  }
}

function openReject(item: AfterSale) {
  detail.value = item
  rejectReason.value = ''
  rejectOpen.value = true
}

async function submitReject() {
  if (!detail.value) return
  if (!rejectReason.value.trim()) {
    ElMessage.warning('驳回必须填写原因')
    return
  }
  busy.value = true
  try {
    await reviewAfterSale(detail.value.asNo, false, rejectReason.value.trim())
    ElMessage.success('已驳回')
    rejectOpen.value = false
    dialog.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    busy.value = false
  }
}

function typeLabel(type: string) {
  return type === 'RETURN_REFUND' ? '退货退款' : '仅退款'
}
</script>

<template>
  <div class="merchant container">
    <header class="head">
      <div>
        <p class="eyebrow">Merchant</p>
        <h1>售后处理</h1>
      </div>
      <nav class="links">
        <RouterLink to="/merchant/orders">本店订单</RouterLink>
        <RouterLink to="/merchant/refunds">旧退款单</RouterLink>
      </nav>
    </header>

    <div class="tabs">
      <button v-for="tab in tabs" :key="tab.value" :class="{ on: status === tab.value }"
        @click="status = tab.value; load()">{{ tab.label }}</button>
    </div>

    <div v-if="loading" class="state">加载中…</div>
    <p v-else-if="!list.length" class="state">当前没有售后单。</p>

    <div v-else class="list">
      <article v-for="item in list" :key="item.asNo" class="card">
        <header class="card__head">
          <div>
            <h2>{{ item.asNo }}</h2>
            <p class="muted">{{ item.subOrderNo }} · {{ item.customerName }} · {{ formatDateTime(item.createdAt) }}</p>
          </div>
          <span class="tag">{{ AFTER_SALE_STATUS_LABELS[item.status] || item.status }}</span>
        </header>
        <p class="line">{{ typeLabel(item.type) }} · {{ REASON_LABELS[item.reasonCode] }} · 退款 ¥{{ yuan(item.refundAmount) }}</p>
        <p v-if="item.description" class="desc">{{ item.description }}</p>
        <div class="actions">
          <button class="btn btn--ghost" @click="openDetail(item)">详情</button>
          <template v-if="item.status === 'APPLIED'">
            <button class="btn btn--solid" :disabled="busy" @click="approve(item)">同意</button>
            <button class="btn btn--ghost" :disabled="busy" @click="openReject(item)">驳回</button>
          </template>
        </div>
      </article>
    </div>

    <el-dialog v-model="dialog" title="售后详情" width="600px">
      <template v-if="detail">
        <div class="detail__head">
          <p class="no">{{ detail.asNo }}</p>
          <span class="tag">{{ AFTER_SALE_STATUS_LABELS[detail.status] || detail.status }}</span>
        </div>
        <p class="line">{{ typeLabel(detail.type) }} · {{ REASON_LABELS[detail.reasonCode] }} · 退款 ¥{{ yuan(detail.refundAmount) }}</p>
        <p v-if="detail.description" class="desc">{{ detail.description }}</p>
        <div v-if="detail.evidence.length" class="shots">
          <a v-for="key in detail.evidence" :key="key" :href="mediaUrl(key)" target="_blank" rel="noreferrer">
            <img :src="mediaUrl(key)" alt="凭证" />
          </a>
        </div>
        <p v-if="detail.rejectReason" class="reject">驳回原因：{{ detail.rejectReason }}</p>
        <p v-if="detail.returnTrackingNo" class="muted">
          寄回：{{ detail.returnCarrier }} · {{ detail.returnTrackingNo }}
        </p>
        <ol v-if="detail.logs?.length" class="timeline">
          <li v-for="(log, index) in detail.logs" :key="index">
            <span class="dot" />
            <div>
              <strong>{{ AFTER_SALE_STATUS_LABELS[log.toStatus] || log.toStatus }}</strong>
              <span>{{ formatDateTime(log.at) }} · {{ log.note || '' }}</span>
            </div>
          </li>
        </ol>
        <div v-if="detail.status === 'APPLIED'" class="actions">
          <button class="btn btn--solid" :disabled="busy" @click="approve(detail)">同意</button>
          <button class="btn btn--ghost" :disabled="busy" @click="openReject(detail)">驳回</button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectOpen" title="驳回售后" width="460px">
      <p class="muted">售后单 {{ detail?.asNo }}</p>
      <textarea v-model="rejectReason" rows="3" class="reason" maxlength="200"
        placeholder="填写驳回原因，顾客可补充证据后重新申请一次" />
      <template #footer>
        <button class="btn btn--ghost" @click="rejectOpen = false">取消</button>
        <button class="btn btn--solid" :disabled="busy" @click="submitReject">确认驳回</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.merchant { padding-top: 48px; max-width: 900px; }
.head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 16px; }
h1 { font-size: 34px; margin: 6px 0 0; }
.links { display: flex; gap: 16px; font-size: 13px; }
.links a { color: var(--ink-soft); }
.links a:hover { color: var(--rust); }
.tabs { display: flex; gap: 10px; margin-bottom: 18px; }
.tabs button { padding: 8px 16px; border: 1px solid var(--line); background: none; border-radius: 999px; cursor: pointer; font-size: 13px; }
.tabs button.on { border-color: var(--rust); color: var(--rust); background: var(--rust-soft); }
.state { color: var(--muted); padding: 40px 0; }
.list { display: grid; gap: 12px; }
.card { background: #fff; border: 1px solid var(--line); border-radius: var(--radius); padding: 18px 20px; }
.card__head { display: flex; justify-content: space-between; gap: 12px; }
.card__head h2 { font-size: 15px; margin: 0 0 4px; }
.muted { color: var(--muted); font-size: 12px; margin: 0; }
.tag { font-size: 11px; color: var(--rust); background: var(--rust-soft); border-radius: 999px; padding: 2px 10px; height: fit-content; white-space: nowrap; }
.line { font-size: 13px; color: var(--ink-soft); margin: 10px 0 4px; }
.desc { font-size: 13px; color: var(--muted); margin: 0; line-height: 1.7; }
.actions { display: flex; gap: 8px; margin-top: 14px; }
.detail__head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.no { margin: 0; font-size: 13px; color: var(--muted); }
.shots { display: flex; gap: 8px; margin: 10px 0; }
.shots img { width: 72px; height: 72px; object-fit: cover; border-radius: 8px; }
.reject { color: var(--rust); font-size: 13px; margin: 8px 0; }
.timeline { list-style: none; margin: 14px 0 0; padding: 0; display: grid; }
.timeline li { position: relative; display: flex; gap: 12px; padding-bottom: 16px; }
.timeline li::before { content: ""; position: absolute; left: 5px; top: 15px; bottom: 0; width: 1px; background: var(--line); }
.timeline li:last-child::before { display: none; }
.dot { flex: none; width: 11px; height: 11px; margin-top: 4px; border-radius: 50%; background: var(--rust); box-shadow: 0 0 0 4px var(--rust-soft); }
.timeline strong { display: block; font-size: 13px; }
.timeline span { font-size: 12px; color: var(--muted); }
.reason { width: 100%; box-sizing: border-box; padding: 10px 12px; border: 1px solid var(--line); border-radius: 10px; font-family: inherit; font-size: 14px; resize: vertical; }
</style>
