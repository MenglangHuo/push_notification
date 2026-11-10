import type { Directive, DirectiveBinding } from 'vue'
import { useAuthStore } from '../stores/auth'

export const vCan: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    checkPermission(el, binding)
  },
  updated(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    checkPermission(el, binding)
  },
}

function checkPermission(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
  const authStore = useAuthStore()
  const value = binding.value

  if (!value) return

  let hasAccess = false
  if (Array.isArray(value)) {
    hasAccess = value.some((perm) => authStore.hasPermission(perm))
  } else {
    hasAccess = authStore.hasPermission(value)
  }

  if (!hasAccess) {
    if (el.parentNode) {
      el.parentNode.removeChild(el)
    } else {
      el.style.display = 'none'
    }
  }
}
