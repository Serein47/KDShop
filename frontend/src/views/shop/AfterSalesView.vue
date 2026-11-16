<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  AFTER_SALE_STATUS_LABELS, REASON_LABELS, fetchMyAfterSale, fetchMyAfterSales, type AfterSale,
} from '@/api/afterSales'
import { useAuthStore } from '@/stores/auth'
import { mediaUrl } from '@/api/media'
import AfterSaleApplyDialog from '@/components/AfterSaleApplyDialog.vue'
import { formatDateTime } from '@/utils/order'
import { yuan } from '@/api/catalog'

const auth = useAuthStore()
const router = useRouter()
const list = ref<AfterSale[]>([])
const loading = ref(true)
const detail = ref<AfterSale | null>(null)
const dialog = ref(false)
const reapplyOpen = ref(false)

const active = computed(() => detail.value)

onMounted(async () => {
  if (!auth.ready) await auth.fetchMe()
  if (!auth.user) {
    router.replace({ path: '/login', query: { redirect: '/after-sales' } })
    return
  }
  await load()
})

async function load() {
  loading.value = true
  try {
    list.value = await fetchMyAfterSales()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function openDetail(item: AfterSale) {
  try {
    detail.value = await fetchMyAfterSale(item.asNo)
    dialog.value = true
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '详情加载失败')
  }
}

function canReapply(item: AfterSale | null): boolean {
  return !!item && item.status === 'REJECTED' && item.reapplyCount < 1
}

function typeLabel(type: string) {
  return type === 'RETURN_REFUND' ? '退货退款' : '仅退款'
}

async function onReapplied() {
  reapplyOpen.value = false
  await load()
  if (detail.value) detail.value = await fetchMyAfterSale(detail.value.asNo)
}
</script>

<template>
  <div class="after container">
    <header class="head">
      <div>
        <p class="eyebrow">After sales</p>
        <h1>售后中心</h1>
      </div>
      <RouterLink to="/orders" class="more">我的订单 →</RouterLink>
    </header>

    <div v-if="loading" class="state">加载中…</div>
    <div v-else-if="!list.length" class="empty">
      <p class="empty__mark">售后</p>
      <h2>还没有售后记录</h2>
      <p class="empty__hint">已完成的订单可在订单详情里发起售后。</p>
      <RouterLink to="/orders" class="btn btn--solid">去订单列表</RouterLink>
    </div>

    <div v-else class="list">
      <article v-for="item in list" :key="item.asNo" class="card" @click="openDetail(item)">
        <div class="card__top">
          <span class="no">{{ item.asNo }}</span>
          <span class="status">{{ AFTER_SALE_STATUS_LABELS[item.status] || item.status }}</span>
        </div>
        <p class="sub">{{ typeLabel(item.type) }} · {{ REASON_LABELS[item.reasonCode] || item.reasonCode }}</p>
        <p class="meta">子单 {{ item.subOrderNo }} · {{ item.shopName }} · 申请于 {{ formatDateTime(item.createdAt) }}</p>
        <div class="foot">
          <span class="amount">退款 ¥{{ yuan(item.refundAmount) }}</span>
          <span class="link">查看进度 →</span>
        </div>
      </article>
    </div>

    <el-dialog v-model="dialog" title="售后进度" width="600px">
      <template v-if="active">
        <div class="detail__head">
          <div>
            <p class="no">{{ active.asNo }}</p>
            <p class="meta">{{ typeLabel(active.type) }} · {{ REASON_LABELS[active.reasonCode] }}</p>
          </div>
          <span class="status">{{ AFTER_SALE_STATUS_LABELS[active.status] || active.status }}</span>
        </div>

        <p v-if="active.description" class="desc">{{ active.description }}</p>
        <div v-if="active.evidence.length" class="shots">
          <a v-for="key in active.evidence" :key="key" :href="mediaUrl(key)" target="_blank" rel="noreferrer">
            <img :src="mediaUrl(key)" alt="凭证" />
          </a>
        </div>

        <p v-if="active.rejectReason" class="reject">驳回原因：{{ active.rejectReason }}</p>
        <p v-if="active.returnTrackingNo" class="meta">
          寄回：{{ active.returnCarrier }} · {{ active.returnTrackingNo }}
        </p>
        <p v-if="active.inspectResult" class="meta">
          质检：{{ active.inspectResult === 'GOOD' ? '良品' : '残次' }}{{ active.inspectNote ? ' · ' + active.inspectNote : '' }}
        </p>

        <ol v-if="active.logs?.length" class="timeline">
          <li v-for="(log, index) in active.logs" :key="index">
            <span class="dot" />
            <div>
              <strong>{{ AFTER_SALE_STATUS_LABELS[log.toStatus] || log.toStatus }}</strong>
              <span>{{ formatDateTime(log.at) }} · {{ log.note || '' }}</span>
            </div>
          </li>
        </ol>

        <div v-if="canReapply(active)" class="actions">
          <button class="btn btn--solid" @click="reapplyOpen = true">补充证据重新申请</button>
        </div>
      </template>
    </el-dialog>

    <AfterSaleApplyDialog
      v-model="reapplyOpen"
      mode="reapply"
      :as-no="active?.asNo"
      :custom-only="false"
      @submitted="onReapplied"
    />
  </div>
</template>

<style scoped>
.after { padding: 46px 0 96px; max-width: 900px; }
.head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 24px; }
h1 { font-size: 34px; margin: 6px 0 0; }
.more { color: var(--muted); font-size: 13px; }
.more:hover { color: var(--rust); }
.state { color: var(--muted); padding: 40px 0; }
.empty { text-align: center; color: var(--muted); background: #fff; border: 1px solid var(--line); border-radius: var(--radius); padding: 60px 20px; }
.empty__mark { display: grid; place-items: center; width: 56px; height: 56px; margin: 0 auto 14px; border-radius: 50%; background: var(--paper-2); font-family: var(--serif); }
.empty h2 { color: var(--ink); font-size: 22px; margin-bottom: 6px; }
.empty__hint { margin: 0 0 20px; font-size: 13px; }
.list { display: grid; gap: 12px; }
.card { background: #fff; border: 1px solid var(--line); border-radius: var(--radius); padding: 18px 20px; cursor: pointer; transition: border-color .18s, box-shadow .18s; }
.card:hover { border-color: #dfc9b4; box-shadow: var(--shadow-sm); }
.card__top { display: flex; justify-content: space-between; gap: 12px; }
.no { font-size: 12px; letter-spacing: .04em; color: var(--muted); }
.status { font-size: 11px; color: var(--rust); background: var(--rust-soft); border-radius: 999px; padding: 2px 10px; white-space: nowrap; }
.sub { margin: 8px 0 4px; font-family: var(--serif); font-size: 17px; }
.meta { margin: 0; color: var(--muted); font-size: 12px; }
.foot { display: flex; justify-content: space-between; margin-top: 10px; font-size: 13px; }
.amount { color: var(--rust); font-weight: 600; }
.link { color: var(--rust); font-size: 12px; }
.detail__head { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.desc { font-size: 13px; color: var(--ink-soft); line-height: 1.8; margin: 0 0 12px; }
.shots { display: flex; gap: 8px; margin-bottom: 12px; }
.shots img { width: 72px; height: 72px; object-fit: cover; border-radius: 8px; }
.reject { font-size: 13px; color: var(--rust); margin: 0 0 10px; }
.timeline { list-style: none; margin: 14px 0 0; padding: 0; display: grid; }
.timeline li { position: relative; display: flex; gap: 12px; padding-bottom: 16px; }
.timeline li::before { content: ""; position: absolute; left: 5px; top: 15px; bottom: 0; width: 1px; background: var(--line); }
.timeline li:last-child::before { display: none; }
.dot { flex: none; width: 11px; height: 11px; margin-top: 4px; border-radius: 50%; background: var(--rust); box-shadow: 0 0 0 4px var(--rust-soft); }
.timeline strong { display: block; font-size: 13px; }
.timeline span { font-size: 12px; color: var(--muted); }
.actions { margin-top: 8px; }
</style>
