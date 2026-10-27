import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface CurrentUser {
  id: number
  nickname: string
  roles: string[]
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<CurrentUser | null>(null)
  const loading = ref(false)

  function setUser(next: CurrentUser | null) {
    user.value = next
  }

  function hasRole(role: string) {
    return user.value?.roles.includes(role) ?? false
  }

  return { user, loading, setUser, hasRole }
})
