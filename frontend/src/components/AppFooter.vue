<template>
  <footer class="foot">
    <div class="container foot__inner">
      <p class="foot__quote">手作的意义，是让每一件东西都带着人的时间。</p>
      <div class="foot__meta">
        <span>KDShop · 独立设计与手作市集</span>
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
