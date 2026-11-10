import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface SplashOptions {
  title?: string
  subtitle?: string
  message?: string
  progress?: number | null
}

export const useSplashStore = defineStore('splash', () => {
  // Visible by default on first load
  const isVisible = ref<boolean>(true)
  const isInitialLoading = ref<boolean>(true)
  const title = ref<string>('Push Notification Platform')
  const subtitle = ref<string>('High-Throughput Delivery Gateway & Realtime Engine')
  const message = ref<string>('Connecting to Realtime Delivery Gateway...')
  const progress = ref<number | null>(null)

  let hideTimer: ReturnType<typeof setTimeout> | null = null

  const isLoading = computed(() => isVisible.value)

  /**
   * Helper to safely remove the inline pre-hydration splash screen from index.html
   */
  function removePreMountSplash(fadeDuration = 350) {
    if (typeof document === 'undefined') return
    const el = document.getElementById('app-splash-screen')
    if (el) {
      el.classList.add('splash-fade-out')
      setTimeout(() => {
        el.remove()
      }, fadeDuration)
    }
  }

  /**
   * Show the splash screen with an optional message and custom options
   */
  function show(msg?: string, options?: SplashOptions) {
    if (hideTimer) {
      clearTimeout(hideTimer)
      hideTimer = null
    }

    if (msg !== undefined) message.value = msg
    if (options?.title !== undefined) title.value = options.title
    if (options?.subtitle !== undefined) subtitle.value = options.subtitle
    if (options?.message !== undefined) message.value = options.message
    if (options?.progress !== undefined) progress.value = options.progress

    isVisible.value = true
  }

  /**
   * Hide the splash screen after an optional delay (ms)
   */
  function hide(delayMs = 200) {
    removePreMountSplash()

    if (hideTimer) {
      clearTimeout(hideTimer)
      hideTimer = null
    }

    if (delayMs <= 0) {
      isVisible.value = false
      isInitialLoading.value = false
      progress.value = null
    } else {
      hideTimer = setTimeout(() => {
        isVisible.value = false
        isInitialLoading.value = false
        progress.value = null
        hideTimer = null
      }, delayMs)
    }
  }

  /**
   * Finish the initial application boot splash sequence smoothly
   */
  function finishInitialLoad(delayMs = 150) {
    hide(delayMs)
  }

  function setProgress(val: number | null) {
    progress.value = val
  }

  function setMessage(msg: string) {
    message.value = msg
  }

  /**
   * Wrap an async task with the splash screen
   */
  async function wrap<T>(
    promise: Promise<T>,
    msg?: string,
    options?: SplashOptions,
  ): Promise<T> {
    show(msg, options)
    try {
      return await promise
    } finally {
      hide()
    }
  }

  return {
    isVisible,
    isInitialLoading,
    isLoading,
    title,
    subtitle,
    message,
    progress,
    show,
    hide,
    finishInitialLoad,
    setProgress,
    setMessage,
    wrap,
    removePreMountSplash,
  }
})
