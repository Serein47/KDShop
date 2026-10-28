<template>
  <footer class="foot">
    <div class="container foot__inner">
      <p class="foot__quote">把日常用得上的，挑得讲究些。</p>
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
.foot__meta {
  display: flex;
  justify-content: space-between;
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.06em;
}
</style>
