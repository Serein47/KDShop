<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createCategory, fetchCategories, updateCategory, type AdminCategory,
} from '@/api/adminCatalog'

const list = ref<AdminCategory[]>([])
const loading = ref(true)
const dialog = ref(false)
const saving = ref(false)
const editing = ref<AdminCategory | null>(null)
const form = ref({ parentId: 0, name: '', sort: 100, featured: false, status: 'ON' })

const tops = computed(() => list.value.filter((c) => !c.parentId))
const chart = computed(() => {
  const rows: { item: AdminCategory; depth: number }[] = []
  for (const top of tops.value) {
    rows.push({ item: top, depth: 0 })
    for (const child of list.value.filter((c) => c.parentId === top.id)) {
      rows.push({ item: child, depth: 1 })
    }
  }
  return rows
})

async function load() {
  loading.value = true
  try {
    list.value = await fetchCategories()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate(parentId = 0) {
  editing.value = null
  form.value = { parentId, name: '', sort: 100, featured: false, status: 'ON' }
  dialog.value = true
}

function openEdit(item: AdminCategory) {
  editing.value = item
  form.value = {
    parentId: item.parentId ?? 0,
    name: item.name,
    sort: item.sort,
    featured: item.featured,
    status: item.status,
  }
  dialog.value = true
}

async function save() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请填写类目名称')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateCategory(editing.value.id, {
        name: form.value.name.trim(),
        sort: form.value.sort,
        featured: form.value.featured,
        status: form.value.status,
      })
    } else {
      await createCategory({
        parentId: form.value.parentId || undefined,
        name: form.value.name.trim(),
        sort: form.value.sort,
        featured: form.value.featured,
      })
    }
    ElMessage.success('已保存')
    dialog.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(item: AdminCategory) {
  try {
    await updateCategory(item.id, { name: item.name, status: item.status === 'ON' ? 'OFF' : 'ON' })
    ElMessage.success(item.status === 'ON' ? '已停用' : '已启用')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}
</script>

<template>
  <div class="cats container">
    <header class="head">
      <div>
        <p class="eyebrow">Platform</p>
        <h1>类目管理</h1>
      </div>
      <button class="btn btn--solid" @click="openCreate(0)">新建一级类目</button>
    </header>

    <div v-if="loading" class="state">加载中…</div>

    <table v-else class="table">
      <thead>
        <tr><th>类目</th><th>层级</th><th>排序</th><th>特色区</th><th>商品数</th><th>状态</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="row in chart" :key="row.item.id">
          <td :style="{ paddingLeft: row.depth ? '34px' : '14px' }">
            {{ row.item.name }}
            <button v-if="!row.depth" class="link" @click="openCreate(row.item.id)">+ 子类目</button>
          </td>
          <td>{{ row.depth ? '二级' : '一级' }}</td>
          <td>{{ row.item.sort }}</td>
          <td>{{ row.item.featured ? '是' : '-' }}</td>
          <td>{{ row.item.productCount }}</td>
          <td>{{ row.item.status === 'ON' ? '启用' : '停用' }}</td>
          <td class="actions">
            <button class="link" @click="openEdit(row.item)">编辑</button>
            <button class="link" @click="toggleStatus(row.item)">{{ row.item.status === 'ON' ? '停用' : '启用' }}</button>
          </td>
        </tr>
      </tbody>
    </table>

    <el-dialog v-model="dialog" :title="editing ? '编辑类目' : '新建类目'" width="460px">
      <div class="form">
        <label>名称<input v-model="form.name" /></label>
        <label>排序<input v-model.number="form.sort" type="number" /></label>
        <label class="check"><input v-model="form.featured" type="checkbox" /> 归入手作特色专区</label>
      </div>
      <template #footer>
        <button class="btn btn--ghost" @click="dialog = false">取消</button>
        <button class="btn btn--solid" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.cats { padding-top: 48px; max-width: 1000px; }
.head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 18px; }
h1 { font-size: 34px; margin: 6px 0 0; }
.table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 14px; overflow: hidden; box-shadow: var(--shadow-sm); font-size: 13px; }
th, td { text-align: left; padding: 12px 14px; border-bottom: 1px solid var(--line); }
th { color: var(--muted); font-weight: 500; }
.link { border: 0; background: none; color: var(--rust); cursor: pointer; font-size: 12px; margin-left: 8px; }
.actions .link:first-child { margin-left: 0; }
.form { display: grid; gap: 14px; }
label { display: grid; gap: 6px; font-size: 13px; color: var(--muted); }
label.check { display: flex; align-items: center; gap: 8px; }
input { padding: 10px 12px; border: 1px solid var(--line); border-radius: 10px; font-size: 14px; }
.state { color: var(--muted); padding: 40px 0; }
</style>
