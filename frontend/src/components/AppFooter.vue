<template>
  <footer class="foot">
    <div class="container foot__inner">
      <p class="foot__quote">把日常用得上的，挑得讲究些。</p>
      <nav class="foot__links">
        <RouterLink to="/products">全部商品</RouterLink>
        <RouterLink to="/favorites">收藏夹</RouterLink>
        <RouterLink to="/orders">我的订单</RouterLink>
        <RouterLink to="/account">个人中心</RouterLink>
        <RouterLink to="/help">帮助 / 客服</RouterLink>
      </nav>
      <div class="foot__meta">
        <span>KDShop · 多商家商城</span>
        <span>演示环境 · 数据均为虚构</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.foot {
  margin-top: 96px;
  border-top: 1px solid var(--line);
  padding: 48px 0 56px;
}
.foot__quote {
  font-family: var(--serif);
  font-size: 20px;
  color: var(--ink-soft);
  margin: 0 0 24px;
}
.foot__links { display: flex; flex-wrap: wrap; gap: 20px; font-size: 13px; margin-bottom: 24px; }
.foot__links a { color: var(--ink-soft); }
.foot__links a:hover { color: var(--rust); }
.foot__meta {
  display: flex;
  justify-content: space-between;
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.06em;
}
</style>
