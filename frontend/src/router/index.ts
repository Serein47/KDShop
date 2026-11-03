import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/shop/HomeView.vue') },
    { path: '/products', name: 'products', component: () => import('@/views/shop/ProductListView.vue') },
    { path: '/products/:id', name: 'product-detail', component: () => import('@/views/shop/ProductDetailView.vue') },
    { path: '/shops/:id', name: 'shop', component: () => import('@/views/shop/ShopView.vue') },
    { path: '/cart', name: 'cart', component: () => import('@/views/shop/CartView.vue') },
    { path: '/login', name: 'login', component: () => import('@/views/shop/LoginView.vue') },
    { path: '/merchant/apply', name: 'merchant-apply', component: () => import('@/views/merchant/ApplyView.vue') },
    { path: '/admin/merchants', name: 'admin-merchants', component: () => import('@/views/admin/MerchantsView.vue') },
    { path: '/checkout', name: 'checkout', component: () => import('@/views/shop/CheckoutView.vue') },
    { path: '/orders', name: 'orders', component: () => import('@/views/shop/OrdersView.vue') },
    { path: '/orders/:orderNo', name: 'order-detail', component: () => import('@/views/shop/OrderDetailView.vue') },
    { path: '/mock-pay/:paymentNo', name: 'mock-pay', component: () => import('@/views/shop/MockPayView.vue') },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/shop/NotFoundView.vue') },
  ],
})

export default router
