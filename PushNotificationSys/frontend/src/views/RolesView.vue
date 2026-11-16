<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { rolesApi } from '@/api/roles'
import type { Role, Permission } from '@/api/types'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import ModernModal from '@/components/common/ModernModal.vue'
import {
  Shield,
  ShieldCheck,
  ShieldAlert,
  Crown,
  Code2,
  Megaphone,
  Eye,
  Key,
  Lock,
  Building2,
  Users,
  LayoutGrid,
  Bell,
  Filter,
  Smartphone,
  FileText,
  BarChart3,
  Search,
  X,
  Check,
  CheckCheck,
  Copy,
  Plus,
  RefreshCw,
  ArrowRight,
  Sparkles,
  Sliders,
} from 'lucide-vue-next'

const authStore = useAuthStore()
const toastStore = useToastStore()

const roles = ref<Role[]>([])
const permissions = ref<Permission[]>([])
const isLoading = ref<boolean>(false)

// Details Modal State
const showDetailsModal = ref(false)
const selectedRoleForDetails = ref<Role | null>(null)
const detailsSearchQuery = ref('')
const detailsActiveTab = ref('all')
const copiedCode = ref<string | null>(null)
const isCopiedAll = ref(false)

// Create Custom Role Modal State
const showCreateModal = ref(false)
const newRoleName = ref('')
const newRoleDescription = ref('')
const selectedPermissions = ref<Set<string>>(new Set())
const createRoleSearch = ref('')
const isCreating = ref(false)
const createError = ref('')

// Prefix Metadata Configuration
interface PrefixMeta {
  prefix: string
  label: string
  icon: any
  badge: string
  accent: string
  bg: string
  border: string
}

const PREFIX_METAS: Record<string, Omit<PrefixMeta, 'prefix'>> = {
  notification: {
    label: 'Push Notifications',
    icon: Bell,
    badge: 'bg-indigo-50 dark:bg-indigo-950/50 text-indigo-700 dark:text-indigo-300 border-indigo-200/80 dark:border-indigo-800/50',
    accent: 'text-indigo-600 dark:text-indigo-400',
    bg: 'bg-indigo-50/50 dark:bg-indigo-950/30',
    border: 'border-indigo-200/70 dark:border-indigo-800/50',
  },
  apikey: {
    label: 'API Keys & Secrets',
    icon: Key,
    badge: 'bg-amber-50 dark:bg-amber-950/50 text-amber-700 dark:text-amber-300 border-amber-200/80 dark:border-amber-800/50',
    accent: 'text-amber-600 dark:text-amber-400',
    bg: 'bg-amber-50/50 dark:bg-amber-950/30',
    border: 'border-amber-200/70 dark:border-amber-800/50',
  },
  app: {
    label: 'Applications',
    icon: LayoutGrid,
    badge: 'bg-sky-50 dark:bg-sky-950/50 text-sky-700 dark:text-sky-300 border-sky-200/80 dark:border-sky-800/50',
    accent: 'text-sky-600 dark:text-sky-400',
    bg: 'bg-sky-50/50 dark:bg-sky-950/30',
    border: 'border-sky-200/70 dark:border-sky-800/50',
  },
  member: {
    label: 'Team Members',
    icon: Users,
    badge: 'bg-violet-50 dark:bg-violet-950/50 text-violet-700 dark:text-violet-300 border-violet-200/80 dark:border-violet-800/50',
    accent: 'text-violet-600 dark:text-violet-400',
    bg: 'bg-violet-50/50 dark:bg-violet-950/30',
    border: 'border-violet-200/70 dark:border-violet-800/50',
  },
  credential: {
    label: 'Provider Credentials',
    icon: Lock,
    badge: 'bg-rose-50 dark:bg-rose-950/50 text-rose-700 dark:text-rose-300 border-rose-200/80 dark:border-rose-900/50',
    accent: 'text-rose-600 dark:text-rose-400',
    bg: 'bg-rose-50/50 dark:bg-rose-950/30',
    border: 'border-rose-200/70 dark:border-rose-800/50',
  },
  device: {
    label: 'Registered Devices',
    icon: Smartphone,
    badge: 'bg-emerald-50 dark:bg-emerald-950/50 text-emerald-700 dark:text-emerald-300 border-emerald-200/80 dark:border-emerald-800/50',
    accent: 'text-emerald-600 dark:text-emerald-400',
    bg: 'bg-emerald-50/50 dark:bg-emerald-950/30',
    border: 'border-emerald-200/70 dark:border-emerald-800/50',
  },
  segment: {
    label: 'Audience Segments',
    icon: Filter,
    badge: 'bg-teal-50 dark:bg-teal-950/50 text-teal-700 dark:text-teal-300 border-teal-200/80 dark:border-teal-800/50',
    accent: 'text-teal-600 dark:text-teal-400',
    bg: 'bg-teal-50/50 dark:bg-teal-950/30',
    border: 'border-teal-200/70 dark:border-teal-800/50',
  },
  analytics: {
    label: 'Analytics & Insights',
    icon: BarChart3,
    badge: 'bg-blue-50 dark:bg-blue-950/50 text-blue-700 dark:text-blue-300 border-blue-200/80 dark:border-blue-800/50',
    accent: 'text-blue-600 dark:text-blue-400',
    bg: 'bg-blue-50/50 dark:bg-blue-950/30',
    border: 'border-blue-200/70 dark:border-blue-800/50',
  },
  log: {
    label: 'Delivery & Event Logs',
    icon: FileText,
    badge: 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border-slate-200 dark:border-slate-700',
    accent: 'text-slate-600 dark:text-slate-400',
    bg: 'bg-slate-50/60 dark:bg-slate-900/40',
    border: 'border-slate-200/80 dark:border-slate-800',
  },
  audit: {
    label: 'Audit Trail',
    icon: ShieldAlert,
    badge: 'bg-orange-50 dark:bg-orange-950/50 text-orange-700 dark:text-orange-300 border-orange-200/80 dark:border-orange-800/50',
    accent: 'text-orange-600 dark:text-orange-400',
    bg: 'bg-orange-50/50 dark:bg-orange-950/30',
    border: 'border-orange-200/70 dark:border-orange-800/50',
  },
  org: {
    label: 'Organization Settings',
    icon: Building2,
    badge: 'bg-purple-50 dark:bg-purple-950/50 text-purple-700 dark:text-purple-300 border-purple-200/80 dark:border-purple-800/50',
    accent: 'text-purple-600 dark:text-purple-400',
    bg: 'bg-purple-50/50 dark:bg-purple-950/30',
    border: 'border-purple-200/70 dark:border-purple-800/50',
  },
  role: {
    label: 'Roles & RBAC',
    icon: ShieldCheck,
    badge: 'bg-cyan-50 dark:bg-cyan-950/50 text-cyan-700 dark:text-cyan-300 border-cyan-200/80 dark:border-cyan-800/50',
    accent: 'text-cyan-600 dark:text-cyan-400',
    bg: 'bg-cyan-50/50 dark:bg-cyan-950/30',
    border: 'border-cyan-200/70 dark:border-cyan-800/50',
  },
}

