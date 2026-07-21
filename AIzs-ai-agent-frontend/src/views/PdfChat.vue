<template>
  <div class="pdf-chat-container">
    <ConversationSidebar 
      :current-chat-id="chatId" 
      :conversations="conversations"
      @select-chat="selectChat"
      @new-chat="newChat"
      @delete-chat="deleteChat"
    />
    
    <div class="chat-main">
      <div class="chat-header">
        <button class="back-btn" @click="$router.push('/')">← 返回</button>
        <h2>📄 PDF文档问答助手</h2>
        <span class="chat-id">会话ID: {{ chatId }}</span>
      </div>
      
      <div class="file-upload-area">
        <div class="upload-box" :class="{ dragging: isDragging }" @dragover.prevent="isDragging = true" @dragleave="isDragging = false" @drop.prevent="handleDrop" @click="triggerFileInput">
          <input type="file" ref="fileInput" accept=".pdf" class="file-input" @change="handleFileSelect" />
          <div class="upload-icon">📁</div>
          <div class="upload-text">点击或拖拽上传PDF文件</div>
          <div class="upload-hint">支持 .pdf 格式，文件不超过50MB</div>
        </div>
        <div v-if="uploadedFileName" class="uploaded-file">
          <span class="file-icon">✅</span>
          <span class="file-name">{{ uploadedFileName }}</span>
          <button class="remove-file-btn" @click="removeFile">移除</button>
        </div>
      </div>
      
      <div class="chat-messages" ref="messagesContainer">
        <div v-for="(message, index) in messages" :key="index" class="message-wrapper" :class="{ 'user-message': message.isUser, 'ai-message': !message.isUser }">
          <div class="message-content" :class="message.type">
            <span v-if="!message.isUser" class="avatar">🤖</span>
            <span v-else class="avatar">👤</span>
            <div class="message-text">{{ message.content }}</div>
          </div>
        </div>
      </div>
      
      <div class="chat-input-area">
        <input 
          type="text" 
          v-model="inputMessage" 
          class="message-input" 
          placeholder="请输入您的问题..." 
          @keyup.enter="sendMessage"
          :disabled="connectionStatus === 'connecting'"
        />
        <button class="send-btn" @click="sendMessage" :disabled="!inputMessage.trim() || connectionStatus === 'connecting'">
          {{ connectionStatus === 'connecting' ? '发送中...' : '发送' }}
        </button>
      </div>
      
      <div class="connection-status" :class="connectionStatus">
        <span class="status-dot"></span>
        <span>{{ connectionStatusText }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ConversationSidebar from '../components/ConversationSidebar.vue'
import { chatWithPdf, uploadPdf, checkPdfFile } from '../api'

const router = useRouter()

useHead({
  title: 'PDF文档问答 - AI超级智能体应用平台',
  meta: [
    {
      name: 'description',
      content: 'PDF文档问答助手，上传PDF文件后可进行智能问答'
    },
    {
      name: 'keywords',
      content: 'PDF问答,文档问答,RAG,智能问答,AI助手'
    }
  ]
})

const chatId = ref('')
const messages = ref([])
const inputMessage = ref('')
const connectionStatus = ref('disconnected')
const eventSource = ref(null)
const isDragging = ref(false)
const uploadedFileName = ref('')
const conversations = ref([])

const connectionStatusText = computed(() => {
  switch (connectionStatus.value) {
    case 'connecting': return '正在连接...'
    case 'connected': return '已连接'
    case 'disconnected': return '已断开'
    case 'error': return '连接错误'
    default: return '未知状态'
  }
})

const conversationsKey = 'pdf_conversations'

const loadConversations = () => {
  const saved = localStorage.getItem(conversationsKey)
  if (saved) {
    conversations.value = JSON.parse(saved)
  }
}

const saveConversations = () => {
  localStorage.setItem(conversationsKey, JSON.stringify(conversations.value))
}

const createNewChat = () => {
  const newChatId = 'chat_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)
  const now = new Date()
  const newConversation = {
    id: newChatId,
    name: 'PDF问答 ' + now.toLocaleTimeString(),
    preview: '开始新对话',
    agentType: 'PDF_APP',
    time: now.toLocaleString()
  }
  conversations.value.unshift(newConversation)
  saveConversations()
  return newChatId
}

const selectChat = (id) => {
  chatId.value = id
  loadMessages()
}

const newChat = () => {
  chatId.value = createNewChat()
  messages.value = []
  uploadedFileName.value = ''
  saveMessages()
}

const deleteChat = (id) => {
  conversations.value = conversations.value.filter(c => c.id !== id)
  saveConversations()
  if (chatId.value === id) {
    newChat()
  }
}

const messagesKey = 'pdf_messages_'

const loadMessages = () => {
  const saved = localStorage.getItem(messagesKey + chatId.value)
  if (saved) {
    messages.value = JSON.parse(saved)
  } else {
    messages.value = []
  }
}

const saveMessages = () => {
  localStorage.setItem(messagesKey + chatId.value, JSON.stringify(messages.value))
  updateConversationPreview()
}

const updateConversationPreview = () => {
  const conversation = conversations.value.find(c => c.id === chatId.value)
  if (conversation && messages.value.length > 0) {
    const lastMessage = messages.value[messages.value.length - 1]
    conversation.preview = lastMessage.content.substring(0, 30) + (lastMessage.content.length > 30 ? '...' : '')
    conversation.time = new Date().toLocaleString()
    saveConversations()
  }
}

const cleanMarkdown = (text) => {
  return text
    .replace(/\*\*/g, '')
    .replace(/\*/g, '')
    .replace(/`/g, '')
    .replace(/~~/g, '')
    .replace(/__/g, '')
}

const addMessage = (content, isUser, type = '') => {
  messages.value.push({
    content: isUser ? content : cleanMarkdown(content),
    isUser,
    type,
    time: new Date().getTime()
  })
}

const triggerFileInput = () => {
  document.querySelector('.file-input').click()
}

const handleFileSelect = (event) => {
  const file = event.target.files[0]
  if (file) {
    processFile(file)
  }
}

const handleDrop = (event) => {
  isDragging.value = false
  const file = event.dataTransfer.files[0]
  if (file && file.type === 'application/pdf') {
    processFile(file)
  }
}

const processFile = async (file) => {
  if (!chatId.value) {
    chatId.value = createNewChat()
  }
  
  try {
    connectionStatus.value = 'connecting'
    const response = await uploadPdf(chatId.value, file)
    if (response.status === 200) {
      uploadedFileName.value = file.name
      addMessage('已上传文件: ' + file.name, true, 'user-question')
      saveMessages()
    } else {
      alert('文件上传失败：' + response.data)
    }
  } catch (error) {
    console.error('文件上传失败:', error)
    alert('文件上传失败，请重试')
  } finally {
    connectionStatus.value = 'disconnected'
  }
}

const removeFile = () => {
  uploadedFileName.value = ''
  addMessage('已移除上传的文件', true, 'user-question')
  saveMessages()
}

const sendMessage = () => {
  if (!inputMessage.value.trim()) return
  
  addMessage(inputMessage.value, true, 'user-question')
  saveMessages()
  
  const originalMessage = inputMessage.value
  inputMessage.value = ''
  
  if (eventSource.value) {
    eventSource.value.close()
  }
  
  const aiMessageIndex = messages.value.length
  addMessage('', false, 'ai-answer')
  
  connectionStatus.value = 'connecting'
  
  eventSource.value = chatWithPdf(originalMessage, chatId.value)
  
  eventSource.value.onmessage = (event) => {
    const data = event.data
    
    if (data && data !== '[DONE]') {
      if (aiMessageIndex < messages.value.length) {
        messages.value[aiMessageIndex].content += data
        saveMessages()
      }
    }
    
    if (data === '[DONE]') {
      if (aiMessageIndex < messages.value.length) {
        messages.value[aiMessageIndex].content = cleanMarkdown(messages.value[aiMessageIndex].content)
        messages.value[aiMessageIndex].type = 'ai-final'
        saveMessages()
      }
      
      connectionStatus.value = 'disconnected'
      eventSource.value.close()
    }
  }
  
  eventSource.value.onerror = (error) => {
    console.error('SSE Error:', error)
    if (aiMessageIndex < messages.value.length) {
      messages.value[aiMessageIndex].content = cleanMarkdown(messages.value[aiMessageIndex].content)
      messages.value[aiMessageIndex].type = 'ai-error'
      saveMessages()
    }
    connectionStatus.value = 'error'
    eventSource.value.close()
  }
}

onMounted(() => {
  loadConversations()
  
  const params = new URLSearchParams(window.location.search)
  const paramChatId = params.get('chatId')
  
  if (paramChatId && conversations.value.some(c => c.id === paramChatId)) {
    chatId.value = paramChatId
    loadMessages()
  } else {
    chatId.value = createNewChat()
  }
})

onBeforeUnmount(() => {
  if (eventSource.value) {
    eventSource.value.close()
  }
})
</script>

<style scoped>
.pdf-chat-container {
  display: flex;
  height: 100vh;
  background-color: #f5f7fa;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background-color: #fff;
}

.chat-header {
  display: flex;
  align-items: center;
  padding: 16px 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.back-btn {
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: white;
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
  margin-right: 16px;
  font-size: 14px;
  transition: background 0.3s;
}

.back-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

.chat-header h2 {
  flex: 1;
  margin: 0;
  font-size: 20px;
}

.chat-id {
  font-size: 12px;
  opacity: 0.8;
}

.file-upload-area {
  padding: 20px;
  background-color: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.upload-box {
  border: 2px dashed #cbd5e1;
  border-radius: 12px;
  padding: 40px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  background-color: white;
}

.upload-box:hover,
.upload-box.dragging {
  border-color: #667eea;
  background-color: #f0f4ff;
}

.file-input {
  display: none;
}

.upload-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.upload-text {
  font-size: 18px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 8px;
}

.upload-hint {
  font-size: 14px;
  color: #94a3b8;
}

.uploaded-file {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
  margin-top: 12px;
  background-color: #dcfce7;
  border-radius: 8px;
}

.file-icon {
  font-size: 18px;
  margin-right: 8px;
}

.file-name {
  font-size: 14px;
  color: #166534;
  font-weight: 500;
}

.remove-file-btn {
  margin-left: 12px;
  padding: 4px 12px;
  background-color: #ef4444;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background-color: #f8fafc;
}

.message-wrapper {
  display: flex;
  margin-bottom: 16px;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.user-message {
  justify-content: flex-end;
}

.ai-message {
  justify-content: flex-start;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 16px;
  display: flex;
  align-items: flex-start;
}

.user-message .message-content {
  background-color: #667eea;
  color: white;
  border-bottom-right-radius: 4px;
}

.ai-message .message-content {
  background-color: white;
  color: #1e293b;
  border-bottom-left-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.avatar {
  font-size: 20px;
  margin-right: 10px;
  flex-shrink: 0;
}

.user-message .avatar {
  margin-right: 0;
  margin-left: 10px;
}

.message-text {
  font-size: 15px;
  line-height: 1.6;
  word-break: break-word;
}

.chat-input-area {
  display: flex;
  padding: 16px 24px;
  background-color: white;
  border-top: 1px solid #e2e8f0;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
}

.message-input {
  flex: 1;
  padding: 12px 20px;
  border: 1px solid #e2e8f0;
  border-radius: 24px;
  font-size: 15px;
  outline: none;
  transition: border-color 0.3s;
}

.message-input:focus {
  border-color: #667eea;
}

.message-input:disabled {
  opacity: 0.5;
}

.send-btn {
  margin-left: 12px;
  padding: 12px 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 24px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.send-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.connection-status {
  padding: 8px 24px;
  text-align: center;
  font-size: 12px;
  background-color: #f8fafc;
}

.connection-status.connecting {
  color: #f59e0b;
}

.connection-status.connected {
  color: #10b981;
}

.connection-status.disconnected {
  color: #94a3b8;
}

.connection-status.error {
  color: #ef4444;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  animation: pulse 1.5s infinite;
}

.connection-status.connecting .status-dot {
  background-color: #f59e0b;
}

.connection-status.connected .status-dot {
  background-color: #10b981;
}

.connection-status.disconnected .status-dot {
  background-color: #94a3b8;
}

.connection-status.error .status-dot {
  background-color: #ef4444;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
</style>