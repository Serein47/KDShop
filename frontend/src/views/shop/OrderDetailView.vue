<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelOrder,
  confirmSubOrder,
  fetchOrder,
  type OrderDetail,
  type SubOrderView,
} from '@/api/order'
import { createPayment } from '@/api/payment'
import { applyRefund } from '@/api/refund'
import { createReview, fetchMyReviews } from '@/api/review'
import { useAuthStore } from '@/stores/auth'
import { yuan } from '@/api/catalog'
import { mediaUrl } from '@/api/media'
import {
  formatDateTime,
  isCancellable,
  isPayable,
  orderStatusLabel,
  orderStatusTone,
  subOrderStatusLabel,
} from '@/utils/order'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const order = ref<OrderDetail | null>(null)
const loading = ref(true)
const failed = ref(false)
const busy = ref(false)
const refundDialog = ref(false)
const refundSub = ref<SubOrderView | null>(null)
const refundReason = ref('')
const reviewed = ref<Set<number>>(new Set())
const reviewDialog = ref(false)
const reviewItem = ref<{ id: number; name: string } | null>(null)
const reviewRating = ref(5)
const reviewContent = ref('')

const orderNo = computed(() => String(route.params.orderNo ?? ''))
const canPay = computed(() => !!order.value && isPayable(order.value.status))
const canCancel = computed(() => !!order.value && isCancellable(order.value.status))

onMounted(async () => {
  if (!auth.ready) await auth.fetchMe()
  if (!auth.user) {
    router.replace({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  await load()
})

async function load() {
  loading.value = true
  failed.value = false
  try {
    order.value = await fetchOrder(orderNo.value)
    try {
      reviewed.value = new Set((await fetchMyReviews()).map((r) => r.orderItemId))
    } catch { /* 未登录或接口异常时不影响订单展示 */ }
  } catch (e: any) {
    if (e?.response?.status === 401) {
      router.replace({ path: '/login', query: { redirect: route.fullPath } })
      return
    }
    failed.value = true
    ElMessage.error(e?.response?.data?.message || '订单加载失败')
  } finally {
    loading.value = false
  }
}

async function pay() {
  if (!order.value) return
  busy.value = true
  try {
    const payment = await createPayment(order.value.orderNo)
    router.push(payment.payUrl)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '创建支付失败')
  } finally {
    busy.value = false
  }
}

async function cancel() {
  if (!order.value) return
  try {
    await ElMessageBox.confirm('取消后库存与优惠券会释放，确定取消这笔订单吗？', '取消订单', {
      confirmButtonText: '取消订单',
      cancelButtonText: '再想想',
      type: 'warning',
    })
  } catch {
    return
  }
  busy.value = true
  try {
    await cancelOrder(order.value.orderNo)
    ElMessage.success('订单已取消')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '取消失败')
  } finally {
    busy.value = false
  }
}

function openRefund(sub: SubOrderView) {
  refundSub.value = sub
  refundReason.value = ''
  refundDialog.value = true
}

async function submitRefund() {
  if (!refundSub.value) return
  if (!refundReason.value.trim()) {
    ElMessage.warning('请填写退款原因')
    return
  }
  busy.value = true
  try {
    await applyRefund(refundSub.value.subOrderNo, refundReason.value.trim())
    ElMessage.success('退款申请已提交，等待商家处理')
    refundDialog.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '提交失败')
  } finally {
    busy.value = false
  }
}

function openReview(item: { id: number; name: string }) {
  reviewItem.value = item
  reviewRating.value = 5
  reviewContent.value = ''
  reviewDialog.value = true
}

async function submitReview() {
  if (!reviewItem.value) return
  if (!reviewContent.value.trim()) {
    ElMessage.warning('写点什么吧')
    return
  }
  busy.value = true
  try {
    await createReview(reviewItem.value.id, { rating: reviewRating.value, content: reviewContent.value.trim() })
    ElMessage.success('评价已提交')
    reviewed.value = new Set([...reviewed.value, reviewItem.value.id])
    reviewDialog.value = false
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '提交失败')
  } finally {
    busy.value = false
  }
}

async function confirmReceive(sub: SubOrderView) {
  if (!order.value) return
  try {
    await ElMessageBox.confirm('确认已经收到这件商品了吗？', '确认收货', {
      confirmButtonText: '确认收货',
      cancelButtonText: '还没收到',
      type: 'info',
    })
  } catch {
    return
  }
  busy.value = true
  try {
    await confirmSubOrder(order.value.orderNo, sub.subOrderNo)
    ElMessage.success('已确认收货')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '确认收货失败')
  } finally {
    busy.value = false
  }
}

