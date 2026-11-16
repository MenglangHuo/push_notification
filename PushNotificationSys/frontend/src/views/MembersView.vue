<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { membersApi } from '@/api/members'
import { filesApi } from '@/api/files'
import { rolesApi } from '@/api/roles'
import { appsApi, type AppSummary } from '@/api/apps'
import type { Invitation, Member, Role } from '@/api/types'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import CustomSelect, { type SelectOption } from '@/components/common/CustomSelect.vue'
import ModernModal from '@/components/common/ModernModal.vue'
import {
  AlertCircle,
  Building2,
  Calendar,
  Camera,
  Check,
  CheckCircle2,
  Code2,
  Copy,
  Crown,
  ExternalLink,
  Eye,
  Globe,
  KeyRound,
  Layers,
  LayoutGrid,
  Link2,
  List,
  Lock,
  Mail,
  Megaphone,
  Plus,
  RefreshCw,
  Save,
  Search,
  Shield,
  ShieldCheck,
  Smartphone,
  Sparkles,
  Trash2,
  Upload,
  User,
  UserCheck,
  UserCog,
  UserPlus,
  Users,
  X,
} from 'lucide-vue-next'

const authStore = useAuthStore()
const toastStore = useToastStore()

const members = ref<Member[]>([])
const roles = ref<Role[]>([])
const apps = ref<AppSummary[]>([])
const isLoading = ref<boolean>(false)

// View Mode (Grid vs Table) with LocalStorage persistence (default 'grid')
const viewMode = ref<'grid' | 'table'>(
  (localStorage.getItem('bronx_members_view_mode') as 'grid' | 'table') || 'grid',
)

function setViewMode(mode: 'grid' | 'table') {
  viewMode.value = mode
  localStorage.setItem('bronx_members_view_mode', mode)
}

// Search & Role Filters
const searchQuery = ref('')
const selectedRoleFilter = ref('ALL')

// User Detail & Edit Modal
const showDetailModal = ref(false)
const selectedMember = ref<Member | null>(null)
const copiedDetailKey = ref<string | null>(null)

// Profile Editing State inside Detail Modal
const editFullName = ref('')
const editAvatarUrl = ref('')
const editAvatarPath = ref<string | null>(null)
const pendingUploadedPath = ref<string | null>(null)
const previewImgError = ref(false)
const showUrlInput = ref(false)
const failedAvatarIds = ref<Set<string>>(new Set())
const isUpdatingProfile = ref(false)
const isUploadingAvatar = ref(false)
const avatarFileInputRef = ref<HTMLInputElement | null>(null)

const isUploadedPhoto = computed(() => {
  return !!editAvatarPath.value || (!!editAvatarUrl.value && editAvatarUrl.value.includes('/uploads/avatars/'))
})

function toggleUrlInput() {
  showUrlInput.value = !showUrlInput.value
}

watch(editAvatarUrl, () => {
  previewImgError.value = false
})

function markMemberAvatarFailed(userId: string) {
  const next = new Set(failedAvatarIds.value)
  next.add(userId)
  failedAvatarIds.value = next
}

function cleanupPendingUpload() {
  if (pendingUploadedPath.value) {
    const orphan = pendingUploadedPath.value
    pendingUploadedPath.value = null
    filesApi.deleteAvatar(orphan).catch(() => {})
  }
}

// Check if selected member is current user (self-only update rule)
const isCurrentMemberSelf = computed(() => {
  if (!selectedMember.value || !authStore.user) return false
  return (
    selectedMember.value.userId === authStore.user.id ||
    selectedMember.value.email.toLowerCase() === authStore.user.email.toLowerCase()
  )
})

const hasProfileChanges = computed(() => {
  if (!selectedMember.value || !isCurrentMemberSelf.value) return false
  const origName = (selectedMember.value.fullName || '').trim()
  const origAvatar = (selectedMember.value.avatarUrl || '').trim()
  return editFullName.value.trim() !== origName || editAvatarUrl.value.trim() !== origAvatar || !!editAvatarPath.value
})

function triggerAvatarUpload() {
  if (!isCurrentMemberSelf.value) return
  avatarFileInputRef.value?.click()
}

async function handleAvatarFileSelected(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    toastStore.error('Invalid File', 'Please select an image file (PNG, JPG, WebP, GIF)')
    return
  }

  if (file.size > 5 * 1024 * 1024) {
    toastStore.error('File Too Large', 'Please select an image smaller than 5MB')
    return
  }

  isUploadingAvatar.value = true
  try {
    const result = await filesApi.uploadAvatar(file)
    // Remove previous unsaved upload if user picks another file before saving
    cleanupPendingUpload()
    pendingUploadedPath.value = result.path
    editAvatarPath.value = result.path
    editAvatarUrl.value = result.url
    showUrlInput.value = false
    previewImgError.value = false
    toastStore.info('Photo Uploaded', 'Click "Save Changes" to apply your new profile photo')
  } catch (err: any) {
    toastStore.error('Upload Failed', err.response?.data?.message || err.response?.data?.detail || err.message || 'Failed to upload avatar')
  } finally {
    isUploadingAvatar.value = false
    target.value = ''
  }
}

function handleExternalAvatarInput() {
  cleanupPendingUpload()
  editAvatarPath.value = null
  previewImgError.value = false
}

function removeAvatarPhoto() {
  if (!isCurrentMemberSelf.value) return
  cleanupPendingUpload()
  editAvatarPath.value = null
  editAvatarUrl.value = ''
  previewImgError.value = false
  showUrlInput.value = false
}

function isMemberOrgWide(m: Member | null): boolean {
  if (!m || !m.roleAssignments || m.roleAssignments.length === 0) return true
  return m.roleAssignments.some((r) => !r.appId)
}

function getMemberAppScopes(m: Member | null): { id: string; name: string }[] {
  if (!m || !m.roleAssignments) return []
  const map = new Map<string, string>()
  for (const r of m.roleAssignments) {
    if (r.appId) {
      const name = getAppScopeName(r.appId)
      map.set(r.appId, name)
    }
  }
  return Array.from(map.entries()).map(([id, name]) => ({ id, name }))
}

function openMemberDetail(m: Member) {
  cleanupPendingUpload()
  selectedMember.value = m
  editFullName.value = m.fullName || ''
  editAvatarUrl.value = m.avatarUrl || ''
  editAvatarPath.value = null
  previewImgError.value = false
  // Expand URL field if member already has an external link; otherwise keep clean
  showUrlInput.value = !!(m.avatarUrl && !m.avatarUrl.includes('/uploads/avatars/'))
  showDetailModal.value = true
}

function closeDetailModal() {
  cleanupPendingUpload()
  editAvatarPath.value = null
  showUrlInput.value = false
  showDetailModal.value = false
  selectedMember.value = null
}