function getPrefixMeta(prefix: string): PrefixMeta {
  const norm = prefix.toLowerCase()
  if (PREFIX_METAS[norm]) {
    return { prefix: norm, ...PREFIX_METAS[norm] }
  }
  return {
    prefix: norm,
    label: norm.charAt(0).toUpperCase() + norm.slice(1),
    icon: Shield,
    badge: 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border-slate-200 dark:border-slate-700',
    accent: 'text-slate-600 dark:text-slate-400',
    bg: 'bg-slate-50 dark:bg-slate-900',
    border: 'border-slate-200 dark:border-slate-800',
  }
}

function getRoleVisualMeta(role: Role) {
  const name = role.name.toLowerCase()
  if (name.includes('owner')) {
    return {
      icon: Crown,
      iconColor: 'text-purple-600 dark:text-purple-400',
      iconBg: 'bg-purple-50 dark:bg-purple-950/40 border border-purple-200/90 dark:border-purple-800/50',
      accentGradient: 'from-purple-500 to-indigo-600',
      tagBadge: 'bg-purple-50 dark:bg-purple-950/40 text-purple-700 dark:text-purple-300 border-purple-200 dark:border-purple-800/50',
    }
  }
  if (name.includes('admin')) {
    return {
      icon: ShieldCheck,
      iconColor: 'text-indigo-600 dark:text-indigo-400',
      iconBg: 'bg-indigo-50 dark:bg-indigo-950/40 border border-indigo-200/90 dark:border-indigo-800/50',
      accentGradient: 'from-indigo-500 to-sky-600',
      tagBadge: 'bg-indigo-50 dark:bg-indigo-950/40 text-indigo-700 dark:text-indigo-300 border-indigo-200 dark:border-indigo-800/50',
    }
  }
  if (name.includes('developer')) {
    return {
      icon: Code2,
      iconColor: 'text-sky-600 dark:text-sky-400',
      iconBg: 'bg-sky-50 dark:bg-sky-950/40 border border-sky-200/90 dark:border-sky-800/50',
      accentGradient: 'from-sky-500 to-teal-600',
      tagBadge: 'bg-sky-50 dark:bg-sky-950/40 text-sky-700 dark:text-sky-300 border-sky-200 dark:border-sky-800/50',
    }
  }
  if (name.includes('market')) {
    return {
      icon: Megaphone,
      iconColor: 'text-emerald-600 dark:text-emerald-400',
      iconBg: 'bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200/90 dark:border-emerald-800/50',
      accentGradient: 'from-emerald-500 to-teal-600',
      tagBadge: 'bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border-emerald-200 dark:border-emerald-800/50',
    }
  }
  if (name.includes('view')) {
    return {
      icon: Eye,
      iconColor: 'text-slate-600 dark:text-slate-400',
      iconBg: 'bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700',
      accentGradient: 'from-slate-400 to-slate-600',
      tagBadge: 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border-slate-200 dark:border-slate-700',
    }
  }
  return {
    icon: Sparkles,
    iconColor: 'text-rose-600 dark:text-rose-400',
    iconBg: 'bg-rose-50 dark:bg-rose-950/40 border border-rose-200/90 dark:border-rose-800/50',
    accentGradient: 'from-rose-500 to-amber-600',
    tagBadge: 'bg-rose-50 dark:bg-rose-950/40 text-rose-700 dark:text-rose-300 border-rose-200 dark:border-rose-800/50',
  }
}

