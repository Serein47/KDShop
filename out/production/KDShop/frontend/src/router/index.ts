import { createRouter, createWebHistory } from 'vue-router'

/**
 * 三套界面共用一个构建，按前缀分包懒加载。
 * 商家端与平台端的路由在对应 Issue 里补齐。
 */
const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/shop/HomeView.vue'),
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/shop/NotFoundView.vue'),
    },
  ],
})

export default router
