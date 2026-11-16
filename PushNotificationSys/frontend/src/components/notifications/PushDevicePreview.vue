<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  BellRing,
  Globe,
  Monitor,
  Smartphone,
  Wifi,
  Battery,
  Signal,
  ExternalLink,
  X,
  Sliders,
  CheckCircle2,
  Info,
  Maximize2,
} from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    title: string
    body: string
    imageUrl?: string
    deepLink?: string
    appName?: string
    priority?: 'HIGH' | 'NORMAL'
  }>(),
  {
    title: '',
    body: '',
    imageUrl: '',
    deepLink: '',
    appName: 'Push Platform',
    priority: 'HIGH',
  },
)

const activePlatform = ref<'ANDROID' | 'IOS' | 'WEB'>('ANDROID')
const isExpanded = ref(true)

const displayTitle = computed(() => props.title.trim() || 'Special Announcement')
const displayBody = computed(
  () =>
    props.body.trim() ||
    'Write your notification message here to see the real-time mobile preview...',
)

const titleLength = computed(() => props.title.length)
const bodyLength = computed(() => props.body.length)

const titleStatusColor = computed(() => {
  if (titleLength.value === 0) return 'text-slate-400'
  if (titleLength.value <= 40) return 'text-emerald-500'
  if (titleLength.value <= 60) return 'text-amber-500'
  return 'text-rose-500'
})

const bodyStatusColor = computed(() => {
  if (bodyLength.value === 0) return 'text-slate-400'
  if (bodyLength.value <= 120) return 'text-emerald-500'
  if (bodyLength.value <= 150) return 'text-amber-500'
  return 'text-rose-500'
})
</script>

