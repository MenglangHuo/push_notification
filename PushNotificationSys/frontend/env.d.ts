/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_APP_ENV?: 'development' | 'production' | 'mock';
  readonly VITE_PORT?: string;
  readonly VITE_HOST?: string;
  readonly VITE_PROXY_TARGET?: string;
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_AUTH_BASE_URL?: string;
  readonly VITE_API_SECRET?: string;
  readonly VITE_ENABLE_MOCK_DATA?: string;
  readonly VITE_USE_MOCK_DATA?: string;
  readonly VITE_USE_PROXY?: string;
  readonly VITE_TEST_MOCK_DATA?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
