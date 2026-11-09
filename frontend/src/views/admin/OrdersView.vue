<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchAdminOrder, fetchAdminOrders, type AdminOrder, type AdminOrderDetail } from '@/api/adminOrder'
import { yuan } from '@/api/catalog'

const list = ref<AdminOrder[]>([])
const loading = ref(true)
const orderNo = ref('')
const status = ref('')
const detail = ref<AdminOrderDetail | null>(null)
const dialog = ref(false)

function label(s: string) {
  const map: Record<string, string> = {
    PENDING_PAYMENT: '待付款', PAID: '已付款', PENDING_SHIPMENT: '待发货', PARTIALLY_SHIPPED: '部分发货',
    SHIPPED: '已发货', COMPLETED: '已完成', CANCELLED: '已取消', REFUNDED: '已退款',
    PARTIALLY_REFUNDED: '部分退款', SUSPENDED: '已封禁',
  }
  return map[s] ?? s
}

async function load() {
  loading.value = true
  try {
    list.value = await fetchAdminOrders({ orderNo: orderNo.value || undefined, status: status.value || undefined })
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function openDetail(item: AdminOrder) {
  try {
    detail.value = await fetchAdminOrder(item.orderNo)
    dialog.value = true
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载详情失败')
  }
}
</script>

<template>
  <div class="orders container">
    <p class="eyebrow">Platform</p>
    <h1>订单检索</h1>

    <div class="filters">
      <input v-model="orderNo" placeholder="订单号" @keyup.enter="load" />
      <select v-model="status" @change="load">
        <option value="">全部状态</option>
        <option value="PENDING_PAYMENT">待付款</option>
        <option value="PAID">已付款</option>
        <option value="SHIPPED">已发货</option>
        <option value="COMPLETED">已完成</option>
        <option value="CANCELLED">已取消</option>
        <option value="PARTIALLY_REFUNDED">部分退款</option>
        <option value="REFUNDED">已退款</option>
      </select>
      <button class="btn btn--ghost" @click="load">查询</button>
    </div>

    <div v-if="loading" class="state">加载中…</div>
    <p v-else-if="!list.length" class="state">没有符合条件的订单。</p>

    <table v-else class="table">
      <thead>
        <tr><th>订单号</th><th>买家</th><th>金额</th><th>包裹</th><th>状态</th><th>下单时间</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="item in list" :key="item.orderNo">
          <td>{{ item.orderNo }}</td>
          <td>{{ item.nickname || ('用户 #' + item.userId) }}</td>
          <td>¥{{ yuan(item.payAmount) }}</td>
          <td>{{ item.merchantCount }}</td>
          <td>{{ label(item.status) }}</td>
          <td>{{ item.createdAt?.replace('T', ' ').slice(0, 16) }}</td>
          <td><button class="link" @click="openDetail(item)">详情</button></td>
        </tr>
      </tbody>
    </table>

    <el-dialog v-model="dialog" title="订单详情" width="720px">
      <div v-if="detail" class="detail">
        <p><strong>{{ detail.orderNo }}</strong> · {{ label(detail.status) }}</p>
        <p class="muted">买家 {{ detail.nickname || ('#' + detail.userId) }} · {{ detail.receiver }} {{ detail.phone }}</p>
        <p class="muted">{{ detail.address }}</p>
        <p class="muted">商品 ¥{{ yuan(detail.goodsAmount) }} · 优惠 -¥{{ yuan(detail.discount) }} · 运费 ¥{{ yuan(detail.shippingFee) }} · 实付 <strong>¥{{ yuan(detail.payAmount) }}</strong></p>

        <h3>包裹</h3>
        <div v-for="sub in detail.subOrders" :key="sub.subOrderNo" class="sub">
          <div>
            <strong>{{ sub.shopName }}</strong> · {{ sub.subOrderNo }}
            <span class="tag">{{ label(sub.status) }}</span>
          </div>
          <span class="muted">实付 ¥{{ yuan(sub.payAmount) }}<template v-if="sub.refundedAmount"> · 已退 ¥{{ yuan(sub.refundedAmount) }}</template></span>
          <span v-if="sub.carrier" class="muted">{{ sub.carrier }} {{ sub.trackingNo }}</span>
        </div>

        <template v-if="detail.refunds.length">
          <h3>退款</h3>
          <div v-for="r in detail.refunds" :key="r.refundNo" class="sub">
            <span>{{ r.refundNo }} · {{ label(r.status) }} · ¥{{ yuan(r.amount) }}</span>
            <span class="muted">{{ r.reason }}</span>
            <span v-if="r.rejectReason" class="reject">{{ r.rejectReason }}</span>
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.orders { padding-top: 48px; max-width: 1050px; }
h1 { font-size: 34px; margin: 6px 0 18px; }
.filters { display: flex; gap: 12px; margin-bottom: 16px; }
input, select { padding: 8px 12px; border: 1px solid var(--line); border-radius: 10px; font-size: 13px; }
.table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 14px; overflow: hidden; box-shadow: var(--shadow-sm); font-size: 13px; }
th, td { text-align: left; padding: 10px 12px; border-bottom: 1px solid var(--line); }
th { color: var(--muted); font-weight: 500; }
.link { border: 0; background: none; color: var(--rust); cursor: pointer; font-size: 12px; }
.detail p { margin: 4px 0; font-size: 13px; }
.detail h3 { font-size: 14px; margin: 16px 0 8px; }
.sub { display: flex; flex-wrap: wrap; gap: 10px; align-items: baseline; padding: 8px 0; border-top: 1px dashed var(--line); font-size: 13px; }
.tag { font-size: 11px; color: var(--rust); border: 1px solid var(--rust); border-radius: 999px; padding: 1px 8px; margin-left: 6px; }
.muted { color: var(--muted); }
.reject { color: #c0392b; }
.state { color: var(--muted); padding: 40px 0; }
</style>
