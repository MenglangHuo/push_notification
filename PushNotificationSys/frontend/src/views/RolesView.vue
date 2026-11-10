<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { rolesApi } from '@/api/roles'
import type { Role, Permission } from '@/api/types'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import ModernModal from '@/components/common/ModernModal.vue'
import {
  Check,
  CheckCircle2,
  Lock,
  Plus,
  RefreshCw,
  Shield,
  ShieldCheck,
  Trash2,
} from 'lucide-vue-next'

const authStore = useAuthStore()
const toastStore = useToastStore()

const roles = ref<Role[]>([])
const permissions = ref<Permission[]>([])
const isLoading = ref<boolean>(false)

// Create modal
const showCreateModal = ref(false)
const newRoleName = ref('')
const newRoleDescription = ref('')
const selectedPermissions = ref<Set<string>>(new Set())
const isCreating = ref(false)
const createError = ref('')

const permissionsByResource = computed(() => {
  const map: Record<string, Permission[]> = {}
  for (const p of permissions.value) {
    if (!map[p.resource]) map[p.resource] = []
    map[p.resource].push(p)
  }
  return map
})

async function loadData() {
  if (!authStore.currentOrgId) return
  isLoading.value = true
  try {
    const [rolesData, permsData] = await Promise.all([
      rolesApi.listRoles(authStore.currentOrgId).catch(() => []),
      rolesApi.listPermissions().catch(() => []),
    ])
    roles.value = rolesData
    permissions.value = permsData
  } catch (err: any) {
    console.error('Failed to load roles', err)
  } finally {
    isLoading.value = false
  }
}

function togglePermission(code: string) {
  if (selectedPermissions.value.has(code)) {
    selectedPermissions.value.delete(code)
  } else {
    selectedPermissions.value.add(code)
  }
}

