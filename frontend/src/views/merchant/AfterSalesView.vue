<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  AFTER_SALE_STATUS_LABELS, REASON_LABELS, fetchMerchantAfterSale, fetchMerchantAfterSales,
  inspectAfterSale, markAfterSaleReceived, reviewAfterSale, uploadEvidence, type AfterSale,
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
const inspectOpen = ref(false)
const inspectForm = ref({ result: 'GOOD' as 'GOOD' | 'DEFECTIVE', note: '' })
const inspectImages = ref<string[]>([])
const uploading = ref(false)

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

async function receive(item: AfterSale) {
  try {
    await ElMessageBox.confirm('确认已经收到顾客寄回的商品？确认后开始 48 小时质检倒计时。', '确认收货', {
      confirmButtonText: '确认收货',
      cancelButtonText: '还没收到',
      type: 'info',
    })
  } catch {
    return
  }
  busy.value = true
  try {
    await markAfterSaleReceived(item.asNo)
    ElMessage.success('已确认收货，请在 48 小时内完成质检')
    detail.value = await fetchMerchantAfterSale(item.asNo)
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    busy.value = false
  }
}

function openInspect(item: AfterSale) {
  detail.value = item
  inspectForm.value = { result: 'GOOD', note: '' }
  inspectImages.value = []
  inspectOpen.value = true
}

async function onInspectFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (inspectImages.value.length >= 3) {
    ElMessage.warning('最多 3 张质检图片')
    return
  }
  uploading.value = true
  try {
    inspectImages.value.push(await uploadEvidence(file))
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function submitInspect() {
  if (!detail.value) return
  busy.value = true
  try {
    const result = await inspectAfterSale(
      detail.value.asNo,
      inspectForm.value.result,
      inspectForm.value.note || undefined,
      inspectImages.value,
    )
    ElMessage.success(result.status === 'REFUNDED' ? '质检完成，已退款' : '质检已提交')
    inspectOpen.value = false
    dialog.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '提交失败')
  } finally {
    busy.value = false
  }
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
        <p v-if="item.returnTrackingNo" class="muted">寄回：{{ item.returnCarrier }} · {{ item.returnTrackingNo }}</p>
        <div class="actions">
          <button class="btn btn--ghost" @click="openDetail(item)">详情</button>
          <template v-if="item.status === 'APPLIED'">
            <button class="btn btn--solid" :disabled="busy" @click="approve(item)">同意</button>
            <button class="btn btn--ghost" :disabled="busy" @click="openReject(item)">驳回</button>
          </template>
          <button v-if="item.status === 'RETURNING' && item.returnTrackingNo" class="btn btn--solid"
            :disabled="busy" @click="receive(item)">确认收货</button>
          <button v-if="item.status === 'RECEIVED'" class="btn btn--solid"
            :disabled="busy" @click="openInspect(item)">提交质检</button>
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
        <p v-if="detail.status === 'RETURNING' && detail.returnDueAt" class="muted">
          寄回截止：{{ formatDateTime(detail.returnDueAt) }}
        </p>
        <p v-if="detail.receivedAt" class="muted">
          收货时间：{{ formatDateTime(detail.receivedAt) }}
          <template v-if="detail.inspectDueAt"> · 质检截止 {{ formatDateTime(detail.inspectDueAt) }}</template>
        </p>
        <p v-if="detail.inspectResult" class="muted">
          质检结果：{{ detail.inspectResult === 'GOOD' ? '良品（已回补库存）' : '残次（不回补库存）' }}
          <template v-if="detail.inspectNote"> · {{ detail.inspectNote }}</template>
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
        <div v-if="detail.status === 'RETURNING' && detail.returnTrackingNo" class="actions">
          <button class="btn btn--solid" :disabled="busy" @click="receive(detail)">确认收货</button>
        </div>
        <div v-if="detail.status === 'RECEIVED'" class="actions">
          <button class="btn btn--solid" :disabled="busy" @click="openInspect(detail)">提交质检</button>
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

    <el-dialog v-model="inspectOpen" title="提交质检" width="520px">
      <p class="muted">售后单 {{ detail?.asNo }} · 良品回补库存并退款，残次仅退款但不回补库存</p>
      <div class="inspect">
        <label>质检结果
          <select v-model="inspectForm.result">
            <option value="GOOD">良品</option>
            <option value="DEFECTIVE">残次</option>
          </select>
        </label>
        <label>说明
          <textarea v-model="inspectForm.note" rows="3" maxlength="300"
            placeholder="例如：商品完好可二次销售 / 杯口磕碰无法二次销售" />
        </label>
        <div>
          <p class="muted">质检图片（最多 3 张）</p>
          <div class="shots">
            <div v-for="(key, index) in inspectImages" :key="key" class="shot">
              <img :src="mediaUrl(key)" alt="质检图" />
              <button type="button" @click="inspectImages.splice(index, 1)">移除</button>
            </div>
            <label v-if="inspectImages.length < 3" class="picker">
              <input type="file" accept="image/jpeg,image/png,image/webp" @change="onInspectFile" />
              {{ uploading ? '上传中…' : '＋ 上传' }}
            </label>
          </div>
        </div>
      </div>
      <template #footer>
        <button class="btn btn--ghost" @click="inspectOpen = false">取消</button>
        <button class="btn btn--solid" :disabled="busy || uploading" @click="submitInspect">
          {{ busy ? '提交中…' : '提交质检' }}
        </button>
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
.inspect { display: grid; gap: 12px; margin-top: 10px; }
.inspect label { display: grid; gap: 6px; font-size: 13px; color: var(--muted); }
.inspect select, .inspect textarea {
  padding: 10px 12px; border: 1px solid var(--line); border-radius: 10px; font: inherit; font-size: 14px; resize: vertical;
}
.shot { position: relative; width: 72px; height: 72px; border-radius: 8px; overflow: hidden; }
.shot img { width: 100%; height: 100%; object-fit: cover; }
.shot button {
  position: absolute; inset: auto 0 0 0; border: 0; background: rgba(0, 0, 0, .55);
  color: #fff; font-size: 10px; padding: 2px 0; cursor: pointer;
}
.picker {
  display: grid; place-items: center; width: 72px; height: 72px;
  border: 1px dashed var(--line); border-radius: 8px; cursor: pointer;
  font-size: 11px; color: var(--muted);
}
.picker input { display: none; }
</style>
