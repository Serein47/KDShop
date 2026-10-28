<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useCartStore } from '@/stores/cart'
import { useAuthStore } from '@/stores/auth'

const cart = useCartStore()
const auth = useAuthStore()

const router = useRouter()
const keyword = ref('')

function search() {
  if (!keyword.value.trim()) return
  router.push({ path: '/products', query: { keyword: keyword.value.trim() } })
}
</script>

<template>
  <div class="promo">
    <div class="container promo__inner">
      <span>满 ¥99 包邮</span>
      <i />
      <span>新人立减 ¥10</span>
      <i />
      <span>定制商品支持工期保障，超时自动赔付</span>
    </div>
  </div>

  <header class="head">
    <div class="container head__inner">
      <RouterLink to="/" class="brand">
        <span class="brand__mark">KD</span>
        <span class="brand__text">好物商城</span>
      </RouterLink>

      <nav class="nav">
        <RouterLink to="/products">全部商品</RouterLink>
        <a href="/#craft">手作专区</a>
        <a href="/#about">关于</a>
      </nav>

      <RouterLink to="/cart" class="cart-link">
        购物车
        <span v-if="cart.count" class="cart-badge">{{ cart.count }}</span>
      </RouterLink>

      <div class="auth">
        <RouterLink v-if="!auth.user" to="/login" class="login-link">登录</RouterLink>
        <template v-else>
          <span class="nick">{{ auth.user.nickname }}</span>
          <button class="logout" @click="auth.logout()">退出</button>
        </template>
      </div>

      <form class="search" @submit.prevent="search">
        <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
          <circle cx="11" cy="11" r="7" fill="none" stroke="currentColor" stroke-width="2" />
          <path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
        </svg>
        <input v-model="keyword" type="search" placeholder="搜一件商品" aria-label="搜索商品" />
      </form>
    </div>
  </header>
</template>

<style scoped>
.promo {
  background: var(--forest);
  color: #efe6d6;
  font-size: 12px;
  letter-spacing: 0.06em;
}
.promo__inner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  height: 34px;
}
.promo i { width: 3px; height: 3px; border-radius: 50%; background: rgba(255,255,255,.45); }

.head {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(250, 246, 239, 0.9);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--line);
}
.head__inner { display: flex; align-items: center; gap: 30px; height: 70px; }
.brand { display: flex; align-items: center; gap: 10px; }
.brand__mark {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: var(--rust);
  color: #fff;
  font-family: var(--serif);
  font-size: 14px;
  box-shadow: 0 6px 14px rgba(217,72,15,.28);
}
.brand__text { font-family: var(--serif); font-size: 17px; letter-spacing: 0.1em; }
.nav { display: flex; gap: 26px; font-size: 14px; color: var(--ink-soft); }
.nav a { position: relative; padding: 6px 0; }
.nav a::after {
  content: "";
  position: absolute;
  left: 0; right: 0; bottom: 0;
  height: 2px;
  background: var(--rust);
  transform: scaleX(0);
  transition: transform .18s ease;
}
.nav a:hover { color: var(--ink); }
.nav a:hover::after { transform: scaleX(1); }

.cart-link {
  position: relative;
  margin-left: auto;
  font-size: 14px;
  color: var(--ink-soft);
  padding: 6px 10px;
  border-radius: 999px;
}
.cart-link:hover { color: var(--rust); }
.cart-badge {
  position: absolute;
  top: -4px;
  right: -8px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--rust);
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}
.auth { display: flex; align-items: center; gap: 10px; font-size: 13px; color: var(--muted); }
.login-link { padding: 6px 14px; border: 1px solid var(--line); border-radius: 999px; color: var(--ink-soft); }
.login-link:hover { border-color: var(--rust); color: var(--rust); }
.nick { color: var(--ink-soft); }
.logout { border: 0; background: none; color: var(--muted); cursor: pointer; font-size: 12px; }
.logout:hover { color: var(--rust); }
.search {
  margin-left: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: #fff;
  color: var(--muted);
  transition: border-color .18s, box-shadow .18s;
}
.search:focus-within {
  border-color: var(--rust);
  box-shadow: 0 0 0 3px var(--rust-soft);
}
.search input {
  border: 0;
  outline: 0;
  width: 180px;
  font-size: 13px;
  background: transparent;
  color: var(--ink);
}
@media (max-width: 900px) {
  .nav { display: none; }
  .search input { width: 120px; }
  .promo__inner span:last-child { display: none; }
}
</style>
