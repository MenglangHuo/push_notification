import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ToastMessage } from '@/types/toast'

export const useToastStore = defineStore('toast', () => {
  const toasts = ref<ToastMessage[]>([])

  function addToast(toast: Omit<ToastMessage, 'id'> & { id?: string }) {
    const id = toast.id || Math.random().toString(36).substring(2, 9)
    const newToast: ToastMessage = {
      id,
      type: toast.type || 'info',
      title: toast.title,
      message: toast.message,
      duration: toast.duration ?? 4000,
    }

    toasts.value.push(newToast)

    if (newToast.duration && newToast.duration > 0) {
      setTimeout(() => {
        removeToast(id)
      }, newToast.duration)
    }
  }

  function removeToast(id: string) {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }

  function success(title: string, message?: string) {
    addToast({ type: 'success', title, message })
  }

  function error(title: string, message?: string) {
    addToast({ type: 'error', title, message, duration: 6000 })
  }

  function info(title: string, message?: string) {
    addToast({ type: 'info', title, message })
  }

  function warning(title: string, message?: string) {
    addToast({ type: 'warning', title, message, duration: 5000 })
  }

  return {
    toasts,
    addToast,
    removeToast,
    success,
    error,
    info,
    warning,
  }
})
