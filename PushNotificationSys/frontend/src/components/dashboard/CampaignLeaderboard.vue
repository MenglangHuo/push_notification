<script setup lang="ts">
import { Trophy } from 'lucide-vue-next'
import ProgressRing from './ProgressRing.vue'

export interface CampaignItem {
  notificationId: string
  title: string
  sentCount: number
  deliveredCount: number
  openedCount: number
  deliveryRate: number
  openRate: number
}

withDefaults(
  defineProps<{
    campaigns: CampaignItem[]
    loading?: boolean
  }>(),
  {
    loading: false,
  },
)

function getRankBadge(idx: number): string {
  if (idx === 0) return '🥇'
  if (idx === 1) return '🥈'
  if (idx === 2) return '🥉'
  return `#${idx + 1}`
}
</script>

<template>
  <div
    class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm dark:shadow-xl transition-colors flex flex-col justify-between"
  >
    <!-- Header -->
    <div class="flex items-center justify-between mb-4">
      <div>
        <h3 class="text-sm font-semibold text-slate-900 dark:text-white flex items-center gap-2">
          <Trophy class="w-4 h-4 text-indigo-600 dark:text-indigo-400" />
          <span>Top Campaigns</span>
        </h3>
        <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
          High-performing dispatches ranked by reach & engagement
        </p>
      </div>
    </div>

    <!-- Skeleton Loading Rows -->
    <div v-if="loading" class="space-y-3 py-1">
      <div
        v-for="i in 5"
        :key="i"
        class="h-10 bg-slate-100 dark:bg-slate-800/60 rounded-xl animate-pulse"
      ></div>
    </div>

    <!-- Empty State -->
    <div
      v-else-if="!campaigns || campaigns.length === 0"
      class="py-12 flex flex-col items-center justify-center text-center"
    >
      <div
        class="w-10 h-10 rounded-xl bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-slate-400 mb-2.5"
      >
        <Trophy class="w-5 h-5" />
      </div>
      <p class="text-xs font-semibold text-slate-700 dark:text-slate-300">No campaigns yet</p>
      <p class="text-[11px] text-slate-400 dark:text-slate-500 max-w-xs mt-0.5">
        Once notifications are dispatched, your top performing campaigns will be ranked here.
      </p>
    </div>

    <!-- Leaderboard Table -->
    <div v-else class="overflow-x-auto">
      <table class="w-full text-left text-xs">
        <thead>
          <tr
            class="border-b border-slate-100 dark:border-slate-800 text-[11px] font-semibold text-slate-400 uppercase tracking-wider"
          >
            <th class="pb-2.5 w-8 font-semibold text-center">#</th>
            <th class="pb-2.5 font-semibold">Title</th>
            <th class="pb-2.5 font-semibold text-right">Sent</th>
            <th class="pb-2.5 font-semibold text-right">Delivered</th>
            <th class="pb-2.5 font-semibold text-right">Opens</th>
            <th class="pb-2.5 font-semibold text-center w-14">Delivery %</th>
            <th class="pb-2.5 font-semibold text-center w-14">Open %</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100 dark:divide-slate-800/60 font-medium">
          <tr
            v-for="(camp, idx) in campaigns.slice(0, 10)"
            :key="camp.notificationId || idx"
            class="hover:bg-slate-50/70 dark:hover:bg-slate-800/40 transition-colors"
          >
            <!-- Rank Column -->
            <td class="py-2.5 text-center font-mono font-semibold text-slate-600 dark:text-slate-300">
              <span v-if="idx < 3" class="text-sm select-none">{{ getRankBadge(idx) }}</span>
              <span v-else class="text-[11px] text-slate-400">{{ getRankBadge(idx) }}</span>
            </td>

            <!-- Campaign Title -->
            <td class="py-2.5 pr-2 max-w-[130px] sm:max-w-[180px]">
              <span
                class="block truncate font-semibold text-slate-900 dark:text-white"
                :title="camp.title"
              >
                {{ camp.title || 'Untitled Notification' }}
              </span>
            </td>

            <!-- Sent Count -->
            <td class="py-2.5 pr-3 text-right font-mono text-slate-700 dark:text-slate-300">
              {{ camp.sentCount.toLocaleString() }}
            </td>

            <!-- Delivered Count -->
            <td class="py-2.5 pr-3 text-right font-mono text-slate-700 dark:text-slate-300">
              {{ camp.deliveredCount.toLocaleString() }}
            </td>

            <!-- Opened Count -->
            <td class="py-2.5 pr-3 text-right font-mono text-amber-600 dark:text-amber-400 font-semibold">
              {{ camp.openedCount.toLocaleString() }}
            </td>

            <!-- Delivery Rate Progress Ring -->
            <td class="py-2.5 text-center">
              <div class="flex items-center justify-center">
                <ProgressRing
                  :value="camp.deliveryRate"
                  :size="28"
                  :stroke-width="3"
                  color="#10b981"
                />
              </div>
            </td>

            <!-- Open Rate Progress Ring -->
            <td class="py-2.5 text-center">
              <div class="flex items-center justify-center">
                <ProgressRing
                  :value="camp.openRate"
                  :size="28"
                  :stroke-width="3"
                  color="#f59e0b"
                />
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