function getActionBadgeStyle(action: string) {
  const act = action.toLowerCase()
  if (act === 'create' || act === 'write') {
    return 'bg-emerald-50 dark:bg-emerald-950/50 text-emerald-700 dark:text-emerald-300 border-emerald-200/70 dark:border-emerald-800/50'
  }
  if (act === 'read' || act === 'view') {
    return 'bg-sky-50 dark:bg-sky-950/50 text-sky-700 dark:text-sky-300 border-sky-200/70 dark:border-sky-800/50'
  }
  if (act === 'update' || act === 'rotate' || act === 'edit') {
    return 'bg-amber-50 dark:bg-amber-950/50 text-amber-700 dark:text-amber-300 border-amber-200/70 dark:border-amber-800/50'
  }
  if (act === 'delete' || act === 'revoke' || act === 'remove' || act === 'cancel') {
    return 'bg-rose-50 dark:bg-rose-950/50 text-rose-700 dark:text-rose-300 border-rose-200/70 dark:border-rose-800/50'
  }
  if (act === 'export' || act === 'download') {
    return 'bg-indigo-50 dark:bg-indigo-950/50 text-indigo-700 dark:text-indigo-300 border-indigo-200/70 dark:border-indigo-800/50'
  }
  if (act === 'invite') {
    return 'bg-violet-50 dark:bg-violet-950/50 text-violet-700 dark:text-violet-300 border-violet-200/70 dark:border-violet-800/50'
  }
  return 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border-slate-200 dark:border-slate-700'
}

// Permission map lookup
const permissionMap = computed(() => {
  const map = new Map<string, Permission>()
  for (const p of permissions.value) {
    map.set(p.code, p)
  }
  return map
})

interface EnrichedPermission {
  code: string
  resource: string
  action: string
  description: string
}

interface RolePrefixGroup {
  prefix: string
  meta: PrefixMeta
  permissions: EnrichedPermission[]
}

function getRoleGroupedPermissions(role: Role): RolePrefixGroup[] {
  const groups: Record<string, EnrichedPermission[]> = {}
  for (const code of role.permissions) {
    const p = permissionMap.value.get(code)
    const resource = p?.resource || (code.includes(':') ? code.split(':')[0] : 'general')
    const action = p?.action || (code.includes(':') ? code.split(':')[1] : code)
    const description = p?.description || `${action.toUpperCase()} operations on ${resource}`

    if (!groups[resource]) {
      groups[resource] = []
    }
    groups[resource].push({
      code,
      resource,
      action,
      description,
    })
  }

  // Sort groups deterministically with notification, apikey, app, member first
  const sortOrder = ['notification', 'apikey', 'app', 'member', 'credential', 'device', 'segment', 'analytics', 'log', 'audit', 'org', 'role']
  return Object.entries(groups)
    .sort(([a], [b]) => {
      const idxA = sortOrder.indexOf(a)
      const idxB = sortOrder.indexOf(b)
      if (idxA !== -1 && idxB !== -1) return idxA - idxB
      if (idxA !== -1) return -1
      if (idxB !== -1) return 1
      return a.localeCompare(b)
    })
    .map(([prefix, perms]) => ({
      prefix,
      meta: getPrefixMeta(prefix),
      permissions: perms,
    }))
}

function getRoleCoverage(role: Role) {
  const total = permissions.value.length || 35
  const count = role.permissions?.length || 0
  return Math.min(100, Math.round((count / total) * 100))
}

// Modal open & filter logic
function openRoleDetails(role: Role) {
  selectedRoleForDetails.value = role
  detailsSearchQuery.value = ''
  detailsActiveTab.value = 'all'
  copiedCode.value = null
  isCopiedAll.value = false
  showDetailsModal.value = true
}

