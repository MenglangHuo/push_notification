<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { membersApi } from '@/api/members'
import { rolesApi } from '@/api/roles'
import { appsApi, type AppSummary } from '@/api/apps'
import type { Invitation, Member, Role } from '@/api/types'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import CustomSelect, { type SelectOption } from '@/components/common/CustomSelect.vue'
import ModernModal from '@/components/common/ModernModal.vue'
import {
  AlertCircle,
  Check,
  CheckCircle2,
  Copy,
  ExternalLink,
  Globe,
  Mail,
  Plus,
  RefreshCw,
  Shield,
  Smartphone,
  Trash2,
  UserCheck,
  UserPlus,
  Users,
} from 'lucide-vue-next'

const authStore = useAuthStore()
const toastStore = useToastStore()

const members = ref<Member[]>([])
const roles = ref<Role[]>([])
const apps = ref<AppSummary[]>([])
const isLoading = ref<boolean>(false)

// Invite modal
const showInviteModal = ref(false)
const inviteEmail = ref('')
const inviteRoleId = ref('')
const inviteAppId = ref('') // empty string = org-wide
const isInviting = ref(false)
const inviteError = ref('')
const createdInvite = ref<Invitation | null>(null)
const hasCopiedLink = ref(false)

const roleOptions = computed<SelectOption[]>(() => {
  return roles.value.map((r) => ({
    label: r.name,
    value: r.id,
  }))
})

const appOptions = computed<SelectOption[]>(() => {
  return [
    { label: '🌐 All Applications (Organization-wide)', value: '' },
    ...apps.value.map((a) => ({
      label: `📱 ${a.name}`,
      value: a.id,
    })),
  ]
})

const inviteLink = computed(() => {
  if (!createdInvite.value?.token) return ''
  return `${window.location.origin}/accept-invite?token=${encodeURIComponent(createdInvite.value.token)}`
})

async function copyInviteLink() {
  if (!inviteLink.value) return
  try {
    await navigator.clipboard.writeText(inviteLink.value)
    hasCopiedLink.value = true
    setTimeout(() => {
      hasCopiedLink.value = false
    }, 2500)
  } catch {
    toastStore.error('Copy Failed', 'Please manually select and copy the link')
  }
}

function resetInviteModal() {
  showInviteModal.value = false
  createdInvite.value = null
  inviteEmail.value = ''
  inviteAppId.value = ''
  inviteError.value = ''
  hasCopiedLink.value = false
}

function startAnotherInvite() {
  createdInvite.value = null
  inviteEmail.value = ''
  inviteAppId.value = ''
  inviteError.value = ''
  hasCopiedLink.value = false
}

async function loadData() {
  if (!authStore.currentOrgId) return
  isLoading.value = true
  try {
    const [m, r, a] = await Promise.all([
      membersApi.list(authStore.currentOrgId).catch(() => []),
      rolesApi.listRoles(authStore.currentOrgId).catch(() => []),
      appsApi.list(authStore.currentOrgId).catch(() => []),
    ])
    members.value = m
    roles.value = r
    apps.value = a
    if (r.length > 0 && !inviteRoleId.value) {
      inviteRoleId.value = r[0].id
    }
  } catch (err: any) {
    console.error('Failed to load members or apps', err)
  } finally {
    isLoading.value = false
  }
}

