<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { useAppStore } from '@/stores/appStore'
import { orgsApi } from '@/api/orgs'
import { ChevronDown, Building2, Plus, Check, Layers, Sparkles } from 'lucide-vue-next'
import ModernModal from '@/components/common/ModernModal.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import { useToastStore } from '@/stores/toastStore'

const authStore = useAuthStore()
const appStore = useAppStore()
const toastStore = useToastStore()

const isOpen = ref(false)
const showCreateModal = ref(false)
const newOrgName = ref('')
const newOrgSlug = ref('')
const isCreating = ref(false)
const createError = ref('')

const appList = computed(() => appStore.apps)
const isLoadingApps = computed(() => appStore.isLoading)

onMounted(() => {
  if (authStore.currentOrgId) {
    appStore.loadApps(authStore.currentOrgId)
  }
})

const currentAppName = computed(() => {
  if (!authStore.currentAppId) return 'All Apps'
  const found = appList.value.find((a) => a.id === authStore.currentAppId)
  return found ? found.name : 'Select App'
})

function toggleDropdown() {
  isOpen.value = !isOpen.value
}

async function selectOrg(orgId: string) {
  authStore.setCurrentOrg(orgId)
  isOpen.value = false
  await appStore.loadApps(orgId, true)
}

function selectApp(appId: string | null) {
  appStore.selectApp(appId)
  isOpen.value = false
}

