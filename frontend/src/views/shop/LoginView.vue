<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'

const auth = useAuthStore()
const cart = useCartStore()
const router = useRouter()
const route = useRoute()

const email = ref('customer@kdshop.dev')
const password = ref('Demo@123456')
const error = ref('')
const submitting = ref(false)

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    await auth.login(email.value.trim(), password.value)
    const merged = await cart.mergeLocal()
    if (merged?.merged) ElMessage.success(`已合并 ${merged.merged} 件本地购物车商品`)
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (e: any) {
    const code = e?.response?.data?.code
    error.value = code === 20003 ? '账号或密码错误' : (e?.response?.data?.message || '登录失败，请稍后再试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login">
    <div class="card">
      <p class="eyebrow">Sign in</p>
      <h1>登录</h1>
      <p class="tip">演示账号已预填，直接点登录即可。</p>

      <label>
        邮箱
        <input v-model="email" type="email" autocomplete="username" />
      </label>
      <label>
        密码
        <input v-model="password" type="password" autocomplete="current-password" @keyup.enter="submit" />
      </label>

      <p v-if="error" class="error">{{ error }}</p>

      <button class="submit" :disabled="submitting" @click="submit">
        {{ submitting ? '登录中…' : '登录' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.login { display: grid; place-items: center; padding: 72px 16px 120px; }
.card {
  width: min(420px, 100%);
  background: #fff;
  border-radius: 20px;
  padding: 36px 34px;
  box-shadow: var(--shadow);
}
.card h1 { font-size: 30px; margin: 8px 0 4px; }
.tip { color: var(--muted); font-size: 13px; margin: 0 0 22px; }
label { display: block; font-size: 13px; color: var(--muted); margin-bottom: 14px; }
input {
  width: 100%;
  margin-top: 6px;
  padding: 11px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  font-size: 14px;
  outline: none;
}
input:focus { border-color: var(--rust); box-shadow: 0 0 0 3px var(--rust-soft); }
.error { color: #c0392b; font-size: 13px; margin: 0 0 12px; }
.submit {
  width: 100%;
  height: 46px;
  border: 0;
  border-radius: 999px;
  background: var(--rust);
  color: #fff;
  font-size: 15px;
  cursor: pointer;
  box-shadow: 0 10px 22px rgba(217, 72, 15, 0.24);
}
.submit:disabled { opacity: .6; cursor: not-allowed; }
</style>
