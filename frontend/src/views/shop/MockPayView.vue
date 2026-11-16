<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { completeMockPayment, fetchPayment, type PaymentView } from '@/api/payment'
import { yuan } from '@/api/catalog'

const route = useRoute()
const payment = ref<PaymentView | null>(null)
const busy = ref(false)
const failed = ref(false)

const purposeLabel = computed(() => {
  if (payment.value?.purpose === 'DEPOSIT') return '定金'
  if (payment.value?.purpose === 'BALANCE') return '尾款'
  return ''
})

const successText = computed(() => {
  if (payment.value?.purpose === 'DEPOSIT') {
    return '定金已入账，订单转为「制作中」，商家按承诺工期制作；尾款在发货前支付。'
  }
  if (payment.value?.purpose === 'BALANCE') {
    return '尾款已入账，订单转为「已付款」，商家开始安排发货。'
  }
  return '订单已转为「已付款」，库存已扣减。'
})

onMounted(async () => {
  try {
    payment.value = await fetchPayment(route.params.paymentNo as string)
  } catch {
    failed.value = true
  }
})

async function finish(success: boolean) {
  if (!payment.value) return
  busy.value = true
  try {
    payment.value = await completeMockPayment(payment.value.paymentNo, success)
    ElMessage.success(success ? '支付成功' : '已标记为支付失败')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '支付失败')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="pay">
    <div class="card">
      <p class="eyebrow">Mock Gateway</p>
      <h1>模拟收银台</h1>

      <div v-if="failed" class="tip">支付单不存在或无权访问。</div>

      <template v-else-if="payment">
        <p class="amount">¥{{ yuan(payment.amount) }}</p>
        <p v-if="purposeLabel" class="purpose">{{ purposeLabel }}</p>
        <p class="meta">订单 {{ payment.orderNo }}</p>
        <p class="meta">支付单 {{ payment.paymentNo }}</p>

        <div v-if="payment.status === 'PENDING'" class="actions">
          <button class="btn btn--solid" :disabled="busy" @click="finish(true)">模拟支付成功</button>
          <button class="btn btn--ghost" :disabled="busy" @click="finish(false)">模拟支付失败</button>
        </div>

        <div v-else class="result">
          <p :class="['state', payment.status === 'SUCCESS' ? 'ok' : 'bad']">
            {{ payment.status === 'SUCCESS' ? '支付成功' : '支付失败' }}
          </p>
          <p class="meta">
            {{ payment.status === 'SUCCESS'
              ? successText
              : '支付未完成，可以回订单详情重新发起支付。' }}
          </p>
          <div class="result__actions">
            <RouterLink :to="`/orders/${payment.orderNo}`" class="btn btn--solid">查看订单</RouterLink>
            <RouterLink to="/products" class="btn btn--ghost">继续逛逛</RouterLink>
          </div>
        </div>

        <p class="note">这是模拟网关：按钮会让服务端签名并回调平台，等价于真实通道的异步通知。</p>
      </template>
    </div>
  </div>
</template>

<style scoped>
.pay { display: grid; place-items: center; padding: 64px 16px 120px; }
.card { width: min(460px, 100%); background: #fff; border-radius: 22px; padding: 36px 32px; box-shadow: var(--shadow); text-align: center; }
.card h1 { font-size: 28px; margin: 8px 0 20px; }
.amount { font-size: 42px; font-weight: 700; color: var(--rust); margin: 0 0 6px; }
.purpose {
  display: inline-block;
  margin: 0 0 8px;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--rust-soft);
  color: var(--rust);
  font-size: 12px;
}
.meta { color: var(--muted); font-size: 13px; margin: 2px 0; }
.actions { display: grid; gap: 12px; margin: 26px 0 6px; }
.actions .btn { justify-content: center; }
.state { font-family: var(--serif); font-size: 24px; margin: 20px 0 6px; }
.state.ok { color: var(--sage); }
.state.bad { color: var(--rust); }
.result { margin-top: 10px; }
.result__actions { display: flex; justify-content: center; gap: 10px; margin-top: 18px; flex-wrap: wrap; }
.note { color: var(--muted); font-size: 11px; line-height: 1.6; margin-top: 22px; text-align: left; }
.tip { color: var(--muted); }
</style>
