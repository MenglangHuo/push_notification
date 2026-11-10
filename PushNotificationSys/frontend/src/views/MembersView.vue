<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { membersApi } from '@/api/members'
import { rolesApi } from '@/api/roles'
import type { Member, Role } from '@/api/types'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import CustomSelect, { type SelectOption } from '@/components/common/CustomSelect.vue'
import ModernModal from '@/components/common/ModernModal.vue'
import {
  AlertCircle,
  CheckCircle2,
  Mail,
  Plus,
  RefreshCw,
  Shield,
  Trash2,
  UserCheck,
  UserPlus,
  Users,
} from 'lucide-vue-next'

const authStore = useAuthStore()
const toastStore = useToastStore()

const members = ref<Member[]>([])
const roles = ref<Role[]>([])
const isLoading = ref<boolean>(false)

// Invite modal
const showInviteModal = ref(false)
const inviteEmail = ref('')
const inviteRoleId = ref('')
const isInviting = ref(false)
const inviteError = ref('')

const roleOptions = computed<SelectOption[]>(() => {
  return roles.value.map((r) => ({
    label: r.name,
    value: r.id,
  }))
})

async function loadData() {
  if (!authStore.currentOrgId) return
  isLoading.value = true
  try {
    const [m, r] = await Promise.all([
      membersApi.list(authStore.currentOrgId).catch(() => []),
      rolesApi.listRoles(authStore.currentOrgId).catch(() => []),
    ])
    members.value = m
    roles.value = r
    if (r.length > 0 && !inviteRoleId.value) {
      inviteRoleId.value = r[0].id
    }
  } catch (err: any) {
    console.error('Failed to load members', err)
  } finally {
    isLoading.value = false
  }
}

async function handleInvite() {
  if (!authStore.currentOrgId || !inviteEmail.value || !inviteRoleId.value) return
  isInviting.value = true
  inviteError.value = ''

  try {
    await membersApi.invite(authStore.currentOrgId, {
      email: inviteEmail.value.trim(),
      roleId: inviteRoleId.value,
    })
    toastStore.success('Invitation Sent', `Invite email sent to ${inviteEmail.value}`)
    showInviteModal.value = false
    inviteEmail.value = ''
    await loadData()
  } catch (err: any) {
    inviteError.value = err.response?.data?.detail || err.message || 'Failed to send invite'
    toastStore.error('Invite Failed', inviteError.value)
  } finally {
    isInviting.value = false
  }
}

