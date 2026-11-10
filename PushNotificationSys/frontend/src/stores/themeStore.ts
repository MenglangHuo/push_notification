import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export type ThemeMode = 'dark' | 'light' | 'system'

export const useThemeStore = defineStore('theme', () => {
  const currentTheme = ref<ThemeMode>((localStorage.getItem('bronx_theme') as ThemeMode) || 'light')

  const isDark = computed(() => {
    if (typeof window === 'undefined') return true
    if (currentTheme.value === 'system') {
      return window.matchMedia('(prefers-color-scheme: dark)').matches
    }
    return currentTheme.value === 'dark'
  })

  function applyTheme(theme: ThemeMode) {
    currentTheme.value = theme
    localStorage.setItem('bronx_theme', theme)

    if (typeof document === 'undefined') return

    const dark =
      theme === 'dark' ||
      (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)

    if (dark) {
      document.documentElement.classList.add('dark')
      document.body.classList.add('dark')
      document.documentElement.style.colorScheme = 'dark'
    } else {
      document.documentElement.classList.remove('dark')
      document.body.classList.remove('dark')
      document.documentElement.style.colorScheme = 'light'
    }
  }

  function toggleTheme() {
    const nextTheme: ThemeMode = isDark.value ? 'light' : 'dark'
    applyTheme(nextTheme)
  }

  function initTheme() {
    applyTheme(currentTheme.value)

    if (typeof window !== 'undefined') {
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
        if (currentTheme.value === 'system') {
          applyTheme('system')
        }
      })
    }
  }

  return {
    currentTheme,
    isDark,
    applyTheme,
    toggleTheme,
    initTheme,
  }
})
