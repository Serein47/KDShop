<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  assignRoles, disableUser, enableUser, fetchAdminUsers, ROLE_LABELS, type AdminUser,
} from '@/api/adminUser'

const list = ref<AdminUser[]>([])
const loading = ref(true)
const keyword = ref('')
const status = ref('')
const roleDialog = ref(false)
const roleTarget = ref<AdminUser | null>(null)
const selectedRoles = ref<string[]>([])
const saving = ref(false)
const allRoles = Object.keys(ROLE_LABELS)

async function load() {
  loading.value = true
  try {
    list.value = await fetchAdminUsers({ keyword: keyword.value || undefined, status: status.value || undefined })
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function toggle(user: AdminUser) {
  try {
    if (user.status === 'DISABLED') {
      await enableUser(user.id)
      ElMessage.success('已启用')
    } else {
      const { value } = await ElMessageBox.prompt('填写禁用原因', '禁用账号', {
        inputPlaceholder: '例如：多次恶意下单',
        inputValidator: (v) => (v && v.trim().length > 0) || '原因不能为空',
      })
      await disableUser(user.id, value)
      ElMessage.success('已禁用')
    }
    await load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

function openRoles(user: AdminUser) {
  roleTarget.value = user
  selectedRoles.value = [...user.roles]
  roleDialog.value = true
}

async function saveRoles() {
  if (!roleTarget.value) return
  saving.value = true
  try {
    await assignRoles(roleTarget.value.id, selectedRoles.value)
    ElMessage.success('角色已更新')
    roleDialog.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="users container">
    <p class="eyebrow">Platform</p>
    <h1>账号与权限</h1>

    <div class="filters">
      <input v-model="keyword" placeholder="邮箱或昵称" @keyup.enter="load" />
      <select v-model="status" @change="load">
        <option value="">全部状态</option>
        <option value="ACTIVE">正常</option>
        <option value="DISABLED">已禁用</option>
      </select>
      <button class="btn btn--ghost" @click="load">查询</button>
    </div>

    <div v-if="loading" class="state">加载中…</div>
    <table v-else class="table">
      <thead>
        <tr><th>账号</th><th>昵称</th><th>角色</th><th>状态</th><th>最近登录</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="u in list" :key="u.id">
          <td>{{ u.email }}</td>
          <td>{{ u.nickname }}</td>
          <td>
            <span v-for="r in u.roles" :key="r" class="tag">{{ ROLE_LABELS[r] || r }}</span>
            <span v-if="!u.roles.length" class="muted">—</span>
          </td>
          <td>{{ u.status === 'ACTIVE' ? '正常' : '已禁用' }}</td>
          <td>{{ u.lastLoginAt ? u.lastLoginAt.replace('T', ' ').slice(0, 16) : '—' }}</td>
          <td class="actions">
            <button class="link" @click="openRoles(u)">角色</button>
            <button class="link" @click="toggle(u)">{{ u.status === 'ACTIVE' ? '禁用' : '启用' }}</button>
          </td>
        </tr>
      </tbody>
    </table>

    <el-dialog v-model="roleDialog" :title="'分配角色：' + (roleTarget?.email || '')" width="420px">
      <el-checkbox-group v-model="selectedRoles" class="roles">
        <el-checkbox v-for="r in allRoles" :key="r" :value="r">{{ ROLE_LABELS[r] }}</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <button class="btn btn--ghost" @click="roleDialog = false">取消</button>
        <button class="btn btn--solid" :disabled="saving" @click="saveRoles">{{ saving ? '保存中…' : '保存' }}</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.users { padding-top: 48px; max-width: 1000px; }
h1 { font-size: 34px; margin: 6px 0 18px; }
.filters { display: flex; gap: 12px; margin-bottom: 16px; }
input, select { padding: 8px 12px; border: 1px solid var(--line); border-radius: 10px; font-size: 13px; }
.table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 14px; overflow: hidden; box-shadow: var(--shadow-sm); font-size: 13px; }
th, td { text-align: left; padding: 10px 12px; border-bottom: 1px solid var(--line); }
th { color: var(--muted); font-weight: 500; }
.tag { display: inline-block; font-size: 11px; border: 1px solid var(--line); border-radius: 999px; padding: 1px 8px; margin-right: 4px; color: var(--muted); }
.link { border: 0; background: none; color: var(--rust); cursor: pointer; font-size: 12px; margin-right: 8px; }
.muted { color: var(--muted); }
.roles { display: grid; gap: 8px; }
.state { color: var(--muted); padding: 40px 0; }
</style>
