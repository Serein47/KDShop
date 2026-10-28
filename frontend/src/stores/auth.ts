import { defineStore } from 'pinia'
import { ref } from 'vue'
import http, { type ApiResponse } from '@/api/http'

export interface CurrentUser {
  id: number
  nickname: string
  email: string
  roles: string[]
  merchant?: { id: number; shopName: string; status: string } | null
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<CurrentUser | null>(null)
  const loading = ref(false)
  const ready = ref(false)

  async function fetchMe() {
    try {
      const { data } = await http.get<ApiResponse<CurrentUser>>('/auth/me')
      user.value = data.data
    } catch {
      user.value = null
    } finally {
      ready.value = true
    }
  }

  async function login(email: string, password: string) {
    loading.value = true
    try {
      const { data } = await http.post<ApiResponse<CurrentUser>>('/auth/login', { email, password })
      user.value = data.data
      return data.data
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    try {
      await http.post('/auth/logout')
    } finally {
      user.value = null
    }
  }

  function hasRole(role: string) {
    return user.value?.roles.includes(role) ?? false
  }

  return { user, loading, ready, fetchMe, login, logout, hasRole, setUser: (u: CurrentUser | null) => (user.value = u) }
})