async function handleUpdateProfile() {
  if (!authStore.currentOrgId || !selectedMember.value) return
  if (!editFullName.value.trim()) {
    toastStore.error('Validation Error', 'Full Name / Username cannot be empty')
    return
  }

  const trimmedUrl = editAvatarUrl.value.trim()
  const isRemoving = !trimmedUrl
  const avatarToSend = isRemoving ? '' : (editAvatarPath.value || trimmedUrl)

  isUpdatingProfile.value = true
  try {
    const updated = await membersApi.updateProfile(authStore.currentOrgId, selectedMember.value.membershipId, {
      fullName: editFullName.value.trim(),
      avatarUrl: avatarToSend,
      removeAvatar: isRemoving,
    })

    // Upload is now persisted on the user record — do not delete on modal close
    pendingUploadedPath.value = null
    editAvatarPath.value = null

    selectedMember.value.fullName = updated.fullName
    selectedMember.value.avatarUrl = updated.avatarUrl
    editAvatarUrl.value = updated.avatarUrl || ''

    // Reset any cached broken-image flag for this user
    if (failedAvatarIds.value.has(updated.userId)) {
      const next = new Set(failedAvatarIds.value)
      next.delete(updated.userId)
      failedAvatarIds.value = next
    }

    // Reflect into the local members list
    const found = members.value.find((item) => item.membershipId === updated.membershipId)
    if (found) {
      found.fullName = updated.fullName
      found.avatarUrl = updated.avatarUrl
    }

    // Sync Topbar / authStore when the current user updates their own profile
    if (isCurrentMemberSelf.value && authStore.user) {
      authStore.user.fullName = updated.fullName
      authStore.user.avatarUrl = updated.avatarUrl || undefined
      localStorage.setItem('bronx_user', JSON.stringify(authStore.user))
    }

    toastStore.success('Profile Updated', `Updated profile for ${updated.fullName || updated.email}`)
  } catch (err: any) {
    toastStore.error('Update Failed', err.response?.data?.message || err.response?.data?.detail || err.message || 'Failed to update member profile')
  } finally {
    isUpdatingProfile.value = false
  }
}

function copyDetailText(text: string, key: string) {
  navigator.clipboard.writeText(text)
  copiedDetailKey.value = key
  toastStore.success('Copied to Clipboard', text.length > 24 ? text.substring(0, 20) + '...' : text)
  setTimeout(() => {
    copiedDetailKey.value = null
  }, 2000)
}

function getRolePermissions(roleId: string): string[] {
  const r = roles.value.find((item) => item.id === roleId)
  return r?.permissions || []
}

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

// KPI Metrics
const totalMembersCount = computed(() => members.value.length)

const ownersAndAdminsCount = computed(() => {
  return members.value.filter((m) =>
    m.roleAssignments?.some((r) => {
      const name = r.roleName.toUpperCase()
      return name.includes('OWNER') || name.includes('ADMIN')
    }),
  ).length
})

const activeRolesCount = computed(() => {
  const set = new Set<string>()
  for (const m of members.value) {
    for (const r of m.roleAssignments || []) {
      set.add(r.roleName)
    }
  }
  return set.size
})

// Filtered Members List
const filteredMembers = computed(() => {
  let list = members.value

  if (selectedRoleFilter.value !== 'ALL') {
    const rf = selectedRoleFilter.value.toUpperCase()
    list = list.filter((m) =>
      m.roleAssignments?.some((r) => r.roleName.toUpperCase().includes(rf)),
    )
  }

  if (!searchQuery.value.trim()) return list
  const q = searchQuery.value.toLowerCase().trim()
  return list.filter(
    (m) =>
      m.fullName?.toLowerCase().includes(q) ||
      m.email.toLowerCase().includes(q) ||
      m.userId.toLowerCase().includes(q) ||
      m.roleAssignments?.some((r) => r.roleName.toLowerCase().includes(q)),
  )
})

function getRoleBadge(roleName: string) {
  const norm = (roleName || '').toUpperCase()
  if (norm.includes('OWNER')) {
    return {
      icon: Crown,
      badgeClass: 'bg-purple-50 dark:bg-purple-500/15 text-purple-700 dark:text-purple-300 border-purple-200 dark:border-purple-500/30',
      iconClass: 'text-purple-500',
    }
  }
  if (norm.includes('ADMIN')) {
    return {
      icon: ShieldCheck,
      badgeClass: 'bg-indigo-50 dark:bg-indigo-500/15 text-indigo-700 dark:text-indigo-300 border-indigo-200 dark:border-indigo-500/30',
      iconClass: 'text-indigo-500',
    }
  }
  if (norm.includes('DEV')) {
    return {
      icon: Code2,
      badgeClass: 'bg-sky-50 dark:bg-sky-500/15 text-sky-700 dark:text-sky-300 border-sky-200 dark:border-sky-500/30',
      iconClass: 'text-sky-500',
    }
  }
  if (norm.includes('MARKET')) {
    return {
      icon: Megaphone,
      badgeClass: 'bg-emerald-50 dark:bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 border-emerald-200 dark:border-emerald-500/30',
      iconClass: 'text-emerald-500',
    }
  }
  return {
    icon: Shield,
    badgeClass: 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border-slate-200 dark:border-slate-700',
    iconClass: 'text-slate-400',
  }
}

function getAppScopeName(appId?: string | null) {
  if (!appId) return 'Organization-wide'
  const found = apps.value.find((a) => a.id === appId)
  return found ? found.name : 'App Scoped'
}

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

async function handleRemoveRoleFromSelected(assignmentId: string) {
  if (!authStore.currentOrgId || !selectedMember.value) return
  try {
    await membersApi.removeRole(authStore.currentOrgId, selectedMember.value.membershipId, assignmentId)
    selectedMember.value.roleAssignments = selectedMember.value.roleAssignments.filter(
      (r) => r.assignmentId !== assignmentId,
    )
    toastStore.success('Role Removed', 'Role removed from member')
    await loadData()
  } catch (err: any) {
    toastStore.error('Remove Role Failed', err.response?.data?.detail || err.message)
  }
}

async function handleRemoveMember(m: Member) {
  if (!confirm(`Are you sure you want to remove ${m.email} from the workspace?`)) return
  try {
    await membersApi.removeMember(authStore.currentOrgId!, m.membershipId)
    toastStore.success('Member Removed', `${m.email} has been removed`)
    if (selectedMember.value?.membershipId === m.membershipId) {
      showDetailModal.value = false
      selectedMember.value = null
    }
    await loadData()
  } catch (err: any) {
    toastStore.error('Remove Failed', err.response?.data?.detail || err.message)
  }
}

