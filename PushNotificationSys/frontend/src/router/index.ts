import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useSplashStore } from '@/stores/splashStore'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guestOnly: true, standalone: true },
  },
  {
    path: '/two-factor',
    name: 'TwoFactor',
    component: () => import('@/views/TwoFactorView.vue'),
    meta: { requiresPendingTwoFactor: true, standalone: true },
    alias: ['/mfa', '/two_factor'],
  },
  {
    path: '/accept-invite',
    name: 'AcceptInvite',
    component: () => import('@/views/AcceptInviteView.vue'),
    meta: { guestOnly: true, standalone: true },
  },
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/DashboardView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/apps',
    name: 'Apps',
    component: () => import('@/views/AppsView.vue'),
    meta: { requiresAuth: true, permission: 'app:read' },
  },

  // Direct Shorthand Routes (uses authStore.currentAppId automatically)
  {
    path: '/notifications',
    name: 'DirectNotifications',
    component: () => import('@/views/NotificationsView.vue'),
    meta: { requiresAuth: true, permission: 'notification:read' },
  },
  {
    path: '/notifications/new',
    name: 'DirectNotificationComposer',
    component: () => import('@/views/NotificationComposerView.vue'),
    meta: { requiresAuth: true, permission: 'notification:create' },
  },
  {
    path: '/devices',
    name: 'DirectDevices',
    component: () => import('@/views/DevicesView.vue'),
    meta: { requiresAuth: true, permission: 'device:read' },
  },
  {
    path: '/segments',
    name: 'DirectSegments',
    component: () => import('@/views/SegmentsView.vue'),
    meta: { requiresAuth: true, permission: 'segment:read' },
  },
  {
    path: '/segments/new',
    name: 'DirectSegmentCreate',
    component: () => import('@/views/SegmentBuilderView.vue'),
    meta: { requiresAuth: true, permission: 'segment:create' },
  },
  {
    path: '/segments/:segmentId/edit',
    name: 'DirectSegmentEdit',
    component: () => import('@/views/SegmentBuilderView.vue'),
    meta: { requiresAuth: true, permission: 'segment:update' },
  },
  {
    path: '/logs',
    name: 'DirectDeliveryLogs',
    component: () => import('@/views/DeliveryLogsView.vue'),
    meta: { requiresAuth: true, permission: 'log:read' },
  },
  {
    path: '/api-keys',
    name: 'DirectApiKeys',
    component: () => import('@/views/ApiKeysView.vue'),
    meta: { requiresAuth: true, permission: 'apikey:read' },
  },
  {
    path: '/realtime',
    name: 'DirectRealtime',
    component: () => import('@/views/RealtimeTestView.vue'),
    meta: { requiresAuth: true, permission: 'notification:read' },
  },
  {
    path: '/web-push-test',
    name: 'DirectWebPushTest',
    component: () => import('@/views/WebPushTestView.vue'),
    meta: { requiresAuth: true, permission: 'notification:create' },
  },
  {
    path: '/app-settings',
    name: 'DirectAppSettings',
    component: () => import('@/views/AppSettingsView.vue'),
    meta: { requiresAuth: true, permission: 'credential:read' },
  },

  // App-Scoped Routes from frontend_v1
  {
    path: '/apps/:appId/settings',
    name: 'AppSettings',
    component: () => import('@/views/AppSettingsView.vue'),
    meta: { requiresAuth: true, permission: 'credential:read' },
  },
  {
    path: '/apps/:appId/api-keys',
    name: 'ApiKeys',
    component: () => import('@/views/ApiKeysView.vue'),
    meta: { requiresAuth: true, permission: 'apikey:read' },
  },
  {
    path: '/apps/:appId/devices',
    name: 'Devices',
    component: () => import('@/views/DevicesView.vue'),
    meta: { requiresAuth: true, permission: 'device:read' },
  },
  {
    path: '/apps/:appId/notifications',
    name: 'Notifications',
    component: () => import('@/views/NotificationsView.vue'),
    meta: { requiresAuth: true, permission: 'notification:read' },
  },
  {
    path: '/apps/:appId/notifications/new',
    name: 'NotificationComposer',
    component: () => import('@/views/NotificationComposerView.vue'),
    meta: { requiresAuth: true, permission: 'notification:create' },
  },
  {
    path: '/apps/:appId/web-push-test',
    name: 'WebPushTestBench',
    component: () => import('@/views/WebPushTestView.vue'),
    meta: { requiresAuth: true, permission: 'notification:create' },
  },
  {
    path: '/apps/:appId/realtime',
    name: 'RealtimeGateway',
    component: () => import('@/views/RealtimeTestView.vue'),
    meta: { requiresAuth: true, permission: 'notification:read' },
  },
  {
    path: '/apps/:appId/segments',
    name: 'SegmentList',
    component: () => import('@/views/SegmentsView.vue'),
    meta: { requiresAuth: true, permission: 'segment:read' },
  },
  {
    path: '/apps/:appId/segments/new',
    name: 'SegmentCreate',
    component: () => import('@/views/SegmentBuilderView.vue'),
    meta: { requiresAuth: true, permission: 'segment:create' },
  },
  {
    path: '/apps/:appId/segments/:segmentId/edit',
    name: 'SegmentEdit',
    component: () => import('@/views/SegmentBuilderView.vue'),
    meta: { requiresAuth: true, permission: 'segment:update' },
  },
  {
    path: '/apps/:appId/logs',
    name: 'DeliveryLogs',
    component: () => import('@/views/DeliveryLogsView.vue'),
    meta: { requiresAuth: true, permission: 'log:read' },
  },

  // Administration Routes
  {
    path: '/members',
    name: 'Members',
    component: () => import('@/views/MembersView.vue'),
    meta: { requiresAuth: true, permission: 'member:read' },
  },
  {
    path: '/roles',
    name: 'Roles',
    component: () => import('@/views/RolesView.vue'),
    meta: { requiresAuth: true, permission: 'role:read' },
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/ForbiddenView.vue'),
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard',
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()

  if (authStore.isInitializing) {
    await authStore.initAuth()
  }

  // Check if routed with appId parameter and sync currentAppId
  if (to.params.appId && typeof to.params.appId === 'string') {
    authStore.setCurrentApp(to.params.appId)
  }

  if (to.meta.guestOnly) {
    if (authStore.isAuthenticated) {
      return next('/dashboard')
    }
    return next()
  }

  if (to.matched.some((r) => r.meta.requiresAuth)) {
    if (!authStore.isAuthenticated) {
      return next({ path: '/login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : undefined })
    }

    if (to.meta.permission) {
      const requiredPerm = to.meta.permission as string
      if (!authStore.hasPermission(requiredPerm)) {
        return next('/403')
      }
    }
  }

  next()
})

router.afterEach(() => {
  const splashStore = useSplashStore()
  splashStore.finishInitialLoad()
})

export default router
