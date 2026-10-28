<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <div class="nf container">
    <p class="eyebrow">404</p>
    <h1>这里空无一物</h1>
    <p class="text">你要找的页面可能已经下架，或者从未存在过。</p>
    <RouterLink to="/" class="btn">回到首页</RouterLink>
  </div>
</template>

<style scoped>
.nf { padding: 140px 0; text-align: center; }
.nf h1 { font-size: clamp(30px, 5vw, 48px); margin: 10px 0 14px; }
.text { color: var(--muted); margin-bottom: 28px; }
.btn {
  display: inline-block;
  padding: 12px 28px;
  border-radius: 999px;
  background: var(--ink);
  color: var(--paper);
}
</style>
