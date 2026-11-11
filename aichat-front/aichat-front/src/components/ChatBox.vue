<script setup lang="ts">
import { ref, onMounted, nextTick, onUnmounted } from 'vue'
import IconName from './IconName.vue'

// 配置
/**
 * 后端地址。留空则使用相对路径 `/api/chat`：
 * - 开发环境走 Vite 代理（见 vite.config.ts），同源请求，不需要 CORS
 * - 生产环境需由反向代理把 /api 转发到后端，或显式设置 VITE_API_BASE_URL
 */
const API_ORIGIN: string = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/+$/, '')
const API_BASE_URL = `${API_ORIGIN}/api/chat`
const MESSAGES_KEY = 'ai_chat_messages'
const CONNECTION_CHECK_INTERVAL = 30000 // 30秒
const CONNECT_TIMEOUT = 5000            // 健康检查超时
const REQUEST_TIMEOUT = 65000           // 聊天请求超时（后端读取超时 60s，略大一点）
const MAX_HISTORY_TURNS = 20            // 最多带 20 轮历史上下文

/**
 * 可选访问令牌。后端未配置 auth.token 时留空即可。
 * 需要时在 .env.local 里设置 VITE_AICHAT_AUTH_TOKEN。
 */
const AUTH_TOKEN: string = import.meta.env.VITE_AICHAT_AUTH_TOKEN ?? ''

/** 构造请求头，令牌存在时自动携带。 */
function buildHeaders(): Record<string, string> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (AUTH_TOKEN) {
    headers.Authorization = `Bearer ${AUTH_TOKEN}`
  }
  return headers
}

/**
 * 带真实超时的 fetch。
 *
 * 注意：`fetch` 不认 `timeout` 选项（会被静默忽略），必须用 AbortController。
 */
async function fetchWithTimeout(
  url: string,
  init: RequestInit,
  timeoutMs: number
): Promise<Response> {
  const controller = new AbortController()
  const externalSignal = init.signal
  const timer = setTimeout(() => controller.abort(), timeoutMs)

  // 外部取消（如组件卸载）时同步终止请求
  const onExternalAbort = () => controller.abort()
  externalSignal?.addEventListener('abort', onExternalAbort)

  try {
    return await fetch(url, { ...init, signal: controller.signal })
  } finally {
    clearTimeout(timer)
    externalSignal?.removeEventListener('abort', onExternalAbort)
  }
}

/** 从后端错误响应体里提取可读信息，取不到就退回状态码描述。 */
async function readErrorMessage(response: Response): Promise<string> {
  try {
    const data = await response.json()
    if (data && typeof data.message === 'string' && data.message) {
      return data.message
    }
  } catch {
    // 响应体不是 JSON，忽略
  }
  return `请求失败（HTTP ${response.status}）`
}

interface ChatMessage {
  content: string
  isUser: boolean
  time: string
  timestamp: number
  /** 本地生成的错误提示，不落库、不进入上下文 */
  isError?: boolean
}

interface StoredMessage {
  content: string
  isUser: boolean
  timestamp: number
  date: string
}

// 响应式数据
const messages = ref<ChatMessage[]>([])
const userInput = ref('')
const isConnected = ref(false)
const statusText = ref('连接中...')
const isLoading = ref(false)
const messagesContainer = ref<HTMLElement | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)

// 定时器引用
let connectionCheckTimer: ReturnType<typeof setInterval> | null = null

// 检查后端连接
async function checkConnection(): Promise<boolean> {
  try {
    const response = await fetchWithTimeout(
      `${API_BASE_URL}/health`,
      {
        method: 'GET',
        headers: buildHeaders()
      },
      CONNECT_TIMEOUT
    )
    if (response.ok) {
      updateStatus(true, '已连接')
      return true
    }
  } catch (error) {
    console.error('连接检查失败:', error)
  }
  updateStatus(false, '连接失败')
  return false
}

// 更新状态显示
function updateStatus(connected: boolean, text: string) {
  isConnected.value = connected
  statusText.value = text
}

// 防抖函数
function debounce<T extends (...args: never[]) => void>(func: T, wait: number): T {
  let timeout: ReturnType<typeof setTimeout> | undefined
  return function executedFunction(this: unknown, ...args: never[]) {
    const later = () => {
      clearTimeout(timeout)
      func(...args)
    }
    clearTimeout(timeout)
    timeout = setTimeout(later, wait)
  } as T
}

