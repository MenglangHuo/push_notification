import { createI18n } from 'vue-i18n'
import en from './en.json'
import km from './km.json'

const savedLocale = localStorage.getItem('push_locale') || 'en'

export const i18n = createI18n({
  legacy: false,
  locale: savedLocale,
  fallbackLocale: 'en',
  messages: {
    en,
    km,
  },
})
