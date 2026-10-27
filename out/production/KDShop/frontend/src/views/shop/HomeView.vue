<script setup lang="ts">
const features = [
  { title: '多商家入驻', desc: '顾客申请开店，平台审核后即可上架商品' },
  { title: '按商家拆单', desc: '一次结算生成主单与子单，各自发货、各自结算' },
  { title: '模拟支付网关', desc: '签名回调、幂等入账、可对账，随时替换真实通道' },
]
</script>

<template>
  <div class="page">
    <header class="hero">
      <h1>KDShop</h1>
      <p>多商家电商平台 · 顾客端 / 商家端 / 平台端</p>
    </header>

    <el-row :gutter="16" class="cards">
      <el-col v-for="item in features" :key="item.title" :xs="24" :sm="8">
        <el-card shadow="hover">
          <h3>{{ item.title }}</h3>
          <p>{{ item.desc }}</p>
        </el-card>
      </el-col>
    </el-row>

    <footer class="foot">
      <el-text type="info">骨架阶段：接口联通后此处替换为商品列表</el-text>
    </footer>
  </div>
</template>

<style scoped>
.page {
  max-width: 1080px;
  margin: 0 auto;
  padding: 48px 16px;
}
.hero h1 {
  margin: 0 0 8px;
  font-size: 40px;
}
.hero p {
  margin: 0 0 32px;
  color: #666;
}
.cards h3 {
  margin: 0 0 8px;
}
.cards p {
  margin: 0;
  color: #666;
  line-height: 1.6;
}
.foot {
  margin-top: 40px;
}
</style>