<template>
  <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-4 border border-slate-200/90 dark:border-slate-800 shadow-xs space-y-3 text-left">
    <!-- Preview Header & Platform Tabs -->
    <div class="flex items-center justify-between pb-2.5 border-b border-slate-100 dark:border-slate-800">
      <div class="flex items-center gap-1.5">
        <Smartphone class="w-4 h-4 text-indigo-500" />
        <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white">
          Device Live Preview
        </h3>
      </div>

      <!-- Platform Switcher -->
      <div class="flex items-center p-0.5 rounded-xl bg-slate-100 dark:bg-slate-800 border border-slate-200/80 dark:border-slate-700/60 shadow-2xs">
        <button
          type="button"
          @click="activePlatform = 'ANDROID'"
          class="px-2 py-0.8 rounded-lg text-[10px] font-bold transition-all flex items-center gap-1 cursor-pointer"
          :class="[
            activePlatform === 'ANDROID'
              ? 'bg-white dark:bg-slate-700 text-indigo-600 dark:text-indigo-300 shadow-2xs'
              : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200',
          ]"
        >
          <Smartphone class="w-3 h-3" />
          <span>Android</span>
        </button>

        <button
          type="button"
          @click="activePlatform = 'IOS'"
          class="px-2 py-0.8 rounded-lg text-[10px] font-bold transition-all flex items-center gap-1 cursor-pointer"
          :class="[
            activePlatform === 'IOS'
              ? 'bg-white dark:bg-slate-700 text-indigo-600 dark:text-indigo-300 shadow-2xs'
              : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200',
          ]"
        >
          <Smartphone class="w-3 h-3" />
          <span>iOS</span>
        </button>

        <button
          type="button"
          @click="activePlatform = 'WEB'"
          class="px-2 py-0.8 rounded-lg text-[10px] font-bold transition-all flex items-center gap-1 cursor-pointer"
          :class="[
            activePlatform === 'WEB'
              ? 'bg-white dark:bg-slate-700 text-indigo-600 dark:text-indigo-300 shadow-2xs'
              : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200',
          ]"
        >
          <Monitor class="w-3 h-3" />
          <span>Web Push</span>
        </button>
      </div>
    </div>

    <!-- ========================================================================= -->
    <!-- 1. ANDROID MATERIAL YOU NOTIFICATION PREVIEW (COMPACT)                    -->
    <!-- ========================================================================= -->
    <div
      v-if="activePlatform === 'ANDROID'"
      class="mx-auto w-full max-w-[245px] bg-slate-950 rounded-[34px] p-2 shadow-xl border-[4px] border-slate-800 relative overflow-hidden animate-in fade-in duration-200"
    >
      <!-- Android Top Status Bar -->
      <div class="flex items-center justify-between px-2.5 pt-0.5 pb-1 text-white/90 text-[9px] font-medium">
        <span class="font-mono">09:41</span>
        <!-- Hole Punch Camera Dot -->
        <div class="w-2.5 h-2.5 rounded-full bg-black border border-slate-800 flex items-center justify-center">
          <div class="w-1 h-1 rounded-full bg-slate-900"></div>
        </div>
        <div class="flex items-center gap-1 text-white/80">
          <Wifi class="w-2.5 h-2.5" />
          <Signal class="w-2.5 h-2.5" />
          <Battery class="w-3 h-3" />
        </div>
      </div>

      <!-- Android Screen Background -->
      <div class="bg-gradient-to-b from-slate-900 via-slate-900/95 to-slate-950 rounded-[24px] p-2 min-h-[300px] max-h-[320px] flex flex-col justify-between text-white relative">
        <!-- Date display -->
        <div class="text-center pt-1">
          <div class="text-2xl font-light tracking-tight font-sans">09:41</div>
          <div class="text-[9px] text-slate-400 font-medium mt-0.5">Tuesday, Nov 12</div>
        </div>

        <!-- Android Material You Notification Shade Card -->
        <div class="my-auto space-y-1.5">
          <div class="bg-slate-800/95 backdrop-blur-md rounded-xl p-2.5 border border-slate-700/70 shadow-lg space-y-1.5 text-slate-100">
            <!-- App Header -->
            <div class="flex items-center justify-between text-[10px]">
              <div class="flex items-center gap-1.5">
                <div class="w-3.5 h-3.5 rounded-md bg-indigo-500 flex items-center justify-center text-white font-bold text-[8px] shadow-2xs">
                  <BellRing class="w-2 h-2" />
                </div>
                <span class="font-semibold text-slate-200 truncate max-w-[105px]">
                  {{ appName }}
                </span>
                <span class="text-slate-400 text-[9px]">• now</span>
              </div>

              <span
                v-if="priority === 'HIGH'"
                class="px-1.5 py-0.2 rounded text-[7px] font-bold uppercase tracking-wider bg-rose-500/20 text-rose-300 border border-rose-500/30"
              >
                High
              </span>
            </div>

            <!-- Content Area -->
            <div>
              <h4 class="text-[11px] font-bold text-white leading-snug">
                {{ displayTitle }}
              </h4>
              <p class="text-[10px] text-slate-300 leading-snug mt-0.5 line-clamp-3">
                {{ displayBody }}
              </p>
            </div>

            <!-- Image Preview (BigPictureStyle) -->
            <div v-if="imageUrl" class="relative mt-1 rounded-lg overflow-hidden border border-white/10">
              <img
                :src="imageUrl"
                alt="Notification banner"
                class="w-full h-16 object-cover"
                @error="(e: any) => (e.target.style.display = 'none')"
              />
            </div>

            <!-- Deep Link Indicator -->
            <div v-if="deepLink" class="flex items-center gap-1 text-[8px] text-indigo-400 pt-0.5 font-mono truncate">
              <ExternalLink class="w-2.5 h-2.5 shrink-0" />
              <span class="truncate">{{ deepLink }}</span>
            </div>

            <!-- Action Buttons -->
            <div class="pt-1.5 flex items-center gap-1.5 border-t border-slate-700/60 text-[9px]">
              <button type="button" class="px-2 py-0.5 rounded-md font-semibold bg-indigo-600/30 text-indigo-300 border border-indigo-500/30">
                Open App
              </button>
              <button type="button" class="px-2 py-0.5 rounded-md font-semibold text-slate-400 hover:text-slate-200">
                Dismiss
              </button>
            </div>
          </div>
        </div>

        <!-- Android Navigation Bar Indicator -->
        <div class="text-center pb-0.5">
          <div class="w-14 h-0.8 bg-white/40 rounded-full mx-auto"></div>
        </div>
      </div>
    </div>

    <!-- ========================================================================= -->
    <!-- 2. APPLE IOS DYNAMIC ISLAND NOTIFICATION PREVIEW (COMPACT)               -->
    <!-- ========================================================================= -->
    <div
      v-else-if="activePlatform === 'IOS'"
      class="mx-auto w-full max-w-[245px] bg-slate-950 rounded-[38px] p-2 shadow-xl border-[4px] border-slate-800 relative overflow-hidden animate-in fade-in duration-200"
    >
      <!-- Dynamic Island Notch -->
      <div class="w-20 h-4 bg-black rounded-full mx-auto mb-1 flex items-center justify-between px-2 border border-slate-800/80">
        <div class="w-1.5 h-1.5 rounded-full bg-slate-900 border border-slate-800"></div>
        <div class="w-1.5 h-1.5 rounded-full bg-indigo-950/60"></div>
      </div>

      <!-- iOS Wallpaper & Lockscreen -->
      <div class="bg-gradient-to-br from-indigo-950 via-slate-900 to-purple-950 rounded-[28px] p-2 min-h-[300px] max-h-[320px] flex flex-col justify-between text-white relative">
        <!-- Lockscreen Clock -->
        <div class="text-center pt-1">
          <div class="text-[9px] font-semibold text-slate-300 tracking-wide">Tuesday, November 12</div>
          <div class="text-2xl font-extralight tracking-tight font-mono mt-0.5">09:41</div>
        </div>

        <!-- iOS Glassmorphic Notification Banner -->
        <div class="my-auto">
          <div class="bg-white/15 backdrop-blur-2xl rounded-xl p-2.5 border border-white/20 shadow-lg text-slate-100 space-y-1 transition-all">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-1.5">
                <div class="w-3.5 h-3.5 rounded-md bg-indigo-500 flex items-center justify-center shadow-2xs">
                  <BellRing class="w-2 h-2 text-white" />
                </div>
                <span class="text-[9px] font-bold uppercase tracking-wider text-slate-200 truncate max-w-[110px]">
                  {{ appName }}
                </span>
              </div>
              <span class="text-[8px] text-slate-400 font-mono">now</span>
            </div>

            <div>
              <h4 class="text-[11px] font-bold text-white leading-snug">
                {{ displayTitle }}
              </h4>
              <p class="text-[10px] text-slate-200 leading-snug mt-0.5 line-clamp-3">
                {{ displayBody }}
              </p>
            </div>

            <!-- Image Banner -->
            <div v-if="imageUrl" class="mt-1 rounded-lg overflow-hidden border border-white/10">
              <img
                :src="imageUrl"
                alt="Notification banner"
                class="w-full h-16 object-cover"
                @error="(e: any) => (e.target.style.display = 'none')"
              />
            </div>

            <div v-if="deepLink" class="text-[8px] text-indigo-300 font-mono truncate pt-0.5 flex items-center gap-1">
              <ExternalLink class="w-2.5 h-2.5" />
              <span>{{ deepLink }}</span>
            </div>
          </div>
        </div>

        <!-- iOS Lockscreen Flashlight & Camera Icons -->
        <div class="flex items-center justify-between px-2 pb-0.5">
          <div class="w-6 h-6 rounded-full bg-white/10 backdrop-blur-md flex items-center justify-center text-[10px]">
            🔦
          </div>
          <div class="w-14 h-0.8 bg-white/50 rounded-full"></div>
          <div class="w-6 h-6 rounded-full bg-white/10 backdrop-blur-md flex items-center justify-center text-[10px]">
            📷
          </div>
        </div>
      </div>
    </div>

    <!-- ========================================================================= -->
    <!-- 3. WEB PUSH (DESKTOP BROWSER BANNER) PREVIEW                              -->
    <!-- ========================================================================= -->
    <div
      v-else
      class="mx-auto w-full max-w-[280px] bg-slate-900 rounded-2xl p-2.5 shadow-xl border border-slate-700/80 animate-in fade-in duration-200 text-left space-y-2"
    >
      <!-- macOS Desktop Header Simulation -->
      <div class="flex items-center justify-between pb-1.5 border-b border-slate-800">
        <div class="flex items-center gap-1.5">
          <div class="w-2 h-2 rounded-full bg-rose-500"></div>
          <div class="w-2 h-2 rounded-full bg-amber-500"></div>
          <div class="w-2 h-2 rounded-full bg-emerald-500"></div>
        </div>
        <span class="text-[9px] text-slate-400 font-mono">Chrome Desktop • WebPush</span>
      </div>

      <!-- Desktop Push Floating Box -->
      <div class="bg-slate-800 rounded-xl p-2.5 border border-slate-700 shadow-md space-y-1.5">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-1.5 text-[9px] text-slate-300 font-medium">
            <Globe class="w-3 h-3 text-indigo-400" />
            <span class="truncate font-semibold">{{ appName }}</span>
            <span class="text-slate-500">• now</span>
          </div>
          <button type="button" class="text-slate-500 hover:text-slate-300">
            <X class="w-3 h-3" />
          </button>
        </div>

        <div class="flex items-start gap-2">
          <div class="w-7 h-7 rounded-lg bg-indigo-600 flex items-center justify-center text-white shrink-0 shadow-xs">
            <BellRing class="w-3.5 h-3.5" />
          </div>
          <div class="min-w-0 flex-1">
            <h4 class="text-[11px] font-bold text-white leading-snug">
              {{ displayTitle }}
            </h4>
            <p class="text-[10px] text-slate-300 leading-snug mt-0.5 line-clamp-3">
              {{ displayBody }}
            </p>
          </div>
        </div>

        <!-- Optional Web Banner -->
        <div v-if="imageUrl" class="rounded-lg overflow-hidden border border-slate-700">
          <img
            :src="imageUrl"
            alt="Web push banner"
            class="w-full h-16 object-cover"
            @error="(e: any) => (e.target.style.display = 'none')"
          />
        </div>

        <!-- Action Buttons -->
        <div class="pt-1.5 flex items-center justify-end gap-1.5 border-t border-slate-700/60 text-[9px]">
          <button type="button" class="px-2 py-0.5 rounded font-semibold text-slate-400 hover:text-slate-200">
            Settings
          </button>
          <button type="button" class="px-2.5 py-0.5 rounded font-semibold bg-indigo-600 text-white hover:bg-indigo-500 shadow-xs">
            View Details
          </button>
        </div>
      </div>
    </div>

    <!-- Length Guidelines & Real-Time Counters (ALWAYS VISIBLE WITHOUT SCROLLING) -->
    <div class="p-2.5 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200/80 dark:border-slate-800 space-y-1.5">
      <div class="flex items-center justify-between text-xs">
        <span class="text-slate-500 dark:text-slate-400 font-medium">Title Length:</span>
        <span class="font-mono font-semibold" :class="titleStatusColor">
          {{ titleLength }} / 50 chars
        </span>
      </div>

      <div class="flex items-center justify-between text-xs">
        <span class="text-slate-500 dark:text-slate-400 font-medium">Body Length:</span>
        <span class="font-mono font-semibold" :class="bodyStatusColor">
          {{ bodyLength }} / 150 chars
        </span>
      </div>

      <div class="pt-1 border-t border-slate-200/60 dark:border-slate-800/60 text-[10px] text-slate-400 flex items-center gap-1.5">
        <Info class="w-3 h-3 shrink-0 text-indigo-500" />
        <span>Keep titles under 40 chars for optimal mobile lockscreen visibility.</span>
      </div>
    </div>
  </div>
</template>
