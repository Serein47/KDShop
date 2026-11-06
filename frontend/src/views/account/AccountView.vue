<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createAddress, deleteAddress, fetchAddresses, setDefaultAddress, updateAddress,
  type Address, type AddressPayload,
} from '@/api/address'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const list = ref<Address[]>([])
const loading = ref(true)
const dialog = ref(false)
const saving = ref(false)
const editing = ref<Address | null>(null)
const form = ref<AddressPayload>({ receiver: '', phone: '', province: '', city: '', district: '', detail: '' })

async function load() {
  loading.value = true
  try {
    list.value = await fetchAddresses()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (!auth.ready) await auth.fetchMe()
  await load()
})

function openCreate() {
  editing.value = null
  form.value = { receiver: '', phone: '', province: '', city: '', district: '', detail: '' }
  dialog.value = true
}

function openEdit(item: Address) {
  editing.value = item
  form.value = {
    receiver: item.receiver,
    phone: item.phone,
    province: item.province,
    city: item.city,
    district: item.district,
    detail: item.detail,
  }
  dialog.value = true
}

async function save() {
  if (!form.value.receiver.trim() || !form.value.phone.trim() || !form.value.detail.trim()) {
    ElMessage.warning('请填写收货人、手机号和详细地址')
    return
  }
  saving.value = true
  try {
    if (editing.value) await updateAddress(editing.value.id, { ...form.value })
    else await createAddress({ ...form.value, isDefault: list.value.length === 0 })
    ElMessage.success('已保存')
    dialog.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(item: Address) {
  try {
    await ElMessageBox.confirm('删除后结算时需要重新填写，确定删除？', '删除地址', { type: 'warning' })
    await deleteAddress(item.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

async function makeDefault(item: Address) {
  try {
    await setDefaultAddress(item.id)
    ElMessage.success('已设为默认地址')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}
</script>

<template>
  <div class="account container">
    <p class="eyebrow">Account</p>
    <h1>个人中心</h1>

    <section class="card">
      <header class="card__head">
        <h2>收货地址</h2>
        <button class="btn btn--solid" @click="openCreate">新增地址</button>
      </header>

      <div v-if="loading" class="state">加载中…</div>
      <p v-else-if="!list.length" class="state">还没有收货地址，新增一个吧。</p>

      <div v-else class="list">
        <article v-for="item in list" :key="item.id" class="addr">
          <div class="addr__main">
            <p class="addr__title">
              {{ item.receiver }}
              <span class="addr__phone">{{ item.phone }}</span>
              <span v-if="item.isDefault === 1" class="tag">默认</span>
            </p>
            <p class="muted">{{ item.province }}{{ item.city }}{{ item.district }}{{ item.detail }}</p>
          </div>
          <div class="addr__actions">
            <button class="link" @click="openEdit(item)">编辑</button>
            <button v-if="item.isDefault !== 1" class="link" @click="makeDefault(item)">设为默认</button>
            <button class="link link--danger" @click="remove(item)">删除</button>
          </div>
        </article>
      </div>
    </section>

    <section class="card">
      <h2>我的</h2>
      <div class="links">
        <RouterLink to="/orders">我的订单</RouterLink>
        <RouterLink to="/favorites">收藏夹</RouterLink>
        <RouterLink to="/cart">购物车</RouterLink>
      </div>
    </section>

    <el-dialog v-model="dialog" :title="editing ? '编辑地址' : '新增地址'" width="520px">
      <div class="form">
        <div class="row">
          <label>收货人<input v-model="form.receiver" /></label>
          <label>手机号<input v-model="form.phone" /></label>
        </div>
        <div class="row">
          <label>省<input v-model="form.province" /></label>
          <label>市<input v-model="form.city" /></label>
          <label>区/县<input v-model="form.district" /></label>
        </div>
        <label>详细地址<input v-model="form.detail" placeholder="街道、门牌号" /></label>
      </div>
      <template #footer>
        <button class="btn btn--ghost" @click="dialog = false">取消</button>
        <button class="btn btn--solid" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.account { padding-top: 48px; max-width: 780px; }
h1 { font-size: 34px; margin: 6px 0 22px; }
.card { background: #fff; border-radius: 18px; padding: 22px 24px; box-shadow: var(--shadow-sm); margin-bottom: 18px; }
.card__head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.card h2 { font-size: 18px; margin: 0; }
.list { display: grid; gap: 12px; }
.addr { display: flex; justify-content: space-between; gap: 16px; border: 1px solid var(--line); border-radius: 12px; padding: 14px 16px; }
.addr__title { margin: 0 0 4px; font-size: 15px; display: flex; align-items: center; gap: 10px; }
.addr__phone { color: var(--muted); font-size: 13px; }
.tag { font-size: 11px; color: var(--rust); border: 1px solid var(--rust); border-radius: 999px; padding: 1px 8px; }
.muted { color: var(--muted); font-size: 13px; margin: 0; }
.addr__actions { display: flex; gap: 12px; align-items: center; }
.link { border: 0; background: none; color: var(--ink-soft); font-size: 13px; cursor: pointer; }
.link:hover { color: var(--rust); }
.link--danger:hover { color: #c0392b; }
.links { display: flex; gap: 18px; font-size: 14px; }
.links a { color: var(--ink-soft); }
.links a:hover { color: var(--rust); }
.form { display: grid; gap: 14px; }
.row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
label { display: grid; gap: 6px; font-size: 13px; color: var(--muted); }
input { padding: 10px 12px; border: 1px solid var(--line); border-radius: 10px; font-size: 14px; }
.state { color: var(--muted); padding: 20px 0; }
</style>
