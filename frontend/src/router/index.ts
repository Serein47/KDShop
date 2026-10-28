import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/shop/HomeView.vue') },
    { path: '/products', name: 'products', component: () => import('@/views/shop/ProductListView.vue') },
    { path: '/products/:id', name: 'product-detail', component: () => import('@/views/shop/ProductDetailView.vue') },
    { path: '/shops/:id', name: 'shop', component: () => import('@/views/shop/ShopView.vue') },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/shop/NotFoundView.vue') },
  ],
})

export default router
