<script setup lang="ts">
</script>

<template>
  <div class="fav container">
    <p class="eyebrow">Favorites</p>
    <h1>收藏夹</h1>
    <div class="empty">
      <p class="empty__title">收藏功能正在接入</p>
      <p class="empty__text">很快就能在这里看到你收藏的商品和关注的店铺。</p>
      <RouterLink to="/products" class="btn btn--solid">去逛逛</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.fav { padding-top: 48px; max-width: 760px; }
h1 { font-size: 34px; margin: 6px 0 24px; }
.empty { text-align: center; padding: 70px 0; }
.empty__title { font-family: var(--serif); font-size: 22px; margin: 0 0 8px; }
.empty__text { color: var(--muted); margin: 0 0 20px; }
</style>
