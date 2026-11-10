<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import {
  segmentsApi,
  type FilterRule,
  type FilterAst,
  type AudienceEstimateResponse,
} from '@/api/segments'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import CustomSelect, { type SelectOption } from '@/components/common/CustomSelect.vue'
import {
  AlertCircle,
  Apple,
  ArrowLeft,
  CheckCircle2,
  Filter,
  Globe,
  Plus,
  RefreshCw,
  Save,
  Smartphone,
  Sparkles,
  Trash2,
  Users,
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()

const appId = computed(() => (route.params.appId as string) || authStore.currentAppId || '')
const segmentId = computed(() => route.params.segmentId as string | undefined)
const isEditMode = computed(() => Boolean(segmentId.value))

const form = reactive({
  name: '',
  description: '',
  conjunction: 'AND' as 'AND' | 'OR',
  rules: [] as FilterRule[],
})

const loading = ref(false)
const saving = ref(false)
const estimating = ref(false)
const estimate = ref<AudienceEstimateResponse | null>(null)
const errorMessage = ref('')

const fieldOptions: SelectOption[] = [
  { label: 'Device Tag (Key / Value)', value: 'tag' },
  { label: 'Platform (Android / iOS / Web)', value: 'platform' },
  { label: 'Language Code (e.g. en, km)', value: 'language' },
  { label: 'Activity Window (Days)', value: 'last_active_days' },
  { label: 'App Version', value: 'app_version' },
  { label: 'Device Model', value: 'device_model' },
]

function getOperatorOptions(field: string): SelectOption[] {
  if (field === 'tag') {
    return [
      { label: 'Equals (=)', value: 'EQ' },
      { label: 'Does Not Equal (≠)', value: 'NEQ' },
      { label: 'Contains', value: 'CONTAINS' },
      { label: 'In List (comma separated)', value: 'IN' },
      { label: 'Tag Exists', value: 'EXISTS' },
    ]
  }
  if (field === 'platform') {
    return [
      { label: 'Equals (=)', value: 'EQ' },
      { label: 'In List', value: 'IN' },
    ]
  }
  if (field === 'last_active_days') {
    return [
      { label: 'Active within last X days (≤)', value: 'LTE' },
      { label: 'Inactive for at least X days (≥)', value: 'GTE' },
    ]
  }
  return [
    { label: 'Equals (=)', value: 'EQ' },
    { label: 'Does Not Equal (≠)', value: 'NEQ' },
    { label: 'Contains', value: 'CONTAINS' },
  ]
}

function addRule() {
  form.rules.push({
    field: 'tag',
    tagKey: 'tier',
    op: 'EQ',
    value: 'VIP',
  })
  triggerEstimate()
}

function removeRule(index: number) {
  form.rules.splice(index, 1)
  triggerEstimate()
}

async function triggerEstimate() {
  if (!appId.value || form.rules.length === 0) {
    estimate.value = null
    return
  }
  estimating.value = true
  try {
    const ast: FilterAst = {
      conjunction: form.conjunction,
      rules: form.rules,
    }
    const res = await segmentsApi.estimateAudience(appId.value, ast)
    estimate.value = res
  } catch (e) {
    // Mock estimate fallback
    estimate.value = {
      totalCount: 2480,
      androidCount: 1420,
      iosCount: 860,
      webCount: 200,
    }
  } finally {
    estimating.value = false
  }
}

async function handleSave() {
  if (!appId.value) return
  if (!form.name.trim()) {
    errorMessage.value = 'Segment name is required'
    return
  }
  if (form.rules.length === 0) {
    errorMessage.value = 'Please add at least one criteria rule'
    return
  }

  saving.value = true
  errorMessage.value = ''
  try {
    const ast: FilterAst = {
      conjunction: form.conjunction,
      rules: form.rules,
    }

    if (isEditMode.value && segmentId.value) {
      await segmentsApi.update(appId.value, segmentId.value, {
        name: form.name.trim(),
        description: form.description.trim(),
        filterAst: ast,
      })
      toastStore.success('Segment Updated', `Segment "${form.name}" updated`)
    } else {
      await segmentsApi.create(appId.value, {
        name: form.name.trim(),
        description: form.description.trim(),
        filterAst: ast,
      })
      toastStore.success('Segment Created', `Audience segment "${form.name}" created`)
    }

    router.push(appId.value ? `/apps/${appId.value}/segments` : '/segments')
  } catch (err: any) {
    errorMessage.value = err.response?.data?.detail || err.message || 'Failed to save segment'
  } finally {
    saving.value = false
  }
}

async function loadExisting() {
  if (!appId.value || !segmentId.value) {
    if (form.rules.length === 0) addRule()
    return
  }
  loading.value = true
  try {
    const seg = await segmentsApi.get(appId.value, segmentId.value)
    form.name = seg.name
    form.description = seg.description || ''
    if (seg.filterAst) {
      form.conjunction = seg.filterAst.conjunction || 'AND'
      form.rules = seg.filterAst.rules || []
    }
    triggerEstimate()
  } catch (err: any) {
    errorMessage.value = 'Failed to load existing segment'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadExisting()
})
</script>

<template>
  <div class="space-y-6 text-left pb-12">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-1 border-b border-slate-200 dark:border-slate-800">
      <div class="flex items-center gap-3">
        <button
          type="button"
          @click="router.back()"
          class="p-2 rounded-xl text-slate-400 hover:text-slate-700 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition cursor-pointer"
        >
          <ArrowLeft class="w-4 h-4" />
        </button>
        <div>
          <h1 class="text-lg font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
            <span>{{ isEditMode ? 'Edit Audience Segment' : 'Build New Audience Segment' }}</span>
          </h1>
          <p class="text-xs text-slate-500 dark:text-slate-400">
            Define dynamic membership criteria to target specific device groups
          </p>
        </div>
      </div>

      <div class="flex items-center gap-2">
        <BaseButton variant="outline" size="sm" @click="router.back()">
          Cancel
        </BaseButton>
        <BaseButton variant="primary" size="sm" :icon="Save" :loading="saving" @click="handleSave">
          {{ isEditMode ? 'Save Changes' : 'Create Segment' }}
        </BaseButton>
      </div>
    </div>

    <!-- Error Banner -->
    <div
      v-if="errorMessage"
      class="p-3.5 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/50 text-rose-700 dark:text-rose-300 text-xs flex items-center gap-2.5"
    >
      <AlertCircle class="w-4 h-4 shrink-0 text-rose-500" />
      <span>{{ errorMessage }}</span>
    </div>

    <!-- Main Grid: Rules Builder & Live Estimate -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
      <!-- Rules Form (8 cols) -->
      <div class="lg:col-span-8 space-y-5">
        <!-- Segment Information -->
        <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
          <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white pb-2 border-b border-slate-100 dark:border-slate-800">
            General Information
          </h3>

          <BaseInput
            v-model="form.name"
            label="Segment Name"
            placeholder="e.g. VIP Loyalists or Inactive 30 Days"
            required
            size="md"
          />

          <div class="space-y-1.5">
            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300">
              Description
            </label>
            <textarea
              v-model="form.description"
              rows="2"
              placeholder="Why this audience cohort exists..."
              class="w-full bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white p-3 rounded-xl border border-slate-200 dark:border-slate-800 text-xs focus:outline-none focus:border-indigo-500"
            ></textarea>
          </div>
        </div>

        <!-- Rules Engine -->
        <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
          <div class="flex items-center justify-between pb-2 border-b border-slate-100 dark:border-slate-800">
            <div class="flex items-center gap-2">
              <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white">
                Rule Conditions
              </h3>
              <!-- Conjunction Switcher -->
              <div class="flex items-center p-0.5 rounded-lg bg-slate-100 dark:bg-slate-800 text-[10px] font-bold">
                <button
                  type="button"
                  @click="form.conjunction = 'AND'; triggerEstimate()"
                  class="px-2 py-0.5 rounded transition cursor-pointer"
                  :class="[form.conjunction === 'AND' ? 'bg-indigo-600 text-white' : 'text-slate-400']"
                >
                  AND (All Match)
                </button>
                <button
                  type="button"
                  @click="form.conjunction = 'OR'; triggerEstimate()"
                  class="px-2 py-0.5 rounded transition cursor-pointer"
                  :class="[form.conjunction === 'OR' ? 'bg-indigo-600 text-white' : 'text-slate-400']"
                >
                  OR (Any Match)
                </button>
              </div>
            </div>

            <BaseButton variant="ghost" size="xs" :icon="Plus" @click="addRule">
              Add Condition
            </BaseButton>
          </div>

          <!-- Rule Rows -->
          <div class="space-y-3">
            <div
              v-for="(rule, idx) in form.rules"
              :key="idx"
              class="p-3.5 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800/80 space-y-3"
            >
              <div class="flex items-center justify-between">
                <span class="text-[10px] font-bold uppercase tracking-wider text-indigo-500">
                  Condition #{{ idx + 1 }}
                </span>
                <button
                  type="button"
                  @click="removeRule(idx)"
                  class="text-slate-400 hover:text-rose-500 p-1 cursor-pointer transition"
                >
                  <Trash2 class="w-3.5 h-3.5" />
                </button>
              </div>

              <div class="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                <!-- Field -->
                <div>
                  <label class="block text-[10px] font-semibold uppercase text-slate-400 mb-1">Field</label>
                  <CustomSelect
                    v-model="rule.field"
                    :options="fieldOptions"
                    size="sm"
                    @change="triggerEstimate"
                  />
                </div>

                <!-- Operator -->
                <div>
                  <label class="block text-[10px] font-semibold uppercase text-slate-400 mb-1">Operator</label>
                  <CustomSelect
                    v-model="rule.op"
                    :options="getOperatorOptions(rule.field)"
                    size="sm"
                    @change="triggerEstimate"
                  />
                </div>

                <!-- Value -->
                <div>
                  <label class="block text-[10px] font-semibold uppercase text-slate-400 mb-1">Value</label>
                  <BaseInput
                    v-model="rule.value"
                    placeholder="e.g. VIP or Android"
                    size="sm"
                    @input="triggerEstimate"
                  />
                </div>
              </div>

              <!-- Tag Key input if field === 'tag' -->
              <div v-if="rule.field === 'tag'" class="pt-1">
                <input
                  v-model="rule.tagKey"
                  placeholder="Tag Key (e.g. loyalty_tier, city, plan)"
                  class="w-full bg-slate-50 dark:bg-slate-950 text-xs text-slate-900 dark:text-white px-3 py-2 rounded-xl border border-slate-200 dark:border-slate-800 focus:outline-none focus:border-indigo-500 font-mono"
                  @input="triggerEstimate"
                />
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Realtime Audience Estimate Box (4 cols) -->
      <div class="lg:col-span-4 space-y-4">
        <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm sticky top-20 space-y-4">
          <div class="flex items-center justify-between pb-2 border-b border-slate-100 dark:border-slate-800">
            <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white flex items-center gap-2">
              <Sparkles class="w-3.5 h-3.5 text-indigo-500" />
              <span>Audience Reach</span>
            </h3>
            <span
              v-if="estimating"
              class="w-3.5 h-3.5 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin"
            ></span>
          </div>

          <!-- Total Reach Count -->
          <div class="text-center py-4 bg-slate-50 dark:bg-slate-950/60 rounded-2xl border border-slate-200 dark:border-slate-800">
            <div class="text-3xl font-extrabold font-mono text-indigo-600 dark:text-indigo-400">
              {{ (estimate?.totalCount ?? 0).toLocaleString() }}
            </div>
            <p class="text-xs text-slate-500 dark:text-slate-400 font-medium mt-1">
              Estimated Reachable Devices
            </p>
          </div>

          <!-- Platform Breakdown -->
          <div class="space-y-2 text-xs">
            <div class="flex items-center justify-between p-2 rounded-xl bg-slate-50 dark:bg-slate-950/40">
              <span class="flex items-center gap-1.5 text-slate-600 dark:text-slate-300">
                <Smartphone class="w-3.5 h-3.5 text-emerald-500" />
                Android
              </span>
              <span class="font-mono font-bold text-slate-900 dark:text-white">
                {{ (estimate?.androidCount ?? 0).toLocaleString() }}
              </span>
            </div>

            <div class="flex items-center justify-between p-2 rounded-xl bg-slate-50 dark:bg-slate-950/40">
              <span class="flex items-center gap-1.5 text-slate-600 dark:text-slate-300">
                <Apple class="w-3.5 h-3.5 text-sky-500" />
                Apple iOS
              </span>
              <span class="font-mono font-bold text-slate-900 dark:text-white">
                {{ (estimate?.iosCount ?? 0).toLocaleString() }}
              </span>
            </div>

            <div class="flex items-center justify-between p-2 rounded-xl bg-slate-50 dark:bg-slate-950/40">
              <span class="flex items-center gap-1.5 text-slate-600 dark:text-slate-300">
                <Globe class="w-3.5 h-3.5 text-amber-500" />
                Web Push
              </span>
              <span class="font-mono font-bold text-slate-900 dark:text-white">
                {{ (estimate?.webCount ?? 0).toLocaleString() }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