// 自动调整文本框高度（带防抖）
const applyAutoResize = debounce((textarea: HTMLTextAreaElement) => {
  textarea.style.height = 'auto'
  textarea.style.height = Math.min(textarea.scrollHeight, 150) + 'px'
}, 100)

// 输入事件处理（供模板绑定）
function handleInput(e: Event) {
  const target = e.target
  if (target instanceof HTMLTextAreaElement) {
    applyAutoResize(target)
  }
}

// 获取时间字符串
function getTimeString(timestamp: number): string {
  const time = new Date(timestamp)
  return time.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 添加消息
function addMessage(
  content: string,
  isUser = false,
  timestamp: number = Date.now(),
  isError = false
) {
  messages.value.push({
    content,
    isUser,
    time: getTimeString(timestamp),
    timestamp,
    isError
  })
  // 本地错误提示不落库，也不参与后续上下文
  if (!isError) {
    saveMessage(content, isUser, timestamp)
  }
  nextTick(() => scrollToBottom())
}

// 滚动到底部
function scrollToBottom() {
  const container = messagesContainer.value
  if (container) {
    requestAnimationFrame(() => {
      container.scrollTop = container.scrollHeight
    })
  }
}

// 批量保存消息到本地存储（减少localStorage操作）
let messageBatch: StoredMessage[] = []
let saveTimer: ReturnType<typeof setTimeout> | null = null

function saveMessage(content: string, isUser: boolean, timestamp: number) {
  messageBatch.push({ content, isUser, timestamp, date: new Date(timestamp).toISOString() })

  // 清除之前的定时器
  if (saveTimer) {
    clearTimeout(saveTimer)
  }

  // 500ms后批量保存，减少localStorage操作
  saveTimer = setTimeout(() => {
    const existingMessages: StoredMessage[] = JSON.parse(localStorage.getItem(MESSAGES_KEY) || '[]')
    const allMessages = [...existingMessages, ...messageBatch]

    // 只保留最近50条消息
    if (allMessages.length > 50) {
      const messagesToSave = allMessages.slice(-50)
      localStorage.setItem(MESSAGES_KEY, JSON.stringify(messagesToSave))
    } else {
      localStorage.setItem(MESSAGES_KEY, JSON.stringify(allMessages))
    }

    // 清空批量数组
    messageBatch = []
  }, 500)
}

// 加载历史消息
function loadHistory() {
  try {
    const history: StoredMessage[] = JSON.parse(localStorage.getItem(MESSAGES_KEY) || '[]')
    messages.value = history.map(msg => ({
      content: msg.content,
      isUser: msg.isUser,
      time: getTimeString(msg.timestamp),
      timestamp: msg.timestamp
    }))
    nextTick(() => scrollToBottom())
  } catch (error) {
    console.error('加载历史消息失败:', error)
    // 清空损坏的本地存储
    localStorage.removeItem(MESSAGES_KEY)
    messages.value = []
  }
}

/**
 * 把当前已显示的消息整理成后端需要的历史上下文。
 * 必须在本轮用户消息 addMessage 之前调用，否则会把自己也当成历史。
 */
function buildHistory(): Array<{ role: 'user' | 'assistant'; content: string }> {
  return messages.value
    .filter(msg => !msg.isError)
    .slice(-MAX_HISTORY_TURNS * 2)
    .filter(msg => msg.content && msg.content.trim())
    .map(msg => ({
      role: msg.isUser ? ('user' as const) : ('assistant' as const),
      content: msg.content
    }))
}

// 发送消息
async function sendMessage() {  const message = userInput.value.trim()

  if (!message || !isConnected.value || isLoading.value) return

  // 清空输入
  userInput.value = ''
  if (textareaRef.value) {
    applyAutoResize(textareaRef.value)
  }

  // 先取历史（不含本轮），再显示用户消息
  const history = buildHistory()

  // 显示用户消息
  addMessage(message, true)

  // 显示加载状态
  isLoading.value = true

  try {
    const response = await fetchWithTimeout(
      API_BASE_URL,
      {
        method: 'POST',
        headers: buildHeaders(),
        body: JSON.stringify({ message, history })
      },
      REQUEST_TIMEOUT
    )

    if (!response.ok) {
      throw new Error(await readErrorMessage(response))
    }

    const data = await response.json()
    addMessage(data.message, false)

  } catch (error) {
    console.error('发送消息失败:', error)

    let tip = '抱歉，服务暂时不可用，请稍后再试。'
    if (error instanceof DOMException && error.name === 'AbortError') {
      tip = '请求超时，AI 服务响应太慢，已取消本次请求。'
    } else if (error instanceof TypeError) {
      // fetch 网络层失败（后端未启动 / 跨域被拦）
      tip = '无法连接后端服务，请确认后端已启动。'
    } else if (error instanceof Error && error.message) {
      tip = `抱歉，${error.message}`
    }
    addMessage(tip, false, Date.now(), true)

    // 网络/超时类错误通常意味着连接已断，标记为断开以触发重新检测
    updateStatus(false, '连接异常')
  } finally {
    isLoading.value = false
  }
}

// 键盘事件处理
function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

// 输入框聚焦处理
function handleFocus() {
  // 可以添加聚焦效果，如改变边框颜色、显示提示等
}

// 输入框失焦处理
function handleBlur() {
  // 可以添加失焦效果，如恢复默认边框颜色等
}

// 初始化
onMounted(async () => {
  await checkConnection()
  loadHistory()

  // 设置连接检查定时器
  connectionCheckTimer = setInterval(checkConnection, CONNECTION_CHECK_INTERVAL)
})

// 清理
onUnmounted(() => {
  // 清除定时器
  if (connectionCheckTimer) {
    clearInterval(connectionCheckTimer)
    connectionCheckTimer = null
  }

  // 取消待执行的批量保存，避免卸载后又写一次导致重复入库
  if (saveTimer) {
    clearTimeout(saveTimer)
    saveTimer = null
  }

  // 确保所有消息都保存到本地存储
  if (messageBatch.length > 0) {
    const existingMessages: StoredMessage[] = JSON.parse(localStorage.getItem(MESSAGES_KEY) || '[]')
    const allMessages = [...existingMessages, ...messageBatch]

    if (allMessages.length > 50) {
      const messagesToSave = allMessages.slice(-50)
      localStorage.setItem(MESSAGES_KEY, JSON.stringify(messagesToSave))
    } else {
      localStorage.setItem(MESSAGES_KEY, JSON.stringify(allMessages))
    }

    messageBatch = []
  }
})
</script>

<template>
  <div class="container">
    <header class="header">
      <h1><IconName name="robot" /> AI 对话助手</h1>
      <div class="status" :class="{ connected: isConnected }">
        <span class="status-indicator"></span>
        <span class="status-text">{{ statusText }}</span>
      </div>
    </header>

    <div class="chat-container">
      <div class="messages" ref="messagesContainer">
        <!-- 空状态：还没有任何对话时给用户引导，避免大片留白 -->
        <div v-if="messages.length === 0 && !isLoading" class="empty-state">
          <div class="icon-badge">
            <IconName name="robot" />
          </div>
          <h2>有什么可以帮你的？</h2>
          <p>输入问题开始对话。你的消息与 AI 回复都会保存在本地，刷新后仍可查看。</p>
        </div>

        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="message"
          :class="msg.isUser ? 'user-message' : 'ai-message'"
        >
          <div class="avatar">
            <IconName :name="msg.isUser ? 'user' : 'robot'" />
          </div>
          <div class="content">
            <div class="text" :class="{ 'error-text': msg.isError }">{{ msg.content }}</div>
            <div class="time">{{ msg.time }}</div>
          </div>
        </div>

        <!-- 加载动画 -->
        <div v-if="isLoading" class="message ai-message">
          <div class="avatar">
            <IconName name="robot" />
          </div>
          <div class="content">
            <div class="loading" aria-label="AI 正在思考">
              <div class="spinner">
                <i></i>
                <i></i>
                <i></i>
                <i></i>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="input-area">
        <div class="input-container">
          <textarea
            ref="textareaRef"
            v-model="userInput"
            placeholder="输入你的问题..."
            rows="1"
            @input="handleInput"
            @keydown="handleKeydown"
            @focus="handleFocus"
            @blur="handleBlur"
          ></textarea>
          <button
            @click="sendMessage"
            :disabled="!isConnected || isLoading"
            class="send-button"
            :class="{ 'sending': isLoading }"
            aria-label="发送消息"
          >
            <IconName name="send" />
          </button>
        </div>
        <div class="hint">
          <span><kbd>Enter</kbd> 发送</span>
          <span><kbd>Shift</kbd> + <kbd>Enter</kbd> 换行</span>
        </div>
      </div>
    </div>
  </div>
</template>
