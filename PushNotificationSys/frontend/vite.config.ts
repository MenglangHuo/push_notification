import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  const port = Number(env.VITE_PORT) || Number(env.PORT) || 5173
  const host = env.VITE_HOST || (env.HOST ? env.HOST : true)

  // Determine whether running in mock sandbox mode
  const isMock =
    mode === 'mock' ||
    env.VITE_APP_ENV === 'mock' ||
    env.VITE_ENABLE_MOCK_DATA === 'true' ||
    env.VITE_USE_MOCK_DATA === 'true'

  // Reverse proxy is enabled by default unless explicitly disabled or running mock mode
  const useReverseProxy = env.VITE_USE_PROXY !== 'false' && !isMock

  // Check if current mode is a development/local environment
  const isDevLikeMode =
    mode === 'development' ||
    mode === 'localdev' ||
    mode === 'local' ||
    mode === 'dev' ||
    env.VITE_APP_ENV === 'local' ||
    env.VITE_APP_ENV === 'development'

  // Default proxy target for PushNotificationSys backend
  const defaultProxyTarget = env.VITE_BACKEND_URL || 'http://localhost:8080'

  // Clean explicit proxy target from environment variables
  const explicitTarget = (
    env.VITE_PROXY_TARGET ||
    env.PROXY_TARGET ||
    (env.VITE_AUTH_BASE_URL && env.VITE_AUTH_BASE_URL.startsWith('http')
      ? env.VITE_AUTH_BASE_URL
      : '') ||
    (env.VITE_API_BASE_URL && env.VITE_API_BASE_URL.startsWith('http')
      ? env.VITE_API_BASE_URL.replace(/\/api(\/.*)?$/, '')
      : '') ||
    ''
  ).trim()

  let rawProxyTarget = useReverseProxy
    ? (explicitTarget || defaultProxyTarget).trim().replace(/\/+$/, '')
    : ''

  if (rawProxyTarget && !/^https?:\/\//i.test(rawProxyTarget)) {
    rawProxyTarget = /^localhost|^127\.0\.0\.1|:\d+$/i.test(rawProxyTarget)
      ? `http://${rawProxyTarget}`
      : `https://${rawProxyTarget}`
  }
  const proxyTarget = rawProxyTarget

  console.log(
    `[vite] Mode: ${mode} | Port: ${port} | Env: ${env.VITE_APP_ENV || mode} | Mock: ${isMock ? 'Enabled' : 'Disabled'}`,
  )
  if (proxyTarget) {
    console.log(`[vite] Reverse proxy target: ${proxyTarget}`)
  } else {
    console.log('[vite] Reverse proxy: Disabled (same-origin / relative mode)')
  }

  // SPA navigation bypass for browser page requests on routes that overlap with API endpoints (/login, /two-factor)
  const bypassHtml = (req: any) => {
    if (req.headers && req.headers.accept && req.headers.accept.includes('text/html')) {
      return '/index.html'
    }
  }

  // Graceful proxy error handler and endpoint call logger
  const configureProxyLogging = (proxy: any) => {
    proxy.on('proxyReq', (_proxyReq: any, req: any) => {
      console.log(`[vite:proxy] -> ${req.method} ${req.url} -> ${proxyTarget}${req.url}`)
    })
    proxy.on('proxyRes', (proxyRes: any, req: any) => {
      console.log(`[vite:proxy] <- [${proxyRes.statusCode}] ${req.method} ${req.url}`)
    })
    proxy.on('error', (err: any, req: any, res: any) => {
      console.warn(
        `[vite:proxy] Proxy error: ${req.method} ${req.url} -> ${proxyTarget} (${err.code || err.message})`,
      )
      if (res.writeHead && !res.headersSent) {
        res.writeHead(502, { 'Content-Type': 'application/json' })
        res.end(
          JSON.stringify({
            success: false,
            message: `Backend proxy target unreachable (${proxyTarget}). Ensure your backend service is running.`,
            error: err.code || err.message,
          }),
        )
      }
    })
  }

  const proxyConfig = proxyTarget
    ? {
        '/admin/api/v1': {
          target: proxyTarget,
          changeOrigin: true,
          secure: false,
          bypass: bypassHtml,
          configure: configureProxyLogging,
        },
        '/api/v1': {
          target: proxyTarget,
          changeOrigin: true,
          secure: false,
          configure: configureProxyLogging,
        },
        '/sdk/v1': {
          target: proxyTarget,
          changeOrigin: true,
          secure: false,
          configure: configureProxyLogging,
        },
        '/ws': {
          target: proxyTarget,
          ws: true,
          changeOrigin: true,
          secure: false,
          configure: configureProxyLogging,
        },
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
          secure: false,
          ws: true,
          configure: configureProxyLogging,
        },
      }
    : undefined

  return {
    plugins: [vue(), vueDevTools(), tailwindcss()],
    server: {
      port,
      host,
      allowedHosts: true,
      headers: {
        'X-Content-Type-Options': 'nosniff',
        'X-Frame-Options': 'DENY',
        'Referrer-Policy': 'strict-origin-when-cross-origin',
        'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
      },
      proxy: proxyConfig,
    },
    preview: {
      port,
      host,
      allowedHosts: true,
      headers: {
        'X-Content-Type-Options': 'nosniff',
        'X-Frame-Options': 'DENY',
        'Referrer-Policy': 'strict-origin-when-cross-origin',
        'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
      },
      proxy: proxyConfig,
    },
    build: {
      chunkSizeWarningLimit: 700,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('node_modules')) {
              if (id.includes('@tiptap') || id.includes('prosemirror')) {
                return 'vendor-tiptap'
              }
              if (id.includes('@lezer')) {
                return 'vendor-lezer'
              }
              if (id.includes('codemirror') || id.includes('vue-codemirror6')) {
                return 'vendor-codemirror'
              }
              if (id.includes('lucide-vue-next')) {
                return 'vendor-icons'
              }
              if (id.includes('echarts') || id.includes('zrender')) {
                return 'vendor-echarts'
              }
              if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) {
                return 'vendor-vue'
              }
            }
          },
        },
      },
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
  }
})
