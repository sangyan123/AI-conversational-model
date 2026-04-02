<template>
  <div class="container">
    <header class="header">
      <h1><i class="fas fa-robot"></i> AI 对话助手</h1>
      <div class="status" :class="{ connected: isConnected }">
        <span class="status-indicator"></span>
        <span class="status-text">{{ statusText }}</span>
      </div>
    </header>
    
    <div class="chat-container">
      <div class="messages" ref="messagesContainer">
        <div 
          v-for="(msg, index) in messages" 
          :key="index"
          class="message"
          :class="msg.isUser ? 'user-message' : 'ai-message'"
        >
          <div class="avatar">
            <i :class="msg.isUser ? 'fas fa-user' : 'fas fa-robot'"></i>
          </div>
          <div class="content">
            <div class="text">{{ msg.content }}</div>
            <div class="time">{{ msg.time }}</div>
          </div>
        </div>
        
        <!-- 加载动画 -->
        <div v-if="isLoading" class="message ai-message">
          <div class="avatar">
            <i class="fas fa-robot"></i>
          </div>
          <div class="content">
            <div class="loading">
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>
        </div>
      </div>
      
      <div class="input-area">
        <div class="input-container">
          <textarea 
            v-model="userInput"
            placeholder="输入你的问题..."
            rows="1"
            @input="autoResize"
            @keydown="handleKeydown"
            @focus="handleFocus"
            @blur="handleBlur"
          ></textarea>
          <button 
            @click="sendMessage" 
            :disabled="!isConnected || isLoading"
            class="send-button"
            :class="{ 'sending': isLoading }"
          >
            <i class="fas fa-paper-plane"></i>
          </button>
        </div>
        <div class="hint">
          按 Enter 发送，Shift + Enter 换行
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, onUnmounted } from 'vue'

// 配置
const API_BASE_URL = 'http://localhost:8080/api/chat'
const MESSAGES_KEY = 'ai_chat_messages'
const CONNECTION_CHECK_INTERVAL = 30000 // 30秒

// 响应式数据
const messages = ref([])
const userInput = ref('')
const isConnected = ref(false)
const statusText = ref('连接中...')
const isLoading = ref(false)
const messagesContainer = ref(null)

// 定时器引用
let connectionCheckTimer = null

// 检查后端连接
async function checkConnection() {
  try {
    const response = await fetch(`${API_BASE_URL}/health`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json'
      },
      timeout: 5000 // 5秒超时
    })
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
function updateStatus(connected, text) {
  isConnected.value = connected
  statusText.value = text
}

// 防抖函数
function debounce(func, wait) {
  let timeout
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout)
      func(...args)
    }
    clearTimeout(timeout)
    timeout = setTimeout(later, wait)
  }
}

// 自动调整文本框高度（带防抖）
const autoResize = debounce((e) => {
  const textarea = e.target
  textarea.style.height = 'auto'
  textarea.style.height = Math.min(textarea.scrollHeight, 150) + 'px'
}, 100)

// 获取时间字符串
function getTimeString(timestamp) {
  const time = new Date(timestamp)
  return time.toLocaleTimeString('zh-CN', { 
    hour: '2-digit', 
    minute: '2-digit' 
  })
}

// 添加消息
function addMessage(content, isUser = false, timestamp = Date.now()) {
  messages.value.push({
    content,
    isUser,
    time: getTimeString(timestamp),
    timestamp
  })
  saveMessage(content, isUser, timestamp)
  nextTick(() => scrollToBottom())
}

// 滚动到底部（使用requestAnimationFrame优化）
function scrollToBottom() {
  if (messagesContainer.value) {
    requestAnimationFrame(() => {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    })
  }
}

// 批量保存消息到本地存储（减少localStorage操作）
let messageBatch = []
let saveTimer = null

function saveMessage(content, isUser, timestamp) {
  messageBatch.push({ content, isUser, timestamp, date: new Date(timestamp).toISOString() })
  
  // 清除之前的定时器
  if (saveTimer) {
    clearTimeout(saveTimer)
  }
  
  // 500ms后批量保存，减少localStorage操作
  saveTimer = setTimeout(() => {
    const existingMessages = JSON.parse(localStorage.getItem(MESSAGES_KEY) || '[]')
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
    const history = JSON.parse(localStorage.getItem(MESSAGES_KEY) || '[]')
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

// 发送消息
async function sendMessage() {
  const message = userInput.value.trim()
  
  if (!message || !isConnected.value || isLoading.value) return
  
  // 清空输入
  userInput.value = ''
  autoResize({ target: document.querySelector('textarea') })
  
  // 显示用户消息
  addMessage(message, true)
  
  // 显示加载状态
  isLoading.value = true
  
  try {
    const response = await fetch(API_BASE_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message }),
      timeout: 30000 // 30秒超时
    })
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    
    const data = await response.json()
    addMessage(data.message, false)
    
  } catch (error) {
    console.error('发送消息失败:', error)
    addMessage('抱歉，服务暂时不可用，请稍后再试。', false)
  } finally {
    isLoading.value = false
  }
}

// 键盘事件处理
function handleKeydown(e) {
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
  }
  
  // 确保所有消息都保存到本地存储
  if (messageBatch.length > 0) {
    const existingMessages = JSON.parse(localStorage.getItem(MESSAGES_KEY) || '[]')
    const allMessages = [...existingMessages, ...messageBatch]
    
    if (allMessages.length > 50) {
      const messagesToSave = allMessages.slice(-50)
      localStorage.setItem(MESSAGES_KEY, JSON.stringify(messagesToSave))
    } else {
      localStorage.setItem(MESSAGES_KEY, JSON.stringify(allMessages))
    }
  }
})
</script>