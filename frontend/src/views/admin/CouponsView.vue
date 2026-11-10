<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createCoupon, fetchAdminCoupons, grantCoupon, type Coupon } from '@/api/coupon'
import { yuan } from '@/api/catalog'

const list = ref<Coupon[]>([])
const loading = ref(true)
const dialog = ref(false)
const saving = ref(false)
const grantTarget = ref<Coupon | null>(null)
const grantMode = ref<'all' | 'emails'>('all')
const grantEmails = ref('')
const granting = ref(false)
const form = ref({
  name: '', type: 'THRESHOLD', value: 1000, minAmount: 9900, total: 200,
  startsAt: '', endsAt: '',
})

function toIso(d: string) {
  return d ? new Date(d).toISOString().slice(0, 19) : ''
}

async function load() {
  loading.value = true
  try {
    list.value = await fetchAdminCoupons()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate() {
  const now = new Date()
  const end = new Date(now.getTime() + 30 * 24 * 3600 * 1000)
  form.value = {
    name: '', type: 'THRESHOLD', value: 1680, minAmount: 30000, total: 200,
    startsAt: now.toISOString().slice(0, 10), endsAt: end.toISOString().slice(0, 10),
  }
  dialog.value = true
}

const grantDialog = ref(false)

function openGrant(c: Coupon) {
  grantTarget.value = c
  grantMode.value = 'all'
  grantEmails.value = ''
  grantDialog.value = true
}

async function doGrant() {
  if (!grantTarget.value) return
  const emails = grantEmails.value.split(/[,，\s]+/).map((x) => x.trim()).filter(Boolean)
  if (grantMode.value === 'emails' && !emails.length) {
    ElMessage.warning('请填写至少一个邮箱')
    return
  }
  granting.value = true
  try {
    const res = await grantCoupon(grantTarget.value.couponId, {
      allUsers: grantMode.value === 'all',
      emails: grantMode.value === 'emails' ? emails : [],
    })
    ElMessage.success(`发放 ${res.granted} 张，跳过 ${res.skipped} 张（已领过）`)
    grantDialog.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '发放失败')
  } finally {
    granting.value = false
  }
}

async function save() {
  if (!form.value.name.trim() || form.value.value <= 0 || !form.value.startsAt || !form.value.endsAt) {
    ElMessage.warning('请填写名称、面额和有效期')
    return
  }
  saving.value = true
  try {
    await createCoupon({
      name: form.value.name.trim(),
      type: form.value.type,
      value: form.value.value,
      minAmount: form.value.minAmount,
      total: form.value.total,
      startsAt: toIso(form.value.startsAt),
      endsAt: toIso(form.value.endsAt),
    })
    ElMessage.success('优惠券已创建')
    dialog.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="coupons container">
    <header class="head">
      <div>
        <p class="eyebrow">Platform</p>
        <h1>优惠券管理</h1>
      </div>
      <button class="btn btn--solid" @click="openCreate">新建优惠券</button>
    </header>

    <div v-if="loading" class="state">加载中…</div>
    <table v-else class="table">
      <thead>
        <tr><th>名称</th><th>面额</th><th>门槛</th><th>剩余</th><th>有效期至</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="c in list" :key="c.couponId">
          <td>{{ c.name }}</td>
          <td>¥{{ yuan(c.value) }}</td>
          <td>{{ c.minAmount ? '满 ¥' + (c.minAmount / 100).toFixed(0) : '无门槛' }}</td>
          <td>{{ c.remaining }}</td>
          <td>{{ c.expiresAt?.slice(0, 10) }}</td>
          <td><button class="link" @click="openGrant(c)">发放</button></td>
        </tr>
      </tbody>
    </table>

    <el-dialog v-model="grantDialog" :title="'发放：' + (grantTarget?.name || '')" width="440px">
      <div class="form">
        <label class="radio"><input v-model="grantMode" type="radio" value="all" /> 发给全部用户</label>
        <label class="radio"><input v-model="grantMode" type="radio" value="emails" /> 只发给指定邮箱</label>
        <label v-if="grantMode === 'emails'">邮箱（逗号或换行分隔）
          <textarea v-model="grantEmails" rows="4" placeholder="customer2@kdshop.dev, merchant1@kdshop.dev" />
        </label>
        <p class="hint">已领过的用户会自动跳过。</p>
      </div>
      <template #footer>
        <button class="btn btn--ghost" @click="grantDialog = false">取消</button>
        <button class="btn btn--solid" :disabled="granting" @click="doGrant">{{ granting ? '发放中…' : '确认发放' }}</button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialog" title="新建优惠券" width="480px">
      <div class="form">
        <label>名称<input v-model="form.name" placeholder="例如：双十一满减" /></label>
        <label>类型
          <select v-model="form.type">
            <option value="THRESHOLD">满减</option>
            <option value="FIXED">固定金额</option>
          </select>
        </label>
        <div class="row">
          <label>面额（分）<input v-model.number="form.value" type="number" min="1" /></label>
          <label>门槛（分）<input v-model.number="form.minAmount" type="number" min="0" /></label>
        </div>
        <label>发行量<input v-model.number="form.total" type="number" min="1" /></label>
        <div class="row">
          <label>开始<input v-model="form.startsAt" type="date" /></label>
          <label>结束<input v-model="form.endsAt" type="date" /></label>
        </div>
      </div>
      <template #footer>
        <button class="btn btn--ghost" @click="dialog = false">取消</button>
        <button class="btn btn--solid" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.coupons { padding-top: 48px; max-width: 900px; }
.head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 18px; }
h1 { font-size: 34px; margin: 6px 0 0; }
.table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 14px; overflow: hidden; box-shadow: var(--shadow-sm); font-size: 13px; }
th, td { text-align: left; padding: 12px 14px; border-bottom: 1px solid var(--line); }
th { color: var(--muted); font-weight: 500; }
.form { display: grid; gap: 12px; }
.row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
label { display: grid; gap: 6px; font-size: 13px; color: var(--muted); }
input, select { padding: 9px 12px; border: 1px solid var(--line); border-radius: 10px; font-size: 14px; }
.link { border: 0; background: none; color: var(--rust); cursor: pointer; font-size: 12px; }
.radio { display: flex; align-items: center; gap: 8px; }
textarea { padding: 9px 12px; border: 1px solid var(--line); border-radius: 10px; font-size: 13px; resize: vertical; }
.hint { color: var(--muted); font-size: 12px; margin: 0; }
.state { color: var(--muted); padding: 40px 0; }
</style>
