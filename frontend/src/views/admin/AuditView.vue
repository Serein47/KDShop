<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchAuditLogs, type AuditLog } from '@/api/admin'

const list = ref<AuditLog[]>([])
const loading = ref(true)
const action = ref('')
const actorType = ref('')

const actions = [
  { value: '', label: '全部动作' },
  { value: 'MERCHANT_APPROVE', label: '通过入驻' },
  { value: 'MERCHANT_REJECT', label: '驳回入驻' },
  { value: 'REFUND_APPROVE', label: '同意退款' },
  { value: 'REFUND_REJECT', label: '驳回退款' },
]

async function load() {
  loading.value = true
  try {
    list.value = await fetchAuditLogs({ action: action.value || undefined, actorType: actorType.value || undefined })
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function fmt(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}
</script>

<template>
  <div class="audit container">
    <header class="head">
      <div>
        <p class="eyebrow">Platform</p>
        <h1>审计日志</h1>
      </div>
      <nav class="links">
        <RouterLink to="/admin">平台看板</RouterLink>
        <RouterLink to="/admin/merchants">商家审核</RouterLink>
        <RouterLink to="/admin/orders">订单</RouterLink>
        <RouterLink to="/admin/refunds">退款</RouterLink>
        <RouterLink to="/admin/categories">类目</RouterLink>
        <RouterLink to="/admin/products">商品</RouterLink>
        <RouterLink to="/admin/settlements">结算</RouterLink>
        <RouterLink to="/admin/coupons">优惠券</RouterLink>
        <RouterLink to="/admin/users">账号</RouterLink>
      </nav>
    </header>

    <div class="filters">
      <select v-model="action" @change="load">
        <option v-for="item in actions" :key="item.value" :value="item.value">{{ item.label }}</option>
      </select>
      <select v-model="actorType" @change="load">
        <option value="">全部操作者</option>
        <option value="ADMIN">平台</option>
        <option value="MERCHANT">商家</option>
      </select>
    </div>

    <div v-if="loading" class="state">加载中…</div>
    <p v-else-if="!list.length" class="state">还没有审计记录。审核商家或处理退款后会写在这里。</p>

    <table v-else class="table">
      <thead>
        <tr><th>时间</th><th>操作者</th><th>动作</th><th>对象</th><th>变更</th></tr>
      </thead>
      <tbody>
        <tr v-for="log in list" :key="log.id">
          <td>{{ fmt(log.createdAt) }}</td>
          <td>{{ log.actorType }} #{{ log.actorId ?? '-' }}</td>
          <td>{{ log.action }}</td>
          <td>{{ log.targetType }} #{{ log.targetId }}</td>
          <td class="muted">{{ log.beforeJson }} → {{ log.afterJson }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.audit { padding-top: 48px; max-width: 1000px; }
.head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 18px; }
h1 { font-size: 34px; margin: 6px 0 0; }
.links { display: flex; gap: 16px; font-size: 13px; }
.links a { color: var(--ink-soft); }
.links a:hover { color: var(--rust); }
.filters { display: flex; gap: 12px; margin-bottom: 16px; }
select { padding: 8px 12px; border: 1px solid var(--line); border-radius: 10px; font-size: 13px; background: #fff; }
.table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 14px; overflow: hidden; box-shadow: var(--shadow-sm); font-size: 13px; }
th, td { text-align: left; padding: 12px 14px; border-bottom: 1px solid var(--line); }
th { color: var(--muted); font-weight: 500; }
.muted { color: var(--muted); }
.state { color: var(--muted); padding: 40px 0; }
</style>