async function handleInvite() {
  if (!authStore.currentOrgId || !inviteEmail.value || !inviteRoleId.value) return
  isInviting.value = true
  inviteError.value = ''

  try {
    const res = await membersApi.invite(authStore.currentOrgId, {
      email: inviteEmail.value.trim(),
      roleId: inviteRoleId.value,
      appId: inviteAppId.value ? inviteAppId.value : undefined,
    })
    createdInvite.value = res
    toastStore.success('Invitation Created', `Invite generated for ${inviteEmail.value}`)
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
      :title="createdInvite ? 'Invitation Created' : 'Invite Team Member'"
      :subtitle="createdInvite ? 'Share the unique onboarding link with your colleague' : 'Send an invitation link to collaborate in this workspace'"
      :icon="createdInvite ? CheckCircle2 : UserPlus"
      :initial-width="520"
      @close="resetInviteModal"
    >
      <!-- Mode 1: Invitation Form -->
      <form
        v-if="!createdInvite"
        id="invite-member-form"
        @submit.prevent="handleInvite"
        class="p-6 space-y-5 text-left"
      >
        <div
          v-if="inviteError"
          class="p-3.5 rounded-2xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/50 text-rose-700 dark:text-rose-300 text-xs flex items-center gap-2"
        >
          <AlertCircle class="w-4 h-4 shrink-0 text-rose-500" />
          <span>{{ inviteError }}</span>
        </div>

        <BaseInput
          v-model="inviteEmail"
          type="email"
          label="Colleague Email Address"
          placeholder="developer@company.com"
          required
          :icon="Mail"
          size="md"
        />

        <div class="space-y-1.5">
          <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300">
            Initial Workspace Role
          </label>
          <CustomSelect
            v-if="roleOptions.length > 0"
            v-model="inviteRoleId"
            :options="roleOptions"
            :block="true"
            size="md"
          />
        </div>

        <div class="space-y-1.5">
          <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300 flex items-center justify-between">
            <span>Application Scope</span>
            <span class="text-[10px] font-normal text-slate-400">Optional</span>
          </label>
          <CustomSelect
            v-model="inviteAppId"
            :options="appOptions"
            :block="true"
            size="md"
          />
          <p class="text-[11px] text-slate-500 dark:text-slate-400">
            Selecting a specific app restricts this collaborator's access strictly to that application. Choose "All Applications" for workspace-wide access.
          </p>
        </div>
      </form>

      <!-- Mode 2: Invitation Created / Copy Link Screen -->
      <div v-else class="p-6 space-y-5 text-left">
        <div class="p-4 rounded-2xl bg-emerald-50 dark:bg-emerald-950/30 border border-emerald-200 dark:border-emerald-900/40 space-y-2">
          <div class="flex items-center gap-2 text-emerald-800 dark:text-emerald-300 font-semibold text-sm">
            <CheckCircle2 class="w-4 h-4 shrink-0 text-emerald-600 dark:text-emerald-400" />
            <span>Invitation successfully generated for {{ createdInvite.email }}</span>
          </div>
          <div class="text-xs text-emerald-700/80 dark:text-emerald-300/80 flex flex-wrap gap-x-4 gap-y-1 pt-1">
            <span><strong>Role:</strong> {{ roles.find(r => r.id === createdInvite?.roleId)?.name || 'Custom' }}</span>
            <span><strong>Scope:</strong> {{ apps.find(a => a.id === createdInvite?.appId)?.name || 'Organization-wide' }}</span>
            <span><strong>Expires:</strong> In 7 days</span>
          </div>
        </div>

        <div class="space-y-2">
          <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300">
            Direct Onboarding Link
          </label>
          <div class="flex items-center gap-2">
            <input
              type="text"
              readonly
              :value="inviteLink"
              class="w-full px-3.5 py-2.5 text-xs font-mono rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-800 text-slate-800 dark:text-slate-200 select-all focus:outline-none"
            />
            <BaseButton
              type="button"
              :variant="hasCopiedLink ? 'secondary' : 'primary'"
              size="sm"
              :icon="hasCopiedLink ? Check : Copy"
              class="shrink-0"
              @click="copyInviteLink"
            >
              {{ hasCopiedLink ? 'Copied!' : 'Copy' }}
            </BaseButton>
          </div>
          <p class="text-[11px] text-slate-500 dark:text-slate-400 leading-relaxed">
            Send this URL to your colleague. When they open it, they will enter their full name and password to complete setup.
          </p>
        </div>
      </div>

      <template #footer>
        <template v-if="!createdInvite">
          <BaseButton variant="ghost" @click="resetInviteModal">
            Cancel
          </BaseButton>
          <BaseButton
            type="submit"
            form="invite-member-form"
            variant="primary"
            :loading="isInviting"
          >
            Generate Invitation
          </BaseButton>
        </template>
        <template v-else>
          <BaseButton variant="outline" @click="startAnotherInvite">
            Invite Another
          </BaseButton>
          <BaseButton variant="primary" @click="resetInviteModal">
            Done
          </BaseButton>
        </template>
      </template>
    </ModernModal>
  </div>
</template>
