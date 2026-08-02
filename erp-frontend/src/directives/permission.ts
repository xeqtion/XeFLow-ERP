import type { App, Directive } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * 按钮级权限指令: v-permission="'system:user:create'"
 * 无权限时移除元素。admin 角色放行全部。
 */
const permission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const userStore = useUserStore()
    const required = binding.value
    const list = Array.isArray(required) ? required : [required]
    const ok =
      userStore.roles.includes('super_admin') ||
      userStore.roles.includes('admin') ||
      list.some((p) => userStore.permissions.includes(p))
    if (!ok) {
      el.parentNode?.removeChild(el)
    }
  }
}

export function setupDirectives(app: App) {
  app.directive('permission', permission)
}