function autoSlug() {
  newOrgSlug.value = newOrgName.value
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

async function handleCreateOrg() {
  if (!newOrgName.value || !newOrgSlug.value) return
  isCreating.value = true
  createError.value = ''

  try {
    const created = await orgsApi.create({
      name: newOrgName.value,
      slug: newOrgSlug.value,
    })
    await authStore.fetchMe()
    authStore.setCurrentOrg(created.id)
    showCreateModal.value = false
    isOpen.value = false
    toastStore.success('Workspace Created', `Switched to ${created.name}`)
    await appStore.loadApps(created.id, true)
  } catch (err: any) {
    createError.value = err.response?.data?.detail || 'Failed to create organization'
  } finally {
    isCreating.value = false
  }
}
</script>

<template>
  <div class="relative">
    <button
      @click="toggleDropdown"
      class="flex items-center gap-2 px-3 py-1.5 rounded-xl border border-slate-200/90 dark:border-slate-800 bg-white/90 dark:bg-slate-900/90 hover:bg-slate-50 dark:hover:bg-slate-800 transition text-xs font-semibold text-slate-800 dark:text-slate-200 shadow-2xs cursor-pointer select-none"
    >
      <div
        class="w-5 h-5 rounded-lg bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 flex items-center justify-center shrink-0 border border-indigo-200/80 dark:border-indigo-500/30"
      >
        <Building2 class="w-3 h-3" />
      </div>

      <div class="text-left leading-tight truncate max-w-[130px] sm:max-w-[170px]">
        <div class="truncate text-[11px] font-bold text-slate-900 dark:text-white">
          {{ authStore.currentOrg?.name || 'Select Org' }}
        </div>
        <div class="truncate text-[10px] text-slate-500 dark:text-slate-400 font-medium">
          {{ currentAppName }}
        </div>
      </div>

      <ChevronDown class="w-3.5 h-3.5 text-slate-400 ml-1 shrink-0" />
    </button>

    <!-- Dropdown Menu -->
    <div
      v-if="isOpen"
      class="absolute left-0 mt-2 w-72 bg-white/95 dark:bg-slate-900/95 backdrop-blur-xl rounded-2xl shadow-xl shadow-slate-950/10 dark:shadow-slate-950/60 p-2 z-50 border border-slate-200 dark:border-slate-800 animate-in fade-in zoom-in-95 duration-100"
    >
      <!-- Organizations Section -->
      <div class="flex items-center justify-between px-2.5 py-1 text-[10px] font-bold tracking-wider text-slate-400 uppercase">
        <span>Workspaces</span>
        <span class="text-[9px] font-mono text-indigo-500">{{ authStore.user?.organizations.length || 0 }}</span>
      </div>

      <div class="max-h-40 overflow-y-auto space-y-0.5 mb-2">
        <button
          v-for="org in authStore.user?.organizations"
          :key="org.orgId"
          @click="selectOrg(org.orgId)"
          class="w-full flex items-center justify-between px-2.5 py-2 rounded-xl text-xs transition text-left cursor-pointer"
          :class="[
            org.orgId === authStore.currentOrgId
              ? 'bg-indigo-50 dark:bg-indigo-500/15 text-indigo-700 dark:text-indigo-300 font-bold border border-indigo-200 dark:border-indigo-500/30'
              : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800/80',
          ]"
        >
          <div class="flex items-center gap-2 truncate">
            <Building2 class="w-3.5 h-3.5 text-slate-400" />
            <span class="truncate">{{ org.name }}</span>
          </div>
          <Check v-if="org.orgId === authStore.currentOrgId" class="w-3.5 h-3.5 text-indigo-600 dark:text-indigo-400 shrink-0" />
        </button>
      </div>

      <!-- Applications Under Selected Org -->
      <div class="border-t border-slate-100 dark:border-slate-800 pt-2 mb-2">
        <div class="flex items-center justify-between px-2.5 py-1 text-[10px] font-bold tracking-wider text-slate-400 uppercase">
          <span>Active App</span>
          <span class="text-[9px] font-mono text-indigo-500">{{ appList.length }}</span>
        </div>

        <div class="max-h-40 overflow-y-auto space-y-0.5">
          <button
            @click="selectApp(null)"
            class="w-full flex items-center justify-between px-2.5 py-1.5 rounded-xl text-xs transition text-left cursor-pointer"
            :class="[
              !authStore.currentAppId
                ? 'bg-indigo-50 dark:bg-indigo-500/15 text-indigo-700 dark:text-indigo-300 font-bold'
                : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800/80',
            ]"
          >
            <div class="flex items-center gap-2 truncate">
              <Layers class="w-3.5 h-3.5 text-slate-400" />
              <span class="truncate">All Apps Overview</span>
            </div>
            <Check v-if="!authStore.currentAppId" class="w-3.5 h-3.5 text-indigo-600 dark:text-indigo-400 shrink-0" />
          </button>

          <button
            v-for="app in appList"
            :key="app.id"
            @click="selectApp(app.id)"
            class="w-full flex items-center justify-between px-2.5 py-1.5 rounded-xl text-xs transition text-left cursor-pointer"
            :class="[
              app.id === authStore.currentAppId
                ? 'bg-indigo-50 dark:bg-indigo-500/15 text-indigo-700 dark:text-indigo-300 font-bold border border-indigo-200 dark:border-indigo-500/30'
                : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800/80',
            ]"
          >
            <div class="flex items-center gap-2 truncate">
              <Layers class="w-3.5 h-3.5 text-slate-400" />
              <span class="truncate">{{ app.name }}</span>
            </div>
            <Check v-if="app.id === authStore.currentAppId" class="w-3.5 h-3.5 text-indigo-600 dark:text-indigo-400 shrink-0" />
          </button>
        </div>
      </div>

      <!-- Action: Create Org -->
      <div class="border-t border-slate-100 dark:border-slate-800 pt-1.5">
        <button
          @click="showCreateModal = true; isOpen = false"
          class="w-full flex items-center gap-2 px-2.5 py-1.5 rounded-xl text-xs font-semibold text-indigo-600 dark:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-500/10 transition cursor-pointer"
        >
          <Plus class="w-3.5 h-3.5" />
          <span>New Workspace...</span>
        </button>
      </div>
    </div>

    <!-- Create Org Modal -->
    <ModernModal
      v-model="showCreateModal"
      title="Create Organization Workspace"
      subtitle="Provision a multi-tenant workspace for push notification apps"
      :icon="Building2"
      :initial-width="500"
      @close="showCreateModal = false"
    >
      <form id="create-org-form" @submit.prevent="handleCreateOrg" class="p-6 space-y-5 text-left">
        <div
          v-if="createError"
          class="p-3.5 rounded-2xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/50 text-rose-700 dark:text-rose-300 text-xs flex items-center gap-2"
        >
          {{ createError }}
        </div>

        <BaseInput
          v-model="newOrgName"
          label="Organization Name"
          placeholder="e.g. Acme Corporation"
          required
          :icon="Building2"
          size="md"
          @input="autoSlug"
        />

        <BaseInput
          v-model="newOrgSlug"
          label="URL Slug (Identifier)"
          placeholder="e.g. acme"
          required
          :icon="Sparkles"
          size="md"
        />
      </form>

      <template #footer>
        <BaseButton variant="ghost" @click="showCreateModal = false">
          Cancel
        </BaseButton>
        <BaseButton
          type="submit"
          form="create-org-form"
          variant="primary"
          :loading="isCreating"
        >
          Create Workspace
        </BaseButton>
      </template>
    </ModernModal>
  </div>
</template>
