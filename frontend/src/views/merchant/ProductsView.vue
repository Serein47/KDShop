<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createProduct, fetchProducts, setProductStatus, updateProduct, uploadImage, type MerchantProduct,
} from '@/api/merchantProduct'
import { fetchCategories, yuan, type CategoryNode } from '@/api/catalog'
import { mediaUrl } from '@/api/media'

const list = ref<MerchantProduct[]>([])
const status = ref('')
const loading = ref(true)
const editing = ref<MerchantProduct | null>(null)
const dialog = ref(false)
const saving = ref(false)
const uploading = ref(false)
const categories = ref<CategoryNode[]>([])
const form = ref({ name: '', subtitle: '', categoryId: 0, price: 0, stock: 0, mainImage: '' })

const leaves = computed(() => {
  const out: { id: number; label: string }[] = []
  for (const top of categories.value) {
    for (const child of top.children ?? []) out.push({ id: child.id, label: top.name + ' / ' + child.name })
  }
  return out
})

async function load() {
  loading.value = true
  try {
    list.value = await fetchProducts(status.value || undefined)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  categories.value = await fetchCategories()
  await load()
})

function openCreate() {
  editing.value = null
  form.value = { name: '', subtitle: '', categoryId: leaves.value[0]?.id ?? 0, price: 0, stock: 0, mainImage: '' }
  dialog.value = true
}

function openEdit(item: MerchantProduct) {
  editing.value = item
  form.value = {
    name: item.name,
    subtitle: item.subtitle ?? '',
    categoryId: item.categoryId,
    price: item.priceMin,
    stock: item.stock,
    mainImage: item.mainImage ?? '',
  }
  dialog.value = true
}

async function onFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    form.value.mainImage = await uploadImage(file)
    ElMessage.success('图片已上传')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function save() {
  if (!form.value.name.trim() || !form.value.categoryId || form.value.price <= 0) {
    ElMessage.warning('请填写名称、类目和价格')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.value.name.trim(),
      subtitle: form.value.subtitle || undefined,
      categoryId: form.value.categoryId,
      price: form.value.price,
      stock: form.value.stock,
      mainImage: form.value.mainImage || undefined,
    }
    if (editing.value) await updateProduct(editing.value.id, payload)
    else await createProduct(payload)
    ElMessage.success(editing.value ? '已保存' : '已创建')
    dialog.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function toggle(item: MerchantProduct) {
  try {
    await setProductStatus(item.id, item.status === 'ON' ? 'OFF' : 'ON')
    ElMessage.success(item.status === 'ON' ? '已下架' : '已上架')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}
</script>

<template>
  <div class="products container">
    <header class="head">
      <div>
        <p class="eyebrow">Merchant</p>
        <h1>商品管理</h1>
      </div>
      <button class="btn btn--solid" @click="openCreate">新建商品</button>
    </header>

    <div class="tabs">
      <button :class="{ on: status === '' }" @click="status = ''; load()">全部</button>
      <button :class="{ on: status === 'ON' }" @click="status = 'ON'; load()">在售</button>
      <button :class="{ on: status === 'OFF' }" @click="status = 'OFF'; load()">已下架</button>
    </div>

    <div v-if="loading" class="state">加载中…</div>
    <p v-else-if="!list.length" class="state">还没有商品，先新建一个。</p>

    <div v-else class="grid">
      <article v-for="item in list" :key="item.id" class="card">
        <div class="thumb">
          <img v-if="item.mainImage" :src="mediaUrl(item.mainImage)" :alt="item.name" />
          <span v-else>{{ item.name.slice(0, 1) }}</span>
        </div>
        <div class="info">
          <h2>{{ item.name }}</h2>
          <p class="muted">{{ item.categoryName }} · {{ item.status === 'ON' ? '在售' : '已下架' }}</p>
          <p class="price">¥{{ yuan(item.priceMin) }}<span v-if="item.priceMax !== item.priceMin"> 起</span></p>
          <p class="muted">库存 {{ item.stock }} · 已售 {{ item.salesCount }}</p>
        </div>
        <div class="actions">
          <button class="btn btn--ghost" @click="openEdit(item)">编辑</button>
          <button class="btn btn--ghost" @click="toggle(item)">{{ item.status === 'ON' ? '下架' : '上架' }}</button>
        </div>
      </article>
    </div>

    <el-dialog v-model="dialog" :title="editing ? '编辑商品' : '新建商品'" width="520px">
      <div class="form">
        <label>名称<input v-model="form.name" placeholder="例如：粗陶马克杯" /></label>
        <label>副标题<input v-model="form.subtitle" placeholder="一句话卖点" /></label>
        <label>类目
          <select v-model.number="form.categoryId">
            <option v-for="leaf in leaves" :key="leaf.id" :value="leaf.id">{{ leaf.label }}</option>
          </select>
        </label>
        <div class="row">
          <label>价格（分）<input v-model.number="form.price" type="number" min="1" /></label>
          <label>库存<input v-model.number="form.stock" type="number" min="0" /></label>
        </div>
        <label>主图
          <input type="file" accept="image/jpeg,image/png,image/webp" @change="onFile" />
        </label>
        <p v-if="uploading" class="muted">上传中…</p>
        <div v-if="form.mainImage" class="preview">
          <img :src="mediaUrl(form.mainImage)" alt="预览" />
          <span class="muted">{{ form.mainImage }}</span>
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
.products { padding-top: 48px; max-width: 1000px; }
.head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 18px; }
h1 { font-size: 34px; margin: 6px 0 0; }
.tabs { display: flex; gap: 10px; margin-bottom: 20px; }
.tabs button { padding: 8px 16px; border: 1px solid var(--line); background: none; border-radius: 999px; cursor: pointer; font-size: 13px; }
.tabs button.on { border-color: var(--rust); color: var(--rust); background: var(--rust-soft); }
.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.card { display: grid; grid-template-columns: 88px 1fr; gap: 14px; background: #fff; border-radius: 16px; padding: 16px; box-shadow: var(--shadow-sm); }
.thumb { width: 88px; height: 88px; border-radius: 12px; background: var(--rust-soft); display: grid; place-items: center; overflow: hidden; font-size: 28px; color: var(--rust); }
.thumb img { width: 100%; height: 100%; object-fit: cover; }
.info h2 { font-size: 16px; margin: 0 0 4px; }
.muted { color: var(--muted); font-size: 12px; margin: 2px 0; }
.price { color: var(--rust); font-weight: 700; margin: 6px 0 2px; }
.actions { grid-column: 1 / -1; display: flex; gap: 8px; }
.state { color: var(--muted); padding: 40px 0; }
.form { display: grid; gap: 14px; }
.row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
label { display: grid; gap: 6px; font-size: 13px; color: var(--muted); }
input, select { padding: 10px 12px; border: 1px solid var(--line); border-radius: 10px; font-size: 14px; }
.preview { display: flex; gap: 10px; align-items: center; }
.preview img { width: 56px; height: 56px; object-fit: cover; border-radius: 8px; }
@media (max-width: 820px) { .grid { grid-template-columns: 1fr; } }
</style>
