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
    { path: '/merchant/shop', name: 'merchant-shop', component: () => import('@/views/merchant/ShopView.vue') },
    { path: '/merchant/products', name: 'merchant-products', component: () => import('@/views/merchant/ProductsView.vue') },
    { path: '/merchant/orders', name: 'merchant-orders', component: () => import('@/views/merchant/OrdersView.vue') },
    { path: '/admin', name: 'admin-dashboard', component: () => import('@/views/admin/DashboardView.vue') },
    { path: '/admin/audit', name: 'admin-audit', component: () => import('@/views/admin/AuditView.vue') },
    { path: '/admin/categories', name: 'admin-categories', component: () => import('@/views/admin/CategoriesView.vue') },
    { path: '/admin/products', name: 'admin-products', component: () => import('@/views/admin/ProductsView.vue') },
    { path: '/admin/settlements', name: 'admin-settlements', component: () => import('@/views/admin/SettlementsView.vue') },
    { path: '/merchant/settlements', name: 'merchant-settlements', component: () => import('@/views/merchant/SettlementsView.vue') },
    { path: '/admin/merchants', name: 'admin-merchants', component: () => import('@/views/admin/MerchantsView.vue') },
    { path: '/checkout', name: 'checkout', component: () => import('@/views/shop/CheckoutView.vue') },
    { path: '/account', name: 'account', component: () => import('@/views/account/AccountView.vue') },
    { path: '/help', name: 'help', component: () => import('@/views/help/HelpView.vue') },
    { path: '/favorites', name: 'favorites', component: () => import('@/views/shop/FavoritesView.vue') },
    { path: '/refunds', name: 'refunds', component: () => import('@/views/refund/RefundsView.vue') },
    { path: '/merchant/refunds', name: 'merchant-refunds', component: () => import('@/views/merchant/RefundsView.vue') },
    { path: '/orders', name: 'orders', component: () => import('@/views/shop/OrdersView.vue') },
    { path: '/orders/:orderNo', name: 'order-detail', component: () => import('@/views/shop/OrderDetailView.vue') },
    { path: '/mock-pay/:paymentNo', name: 'mock-pay', component: () => import('@/views/shop/MockPayView.vue') },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/shop/NotFoundView.vue') },
  ],
})

export default router
