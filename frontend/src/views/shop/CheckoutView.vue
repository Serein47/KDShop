<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createAddress, fetchAddresses, fetchPreview, type Address, type CheckoutPreview } from '@/api/checkout'
import { yuan } from '@/api/catalog'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const preview = ref<CheckoutPreview | null>(null)
const addresses = ref<Address[]>([])
const addressId = ref<number | undefined>()
const couponId = ref<number | undefined>()
const loading = ref(true)
const showForm = ref(false)
const form = ref({ receiver: '', phone: '', province: '', city: '', district: '', detail: '' })
const submitting = ref(false)

const selectedCoupon = computed(() => preview.value?.coupons.usable.find((c) => c.couponId === couponId.value))

async function load(preserve = true) {
  loading.value = true
  try {
    preview.value = await fetchPreview({
      addressId: preserve ? addressId.value : undefined,
      couponId: couponId.value,
    })
  } catch (e: any) {
    if (e?.response?.data?.code === 40003) {
      ElMessage.warning('购物车是空的')
      router.push('/cart')
      return
    }
    ElMessage.error(e?.response?.data?.message || '结算信息加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (!auth.ready) await auth.fetchMe()
  if (!auth.user) {
    router.push({ path: '/login', query: { redirect: '/checkout' } })
    return
  }
  addresses.value = await fetchAddresses()
  addressId.value = addresses.value.find((a) => a.isDefault === 1)?.id ?? addresses.value[0]?.id
  await load()
})

watch([addressId, couponId], () => load())

async function submitAddress() {
  if (!form.value.receiver || !form.value.phone || !form.value.detail) {
    ElMessage.warning('请填写收货人、手机号与详细地址')
    return
  }
  submitting.value = true
  try {
    const created = await createAddress({ ...form.value, isDefault: !addresses.value.length })
    addresses.value = await fetchAddresses()
    addressId.value = created.id
    showForm.value = false
    form.value = { receiver: '', phone: '', province: '', city: '', district: '', detail: '' }
    ElMessage.success('地址已保存')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

function placeOrder() {
  ElMessage.info('提交订单与支付将在下一版开放')
}
</script>

<template>
  <div class="checkout container">
    <header class="head">
      <div>
        <p class="eyebrow">Checkout</p>
        <h1>确认订单</h1>
      </div>
      <RouterLink to="/cart" class="more">返回购物车</RouterLink>
    </header>

    <div v-if="loading" class="state">正在计算金额…</div>

    <div v-else-if="preview" class="layout">
      <div class="main">
        <section class="block">
          <div class="block__head">
            <h2>收货地址</h2>
            <button class="link" @click="showForm = !showForm">{{ showForm ? '取消' : '新增地址' }}</button>
          </div>

          <div v-if="showForm" class="form">
            <div class="form__row">
              <input v-model="form.receiver" placeholder="收货人" />
              <input v-model="form.phone" placeholder="手机号" />
            </div>
            <div class="form__row">
              <input v-model="form.province" placeholder="省" />
              <input v-model="form.city" placeholder="市" />
              <input v-model="form.district" placeholder="区/县" />
            </div>
            <input v-model="form.detail" placeholder="详细地址（街道、门牌号）" />
            <button class="btn btn--solid" :disabled="submitting" @click="submitAddress">保存地址</button>
          </div>

          <div class="addresses">
            <button
              v-for="addr in addresses"
              :key="addr.id"
              class="address"
              :class="{ on: addressId === addr.id }"
              @click="addressId = addr.id"
            >
              <strong>{{ addr.receiver }}</strong>
              <span>{{ addr.phone }}</span>
              <span>{{ addr.province }}{{ addr.city }}{{ addr.district }}{{ addr.detail }}</span>
              <em v-if="addr.isDefault === 1">默认</em>
            </button>
            <p v-if="!addresses.length" class="empty">还没有收货地址，先添加一个。</p>
          </div>
        </section>

        <section class="block">
          <div class="block__head">
            <h2>商品</h2>
            <span class="muted">{{ preview.subOrders.length }} 个包裹</span>
          </div>
          <div v-for="sub in preview.subOrders" :key="sub.merchantId" class="shop">
            <p class="shop__name">{{ sub.shopName }}</p>
            <div v-for="line in sub.items" :key="line.skuId" class="line">
              <span class="line__name">{{ line.name }}</span>
              <span class="line__spec">{{ Object.values(line.spec).join(' · ') || '标准规格' }}</span>
              <span class="line__qty">×{{ line.quantity }}</span>
              <span class="line__amount">¥{{ yuan(line.subtotal) }}</span>
            </div>
            <div class="shop__foot">
              <span>运费 ¥{{ yuan(sub.shippingFee) }}</span>
              <span v-if="sub.discount">优惠 −¥{{ yuan(sub.discount) }}</span>
              <span>小计 ¥{{ yuan(sub.payAmount) }}</span>
            </div>
          </div>
        </section>

        <section class="block">
          <div class="block__head">
            <h2>优惠券</h2>
            <span class="muted">共 {{ preview.coupons.usable.length }} 张可用</span>
          </div>
          <div class="coupons">
            <button class="coupon" :class="{ on: !couponId }" @click="couponId = undefined">不使用</button>
            <button
              v-for="coupon in preview.coupons.usable"
              :key="coupon.couponId"
              class="coupon"
              :class="{ on: couponId === coupon.couponId }"
              @click="couponId = coupon.couponId"
            >
              <strong>{{ coupon.name }}</strong>
              <span>{{ coupon.minAmount ? '满 ¥' + yuan(coupon.minAmount) + ' 可用' : '无门槛' }}</span>
            </button>
          </div>
          <p v-if="preview.coupons.unusable.length" class="muted small">
            {{ preview.coupons.unusable.length }} 张不可用（未达门槛或已过期）
          </p>
        </section>
      </div>

      <aside class="summary">
        <h2>订单金额</h2>
        <div class="row"><span>商品金额</span><span>¥{{ yuan(preview.summary.goodsAmount) }}</span></div>
        <div class="row"><span>运费</span><span>¥{{ yuan(preview.summary.shippingFee) }}</span></div>
        <div class="row"><span>优惠{{ selectedCoupon ? '（' + selectedCoupon.name + '）' : '' }}</span>
          <span>−¥{{ yuan(preview.summary.discount) }}</span>
        </div>
        <div class="row total"><span>应付</span><strong>¥{{ yuan(preview.summary.payAmount) }}</strong></div>

        <button class="btn btn--solid submit" :disabled="!addressId" @click="placeOrder">
          {{ addressId ? '提交订单' : '请选择收货地址' }}
        </button>
        <p class="hint">下单与支付在下一版开放；当前仅预览金额与拆单结果。</p>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.checkout { padding-top: 48px; }
.head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 28px; }
.head h1 { font-size: 34px; margin-top: 4px; }
.more { font-size: 13px; color: var(--muted); }
.more:hover { color: var(--rust); }

.layout { display: grid; grid-template-columns: 1fr 340px; gap: 32px; align-items: start; }
.block { background: #fff; border-radius: 18px; padding: 22px 24px; box-shadow: var(--shadow-sm); margin-bottom: 18px; }
.block__head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 16px; }
.block__head h2 { font-size: 18px; }
.link { border: 0; background: none; color: var(--rust); font-size: 13px; cursor: pointer; }
.muted { color: var(--muted); font-size: 12px; }

.form { display: grid; gap: 10px; margin-bottom: 16px; }
.form__row { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 10px; }
.form input { padding: 10px 12px; border: 1px solid var(--line); border-radius: 10px; font-size: 14px; }
.form input:focus { outline: none; border-color: var(--rust); box-shadow: 0 0 0 3px var(--rust-soft); }
.form .btn { justify-self: start; }

.addresses { display: grid; gap: 10px; }
.address {
  display: grid;
  grid-template-columns: auto auto 1fr auto;
  gap: 10px;
  align-items: baseline;
  text-align: left;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: none;
  cursor: pointer;
  font-size: 13px;
  color: var(--ink-soft);
}
.address.on { border-color: var(--rust); box-shadow: 0 0 0 3px var(--rust-soft); }
.address strong { font-size: 15px; color: var(--ink); }
.address em { font-style: normal; font-size: 11px; color: var(--rust); border: 1px solid var(--rust); border-radius: 999px; padding: 1px 8px; }
.empty { color: var(--muted); font-size: 13px; }

.shop { border-top: 1px dashed var(--line); padding-top: 14px; margin-top: 14px; }
.shop:first-of-type { border-top: 0; margin-top: 0; padding-top: 0; }
.shop__name { margin: 0 0 8px; font-size: 14px; color: var(--ink-soft); }
.line { display: grid; grid-template-columns: 1fr auto auto auto; gap: 14px; font-size: 13px; padding: 6px 0; }
.line__spec { color: var(--muted); }
.line__qty { color: var(--muted); }
.line__amount { color: var(--ink); }
.shop__foot { display: flex; gap: 18px; justify-content: flex-end; font-size: 12px; color: var(--muted); border-top: 1px dashed var(--line); margin-top: 8px; padding-top: 8px; }

.coupons { display: flex; flex-wrap: wrap; gap: 10px; }
.coupon {
  display: grid;
  gap: 2px;
  padding: 12px 16px;
  border: 1px dashed var(--line);
  border-radius: 12px;
  background: none;
  text-align: left;
  cursor: pointer;
  font-size: 12px;
  color: var(--muted);
}
.coupon strong { color: var(--ink); font-size: 14px; }
.coupon.on { border-style: solid; border-color: var(--rust); background: var(--rust-soft); }
.small { font-size: 12px; }

.summary {
  position: sticky;
  top: 96px;
  background: #fff;
  border-radius: 18px;
  padding: 22px 24px;
  box-shadow: var(--shadow);
}
.summary h2 { font-size: 18px; margin-bottom: 16px; }
.row { display: flex; justify-content: space-between; font-size: 13px; color: var(--muted); padding: 7px 0; }
.row.total { border-top: 1px solid var(--line); margin-top: 8px; padding-top: 14px; color: var(--ink); align-items: baseline; }
.row.total strong { font-size: 26px; color: var(--rust); }
.submit { width: 100%; justify-content: center; margin-top: 18px; }
.hint { font-size: 11px; color: var(--muted); margin-top: 10px; line-height: 1.6; }
.state { padding: 90px 0; text-align: center; color: var(--muted); }
@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
  .summary { position: static; }
  .form__row { grid-template-columns: 1fr; }
}
</style>
