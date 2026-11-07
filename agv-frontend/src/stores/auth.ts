import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as loginApi, type UserInfo } from '@/api/auth'

const TOKEN_KEY = 'agv_token'
const USER_KEY = 'agv_user'

function readUser(): UserInfo | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as UserInfo) : null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref<UserInfo | null>(readUser())

  const role = computed(() => user.value?.role || '')
  const isAdmin = computed(() => role.value === 'admin')
  const displayName = computed(() => user.value?.realName || user.value?.username || '')

  async function login(username: string, password: string) {
    const result = await loginApi(username, password)
    token.value = result.token
    user.value = result.user
    localStorage.setItem(TOKEN_KEY, result.token)
    localStorage.setItem(USER_KEY, JSON.stringify(result.user))
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return { token, user, role, isAdmin, displayName, login, logout }
})