async function handleCreateRole() {
  if (!authStore.currentOrgId || !newRoleName.value.trim()) return
  if (selectedPermissions.value.size === 0) {
    createError.value = 'Please select at least one permission'
    return
  }

  isCreating.value = true
  createError.value = ''
  try {
    await rolesApi.createCustomRole(authStore.currentOrgId, {
      name: newRoleName.value.trim(),
      description: newRoleDescription.value.trim(),
      permissionCodes: Array.from(selectedPermissions.value),
    })
    toastStore.success('Role Created', `Role "${newRoleName.value}" created successfully`)
    showCreateModal.value = false
    newRoleName.value = ''
    newRoleDescription.value = ''
    selectedPermissions.value.clear()
    await loadData()
  } catch (err: any) {
    createError.value = err.response?.data?.detail || err.message || 'Failed to create role'
  } finally {
    isCreating.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="space-y-4 text-left">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
      <div>
        <div class="flex items-center gap-2.5">
          <div
            class="w-8 h-8 rounded-lg bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 flex items-center justify-center border border-indigo-200/90 dark:border-indigo-500/30 shadow-xs shrink-0"
          >
            <ShieldCheck class="w-4 h-4" />
          </div>
          <div>
            <h1 class="text-lg font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
              <span>Roles & Permission Matrix</span>
              <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30">
                {{ roles.length }} Roles
              </span>
            </h1>
            <p class="text-[11px] text-slate-500 dark:text-slate-400">
              Role-based access control (RBAC) governance for notification dispatch, device registry, and credentials
            </p>
          </div>
        </div>
      </div>

      <div class="flex items-center gap-2">
        <BaseButton
          variant="outline"
          size="sm"
          :icon="RefreshCw"
          :loading="isLoading"
          @click="loadData"
        >
          Refresh
        </BaseButton>

        <BaseButton
          variant="primary"
          size="sm"
          :icon="Plus"
          @click="showCreateModal = true"
        >
          Create Custom Role
        </BaseButton>
      </div>
    </div>

    <!-- Roles Grid -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <div
        v-for="role in roles"
        :key="role.id"
        class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm flex flex-col justify-between"
      >
        <div>
          <div class="flex items-center justify-between mb-2">
            <h3 class="text-sm font-bold text-slate-900 dark:text-white">
              {{ role.name }}
            </h3>
            <span
              v-if="role.system"
              class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400"
            >
              System Role
            </span>
          </div>

          <p class="text-xs text-slate-500 dark:text-slate-400 mb-4 line-clamp-2">
            {{ role.description || 'Standard role configuration.' }}
          </p>

          <div class="space-y-1">
            <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400">Assigned Permissions</span>
            <div class="flex flex-wrap gap-1 max-h-32 overflow-y-auto">
              <span
                v-for="p in role.permissions"
                :key="p"
                class="px-1.5 py-0.2 rounded text-[9px] font-mono bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-300 border border-indigo-200/60 dark:border-indigo-500/20"
              >
                {{ p }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Create Role Modal -->
    <ModernModal
      v-model="showCreateModal"
      title="Create Custom RBAC Role"
      subtitle="Assemble granular permissions for team member delegation"
      :icon="Shield"
      :initial-width="660"
      @close="showCreateModal = false"
    >
      <form id="create-role-form" @submit.prevent="handleCreateRole" class="p-6 space-y-5 text-left">
        <div
          v-if="createError"
          class="p-3.5 rounded-2xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/50 text-rose-700 dark:text-rose-300 text-xs flex items-center gap-2"
        >
          {{ createError }}
        </div>

        <BaseInput
          v-model="newRoleName"
          label="Role Title"
          placeholder="e.g. Campaign Dispatcher or Security Auditor"
          required
          :icon="Shield"
          size="md"
        />

        <div class="space-y-1.5">
          <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300">
            Description
          </label>
          <input
            v-model="newRoleDescription"
            type="text"
            placeholder="Scope and responsibilities of this role..."
            class="w-full h-10 bg-slate-50/70 dark:bg-slate-950/70 hover:bg-white dark:hover:bg-slate-900 focus:bg-white dark:focus:bg-slate-900 text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 px-3.5 rounded-xl border border-slate-200 dark:border-slate-800 text-xs sm:text-sm focus:outline-none focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10 shadow-2xs transition-all"
          />
        </div>

        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300">
              Select Permissions
            </label>
            <span class="text-[11px] font-medium text-slate-500 dark:text-slate-400">
              {{ selectedPermissions.size }} selected
            </span>
          </div>

          <div
            class="max-h-64 overflow-y-auto p-4 rounded-2xl bg-slate-50/60 dark:bg-slate-950/40 border border-slate-200/90 dark:border-slate-800 space-y-4 custom-modal-scroll"
          >
            <div
              v-for="(perms, resource) in permissionsByResource"
              :key="resource"
              class="space-y-2"
            >
              <div class="flex items-center gap-2">
                <span
                  class="px-2 py-0.5 rounded-md text-[10px] font-bold tracking-wider uppercase bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 border border-indigo-200/60 dark:border-indigo-800/40"
                >
                  {{ resource }}
                </span>
              </div>

              <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
                <label
                  v-for="p in perms"
                  :key="p.code"
                  class="flex items-start gap-2.5 text-xs cursor-pointer p-2.5 rounded-xl border transition-all duration-150 select-none"
                  :class="[
                    selectedPermissions.has(p.code)
                      ? 'bg-indigo-50/70 dark:bg-indigo-950/40 border-indigo-200 dark:border-indigo-800/70 shadow-2xs'
                      : 'bg-white dark:bg-slate-900 border-slate-200/80 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700',
                  ]"
                >
                  <input
                    type="checkbox"
                    :checked="selectedPermissions.has(p.code)"
                    @change="togglePermission(p.code)"
                    class="mt-0.5 rounded border-slate-300 dark:border-slate-700 text-indigo-600 focus:ring-indigo-500/20 cursor-pointer"
                  />
                  <div class="min-w-0 flex-1">
                    <div
                      class="font-mono text-[11px] font-semibold text-slate-900 dark:text-slate-100"
                    >
                      {{ p.code }}
                    </div>
                    <div
                      class="text-[10px] text-slate-500 dark:text-slate-400 leading-tight mt-0.5"
                    >
                      {{ p.description }}
                    </div>
                  </div>
                </label>
              </div>
            </div>
          </div>
        </div>
      </form>

      <template #footer>
        <BaseButton variant="ghost" @click="showCreateModal = false">
          Cancel
        </BaseButton>
        <BaseButton
          type="submit"
          form="create-role-form"
          variant="primary"
          :loading="isCreating"
        >
          Save Role
        </BaseButton>
      </template>
    </ModernModal>
  </div>
</template>