function specText(spec: Record<string, string>): string {
  const values = Object.values(spec ?? {})
  return values.length ? values.join(' / ') : '默认规格'
}

function actorLabel(actor: string): string {
  const labels: Record<string, string> = { USER: '你', MERCHANT: '商家', ADMIN: '平台', SYSTEM: '系统' }
  return labels[actor] ?? actor
}
</script>

<template>
  <div class="detail container">
    <div v-if="loading" class="state">正在加载订单…</div>

    <div v-else-if="failed" class="state">
      <p>订单没有加载出来。</p>
      <button class="btn btn--ghost" @click="load">重试</button>
      <RouterLink to="/orders" class="btn btn--ghost">返回订单列表</RouterLink>
    </div>

    <template v-else-if="order">
      <nav class="crumb" aria-label="面包屑">
        <RouterLink to="/orders">我的订单</RouterLink>
        <span>/</span>
        <span>{{ order.orderNo }}</span>
      </nav>

      <header class="head">
        <div>
          <p class="eyebrow">Order detail</p>
          <h1>{{ order.orderNo }}</h1>
          <p class="sub">
            下单于 {{ formatDateTime(order.createdAt) }} · {{ order.subOrders.length }} 个包裹
          </p>
        </div>
        <div class="head__side">
          <span class="pill" :class="`pill--${orderStatusTone(order.status)}`">
            {{ orderStatusLabel(order.status) }}
          </span>
          <button v-if="canPay" class="btn btn--solid" :disabled="busy" @click="pay">去支付</button>
          <button v-if="canCancel" class="btn btn--ghost" :disabled="busy" @click="cancel">
            取消订单
          </button>
        </div>
      </header>

      <div class="layout">
        <div class="main">
          <section class="card">
            <div class="card__head">
              <h2>订单进度</h2>
            </div>
            <ol v-if="order.timeline.length" class="timeline">
              <li v-for="(entry, index) in order.timeline" :key="`${entry.status}-${index}`">
                <span class="dot" />
                <div>
                  <strong>{{ orderStatusLabel(entry.status) }}</strong>
                  <span>{{ formatDateTime(entry.at) }} · {{ actorLabel(entry.actor) }}</span>
                </div>
              </li>
            </ol>
            <p v-else class="muted">暂无状态记录。</p>
          </section>

          <section v-for="sub in order.subOrders" :key="sub.subOrderNo" class="card sub">
            <div class="card__head">
              <div>
                <h2>{{ sub.shopName }}</h2>
                <p class="muted">{{ sub.subOrderNo }}</p>
              </div>
              <span class="pill" :class="`pill--${orderStatusTone(sub.status)}`">
                {{ subOrderStatusLabel(sub.status) }}
              </span>
            </div>

            <div class="items">
              <div v-for="(item, index) in sub.items" :key="`${item.name}-${index}`" class="item">
                <span class="item__mark">
          <img v-if="item.image" :src="mediaUrl(item.image)" :alt="item.name" />
          <template v-else>{{ item.name.slice(0, 1) }}</template>
        </span>
                <div class="item__main">
                  <p>{{ item.name }}</p>
                  <span>{{ specText(item.spec) }} · ¥{{ yuan(item.price) }} × {{ item.quantity }}</span>
                </div>
                <strong>¥{{ yuan(item.subtotal) }}</strong>
                <button v-if="sub.status === 'COMPLETED' && !reviewed.has(item.id)" class="btn btn--ghost btn--sm item__review" @click="openReview(item)">评价</button>
                <span v-else-if="sub.status === 'COMPLETED'" class="muted item__review">已评价</span>
              </div>
            </div>

            <div class="sub__foot">
              <p v-if="sub.shipment" class="ship">
                {{ sub.shipment.carrier }} · {{ sub.shipment.trackingNo }}
                <span v-if="sub.shippedAt"> · {{ formatDateTime(sub.shippedAt) }}</span>
              </p>
              <p v-else-if="sub.status === 'PENDING_PAYMENT'" class="ship muted">支付后商家安排发货</p>
              <p v-else-if="sub.status === 'PENDING_SHIPMENT'" class="ship muted">等待商家发货</p>
              <p v-else-if="sub.status === 'CANCELLED'" class="ship muted">子单已取消</p>
              <p v-else class="ship muted">暂无物流信息</p>

              <div class="sub__right">
                <span>子单实付 <strong>¥{{ yuan(sub.payAmount) }}</strong></span>
                <button
                  v-if="['PENDING_SHIPMENT', 'SHIPPED', 'COMPLETED'].includes(sub.status)"
                  class="btn btn--ghost btn--sm"
                  @click="openRefund(sub)"
                >
                  申请退款
                </button>
                <button
                  v-if="sub.actions.includes('CONFIRM')"
                  class="btn btn--solid btn--sm"
                  :disabled="busy"
                  @click="confirmReceive(sub)"
                >
                  确认收货
                </button>
              </div>
            </div>
          </section>
        </div>

        <aside class="side">
          <section class="card">
            <div class="card__head"><h2>收货信息</h2></div>
            <template v-if="order.address">
              <p class="addr">
                <strong>{{ order.address.receiver }}</strong>
                <span>{{ order.address.phone }}</span>
              </p>
              <p class="muted">
                {{ order.address.province }}{{ order.address.city }}{{ order.address.district
                }}{{ order.address.detail }}
              </p>
            </template>
            <p v-else class="muted">地址快照不可用。</p>
          </section>

          <section class="card">
            <div class="card__head"><h2>金额</h2></div>
            <dl class="money">
              <div><dt>商品金额</dt><dd>¥{{ yuan(order.goodsAmount) }}</dd></div>
              <div v-if="order.discount">
                <dt>优惠</dt><dd class="minus">−¥{{ yuan(order.discount) }}</dd>
              </div>
              <div>
                <dt>运费</dt>
                <dd>{{ order.shippingFee ? `¥${yuan(order.shippingFee)}` : '免运费' }}</dd>
              </div>
              <div class="total"><dt>实付</dt><dd>¥{{ yuan(order.payAmount) }}</dd></div>
            </dl>
          </section>

          <section class="card">
            <div class="card__head"><h2>订单信息</h2></div>
            <div class="meta">
              <p><span>订单号</span>{{ order.orderNo }}</p>
              <p><span>下单时间</span>{{ formatDateTime(order.createdAt) }}</p>
              <p v-if="order.paidAt"><span>支付时间</span>{{ formatDateTime(order.paidAt) }}</p>
              <p v-if="order.remark"><span>备注</span>{{ order.remark }}</p>
            </div>
          </section>
        </aside>
      </div>

      <el-dialog v-model="reviewDialog" title="评价商品" width="460px">
        <p class="muted">{{ reviewItem?.name }}</p>
        <div class="review-form">
          <el-rate v-model="reviewRating" />
          <el-input v-model="reviewContent" type="textarea" :rows="4" maxlength="500" show-word-limit
            placeholder="说说使用感受，帮助其他顾客判断" />
        </div>
        <template #footer>
          <button class="btn btn--ghost" @click="reviewDialog = false">取消</button>
          <button class="btn btn--solid" :disabled="busy" @click="submitReview">提交评价</button>
        </template>
      </el-dialog>

      <el-dialog v-model="refundDialog" title="申请退款" width="460px">
        <p class="muted">子单 {{ refundSub?.subOrderNo }} · 可退 ¥{{ yuan(refundSub?.payAmount ?? 0) }}</p>
        <textarea v-model="refundReason" rows="3" class="refund-input" placeholder="说明退款原因，例如：尺寸不合适 / 商品破损" />
        <template #footer>
          <button class="btn btn--ghost" @click="refundDialog = false">取消</button>
          <button class="btn btn--solid" :disabled="busy" @click="submitRefund">提交申请</button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<style scoped>
