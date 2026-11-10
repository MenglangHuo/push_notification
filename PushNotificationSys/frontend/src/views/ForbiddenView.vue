<script setup lang="ts">
import { ShieldAlert, ArrowLeft } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import BaseButton from '@/components/common/BaseButton.vue'

const router = useRouter()
</script>

<template>
  <div class="min-h-[70vh] flex items-center justify-center p-4 text-center">
    <div
      class="max-w-md w-full bg-white dark:bg-slate-900/90 rounded-3xl p-8 border border-slate-200 dark:border-slate-800 shadow-xl space-y-4"
    >
      <div
        class="w-14 h-14 rounded-2xl bg-rose-50 dark:bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-200 dark:border-rose-500/30 flex items-center justify-center mx-auto"
      >
        <ShieldAlert class="w-7 h-7" />
      </div>

      <div>
        <h1 class="text-xl font-bold tracking-tight text-slate-900 dark:text-white">
          403 — Access Restricted
        </h1>
        <p class="text-xs text-slate-500 dark:text-slate-400 mt-1.5 leading-relaxed">
          Your role in this workspace does not have sufficient permissions to view or interact with this resource.
        </p>
      </div>

      <div class="pt-3">
        <BaseButton
          variant="primary"
          size="sm"
          :icon="ArrowLeft"
          @click="router.push('/dashboard')"
        >
          Return to Dashboard
        </BaseButton>
      </div>
    </div>
  </div>
</template>