const allGroupsForSelectedRole = computed(() => {
  if (!selectedRoleForDetails.value) return []
  return getRoleGroupedPermissions(selectedRoleForDetails.value)
})

const activeRoleGroupedPermissions = computed(() => {
  if (!selectedRoleForDetails.value) return []
  const allGroups = allGroupsForSelectedRole.value
  const q = detailsSearchQuery.value.trim().toLowerCase()
  const tab = detailsActiveTab.value

  return allGroups
    .filter((g) => tab === 'all' || g.prefix === tab)
    .map((g) => {
      if (!q) return g
      const filtered = g.permissions.filter(
        (p) =>
          p.code.toLowerCase().includes(q) ||
          p.action.toLowerCase().includes(q) ||
          p.description.toLowerCase().includes(q) ||
          g.meta.label.toLowerCase().includes(q) ||
          g.prefix.toLowerCase().includes(q),
      )
      return { ...g, permissions: filtered }
    })
    .filter((g) => g.permissions.length > 0)
})

const activeRoleTotalMatched = computed(() => {
  return activeRoleGroupedPermissions.value.reduce((acc, g) => acc + g.permissions.length, 0)
})

async function copyPermissionCode(code: string) {
  try {
    await navigator.clipboard.writeText(code)
    copiedCode.value = code
    setTimeout(() => {
      if (copiedCode.value === code) copiedCode.value = null
    }, 2000)
    toastStore.success('Copied', `Permission code "${code}" copied to clipboard`)
  } catch (err) {
    console.error('Failed to copy', err)
  }
}

async function copyAllRolePermissions() {
  if (!selectedRoleForDetails.value) return
  try {
    const text = selectedRoleForDetails.value.permissions.join(', ')
    await navigator.clipboard.writeText(text)
    isCopiedAll.value = true
    setTimeout(() => {
      isCopiedAll.value = false
    }, 2500)
    toastStore.success(
      'Copied All Codes',
      `${selectedRoleForDetails.value.permissions.length} permission codes copied to clipboard`,
    )
  } catch (err) {
    console.error('Failed to copy', err)
  }
}

// Create custom role helpers
const createModalGroupedPermissions = computed(() => {
  const q = createRoleSearch.value.trim().toLowerCase()
  const map: Record<string, Permission[]> = {}

  for (const p of permissions.value) {
    if (q) {
      const match =
        p.code.toLowerCase().includes(q) ||
        p.description?.toLowerCase().includes(q) ||
        p.resource.toLowerCase().includes(q) ||
        p.action.toLowerCase().includes(q)
      if (!match) continue
    }
    if (!map[p.resource]) map[p.resource] = []
    map[p.resource].push(p)
  }

  const sortOrder = ['notification', 'apikey', 'app', 'member', 'credential', 'device', 'segment', 'analytics', 'log', 'audit', 'org', 'role']
  return Object.entries(map)
    .sort(([a], [b]) => {
      const idxA = sortOrder.indexOf(a)
      const idxB = sortOrder.indexOf(b)
      if (idxA !== -1 && idxB !== -1) return idxA - idxB
      if (idxA !== -1) return -1
      if (idxB !== -1) return 1
      return a.localeCompare(b)
    })
    .map(([resource, perms]) => ({
      resource,
      meta: getPrefixMeta(resource),
      permissions: perms,
    }))
})

function togglePermission(code: string) {
  if (selectedPermissions.value.has(code)) {
    selectedPermissions.value.delete(code)
  } else {
    selectedPermissions.value.add(code)
  }
}

function toggleResourceGroup(resource: string) {
  const perms = permissions.value.filter((p) => p.resource === resource)
  const allSelected = perms.every((p) => selectedPermissions.value.has(p.code))

  for (const p of perms) {
    if (allSelected) {
      selectedPermissions.value.delete(p.code)
    } else {
      selectedPermissions.value.add(p.code)
    }
  }
}

function isResourceGroupAllSelected(resource: string) {
  const perms = permissions.value.filter((p) => p.resource === resource)
  return perms.length > 0 && perms.every((p) => selectedPermissions.value.has(p.code))
}