async function handleRemoveMember(m: Member) {
  if (!confirm(`Are you sure you want to remove ${m.email} from the workspace?`)) return
  try {
    await membersApi.removeMember(authStore.currentOrgId!, m.membershipId)
    toastStore.success('Member Removed', `${m.email} has been removed`)
    await loadData()
  } catch (err: any) {
    toastStore.error('Remove Failed', err.response?.data?.detail || err.message)
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
            <UserCheck class="w-4 h-4" />
          </div>
          <div>
            <h1 class="text-lg font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
              <span>Organization Members</span>
              <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30">
                {{ members.length }} Members
              </span>
            </h1>
            <p class="text-[11px] text-slate-500 dark:text-slate-400">
              Manage workspace collaborators, invite team members, and configure access levels
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
          :icon="UserPlus"
          @click="showInviteModal = true"
        >
          Invite Member
        </BaseButton>
      </div>
    </div>

    <!-- Members Table Card -->
    <div
      class="bg-white dark:bg-slate-900/90 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm dark:shadow-xl overflow-hidden transition-colors"
    >
      <div class="overflow-x-auto">
        <table class="w-full text-left text-xs">
          <thead>
            <tr
              class="bg-slate-50/70 dark:bg-slate-950/70 border-b border-slate-200 dark:border-slate-800 text-[11px] font-semibold text-slate-400 uppercase tracking-wider"
            >
              <th class="py-3 px-4">Member Name & Email</th>
              <th class="py-3 px-4">Workspace Role</th>
              <th class="py-3 px-4">Status</th>
              <th class="py-3 px-4">Joined At</th>
              <th class="py-3 px-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 dark:divide-slate-800/60 font-medium">
            <tr v-if="isLoading" class="animate-pulse">
              <td colspan="5" class="py-8 text-center text-slate-400">Loading members...</td>
            </tr>

            <tr v-else-if="members.length === 0">
              <td colspan="5" class="py-8 text-center text-slate-400">
                No members found in this workspace. Invite colleagues to collaborate.
              </td>
            </tr>

            <tr
              v-for="m in members"
              :key="m.membershipId"
              class="hover:bg-slate-50/80 dark:hover:bg-slate-800/40 transition-colors"
            >
              <!-- Name & Email -->
              <td class="py-3.5 px-4 whitespace-nowrap">
                <div class="font-bold text-slate-900 dark:text-white">
                  {{ m.fullName || m.email.split('@')[0] }}
                </div>
                <div class="text-[11px] text-slate-500 dark:text-slate-400">
                  {{ m.email }}
                </div>
              </td>

              <!-- Role -->
              <td class="py-3.5 px-4 whitespace-nowrap">
                <div class="flex flex-wrap gap-1">
                  <span
                    v-for="r in m.roleAssignments"
                    :key="r.assignmentId"
                    class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-700 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-500/30"
                  >
                    {{ r.roleName }}
                  </span>
                  <span v-if="!m.roleAssignments || m.roleAssignments.length === 0" class="text-slate-400 text-[11px]">
                    Member
                  </span>
                </div>
              </td>

              <!-- Status -->
              <td class="py-3.5 px-4 whitespace-nowrap">
                <span
                  class="px-2 py-0.5 rounded-full text-[10px] font-bold inline-flex items-center gap-1"
                  :class="[
                    m.status === 'ACTIVE'
                      ? 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-500/20'
                      : 'bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-500/20',
                  ]"
                >
                  <span
                    class="w-1.5 h-1.5 rounded-full"
                    :class="m.status === 'ACTIVE' ? 'bg-emerald-500' : 'bg-amber-500'"
                  ></span>
                  {{ m.status }}
                </span>
              </td>

              <!-- Joined -->
              <td class="py-3.5 px-4 text-slate-500 dark:text-slate-400 whitespace-nowrap text-[11px]">
                Active Member
              </td>

              <!-- Actions -->
              <td class="py-3.5 px-4 text-right whitespace-nowrap">
                <button
                  type="button"
                  @click="handleRemoveMember(m)"
                  title="Remove Member"
                  class="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-500/10 transition cursor-pointer"
                >
                  <Trash2 class="w-3.5 h-3.5" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Invite Member Modal -->
    <ModernModal
      v-model="showInviteModal"
      title="Invite Team Member"
      subtitle="Send an invitation link to collaborate in this workspace"
      :initial-width="480"
      :initial-height="340"
      @close="showInviteModal = false"
    >
      <form @submit.prevent="handleInvite" class="space-y-4 p-2 text-left">
        <div v-if="inviteError" class="p-3 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/50 text-rose-700 dark:text-rose-300 text-xs">
          {{ inviteError }}
        </div>

        <BaseInput
          v-model="inviteEmail"
          type="email"
          label="Colleague Email Address"
          placeholder="developer@company.com"
          required
          :icon="Mail"
        />

        <div>
          <label class="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-1.5">
            Initial Workspace Role
          </label>
          <CustomSelect
            v-if="roleOptions.length > 0"
            v-model="inviteRoleId"
            :options="roleOptions"
            size="md"
          />
        </div>

        <div class="flex items-center justify-end gap-2 pt-2">
          <BaseButton variant="ghost" @click="showInviteModal = false">
            Cancel
          </BaseButton>
          <BaseButton type="submit" variant="primary" :loading="isInviting">
            Send Invite
          </BaseButton>
        </div>
      </form>
    </ModernModal>
  </div>
</template>
