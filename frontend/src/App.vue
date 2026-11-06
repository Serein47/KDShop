<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { RouterView } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'

const auth = useAuthStore()
const cart = useCartStore()
onMounted(async () => {
  await auth.fetchMe()
  await cart.load()
})

watch(() => auth.user?.id, async () => {
  await cart.load()
})
</script>

<template>
  <AppHeader />
  <main>
    <RouterView />
  </main>
  <AppFooter />
</template>
