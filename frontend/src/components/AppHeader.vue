<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

const router = useRouter()
const keyword = ref('')

function search() {
  if (!keyword.value.trim()) return
  router.push({ path: '/products', query: { keyword: keyword.value.trim() } })
}
</script>

<template>
  <header class="head">
    <div class="container head__inner">
      <RouterLink to="/" class="brand">
        <span class="brand__mark">KD</span>
        <span class="brand__text">好物商城</span>
      </RouterLink>

      <nav class="nav">
        <RouterLink to="/products">全部商品</RouterLink>
        <a href="/#craft">按工艺</a>
        <a href="/#about">关于</a>
      </nav>

      <form class="search" @submit.prevent="search">
        <input v-model="keyword" type="search" placeholder="搜一件手作" aria-label="搜索作品" />
      </form>
    </div>
  </header>
</template>

<style scoped>
.head {
  position: sticky;
  top: 0;
  z-index: 20;
  backdrop-filter: blur(10px);
  background: color-mix(in srgb, var(--paper) 82%, transparent);
  border-bottom: 1px solid var(--line);
}
.head__inner {
  display: flex;
  align-items: center;
  gap: 32px;
  height: 68px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.brand__mark {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--ink);
  color: var(--paper);
  font-family: var(--serif);
  font-size: 14px;
  letter-spacing: 0.02em;
}
.brand__text {
  font-family: var(--serif);
  font-size: 17px;
  letter-spacing: 0.12em;
}
.nav {
  display: flex;
  gap: 24px;
  font-size: 14px;
  color: var(--ink-soft);
}
.nav a { position: relative; }
.nav a:hover { color: var(--rust); }
.search input {
  width: 200px;
  padding: 8px 14px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.55);
  font-size: 13px;
  outline: none;
  transition: width 0.2s ease, border-color 0.2s;
}
.search input:focus {
  width: 240px;
  border-color: var(--rust);
}
@media (max-width: 860px) {
  .nav { display: none; }
  .search input { width: 130px; }
}
</style>
