/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 可选的访问令牌。后端未启用 auth.token 时留空。 */
  readonly VITE_AICHAT_AUTH_TOKEN?: string
  /**
   * 后端地址（含协议与端口，不带结尾斜杠）。
   * 留空则使用相对路径 /api/chat，开发期由 Vite 代理转发。
   */
  readonly VITE_API_BASE_URL?: string
  /** 仅开发期生效：Vite 代理目标，默认 http://localhost:8080 */
  readonly VITE_DEV_API_TARGET?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
