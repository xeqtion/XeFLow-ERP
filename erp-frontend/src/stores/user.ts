import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  login as loginApi,
  logout as logoutApi,
  getUserInfo,
  getMenus,
  type LoginParams,
  type MenuNode
} from '@/api/auth'

const TOKEN_KEY = 'erp_token'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const userId = ref<number | string>('')
  const username = ref('')
  const realName = ref('')
  const nickname = ref('')
  const email = ref('')
  const phone = ref('')
  const avatar = ref('')
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])
  const menus = ref<MenuNode[]>([])
  const loaded = ref(false)

  function setToken(value: string) {
    token.value = value
    localStorage.setItem(TOKEN_KEY, value)
  }

  async function login(params: LoginParams) {
    const res = await loginApi(params)
    setToken(res.data.token)
  }

  async function loadUserInfo() {
    const [infoRes, menuRes] = await Promise.all([getUserInfo(), getMenus()])
    const info = infoRes.data
    userId.value = info.userId
    username.value = info.username
    realName.value = info.realName
    nickname.value = info.nickname || ''
    email.value = info.email || ''
    phone.value = info.phone || ''
    avatar.value = info.avatar || ''
    roles.value = info.roles || []
    permissions.value = info.permissions || []
    menus.value = menuRes.data || []
    loaded.value = true
  }

  function hasPermission(perm: string) {
    return permissions.value.includes(perm) || roles.value.includes('super_admin') || roles.value.includes('admin')
  }

  function reset() {
    token.value = ''
    userId.value = ''
    username.value = ''
    realName.value = ''
    nickname.value = ''
    email.value = ''
    phone.value = ''
    avatar.value = ''
    roles.value = []
    permissions.value = []
    menus.value = []
    loaded.value = false
    localStorage.removeItem(TOKEN_KEY)
  }

  async function logout() {
    try {
      await logoutApi()
    } finally {
      reset()
    }
  }

  return {
    token,
    userId,
    username,
    realName,
    nickname,
    email,
    phone,
    avatar,
    roles,
    permissions,
    menus,
    loaded,
    setToken,
    login,
    loadUserInfo,
    hasPermission,
    logout,
    reset
  }
})
