<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchAdminProducts, forceOffline, forceOnline, type AdminProduct,
} from '@/api/adminCatalog'
import { yuan } from '@/api/catalog'
import { mediaUrl } from '@/api/media'

const list = ref<AdminProduct[]>([])
const loading = ref(true)
const keyword = ref('')
const status = ref('')

async function load() {
  loading.value = true
  try {
    list.value = await fetchAdminProducts({
      keyword: keyword.value || undefined,
      status: status.value || undefined,
    })
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function offline(item: AdminProduct) {
  try {
    const { value } = await ElMessageBox.prompt('填写下架原因（商家可见）', '强制下架', {
      inputPlaceholder: '例如：标题含违规词',
      inputValidator: (v) => (v && v.trim().length > 0) || '原因不能为空',
    })
    await forceOffline(item.id, value)
    ElMessage.success('已下架')
    await load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

async function online(item: AdminProduct) {
  try {
    await forceOnline(item.id)
    ElMessage.success('已恢复上架')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}
</script>

<template>
  <div class="products container">
    <p class="eyebrow">Platform</p>
    <h1>商品治理</h1>

    <div class="filters">
      <input v-model="keyword" placeholder="搜商品名" @keyup.enter="load" />
      <select v-model="status" @change="load">
        <option value="">全部状态</option>
        <option value="ON">在售</option>
        <option value="OFF">已下架</option>
      </select>
      <button class="btn btn--ghost" @click="load">查询</button>
    </div>

    <div v-if="loading" class="state">加载中…</div>
    <p v-else-if="!list.length" class="state">没有符合条件的商品。</p>

    <table v-else class="table">
      <thead>
        <tr><th>商品</th><th>店铺</th><th>类目</th><th>价格</th><th>库存</th><th>销量</th><th>状态</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="item in list" :key="item.id">
          <td class="cell">
            <img v-if="item.mainImage" :src="mediaUrl(item.mainImage)" :alt="item.name" class="thumb" />
            <span>{{ item.name }}</span>
          </td>
          <td>{{ item.shopName }}</td>
          <td>{{ item.categoryName }}</td>
          <td>¥{{ yuan(item.priceMin) }}</td>
          <td>{{ item.stock }}</td>
          <td>{{ item.salesCount }}</td>
          <td>
            {{ item.status === 'ON' ? '在售' : '已下架' }}
            <span v-if="item.offlineReason" class="reason">（{{ item.offlineReason }}）</span>
          </td>
          <td>
            <button v-if="item.status === 'ON'" class="link" @click="offline(item)">强制下架</button>
            <button v-else class="link" @click="online(item)">恢复上架</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.products { padding-top: 48px; max-width: 1100px; }
h1 { font-size: 34px; margin: 6px 0 18px; }
.filters { display: flex; gap: 12px; margin-bottom: 16px; }
input, select { padding: 8px 12px; border: 1px solid var(--line); border-radius: 10px; font-size: 13px; }
.table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 14px; overflow: hidden; box-shadow: var(--shadow-sm); font-size: 13px; }
th, td { text-align: left; padding: 10px 12px; border-bottom: 1px solid var(--line); }
th { color: var(--muted); font-weight: 500; }
.cell { display: flex; align-items: center; gap: 10px; }
.thumb { width: 34px; height: 34px; border-radius: 6px; object-fit: cover; }
.reason { color: #c0392b; font-size: 11px; }
.link { border: 0; background: none; color: var(--rust); cursor: pointer; font-size: 12px; }
.state { color: var(--muted); padding: 40px 0; }
</style>