function isResourceGroupPartiallySelected(resource: string) {
  const perms = permissions.value.filter((p) => p.resource === resource)
  const count = perms.filter((p) => selectedPermissions.value.has(p.code)).length
  return count > 0 && count < perms.length
}

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
            <ShieldCheck class="w-5 h-5" />
          </div>
          <div>
            <h1 class="text-lg font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
              <span>Roles & Permission Matrix</span>
              <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30">
                {{ roles.length }} Roles
              </span>
            </h1>
            <p class="text-xs text-slate-500 dark:text-slate-400">
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
        class="group bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200/90 dark:border-slate-800 shadow-xs hover:shadow-md hover:border-slate-300 dark:hover:border-slate-700 transition-all duration-200 flex flex-col justify-between"
      >
        <div class="space-y-3.5">
          <!-- Role Title & Badge -->
          <div class="flex items-start justify-between gap-3">
            <div class="flex items-center gap-2.5">
              <div
                class="w-8 h-8 rounded-lg flex items-center justify-center shrink-0 shadow-2xs"
                :class="getRoleVisualMeta(role).iconBg"
              >
                <component :is="getRoleVisualMeta(role).icon" class="w-4 h-4" :class="getRoleVisualMeta(role).iconColor" />
              </div>
              <div>
                <h3 class="text-sm font-bold text-slate-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">
                  {{ role.name }}
                </h3>
                <span class="text-[10px] text-slate-400 dark:text-slate-500 font-mono">
                  {{ role.permissions.length }} permissions
                </span>
              </div>
            </div>

            <span
              v-if="role.system"
              class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 border border-slate-200/80 dark:border-slate-700/60 shrink-0"
            >
              <Lock class="w-2.5 h-2.5" />
              System Role
            </span>
            <span
              v-else
              class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-rose-50 dark:bg-rose-950/40 text-rose-600 dark:text-rose-400 border border-rose-200/80 dark:border-rose-900/50 shrink-0"
            >
              <Sparkles class="w-2.5 h-2.5" />
              Custom Role
            </span>
          </div>

          <!-- Description -->
          <p class="text-xs text-slate-500 dark:text-slate-400 line-clamp-2 min-h-[32px] leading-relaxed">
            {{ role.description || 'Standard role configuration for team access control.' }}
          </p>

          <!-- Access Coverage Meter -->
          <div class="space-y-1.5 p-2.5 rounded-xl bg-slate-50/70 dark:bg-slate-950/40 border border-slate-200/60 dark:border-slate-800/60">
            <div class="flex items-center justify-between text-[11px]">
              <span class="font-medium text-slate-500 dark:text-slate-400">Access Scope</span>
              <span class="font-semibold text-slate-800 dark:text-slate-200">
                {{ role.permissions.length }} / {{ permissions.length || 35 }} ({{ getRoleCoverage(role) }}%)
              </span>
            </div>
            <div class="w-full h-1.5 rounded-full bg-slate-200/70 dark:bg-slate-800 overflow-hidden">
              <div
                class="h-full rounded-full bg-gradient-to-r transition-all duration-300"
                :class="getRoleVisualMeta(role).accentGradient"
                :style="{ width: `${getRoleCoverage(role)}%` }"
              />
            </div>
          </div>

          <!-- Module Prefix Summary Badges -->
          <div class="space-y-1.5">
            <div class="flex items-center justify-between">
              <span class="text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
                Resource Modules ({{ getRoleGroupedPermissions(role).length }})
              </span>
            </div>

            <div class="flex flex-wrap gap-1.5">
              <span
                v-for="group in getRoleGroupedPermissions(role).slice(0, 4)"
                :key="group.prefix"
                class="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-medium border"
                :class="group.meta.badge"
              >
                <component :is="group.meta.icon" class="w-2.5 h-2.5 shrink-0" />
                <span>{{ group.meta.label }}</span>
                <span class="font-bold opacity-80">({{ group.permissions.length }})</span>
              </span>

              <span
                v-if="getRoleGroupedPermissions(role).length > 4"
                class="inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-semibold bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 border border-slate-200 dark:border-slate-700"
              >
                +{{ getRoleGroupedPermissions(role).length - 4 }} more
              </span>
            </div>
          </div>
        </div>

        <!-- Action Button -->
        <button
          type="button"
          @click="openRoleDetails(role)"
          class="mt-4 w-full py-2 px-3 rounded-xl border border-slate-200 dark:border-slate-800 hover:border-indigo-300 dark:hover:border-indigo-700 bg-slate-50/70 hover:bg-indigo-50/60 dark:bg-slate-950/40 dark:hover:bg-indigo-950/30 text-xs font-semibold text-slate-700 dark:text-slate-200 hover:text-indigo-600 dark:hover:text-indigo-400 flex items-center justify-between transition-all duration-150 group/btn cursor-pointer"
        >
          <span class="flex items-center gap-1.5">
            <Eye class="w-3.5 h-3.5" />
            <span>View Permissions Details</span>
          </span>
          <ArrowRight class="w-3.5 h-3.5 text-slate-400 group-hover/btn:text-indigo-500 group-hover/btn:translate-x-0.5 transition-all" />
        </button>
      </div>
    </div>

    <!-- ========================================================================= -->
    <!-- DETAILS MODAL (Permissions Grouped by Prefix)                             -->
    <!-- ========================================================================= -->
    <ModernModal
      v-model="showDetailsModal"
      :title="`${selectedRoleForDetails?.name || 'Role'} Permissions Matrix`"
      :subtitle="selectedRoleForDetails?.description || 'Assigned permissions breakdown by resource prefix'"
      :icon="selectedRoleForDetails ? getRoleVisualMeta(selectedRoleForDetails).icon : ShieldCheck"
      :initial-width="820"
      size="xl"
      @close="showDetailsModal = false"
    >
      <div v-if="selectedRoleForDetails" class="p-6 space-y-5 text-left">
        <!-- Top Summary Bar -->
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-3.5 rounded-2xl bg-slate-50 dark:bg-slate-950/40 border border-slate-200/90 dark:border-slate-800">
          <div class="flex items-center gap-3">
            <div
              class="w-10 h-10 rounded-xl flex items-center justify-center shrink-0 shadow-xs"
              :class="getRoleVisualMeta(selectedRoleForDetails).iconBg"
            >
              <component :is="getRoleVisualMeta(selectedRoleForDetails).icon" class="w-5 h-5" :class="getRoleVisualMeta(selectedRoleForDetails).iconColor" />
            </div>
            <div>
              <div class="flex items-center gap-2">
                <span class="text-sm font-bold text-slate-900 dark:text-white">
                  {{ selectedRoleForDetails.name }}
                </span>
                <span
                  v-if="selectedRoleForDetails.system"
                  class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-slate-200/70 dark:bg-slate-800 text-slate-700 dark:text-slate-300"
                >
                  System Preset
                </span>
                <span
                  v-else
                  class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-rose-100 dark:bg-rose-900/50 text-rose-700 dark:text-rose-300"
                >
                  Custom Role
                </span>
              </div>
              <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                {{ selectedRoleForDetails.permissions.length }} permissions granted across {{ allGroupsForSelectedRole.length }} resource modules
              </p>
            </div>
          </div>

          <div class="flex items-center gap-2">
            <BaseButton
              variant="outline"
              size="sm"
              :icon="isCopiedAll ? CheckCheck : Copy"
              @click="copyAllRolePermissions"
            >
              {{ isCopiedAll ? 'Copied Codes!' : 'Copy All Codes' }}
            </BaseButton>
          </div>
        </div>

        <!-- Filter & Search Toolbar -->
        <div class="space-y-3">
          <div class="flex flex-col sm:flex-row sm:items-center gap-2.5">
            <!-- Search Input -->
            <div class="relative flex-1">
              <Search class="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
              <input
                v-model="detailsSearchQuery"
                type="text"
                placeholder="Filter permissions by prefix, code, action, or description..."
                class="w-full h-9 pl-9 pr-8 bg-slate-50/70 dark:bg-slate-950/70 hover:bg-white dark:hover:bg-slate-900 focus:bg-white dark:focus:bg-slate-900 text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 rounded-xl border border-slate-200 dark:border-slate-800 text-xs focus:outline-none focus:border-indigo-500 focus:ring-3 focus:ring-indigo-500/10 shadow-2xs transition-all"
              />
              <button
                v-if="detailsSearchQuery"
                type="button"
                @click="detailsSearchQuery = ''"
                class="absolute right-2.5 top-1/2 -translate-y-1/2 p-0.5 rounded-md text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-200/50 dark:hover:bg-slate-800 transition-colors"
              >
                <X class="w-3.5 h-3.5" />
              </button>
            </div>

            <!-- Total Matched Counter -->
            <span class="text-xs text-slate-500 dark:text-slate-400 font-medium shrink-0">
              Showing <strong class="text-slate-900 dark:text-white">{{ activeRoleTotalMatched }}</strong> of {{ selectedRoleForDetails.permissions.length }}
            </span>
          </div>

          <!-- Prefix Filter Tabs -->
          <div class="flex items-center gap-1.5 overflow-x-auto pb-1.5 scrollbar-thin">
            <button
              type="button"
              @click="detailsActiveTab = 'all'"
              class="px-2.5 py-1 rounded-lg text-xs font-semibold whitespace-nowrap transition-all duration-150 flex items-center gap-1.5 cursor-pointer"
              :class="[
                detailsActiveTab === 'all'
                  ? 'bg-slate-900 dark:bg-white text-white dark:text-slate-900 shadow-2xs'
                  : 'bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-600 dark:text-slate-400',
              ]"
            >
              <span>All Modules</span>
              <span class="text-[10px] opacity-75">({{ selectedRoleForDetails.permissions.length }})</span>
            </button>

            <button
              v-for="group in allGroupsForSelectedRole"
              :key="group.prefix"
              type="button"
              @click="detailsActiveTab = group.prefix"
              class="px-2.5 py-1 rounded-lg text-xs font-semibold whitespace-nowrap transition-all duration-150 flex items-center gap-1.5 cursor-pointer"
              :class="[
                detailsActiveTab === group.prefix
                  ? 'bg-indigo-600 text-white shadow-2xs'
                  : 'bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-600 dark:text-slate-400',
              ]"
            >
              <component :is="group.meta.icon" class="w-3 h-3" />
              <span>{{ group.meta.label }}</span>
              <span class="text-[10px] opacity-75 font-mono">({{ group.permissions.length }})</span>
            </button>
          </div>
        </div>

        <!-- Permissions List Grouped by Prefix -->
        <div class="space-y-4 max-h-[55vh] overflow-y-auto pr-1 custom-modal-scroll">
          <div
            v-for="group in activeRoleGroupedPermissions"
            :key="group.prefix"
            class="rounded-2xl border p-4 space-y-3"
            :class="[group.meta.bg, group.meta.border]"
          >
            <!-- Prefix Group Header -->
            <div class="flex items-center justify-between pb-2 border-b border-slate-200/80 dark:border-slate-800/80">
              <div class="flex items-center gap-2.5">
                <div
                  class="w-7 h-7 rounded-lg flex items-center justify-center border shadow-2xs"
                  :class="group.meta.badge"
                >
                  <component :is="group.meta.icon" class="w-3.5 h-3.5" />
                </div>
                <div>
                  <h4 class="text-xs font-bold text-slate-900 dark:text-white flex items-center gap-2">
                    <span>{{ group.meta.label }}</span>
                    <span class="px-1.5 py-0.2 rounded text-[9px] font-mono bg-white dark:bg-slate-900 text-slate-500 dark:text-slate-400 border border-slate-200 dark:border-slate-800">
                      prefix: {{ group.prefix }}
                    </span>
                  </h4>
                </div>
              </div>

              <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-white/80 dark:bg-slate-900/80 text-slate-700 dark:text-slate-300 border border-slate-200/60 dark:border-slate-800/60 shadow-2xs">
                {{ group.permissions.length }} {{ group.permissions.length === 1 ? 'action' : 'actions' }}
              </span>
            </div>

            <!-- Permission Items Grid -->
            <div class="grid grid-cols-1 md:grid-cols-2 gap-2.5">
              <div
                v-for="p in group.permissions"
                :key="p.code"
                class="p-3 rounded-xl bg-white dark:bg-slate-900 border border-slate-200/80 dark:border-slate-800 shadow-2xs hover:border-slate-300 dark:hover:border-slate-700 transition-all flex flex-col justify-between gap-1.5"
              >
                <!-- Code & Action Header -->
                <div class="flex items-center justify-between gap-2">
                  <div class="flex items-center gap-1.5 min-w-0">
                    <span
                      class="px-1.5 py-0.5 rounded text-[9px] font-bold uppercase tracking-wider border shrink-0"
                      :class="getActionBadgeStyle(p.action)"
                    >
                      {{ p.action }}
                    </span>
                    <span class="font-mono text-[11px] font-bold text-slate-800 dark:text-slate-200 truncate" :title="p.code">
                      {{ p.code }}
                    </span>
                  </div>

                  <button
                    type="button"
                    @click="copyPermissionCode(p.code)"
                    title="Copy permission code"
                    class="p-1 rounded text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors shrink-0 cursor-pointer"
                  >
                    <Check v-if="copiedCode === p.code" class="w-3.5 h-3.5 text-emerald-500" />
                    <Copy v-else class="w-3.5 h-3.5" />
                  </button>
                </div>

                <!-- Description -->
                <p class="text-[11px] text-slate-500 dark:text-slate-400 leading-snug line-clamp-2">
                  {{ p.description }}
                </p>

                <!-- Status Pill -->
                <div class="pt-1 flex items-center justify-between text-[10px] text-slate-400 border-t border-slate-100 dark:border-slate-800/60">
                  <span class="inline-flex items-center gap-1 text-emerald-600 dark:text-emerald-400 font-semibold">
                    <Check class="w-3 h-3" />
                    Granted
                  </span>
                  <span class="font-mono text-[9px] text-slate-400">
                    {{ group.prefix }}:{{ p.action }}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <!-- Empty Search State -->
          <div
            v-if="activeRoleGroupedPermissions.length === 0"
            class="py-12 text-center space-y-2 rounded-2xl border border-dashed border-slate-200 dark:border-slate-800 p-8"
          >
            <div class="w-10 h-10 rounded-xl bg-slate-100 dark:bg-slate-800 text-slate-400 flex items-center justify-center mx-auto">
              <Search class="w-5 h-5" />
            </div>
            <p class="text-sm font-semibold text-slate-900 dark:text-white">
              No matching permissions found
            </p>
            <p class="text-xs text-slate-500 dark:text-slate-400 max-w-sm mx-auto">
              No permissions match "{{ detailsSearchQuery }}" in role {{ selectedRoleForDetails.name }}.
            </p>
            <button
              type="button"
              @click="detailsSearchQuery = ''; detailsActiveTab = 'all'"
              class="text-xs font-semibold text-indigo-600 dark:text-indigo-400 hover:underline cursor-pointer"
            >
              Clear filters
            </button>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="flex items-center justify-between w-full">
          <span class="text-xs text-slate-400 dark:text-slate-500">
            RBAC Matrix Policy
          </span>
          <BaseButton variant="primary" size="sm" @click="showDetailsModal = false">
            Close
          </BaseButton>
        </div>
      </template>
    </ModernModal>

    <!-- ========================================================================= -->
    <!-- CREATE CUSTOM ROLE MODAL                                                  -->
    <!-- ========================================================================= -->
    <ModernModal
      v-model="showCreateModal"
      title="Create Custom RBAC Role"
      subtitle="Assemble granular permissions organized by resource prefix for fine-grained delegation"
      :icon="Shield"
      :initial-width="700"
      @close="showCreateModal = false"
    >
      <form id="create-role-form" @submit.prevent="handleCreateRole" class="p-6 space-y-5 text-left">
        <div
          v-if="createError"
          class="p-3.5 rounded-2xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/50 text-rose-700 dark:text-rose-300 text-xs flex items-center gap-2"
        >
          <ShieldAlert class="w-4 h-4 shrink-0 text-rose-500" />
          <span>{{ createError }}</span>
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

        <div class="space-y-2.5">
          <div class="flex items-center justify-between">
            <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300">
              Select Permissions (Grouped by Prefix)
            </label>
            <span class="text-[11px] font-semibold text-indigo-600 dark:text-indigo-400">
              {{ selectedPermissions.size }} selected
            </span>
          </div>

          <!-- Quick Filter for creation -->
          <div class="relative">
            <Search class="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
            <input
              v-model="createRoleSearch"
              type="text"
              placeholder="Search permissions to assign..."
              class="w-full h-8 pl-8 pr-7 bg-white dark:bg-slate-900 text-slate-900 dark:text-white placeholder-slate-400 text-xs rounded-lg border border-slate-200 dark:border-slate-800 focus:outline-none focus:border-indigo-500"
            />
            <button
              v-if="createRoleSearch"
              type="button"
              @click="createRoleSearch = ''"
              class="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
            >
              <X class="w-3 h-3" />
            </button>
          </div>

          <div class="max-h-64 overflow-y-auto p-4 rounded-2xl bg-slate-50/60 dark:bg-slate-950/40 border border-slate-200/90 dark:border-slate-800 space-y-4 custom-modal-scroll">
            <div
              v-for="group in createModalGroupedPermissions"
              :key="group.resource"
              class="space-y-2 p-3 rounded-xl border"
              :class="[group.meta.bg, group.meta.border]"
            >
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-2">
                  <span
                    class="px-2 py-0.5 rounded-md text-[10px] font-bold tracking-wider uppercase border flex items-center gap-1.5"
                    :class="group.meta.badge"
                  >
                    <component :is="group.meta.icon" class="w-3 h-3" />
                    <span>{{ group.meta.label }} ({{ group.resource }})</span>
                  </span>
                </div>

                <button
                  type="button"
                  @click="toggleResourceGroup(group.resource)"
                  class="text-[10px] font-semibold text-indigo-600 dark:text-indigo-400 hover:underline cursor-pointer"
                >
                  {{ isResourceGroupAllSelected(group.resource) ? 'Deselect All' : 'Select All' }}
                </button>
              </div>

              <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
                <label
                  v-for="p in group.permissions"
                  :key="p.code"
                  class="flex items-start gap-2.5 text-xs cursor-pointer p-2.5 rounded-xl border transition-all duration-150 select-none"
                  :class="[
                    selectedPermissions.has(p.code)
                      ? 'bg-indigo-50/80 dark:bg-indigo-950/60 border-indigo-200 dark:border-indigo-800/70 shadow-2xs'
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
                    <div class="flex items-center gap-1">
                      <span
                        class="px-1 py-0.2 rounded text-[8px] font-bold uppercase tracking-wider border shrink-0"
                        :class="getActionBadgeStyle(p.action)"
                      >
                        {{ p.action }}
                      </span>
                      <span class="font-mono text-[11px] font-semibold text-slate-900 dark:text-slate-100 truncate">
                        {{ p.code }}
                      </span>
                    </div>
                    <div class="text-[10px] text-slate-500 dark:text-slate-400 leading-tight mt-0.5">
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
