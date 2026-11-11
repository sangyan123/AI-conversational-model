/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 可选的访问令牌。后端未启用 auth.token 时留空。 */
  readonly VITE_AICHAT_AUTH_TOKEN?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