watch(
  () => authStore.currentOrgId,
  (newOrgId) => {
    if (newOrgId) {
      loadData()
    }
  },
)

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="space-y-5 text-left">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
      <div>
        <div class="flex items-center gap-2.5">
          <div
            class="w-9 h-9 rounded-xl bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 flex items-center justify-center border border-indigo-200/90 dark:border-indigo-500/30 shadow-xs shrink-0"
          >
            <UserCheck class="w-4.5 h-4.5" />
          </div>
          <div>
            <h1 class="text-lg font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
              <span>Organization Members</span>
              <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30">
                {{ members.length }} Members
              </span>
            </h1>
            <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
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

    <!-- 3-Card KPI Summary Strip -->
    <div class="grid grid-cols-1 sm:grid-cols-3 gap-3.5">
      <!-- Total Members -->
      <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-4 border border-slate-200 dark:border-slate-800 shadow-xs flex items-center justify-between">
        <div class="space-y-0.5">
          <p class="text-[11px] font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
            Total Collaborators
          </p>
          <div class="text-2xl font-black font-mono text-slate-900 dark:text-white">
            {{ totalMembersCount }}
          </div>
          <p class="text-[11px] text-slate-400">
            Active workspace accounts
          </p>
        </div>
        <div class="w-10 h-10 rounded-xl bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 flex items-center justify-center border border-indigo-200/60 dark:border-indigo-500/20">
          <Users class="w-5 h-5" />
        </div>
      </div>

      <!-- Workspace Leadership -->
      <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-4 border border-slate-200 dark:border-slate-800 shadow-xs flex items-center justify-between">
        <div class="space-y-0.5">
          <p class="text-[11px] font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
            Workspace Leadership
          </p>
          <div class="text-2xl font-black font-mono text-purple-600 dark:text-purple-400">
            {{ ownersAndAdminsCount }}
          </div>
          <p class="text-[11px] text-slate-400">
            Owners and Administrators
          </p>
        </div>
        <div class="w-10 h-10 rounded-xl bg-purple-50 dark:bg-purple-500/10 text-purple-600 dark:text-purple-400 flex items-center justify-center border border-purple-200/60 dark:border-purple-500/20">
          <Crown class="w-5 h-5" />
        </div>
      </div>

      <!-- Active RBAC Roles -->
      <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-4 border border-slate-200 dark:border-slate-800 shadow-xs flex items-center justify-between">
        <div class="space-y-0.5">
          <p class="text-[11px] font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
            Active Roles
          </p>
          <div class="text-2xl font-black font-mono text-emerald-600 dark:text-emerald-400">
            {{ activeRolesCount }}
          </div>
          <p class="text-[11px] text-slate-400">
            Configured access scopes
          </p>
        </div>
        <div class="w-10 h-10 rounded-xl bg-emerald-50 dark:bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 flex items-center justify-center border border-emerald-200/60 dark:border-emerald-500/20">
          <ShieldCheck class="w-5 h-5" />
        </div>
      </div>
    </div>

    <!-- Toolbar: Search, Role Filter, and View Mode Switcher -->
    <div
      class="bg-white dark:bg-slate-900/90 p-3 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-3"
    >
      <div class="flex-1 max-w-md">
        <BaseInput
          v-model="searchQuery"
          placeholder="Search by name, email, role, or user ID..."
          size="sm"
          :icon="Search"
        />
      </div>

      <div class="flex items-center justify-between md:justify-end gap-3 flex-wrap">
        <!-- Role Filter Pills -->
        <div class="flex items-center gap-1 text-xs overflow-x-auto pb-1 md:pb-0">
          <button
            type="button"
            @click="selectedRoleFilter = 'ALL'"
            class="px-2.5 py-1.5 rounded-xl font-medium transition cursor-pointer shrink-0"
            :class="[
              selectedRoleFilter === 'ALL'
                ? 'bg-indigo-600 text-white shadow-xs font-semibold'
                : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800',
            ]"
          >
            All Members
          </button>
          <button
            type="button"
            @click="selectedRoleFilter = 'OWNER'"
            class="px-2.5 py-1.5 rounded-xl font-medium transition cursor-pointer flex items-center gap-1.5 shrink-0"
            :class="[
              selectedRoleFilter === 'OWNER'
                ? 'bg-purple-600 text-white shadow-xs font-semibold'
                : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800',
            ]"
          >
            <Crown class="w-3.5 h-3.5" />
            Owners
          </button>
          <button
            type="button"
            @click="selectedRoleFilter = 'ADMIN'"
            class="px-2.5 py-1.5 rounded-xl font-medium transition cursor-pointer flex items-center gap-1.5 shrink-0"
            :class="[
              selectedRoleFilter === 'ADMIN'
                ? 'bg-indigo-600 text-white shadow-xs font-semibold'
                : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800',
            ]"
          >
            <ShieldCheck class="w-3.5 h-3.5" />
            Admins
          </button>
          <button
            type="button"
            @click="selectedRoleFilter = 'DEV'"
            class="px-2.5 py-1.5 rounded-xl font-medium transition cursor-pointer flex items-center gap-1.5 shrink-0"
            :class="[
              selectedRoleFilter === 'DEV'
                ? 'bg-sky-600 text-white shadow-xs font-semibold'
                : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800',
            ]"
          >
            <Code2 class="w-3.5 h-3.5" />
            Developers
          </button>
        </div>

        <!-- View Mode Switcher: Grid vs Table (Saved to LocalStorage) -->
        <div class="h-9 flex items-center p-0.5 sm:p-1 rounded-xl bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700/80 shrink-0">
          <button
            type="button"
            @click="setViewMode('grid')"
            class="h-7.5 sm:h-8 px-2.5 sm:px-3 rounded-lg text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap transition-all cursor-pointer"
            :class="[
              viewMode === 'grid'
                ? 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-xs'
                : 'text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white',
            ]"
            title="Switch to Grid View (Default)"
          >
            <LayoutGrid class="w-3.5 h-3.5 shrink-0" />
            <span class="hidden sm:inline">Grid</span>
          </button>

          <button
            type="button"
            @click="setViewMode('table')"
            class="h-7.5 sm:h-8 px-2.5 sm:px-3 rounded-lg text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap transition-all cursor-pointer"
            :class="[
              viewMode === 'table'
                ? 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-xs'
                : 'text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white',
            ]"
            title="Switch to Table View"
          >
            <List class="w-3.5 h-3.5 shrink-0" />
            <span class="hidden sm:inline">Table</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div
      v-if="!isLoading && filteredMembers.length === 0"
      class="bg-white dark:bg-slate-900/90 rounded-2xl p-10 border border-slate-200 dark:border-slate-800 text-center space-y-3"
    >
      <Users class="w-10 h-10 text-slate-400 mx-auto" />
      <h3 class="text-sm font-semibold text-slate-900 dark:text-white">
        {{ members.length === 0 ? 'No Members Found' : 'No Members Match Filter' }}
      </h3>
      <p class="text-xs text-slate-500 max-w-sm mx-auto">
        {{
          members.length === 0
            ? 'No collaborators found in this workspace. Invite colleagues to get started.'
            : 'Try adjusting your search query or role filter.'
        }}
      </p>
      <BaseButton
        v-if="members.length === 0"
        variant="primary"
        size="sm"
        :icon="UserPlus"
        @click="showInviteModal = true"
      >
        Invite Member
      </BaseButton>
      <BaseButton
        v-else
        variant="outline"
        size="sm"
        @click="searchQuery = ''; selectedRoleFilter = 'ALL'"
      >
        Clear Filters
      </BaseButton>
    </div>

    <!-- Loading Skeleton -->
    <div v-else-if="isLoading" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <div
        v-for="i in 3"
        :key="i"
        class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 animate-pulse space-y-4"
      >
        <div class="h-4 bg-slate-200 dark:bg-slate-800 rounded w-1/2"></div>
        <div class="h-3 bg-slate-100 dark:bg-slate-850 rounded w-3/4"></div>
        <div class="h-10 bg-slate-50 dark:bg-slate-950 rounded-xl"></div>
      </div>
    </div>

    <!-- 1. GRID VIEW (Default) -->
    <div v-else-if="viewMode === 'grid'" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4.5">
      <div
        v-for="m in filteredMembers"
        :key="m.membershipId"
        class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-xs hover:shadow-md dark:shadow-xl flex flex-col justify-between transition-all duration-200 hover:border-indigo-300 dark:hover:border-indigo-500/40 relative group"
      >
        <div>
          <!-- Header Status Pill & Remove Action -->
          <div class="flex items-center justify-between mb-3.5">
            <span
              class="px-2.5 py-1 rounded-full text-[10px] font-bold inline-flex items-center gap-1.5"
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

            <button
              type="button"
              @click.stop="handleRemoveMember(m)"
              title="Remove Member"
              class="p-1 rounded-lg text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-500/10 transition cursor-pointer"
            >
              <Trash2 class="w-3.5 h-3.5" />
            </button>
          </div>

          <!-- Member Identity Hero -->
          <div class="flex items-start gap-3 mb-3.5">
            <div
              class="w-12 h-12 rounded-2xl text-white flex items-center justify-center font-black text-sm shadow-xs shrink-0 overflow-hidden ring-1 ring-slate-200/70 dark:ring-slate-700/70 bg-slate-100 dark:bg-slate-800"
              :class="[
                (!m.avatarUrl || failedAvatarIds.has(m.userId)) && (m.roleAssignments?.some(r => r.roleName.toUpperCase().includes('OWNER'))
                  ? 'bg-gradient-to-br from-purple-500 to-indigo-600'
                  : 'bg-gradient-to-br from-indigo-500 to-sky-600'),
              ]"
            >
              <img
                v-if="m.avatarUrl && !failedAvatarIds.has(m.userId)"
                :src="m.avatarUrl"
                :alt="m.fullName || m.email"
                class="w-full h-full object-cover"
                @error="markMemberAvatarFailed(m.userId)"
              />
              <span v-else>
                {{ (m.fullName || m.email).charAt(0).toUpperCase() }}
              </span>
            </div>
            <div class="min-w-0 flex-1">
              <h3 class="text-sm font-bold text-slate-900 dark:text-white truncate group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">
                {{ m.fullName || m.email.split('@')[0] }}
              </h3>
              <p class="text-xs text-slate-500 dark:text-slate-400 truncate mt-0.5">
                {{ m.email }}
              </p>
            </div>
          </div>

          <!-- Role Badges Strip -->
          <div class="flex flex-wrap gap-1.5 mb-3.5">
            <span
              v-for="r in m.roleAssignments"
              :key="r.assignmentId"
              class="px-2 py-0.5 rounded-lg text-[10px] font-bold border inline-flex items-center gap-1 shadow-2xs"
              :class="getRoleBadge(r.roleName).badgeClass"
            >
              <component :is="getRoleBadge(r.roleName).icon" class="w-3 h-3" :class="getRoleBadge(r.roleName).iconClass" />
              <span>{{ r.roleName }}</span>
              <span v-if="r.appId" class="text-[9px] opacity-75">({{ getAppScopeName(r.appId) }})</span>
            </span>
            <span v-if="!m.roleAssignments || m.roleAssignments.length === 0" class="text-slate-400 text-xs italic">
              No roles assigned
            </span>
          </div>

          <!-- Inset Details Box -->
          <div
            class="space-y-1.5 bg-slate-50 dark:bg-slate-950/60 p-3 rounded-xl border border-slate-200 dark:border-slate-800/80 text-xs mb-4"
          >
            <div class="flex items-center justify-between">
              <span class="text-[10px] uppercase font-semibold text-slate-400">User ID:</span>
              <span class="font-mono text-[11px] text-slate-700 dark:text-slate-300 truncate max-w-[170px]" :title="m.userId">
                {{ m.userId }}
              </span>
            </div>
            <div class="flex items-center justify-between pt-1 border-t border-slate-200/60 dark:border-slate-800/60">
              <span class="text-[10px] uppercase font-semibold text-slate-400">Scope:</span>
              <span class="text-[11px] text-slate-600 dark:text-slate-300">
                {{ m.roleAssignments?.some(r => r.appId) ? 'App Scoped' : 'Organization-wide' }}
              </span>
            </div>
          </div>
        </div>

        <!-- Footer Action -->
        <div class="pt-3 border-t border-slate-100 dark:border-slate-800">
          <BaseButton
            variant="outline"
            size="md"
            class="w-full flex items-center justify-center gap-2 font-semibold text-xs sm:text-sm py-2.5 px-4 rounded-xl shadow-2xs hover:shadow-xs hover:border-indigo-400 dark:hover:border-indigo-500 hover:text-indigo-600 dark:hover:text-indigo-400 transition-all cursor-pointer"
            :icon="UserCog"
            @click="openMemberDetail(m)"
          >
            Manage Profile & Access
          </BaseButton>
        </div>
      </div>
    </div>

    <!-- 2. TABLE VIEW -->
    <div
      v-else-if="viewMode === 'table'"
      class="bg-white dark:bg-slate-900/90 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-xs overflow-hidden"
    >
      <div class="overflow-x-auto">
        <table class="w-full text-left text-xs border-collapse">
          <thead>
            <tr
              class="border-b border-slate-200 dark:border-slate-800 bg-slate-50/75 dark:bg-slate-950/50 text-[11px] font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400"
            >
              <th class="py-3 px-4">Member</th>
              <th class="py-3 px-4">User ID</th>
              <th class="py-3 px-4">Workspace Roles</th>
              <th class="py-3 px-4">Scope</th>
              <th class="py-3 px-4">Status</th>
              <th class="py-3 px-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 dark:divide-slate-800/70">
            <tr
              v-for="m in filteredMembers"
              :key="m.membershipId"
              class="hover:bg-slate-50/80 dark:hover:bg-slate-800/40 transition-colors"
            >
              <!-- Member Info -->
              <td class="py-3.5 px-4 whitespace-nowrap">
                <div class="flex items-center gap-3">
                  <div
                    class="w-9 h-9 rounded-xl text-white flex items-center justify-center font-bold text-xs shadow-xs shrink-0 overflow-hidden ring-1 ring-slate-200/70 dark:ring-slate-700/70 bg-slate-100 dark:bg-slate-800"
                    :class="[
                      (!m.avatarUrl || failedAvatarIds.has(m.userId)) && (m.roleAssignments?.some(r => r.roleName.toUpperCase().includes('OWNER'))
                        ? 'bg-gradient-to-br from-purple-500 to-indigo-600'
                        : 'bg-gradient-to-br from-indigo-500 to-sky-600'),
                    ]"
                  >
                    <img
                      v-if="m.avatarUrl && !failedAvatarIds.has(m.userId)"
                      :src="m.avatarUrl"
                      :alt="m.fullName || m.email"
                      class="w-full h-full object-cover"
                      @error="markMemberAvatarFailed(m.userId)"
                    />
                    <span v-else>
                      {{ (m.fullName || m.email).charAt(0).toUpperCase() }}
                    </span>
                  </div>
                  <div>
                    <div class="font-bold text-slate-900 dark:text-white">
                      {{ m.fullName || m.email.split('@')[0] }}
                    </div>
                    <div class="text-[11px] text-slate-500 dark:text-slate-400">
                      {{ m.email }}
                    </div>
                  </div>
                </div>
              </td>

              <!-- User ID -->
              <td class="py-3.5 px-4 font-mono text-[11px] text-slate-600 dark:text-slate-300">
                {{ m.userId.slice(0, 16) }}...
              </td>

              <!-- Roles -->
              <td class="py-3.5 px-4">
                <div class="flex flex-wrap gap-1">
                  <span
                    v-for="r in m.roleAssignments"
                    :key="r.assignmentId"
                    class="px-2 py-0.5 rounded-lg text-[10px] font-bold border inline-flex items-center gap-1 shadow-2xs"
                    :class="getRoleBadge(r.roleName).badgeClass"
                  >
                    <component :is="getRoleBadge(r.roleName).icon" class="w-3 h-3" :class="getRoleBadge(r.roleName).iconClass" />
                    <span>{{ r.roleName }}</span>
                  </span>
                  <span v-if="!m.roleAssignments || m.roleAssignments.length === 0" class="text-slate-400 text-[11px]">
                    Member
                  </span>
                </div>
              </td>

              <!-- Scope -->
              <td class="py-3.5 px-4 text-slate-500 dark:text-slate-400 text-[11px] whitespace-nowrap">
                {{ m.roleAssignments?.some(r => r.appId) ? 'App Scoped' : 'Organization-wide' }}
              </td>

              <!-- Status -->
              <td class="py-3.5 px-4 whitespace-nowrap">
                <span
                  class="px-2.5 py-1 rounded-full text-[10px] font-bold inline-flex items-center gap-1.5"
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

              <!-- Actions -->
              <td class="py-3.5 px-4 text-right whitespace-nowrap">
                <div class="flex items-center justify-end gap-1.5">
                  <BaseButton
                    variant="outline"
                    size="xs"
                    :icon="UserCog"
                    title="View & Edit Member Profile"
                    @click="openMemberDetail(m)"
                  >
                    View & Edit
                  </BaseButton>

                  <button
                    type="button"
                    @click="handleRemoveMember(m)"
                    title="Remove Member"
                    class="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-500/10 transition cursor-pointer"
                  >
                    <Trash2 class="w-3.5 h-3.5" />
                  </button>
                </div>
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

    <!-- User Detail & Access Modal -->
    <ModernModal
      v-model="showDetailModal"
      title="Member Profile & Access"
      subtitle="Workspace collaborator identity, organization affiliation, and access scope"
      :icon="UserCog"
      :initial-width="700"
      size="lg"
      @close="closeDetailModal"
    >
      <div v-if="selectedMember" class="p-6 space-y-5 text-left">
        <!-- Hidden Avatar File Input for Direct Upload -->
        <input
          ref="avatarFileInputRef"
          type="file"
          accept="image/jpeg,image/png,image/webp,image/gif"
          class="hidden"
          @change="handleAvatarFileSelected"
        />

        <!-- 1. PROFILE INFORMATION (Self-only edit, read-only for others) -->
        <div class="p-5 rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-xs space-y-4">
          <div class="flex items-center justify-between pb-3 border-b border-slate-100 dark:border-slate-800/80">
            <div class="flex items-center gap-2.5">
              <div class="w-8 h-8 rounded-xl bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 flex items-center justify-center shadow-2xs">
                <UserCog class="w-4 h-4" />
              </div>
              <div>
                <h4 class="text-xs font-bold uppercase tracking-wider text-slate-800 dark:text-slate-200">
                  Profile Information
                </h4>
                <p class="text-[11px] text-slate-400">
                  {{ isCurrentMemberSelf ? 'Manage your display name and profile picture' : 'Collaborator identity details (read-only)' }}
                </p>
              </div>
            </div>

            <!-- Self Indicator or Unsaved Changes Badge -->
            <div class="flex items-center gap-2">
              <span
                v-if="hasProfileChanges"
                class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-50 dark:bg-amber-500/15 text-amber-600 dark:text-amber-400 border border-amber-200 dark:border-amber-500/30 inline-flex items-center gap-1 animate-pulse"
              >
                <Sparkles class="w-3 h-3" />
                Unsaved Changes
              </span>
              <span
                v-else-if="isCurrentMemberSelf"
                class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/20 inline-flex items-center gap-1"
              >
                <UserCheck class="w-3 h-3" />
                Your Profile
              </span>
              <span
                v-else
                class="px-2 py-0.5 rounded-full text-[10px] font-medium bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 border border-slate-200 dark:border-slate-700 inline-flex items-center gap-1"
              >
                <Lock class="w-3 h-3" />
                View Only
              </span>
            </div>
          </div>

          <!-- Self Profile Editor -->
          <div v-if="isCurrentMemberSelf" class="space-y-4">
            <!-- Dedicated Avatar Management Card -->
            <div class="p-4 rounded-2xl bg-slate-50/80 dark:bg-slate-950/40 border border-slate-200/90 dark:border-slate-800 space-y-3.5">
              <div class="flex flex-col sm:flex-row items-center sm:items-start gap-4">
                <!-- Avatar Preview Thumbnail with Interactive Hover -->
                <div class="relative shrink-0 group">
                  <div
                    class="w-20 h-20 rounded-2xl text-white flex items-center justify-center font-black text-2xl shadow-sm overflow-hidden ring-2 ring-indigo-500/20 dark:ring-indigo-500/30 bg-slate-100 dark:bg-slate-800 cursor-pointer hover:ring-indigo-500 transition-all relative"
                    :class="[
                      (!editAvatarUrl || previewImgError) && (selectedMember.roleAssignments?.some(r => r.roleName.toUpperCase().includes('OWNER'))
                        ? 'bg-gradient-to-br from-purple-500 to-indigo-600'
                        : 'bg-gradient-to-br from-indigo-500 to-sky-600'),
                    ]"
                    title="Click to upload a new photo"
                    @click="triggerAvatarUpload"
                  >
                    <img
                      v-if="editAvatarUrl && !previewImgError"
                      :src="editAvatarUrl"
                      :alt="editFullName || selectedMember.email"
                      class="w-full h-full object-cover"
                      @error="previewImgError = true"
                    />
                    <span v-else>
                      {{ (editFullName || selectedMember.email).charAt(0).toUpperCase() }}
                    </span>

                    <!-- Hover Camera Overlay -->
                    <div
                      class="absolute inset-0 bg-black/60 flex flex-col items-center justify-center text-white opacity-0 group-hover:opacity-100 transition-opacity rounded-2xl"
                    >
                      <RefreshCw v-if="isUploadingAvatar" class="w-5 h-5 mb-0.5 animate-spin" />
                      <Camera v-else class="w-5 h-5 mb-0.5" />
                      <span class="text-[9px] font-bold">{{ isUploadingAvatar ? 'Uploading' : 'Change' }}</span>
                    </div>
                  </div>
                </div>

                <!-- Avatar Actions & Status -->
                <div class="flex-1 min-w-0 space-y-2.5 text-center sm:text-left w-full">
                  <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                    <div>
                      <h5 class="text-xs font-bold text-slate-900 dark:text-white">
                        Profile Photo
                      </h5>
                      <p class="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">
                        Upload an image from your device or link an external photo URL
                      </p>
                    </div>

                    <!-- Status Indicator Badge -->
                    <div v-if="isUploadedPhoto" class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-semibold bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border border-emerald-200/80 dark:border-emerald-800/60 self-center sm:self-auto">
                      <CheckCircle2 class="w-3 h-3 text-emerald-500 shrink-0" />
                      <span>Uploaded Photo</span>
                    </div>
                    <div v-else-if="editAvatarUrl && !previewImgError" class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-semibold bg-indigo-50 dark:bg-indigo-950/40 text-indigo-700 dark:text-indigo-300 border border-indigo-200/80 dark:border-indigo-800/60 self-center sm:self-auto">
                      <Globe class="w-3 h-3 text-indigo-500 shrink-0" />
                      <span>External Link</span>
                    </div>
                  </div>

                  <!-- Action Buttons Row -->
                  <div class="flex flex-wrap items-center justify-center sm:justify-start gap-2">
                    <button
                      type="button"
                      :disabled="isUploadingAvatar"
                      @click="triggerAvatarUpload"
                      class="h-7 px-3 rounded-lg text-xs font-semibold bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700/80 text-slate-700 dark:text-slate-200 border border-slate-200 dark:border-slate-700 shadow-2xs flex items-center gap-1.5 transition-all cursor-pointer disabled:opacity-50"
                    >
                      <RefreshCw v-if="isUploadingAvatar" class="w-3 h-3 animate-spin text-indigo-500" />
                      <Upload v-else class="w-3 h-3 text-indigo-500" />
                      <span>{{ isUploadingAvatar ? 'Uploading...' : 'Upload Photo' }}</span>
                    </button>

                    <button
                      type="button"
                      @click="toggleUrlInput"
                      class="h-7 px-3 rounded-lg text-xs font-semibold transition-all cursor-pointer flex items-center gap-1.5 border"
                      :class="showUrlInput
                        ? 'bg-indigo-50 dark:bg-indigo-950/50 text-indigo-600 dark:text-indigo-400 border-indigo-200 dark:border-indigo-800'
                        : 'bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700/80 text-slate-700 dark:text-slate-200 border-slate-200 dark:border-slate-700 shadow-2xs'"
                    >
                      <Link2 class="w-3 h-3 text-indigo-500" />
                      <span>{{ showUrlInput ? 'Hide URL' : 'Paste Image URL' }}</span>
                    </button>

                    <button
                      v-if="editAvatarUrl"
                      type="button"
                      @click="removeAvatarPhoto"
                      class="h-7 px-2.5 rounded-lg text-xs font-medium text-rose-500 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-950/30 transition-all cursor-pointer flex items-center gap-1"
                    >
                      <Trash2 class="w-3 h-3" />
                      <span>Remove</span>
                    </button>
                  </div>

                  <!-- Expandable URL Input Field -->
                  <div v-if="showUrlInput" class="pt-2 animate-in fade-in space-y-1.5">
                    <div class="relative">
                      <Globe class="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
                      <input
                        v-model="editAvatarUrl"
                        type="url"
                        placeholder="https://example.com/avatar.jpg"
                        class="w-full h-8 pl-8 pr-8 bg-white dark:bg-slate-900 text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 rounded-lg border border-slate-200 dark:border-slate-800 text-xs focus:outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/10 shadow-2xs font-mono transition-all"
                        @input="handleExternalAvatarInput"
                      />
                      <button
                        v-if="editAvatarUrl"
                        type="button"
                        @click="editAvatarUrl = ''"
                        class="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer p-0.5"
                        title="Clear URL"
                      >
                        <X class="w-3 h-3" />
                      </button>
                    </div>

                    <div v-if="previewImgError" class="text-[11px] text-rose-500 dark:text-rose-400 flex items-center gap-1 font-medium">
                      <AlertCircle class="w-3 h-3 shrink-0" />
                      <span>Unable to load image preview from this URL. Please verify the link.</span>
                    </div>
                    <p v-else class="text-[10px] text-slate-400">
                      Paste a direct link to any public image (GitHub, Gravatar, Unsplash, or CDN).
                    </p>
                  </div>

                  <!-- File Format Helper when URL input is closed -->
                  <p v-else class="text-[10px] text-slate-400">
                    Supports PNG, JPG, GIF or WebP up to 5MB. Square aspect ratio recommended.
                  </p>
                </div>
              </div>
            </div>

            <!-- Identity Fields Grid: Full Name & Email -->
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-1">
              <!-- Full Name / Username -->
              <div class="space-y-1.5">
                <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                  Full Name / Username <span class="text-rose-500 font-bold">*</span>
                </label>
                <div class="relative">
                  <User class="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
                  <input
                    v-model="editFullName"
                    type="text"
                    placeholder="Enter your full name or handle"
                    class="w-full h-9 pl-9 pr-3 bg-slate-50/70 dark:bg-slate-950/70 hover:bg-white dark:hover:bg-slate-900 focus:bg-white dark:focus:bg-slate-900 text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 rounded-xl border border-slate-200 dark:border-slate-800 text-xs focus:outline-none focus:border-indigo-500 focus:ring-3 focus:ring-indigo-500/10 shadow-2xs transition-all"
                  />
                </div>
              </div>

              <!-- Email Address (Read-Only) -->
              <div class="space-y-1.5">
                <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                  Email Address
                </label>
                <div class="flex items-center justify-between px-3 h-9 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200/80 dark:border-slate-800/80 text-xs text-slate-900 dark:text-white font-medium">
                  <div class="flex items-center gap-2 truncate">
                    <Mail class="w-3.5 h-3.5 text-slate-400 shrink-0" />
                    <span class="truncate">{{ selectedMember.email }}</span>
                  </div>
                  <button
                    type="button"
                    @click="copyDetailText(selectedMember.email, 'email')"
                    class="p-1 text-slate-400 hover:text-indigo-600 transition cursor-pointer shrink-0"
                    title="Copy Email"
                  >
                    <Check v-if="copiedDetailKey === 'email'" class="w-3.5 h-3.5 text-emerald-500" />
                    <Copy v-else class="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- Read-Only View for Other Members -->
          <div v-else class="space-y-4">
            <div class="flex items-center gap-4 pb-3 border-b border-slate-100 dark:border-slate-800/80">
              <div
                class="w-16 h-16 rounded-2xl text-white flex items-center justify-center font-black text-xl shadow-xs overflow-hidden ring-1 ring-slate-200/70 dark:ring-slate-700/70 bg-slate-100 dark:bg-slate-800 shrink-0"
                :class="[
                  (!selectedMember.avatarUrl || failedAvatarIds.has(selectedMember.userId)) && (selectedMember.roleAssignments?.some(r => r.roleName.toUpperCase().includes('OWNER'))
                    ? 'bg-gradient-to-br from-purple-500 to-indigo-600'
                    : 'bg-gradient-to-br from-indigo-500 to-sky-600'),
                ]"
              >
                <img
                  v-if="selectedMember.avatarUrl && !failedAvatarIds.has(selectedMember.userId)"
                  :src="selectedMember.avatarUrl"
                  :alt="selectedMember.fullName || selectedMember.email"
                  class="w-full h-full object-cover"
                  @error="markMemberAvatarFailed(selectedMember.userId)"
                />
                <span v-else>
                  {{ (selectedMember.fullName || selectedMember.email).charAt(0).toUpperCase() }}
                </span>
              </div>

              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-2">
                  <h3 class="text-sm font-bold text-slate-900 dark:text-white truncate">
                    {{ selectedMember.fullName || selectedMember.email.split('@')[0] }}
                  </h3>
                  <span class="px-2 py-0.5 rounded-full text-[10px] font-medium bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 border border-slate-200 dark:border-slate-700">
                    Collaborator
                  </span>
                </div>
                <p class="text-xs text-slate-500 dark:text-slate-400 truncate mt-0.5">
                  {{ selectedMember.email }}
                </p>
                <p class="text-[10px] text-slate-400 mt-1 flex items-center gap-1">
                  <Lock class="w-3 h-3 text-slate-400" />
                  Profile details managed by this collaborator
                </p>
              </div>
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div class="space-y-1.5">
                <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                  Full Name / Username
                </label>
                <div class="flex items-center justify-between px-3 h-9 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200/80 dark:border-slate-800/80 text-xs text-slate-900 dark:text-white font-medium">
                  <div class="flex items-center gap-2 truncate">
                    <User class="w-3.5 h-3.5 text-slate-400 shrink-0" />
                    <span class="truncate">{{ selectedMember.fullName || selectedMember.email.split('@')[0] }}</span>
                  </div>
                  <Lock class="w-3 h-3 text-slate-400 shrink-0" />
                </div>
              </div>

              <div class="space-y-1.5">
                <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                  Email Address
                </label>
                <div class="flex items-center justify-between px-3 h-9 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200/80 dark:border-slate-800/80 text-xs text-slate-900 dark:text-white font-medium">
                  <div class="flex items-center gap-2 truncate">
                    <Mail class="w-3.5 h-3.5 text-slate-400 shrink-0" />
                    <span class="truncate">{{ selectedMember.email }}</span>
                  </div>
                  <button
                    type="button"
                    @click="copyDetailText(selectedMember.email, 'email')"
                    class="p-1 text-slate-400 hover:text-indigo-600 transition cursor-pointer shrink-0"
                    title="Copy Email"
                  >
                    <Check v-if="copiedDetailKey === 'email'" class="w-3.5 h-3.5 text-emerald-500" />
                    <Copy v-else class="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 2. HOW USER KNOWS THEY BELONG TO ORGANIZATION(S) (Requirement 4 & 5) -->
        <div class="p-4 rounded-2xl bg-slate-50/80 dark:bg-slate-950/40 border border-slate-200/90 dark:border-slate-800 space-y-3">
          <div class="flex items-center justify-between">
            <h4 class="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300 flex items-center gap-1.5">
              <Building2 class="w-3.5 h-3.5 text-indigo-500" />
              Organization Affiliation
            </h4>
            <span class="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/20">
              {{ (selectedMember.organizations?.length || 1) === 1 ? '1 Organization' : `${selectedMember.organizations?.length} Organizations` }}
            </span>
          </div>

          <!-- Primary/Current Workspace Org Card -->
          <div class="p-3.5 rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800/80 shadow-2xs flex items-center justify-between gap-3">
            <div class="flex items-center gap-3 min-w-0">
              <div class="w-9 h-9 rounded-xl bg-gradient-to-br from-indigo-500 to-purple-600 text-white flex items-center justify-center font-bold text-xs shrink-0 shadow-xs">
                <Building2 class="w-4 h-4" />
              </div>
              <div class="min-w-0">
                <div class="text-xs font-bold text-slate-900 dark:text-white truncate flex items-center gap-2">
                  <span>{{ selectedMember.organizationName || authStore.currentOrg?.name || 'Workspace Organization' }}</span>
                  <span class="text-[9px] font-bold px-1.5 py-0.2 rounded bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30">
                    Active Workspace
                  </span>
                </div>
                <p class="text-[11px] text-slate-400 font-mono mt-0.5">
                  ID: {{ selectedMember.organizationId || authStore.currentOrgId }}
                </p>
              </div>
            </div>

            <span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-500/20 shrink-0 flex items-center gap-1">
              <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
              Active Member
            </span>
          </div>

          <!-- Multiple Organizations List (if member belongs to more than 1) -->
          <div v-if="selectedMember.organizations && selectedMember.organizations.length > 1" class="space-y-1.5 pt-1">
            <span class="text-[10px] font-semibold uppercase text-slate-400">All Associated Organizations:</span>
            <div class="flex flex-wrap gap-2">
              <div
                v-for="org in selectedMember.organizations"
                :key="org.organizationId"
                class="px-2.5 py-1 rounded-xl text-xs font-medium border flex items-center gap-1.5 bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-800 shadow-2xs"
                :class="org.organizationId === (selectedMember.organizationId || authStore.currentOrgId) ? 'ring-1 ring-indigo-500/40 text-indigo-600 dark:text-indigo-400' : 'text-slate-600 dark:text-slate-300'"
              >
                <Building2 class="w-3 h-3 text-slate-400" />
                <span>{{ org.organizationName }}</span>
                <span v-if="org.organizationId === (selectedMember.organizationId || authStore.currentOrgId)" class="text-[9px] font-bold text-indigo-500">
                  (Current)
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- 3. ASSIGNED ROLES (Requirement 5: Just role name with icon with different color) -->
        <div class="p-4 rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-xs space-y-3">
          <div class="flex items-center justify-between">
            <h4 class="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300 flex items-center gap-1.5">
              <Shield class="w-3.5 h-3.5 text-indigo-500" />
              Assigned Roles
            </h4>
            <span class="text-[11px] text-slate-400">
              {{ selectedMember.roleAssignments?.length || 0 }} {{ (selectedMember.roleAssignments?.length || 0) === 1 ? 'Role' : 'Roles' }}
            </span>
          </div>

          <div class="flex flex-wrap gap-2">
            <span
              v-for="r in selectedMember.roleAssignments"
              :key="r.assignmentId"
              class="px-3 py-1.5 rounded-xl text-xs font-bold border inline-flex items-center gap-2 shadow-2xs"
              :class="getRoleBadge(r.roleName).badgeClass"
            >
              <component :is="getRoleBadge(r.roleName).icon" class="w-4 h-4" :class="getRoleBadge(r.roleName).iconClass" />
              <span>{{ r.roleName }}</span>
            </span>
            <span v-if="!selectedMember.roleAssignments || selectedMember.roleAssignments.length === 0" class="text-slate-400 text-xs italic">
              Standard Member (No elevated roles)
            </span>
          </div>
        </div>

        <!-- 4. MEMBER ACCESS SCOPE (Requirement 6: Scope of Member) -->
        <div
          class="p-4 rounded-2xl border space-y-2.5 shadow-2xs"
          :class="[
            isMemberOrgWide(selectedMember)
              ? 'bg-emerald-50/50 dark:bg-emerald-950/20 border-emerald-200/80 dark:border-emerald-800/60'
              : 'bg-indigo-50/50 dark:bg-indigo-950/20 border-indigo-200/80 dark:border-indigo-800/60',
          ]"
        >
          <div class="flex items-center justify-between">
            <h4
              class="text-xs font-bold uppercase tracking-wider flex items-center gap-1.5"
              :class="isMemberOrgWide(selectedMember) ? 'text-emerald-700 dark:text-emerald-400' : 'text-indigo-700 dark:text-indigo-400'"
            >
              <Globe v-if="isMemberOrgWide(selectedMember)" class="w-4 h-4 text-emerald-500" />
              <Smartphone v-else class="w-4 h-4 text-indigo-500" />
              Member Access Scope
            </h4>

            <span
              class="px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider"
              :class="[
                isMemberOrgWide(selectedMember)
                  ? 'bg-emerald-100 dark:bg-emerald-900/50 text-emerald-800 dark:text-emerald-300'
                  : 'bg-indigo-100 dark:bg-indigo-900/50 text-indigo-800 dark:text-indigo-300',
              ]"
            >
              {{ isMemberOrgWide(selectedMember) ? 'Organization-wide' : 'App Restricted' }}
            </span>
          </div>

          <p
            class="text-xs leading-relaxed"
            :class="isMemberOrgWide(selectedMember) ? 'text-emerald-800 dark:text-emerald-300' : 'text-indigo-800 dark:text-indigo-300'"
          >
            <template v-if="isMemberOrgWide(selectedMember)">
              🌐 <strong>Global Scope:</strong> This member has full organization-wide access. They can view, manage, and dispatch across all applications and resources configured in this workspace.
            </template>
            <template v-else>
              📱 <strong>Restricted Scope:</strong> This member's access is bounded to the designated application(s) below. They cannot access or modify resources outside their assigned application boundary.
            </template>
          </p>

          <!-- List of assigned apps if App Scoped -->
          <div v-if="!isMemberOrgWide(selectedMember)" class="pt-1 flex flex-wrap gap-1.5 items-center">
            <span class="text-[10px] font-semibold text-slate-500 dark:text-slate-400">Assigned Apps:</span>
            <span
              v-for="appScope in getMemberAppScopes(selectedMember)"
              :key="appScope.id"
              class="px-2.5 py-1 rounded-lg text-xs font-semibold bg-white dark:bg-slate-900 border border-indigo-200 dark:border-indigo-800 text-indigo-700 dark:text-indigo-300 inline-flex items-center gap-1 shadow-2xs"
            >
              <Smartphone class="w-3.5 h-3.5 text-indigo-500" />
              {{ appScope.name }}
            </span>
          </div>
        </div>

        <!-- 5. TECHNICAL IDENTIFIERS GRID -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <!-- User ID -->
          <div class="p-3 rounded-xl bg-slate-50 dark:bg-slate-950/50 border border-slate-200/80 dark:border-slate-800/80 space-y-1">
            <span class="text-[10px] uppercase font-semibold text-slate-400 flex items-center gap-1">
              <KeyRound class="w-3 h-3 text-slate-400" />
              User ID
            </span>
            <div class="flex items-center justify-between gap-1">
              <span class="font-mono text-xs text-slate-800 dark:text-slate-200 truncate" :title="selectedMember.userId">
                {{ selectedMember.userId }}
              </span>
              <button
                type="button"
                @click="copyDetailText(selectedMember.userId, 'userId')"
                class="p-1 text-slate-400 hover:text-indigo-600 transition cursor-pointer"
                title="Copy User ID"
              >
                <Check v-if="copiedDetailKey === 'userId'" class="w-3.5 h-3.5 text-emerald-500" />
                <Copy v-else class="w-3.5 h-3.5" />
              </button>
            </div>
          </div>

          <!-- Membership ID -->
          <div class="p-3 rounded-xl bg-slate-50 dark:bg-slate-950/50 border border-slate-200/80 dark:border-slate-800/80 space-y-1">
            <span class="text-[10px] uppercase font-semibold text-slate-400 flex items-center gap-1">
              <Building2 class="w-3 h-3 text-slate-400" />
              Membership ID
            </span>
            <div class="flex items-center justify-between gap-1">
              <span class="font-mono text-xs text-slate-800 dark:text-slate-200 truncate" :title="selectedMember.membershipId">
                {{ selectedMember.membershipId }}
              </span>
              <button
                type="button"
                @click="copyDetailText(selectedMember.membershipId, 'membershipId')"
                class="p-1 text-slate-400 hover:text-indigo-600 transition cursor-pointer"
                title="Copy Membership ID"
              >
                <Check v-if="copiedDetailKey === 'membershipId'" class="w-3.5 h-3.5 text-emerald-500" />
                <Copy v-else class="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- FOOTER (Requirement 7: Remove Member button removed) -->
      <template #footer>
        <div class="flex items-center justify-end w-full gap-2.5">
          <!-- When viewing self: Cancel & Save Changes -->
          <template v-if="isCurrentMemberSelf">
            <BaseButton
              variant="outline"
              size="sm"
              @click="closeDetailModal"
            >
              Cancel
            </BaseButton>

            <BaseButton
              variant="primary"
              size="sm"
              :icon="Save"
              :loading="isUpdatingProfile"
              :disabled="!hasProfileChanges"
              @click="handleUpdateProfile"
            >
              Save Changes
            </BaseButton>
          </template>

          <!-- When viewing another member: Close button only -->
          <template v-else>
            <BaseButton
              variant="primary"
              size="sm"
              @click="closeDetailModal"
            >
              Close
            </BaseButton>
          </template>
        </div>
      </template>
    </ModernModal>
  </div>
</template>
