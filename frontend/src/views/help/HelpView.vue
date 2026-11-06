<script setup lang="ts">
const supportEmail = (import.meta.env.VITE_SUPPORT_EMAIL as string | undefined) ?? 'support@kdshop.dev'
const faqs = [
  { q: '多久发货？', a: '现货商品在付款后 48 小时内发出；预售与定制商品按详情页标注的工期发出。' },
  { q: '运费怎么算？', a: '单个店铺满 ¥99 包邮，未满收 ¥10；多商家订单按店铺分别计算。' },
  { q: '支持退换吗？', a: '支持仅退款与退货退款。定制商品除质量问题外不支持无理由退货，下单前请确认规格。' },
  { q: '怎么修改收货地址？', a: '登录后在「个人中心 → 收货地址」中新增、修改或设为默认。' },
  { q: '订单多久会自动取消？', a: '下单后 15 分钟内未支付会自动取消，锁定库存与优惠券同时释放。' },
  { q: '怎么成为商家？', a: '登录后点顶部「商家入驻」提交申请，平台审核通过即可开通店铺。' },
]
</script>

<template>
  <div class="help container">
    <p class="eyebrow">Support</p>
    <h1>帮助与客服</h1>

    <section class="card">
      <h2>常见问题</h2>
      <dl class="faq">
        <template v-for="item in faqs" :key="item.q">
          <dt>{{ item.q }}</dt>
          <dd>{{ item.a }}</dd>
        </template>
      </dl>
    </section>

    <section class="card">
      <h2>联系我们</h2>
      <p class="line">邮箱：<a :href="`mailto:${supportEmail}`">{{ supportEmail }}</a></p>
      <p class="line">工作时间：周一至周五 10:00–18:00</p>
      <p class="note">演示环境说明：<strong>kdshop.dev 是虚构域名，这个邮箱不存在</strong>，发信不会有人收到。接入真实客服时，用环境变量 <code>VITE_SUPPORT_EMAIL</code> 换成真实地址即可。</p>
    </section>

    <RouterLink to="/products" class="btn btn--ghost">继续逛逛</RouterLink>
  </div>
</template>

<style scoped>
.help { padding-top: 48px; max-width: 760px; }
h1 { font-size: 34px; margin: 6px 0 22px; }
.card { background: #fff; border-radius: 18px; padding: 22px 24px; box-shadow: var(--shadow-sm); margin-bottom: 18px; }
.card h2 { font-size: 18px; margin: 0 0 16px; }
.faq { margin: 0; }
.faq dt { font-size: 15px; margin-top: 16px; }
.faq dt:first-child { margin-top: 0; }
.faq dd { margin: 6px 0 0; color: var(--muted); font-size: 13px; line-height: 1.8; }
.line { font-size: 14px; margin: 0 0 8px; }
.line a { color: var(--rust); }
.note { color: var(--muted); font-size: 12px; margin: 14px 0 0; line-height: 1.8; }
.note code { background: var(--rust-soft); padding: 1px 6px; border-radius: 4px; font-size: 11px; }
</style>
