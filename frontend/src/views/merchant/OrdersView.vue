<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchSubOrders, shipSubOrder, type MerchantSubOrder } from '@/api/merchantOrder'
import { yuan } from '@/api/catalog'
import { mediaUrl } from '@/api/media'

const list = ref<MerchantSubOrder[]>([])
const loading = ref(true)
const status = ref('PENDING_SHIPMENT')
const shipping = ref<MerchantSubOrder | null>(null)
const form = ref({ carrier: '顺丰速运', trackingNo: '' })
const submitting = ref(false)

const carriers = ['顺丰速运', '中通快递', '圆通速递', '韵达快递', '京东物流']

const tabs = [
  { value: '', label: '全部' },
  { value: 'PRODUCING', label: '制作中' },
  { value: 'PENDING_SHIPMENT', label: '待发货' },
  { value: 'SHIPPED', label: '已发货' },
  { value: 'COMPLETED', label: '已完成' },
]

function statusLabel(value: string) {
  const labels: Record<string, string> = {
    PENDING_PAYMENT: '待付款',
    PRODUCING: '制作中',
    PENDING_SHIPMENT: '待发货',
    SHIPPED: '已发货',
    COMPLETED: '已完成',
  }
  return labels[value] ?? value
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/** 打印包裹单：含商品、定制项、收货信息与贺卡内容。 */
function printPackingSlip(sub: MerchantSubOrder) {
  const rows = sub.items.map((item) => {
    const options = item.options?.length
      ? `<br><small>${escapeHtml(item.options.map((o) => `${o.name}：${o.value}`).join(' · '))}</small>`
      : ''
    return `<tr><td>${escapeHtml(item.name)}${options}</td><td>×${item.quantity}</td>`
      + `<td>¥${(item.subtotal / 100).toFixed(2)}</td></tr>`
  }).join('')
  const gift = sub.isGift
    ? `<p><strong>礼物包装：</strong>${sub.giftWrap ? '是' : '否'}</p>`
      + (sub.giftMessage ? `<p><strong>贺卡：</strong>${escapeHtml(sub.giftMessage)}</p>` : '')
    : ''
  const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
    <title>包裹单 ${escapeHtml(sub.subOrderNo)}</title>
    <style>
      body { font-family: -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif; padding: 24px; color: #191512; }
      h1 { font-size: 20px; margin: 0 0 16px; }
      table { width: 100%; border-collapse: collapse; margin: 12px 0; }
      th, td { border-bottom: 1px solid #ddd; padding: 8px; text-align: left; font-size: 13px; vertical-align: top; }
      small { color: #8a7d6c; }
    </style></head><body>
    <h1>KDShop 包裹单</h1>
    <p>子单：${escapeHtml(sub.subOrderNo)} · 订单：${escapeHtml(sub.orderNo)}</p>
    <p>收货：${escapeHtml(sub.receiver ?? '')} ${escapeHtml(sub.phone ?? '')}<br>${escapeHtml(sub.address ?? '')}</p>
    ${gift}
    <table><thead><tr><th>商品</th><th>数量</th><th>小计</th></tr></thead><tbody>${rows}</tbody></table>
    <p>打印时间：${new Date().toLocaleString('zh-CN')}</p>
    </body></html>`
  const win = window.open('', '_blank', 'width=720,height=900')
  if (!win) {
    ElMessage.warning('请允许弹出窗口后再打印')
    return
  }
  win.document.write(html)
  win.document.close()
  win.focus()
  win.print()
}

async function load() {
  loading.value = true
  try {
    list.value = await fetchSubOrders(status.value)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openShip(sub: MerchantSubOrder) {
  shipping.value = sub
  form.value = { carrier: '顺丰速运', trackingNo: '' }
}

async function submitShip() {
  if (!shipping.value) return
  if (!form.value.trackingNo.trim()) {
    ElMessage.warning('请填写运单号')
    return
  }
  submitting.value = true
  try {
    await shipSubOrder(shipping.value.subOrderNo, form.value.carrier, form.value.trackingNo.trim())
    ElMessage.success('已发货，顾客会收到通知')
    shipping.value = null
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '发货失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="merchant container">
    <p class="eyebrow">Merchant</p>
    <h1>本店订单</h1>

    <div class="tabs">
      <button v-for="tab in tabs" :key="tab.value" :class="{ on: status === tab.value }"
        @click="status = tab.value; load()">{{ tab.label }}</button>
    </div>

    <div v-if="loading" class="state">加载中…</div>
    <p v-else-if="!list.length" class="state">这个状态下没有订单。</p>

    <div v-else class="list">
      <article v-for="sub in list" :key="sub.subOrderNo" class="card">
        <header class="card__head">
          <div>
            <h2>{{ sub.subOrderNo }}</h2>
            <p class="muted">订单 {{ sub.orderNo }} · {{ sub.createdAt?.slice(0, 16).replace('T', ' ') }}</p>
          </div>
          <span class="tag">{{ statusLabel(sub.status) }}</span>
        </header>

        <div class="items">
          <div v-for="(item, i) in sub.items" :key="i" class="item">
            <span class="thumb">
              <img v-if="item.image" :src="mediaUrl(item.image)" :alt="item.name" />
              <template v-else>{{ item.name.slice(0, 1) }}</template>
            </span>
            <div class="item__info">
              <p>{{ item.name }}</p>
              <span>{{ Object.values(item.spec).join(' · ') || '标准规格' }} × {{ item.quantity }}</span>
              <span v-if="item.options.length" class="item__options">
                {{ item.options.map((o) => `${o.name}：${o.value}`).join(' · ') }}
              </span>
            </div>
            <strong>¥{{ yuan(item.subtotal) }}</strong>
          </div>
        </div>

        <div class="meta">
          <span>收货：{{ sub.receiver }} {{ sub.phone }}</span>
          <span>{{ sub.address }}</span>
          <span>实付 ¥{{ yuan(sub.payAmount) }}</span>
        </div>

        <div v-if="sub.isGift" class="gift">
          <span>礼物包装：{{ sub.giftWrap ? '是' : '否' }}</span>
          <span v-if="sub.giftMessage">贺卡：{{ sub.giftMessage }}</span>
        </div>

        <div class="actions">
          <button v-if="sub.status === 'PENDING_SHIPMENT'" class="btn btn--solid" @click="openShip(sub)">发货</button>
          <span v-else-if="sub.status === 'SHIPPED'" class="muted">{{ sub.carrier }} · {{ sub.trackingNo }}</span>
          <button class="btn btn--ghost" @click="printPackingSlip(sub)">打印包裹单</button>
        </div>
      </article>
    </div>

    <el-dialog v-model="shipping" title="发货" width="420px">
      <div v-if="shipping" class="dialog">
        <p class="muted">子单 {{ shipping.subOrderNo }}</p>
        <label>承运商
          <select v-model="form.carrier">
            <option v-for="c in carriers" :key="c" :value="c">{{ c }}</option>
          </select>
        </label>
        <label>运单号
          <input v-model="form.trackingNo" placeholder="例如：SF1234567890" />
        </label>
        <button class="btn btn--solid" :disabled="submitting" @click="submitShip">
          {{ submitting ? '提交中…' : '确认发货' }}
        </button>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.merchant { padding-top: 48px; max-width: 900px; }
h1 { font-size: 34px; margin: 6px 0 20px; }
.tabs { display: flex; gap: 10px; margin-bottom: 20px; }
.tabs button { padding: 8px 16px; border: 1px solid var(--line); background: none; border-radius: 999px; cursor: pointer; font-size: 13px; }
.tabs button.on { border-color: var(--rust); color: var(--rust); background: var(--rust-soft); }
.list { display: grid; gap: 16px; }
.card { background: #fff; border-radius: 18px; padding: 20px 22px; box-shadow: var(--shadow-sm); }
.card__head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.card__head h2 { font-size: 16px; margin: 0 0 4px; }
.muted { color: var(--muted); font-size: 12px; margin: 0; }
.tag { font-size: 12px; color: var(--rust); }
.items { border-top: 1px dashed var(--line); border-bottom: 1px dashed var(--line); padding: 10px 0; }
.item { display: grid; grid-template-columns: 44px 1fr auto; gap: 12px; align-items: center; padding: 6px 0; }
.thumb { width: 44px; height: 44px; border-radius: 8px; background: var(--rust-soft); display: grid; place-items: center; overflow: hidden; font-size: 16px; }
.thumb img { width: 100%; height: 100%; object-fit: cover; }
.item__info p { margin: 0; font-size: 14px; }
.item__info span { font-size: 12px; color: var(--muted); }
.item__options { display: block; color: var(--rust) !important; font-size: 11px; }
.meta { display: flex; gap: 18px; flex-wrap: wrap; font-size: 12px; color: var(--muted); padding: 10px 0 4px; }
.gift {
  display: flex; gap: 18px; flex-wrap: wrap;
  margin-top: 6px; padding: 8px 10px;
  background: var(--rust-soft); border-radius: 8px;
  font-size: 12px; color: var(--rust);
}
.actions { padding-top: 10px; display: flex; gap: 8px; align-items: center; }
.dialog { display: grid; gap: 14px; }
label { display: grid; gap: 6px; font-size: 13px; color: var(--muted); }
select, input { padding: 10px 12px; border: 1px solid var(--line); border-radius: 10px; font-size: 14px; }
.state { color: var(--muted); padding: 40px 0; }
</style>