.item__review { margin-left: 12px; }
.review-form { display: grid; gap: 14px; margin-top: 12px; }
.detail { padding: 30px 0 96px; }
.crumb { display: flex; gap: 8px; color: var(--muted); font-size: 12px; margin-bottom: 18px; }
.crumb a:hover { color: var(--rust); }
.head { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; }
.head h1 { font-size: 32px; margin-top: 4px; letter-spacing: 0.02em; }
.sub { color: var(--muted); font-size: 12px; margin: 6px 0 0; }
.head__side { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; justify-content: flex-end; }

.pill { padding: 3px 10px; border-radius: 999px; font-size: 11px; line-height: 20px; }
.pill--wait { background: var(--rust-soft); color: var(--rust); }
.pill--moving { background: #eef2e8; color: var(--sage); }
.pill--done { background: #e8efe5; color: #3f5b34; }
.pill--muted { background: #f1ede6; color: var(--muted); }

.layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 18px; margin-top: 26px; align-items: start; }
.main { display: grid; gap: 14px; }
.side { display: grid; gap: 14px; }
.card { background: #fff; border: 1px solid var(--line); border-radius: var(--radius); padding: 22px; }
.card__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.card__head h2 { font-size: 18px; }
.card__head .muted { margin: 4px 0 0; font-size: 12px; }
.muted { color: var(--muted); }
.btn--sm { padding: 7px 16px; font-size: 12px; }

.timeline { list-style: none; margin: 0; padding: 0; display: grid; gap: 0; }
.timeline li { position: relative; display: flex; gap: 14px; padding: 0 0 18px 0; }
.timeline li:last-child { padding-bottom: 0; }
.timeline li::before {
  content: "";
  position: absolute;
  left: 5px;
  top: 16px;
  bottom: 0;
  width: 1px;
  background: var(--line);
}
.timeline li:last-child::before { display: none; }
.dot {
  position: relative;
  z-index: 1;
  flex: none;
  width: 11px;
  height: 11px;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--rust);
  box-shadow: 0 0 0 4px var(--rust-soft);
}
.timeline strong { display: block; font-size: 13px; font-weight: 600; }
.timeline span { color: var(--muted); font-size: 12px; }

.items { display: grid; gap: 2px; border-top: 1px solid var(--line); }
.item { display: flex; align-items: center; gap: 14px; padding: 14px 0; border-bottom: 1px solid var(--line); }
.item__mark img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
  display: block;
}
.item__mark {
  display: grid; place-items: center;
  width: 46px; height: 46px; flex: none;
  border-radius: 12px;
  background: linear-gradient(145deg, #e9d8bf, #d3b895);
  color: rgba(255, 255, 255, .85);
  font-family: var(--serif);
}
.item__main { min-width: 0; flex: 1; }
.item__main p { margin: 0; font-size: 14px; }
.item__main span { color: var(--muted); font-size: 12px; }
.item strong { font-size: 13px; white-space: nowrap; }

.sub__foot { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding-top: 14px; flex-wrap: wrap; }
.ship { margin: 0; font-size: 12px; color: var(--ink-soft); }
.refund-input { width: 100%; box-sizing: border-box; padding: 10px 12px; border: 1px solid var(--line); border-radius: 10px; font-size: 14px; font-family: inherit; resize: vertical; }
.sub__right { display: flex; align-items: center; gap: 14px; margin-left: auto; font-size: 12px; }
.sub__right strong { font-size: 15px; }

.addr { display: flex; gap: 10px; align-items: baseline; margin: 0 0 4px; }
.addr strong { font-size: 15px; }
.addr span { color: var(--muted); font-size: 12px; }
.card .muted { font-size: 13px; margin: 0; line-height: 1.7; }

.money { margin: 0; display: grid; gap: 9px; font-size: 13px; }
.money div { display: flex; justify-content: space-between; gap: 12px; color: var(--ink-soft); }
.money dt, .money dd { margin: 0; }
.money .minus { color: var(--rust); }
.money .total { border-top: 1px solid var(--line); padding-top: 11px; margin-top: 2px; color: var(--ink); }
.money .total dd { font-size: 20px; font-weight: 600; }

.meta { display: grid; gap: 7px; font-size: 12px; }
.meta p { display: flex; gap: 10px; margin: 0; color: var(--ink-soft); }
.meta span { width: 64px; flex: none; color: var(--muted); }

.state {
  padding: 60px 20px;
  text-align: center;
  color: var(--muted);
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius);
}
.state .btn { margin: 6px; }

@media (max-width: 960px) {
  .layout { grid-template-columns: 1fr; }
  .side { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 680px) {
  .head { flex-direction: column; align-items: flex-start; }
  .head h1 { font-size: 25px; }
  .side { grid-template-columns: 1fr; }
  .sub__foot { flex-direction: column; align-items: flex-start; }
  .sub__right { margin-left: 0; width: 100%; justify-content: space-between; }
}
</style>
