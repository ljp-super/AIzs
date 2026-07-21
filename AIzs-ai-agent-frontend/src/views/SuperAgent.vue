<template>
  <div class="super-agent-container">
    <div class="main-layout">
      <ConversationSidebar 
        :current-chat-id="chatId"
        agent-type="MANUS"
        :current-messages="messages"
        @select-chat="loadConversation"
        @new-chat="createNewChat"
      />
      
      <div class="chat-content">
        <div class="header">
          <div class="back-button" @click="goBack">返回</div>
          <h1 class="title">AI超级智能体</h1>
          <div class="clear-chat" @click="clearChat">清除记录</div>
        </div>
        
        <div class="content-wrapper">
          <div class="chat-area">
            <ChatRoom 
              :messages="messages" 
              :connection-status="connectionStatus"
              ai-type="super"
              @send-message="sendMessage"
            />
          </div>
        </div>
        
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import ConversationSidebar from '../components/ConversationSidebar.vue'
import { chatWithManus } from '../api'

// 设置页面标题和元数据
useHead({
  title: 'AI超级智能体 - AI超级智能体应用平台',
  meta: [
    {
      name: 'description',
      content: 'AI超级智能体是AI超级智能体应用平台的全能助手，能解答各类专业问题，提供精准建议和解决方案'
    },
    {
      name: 'keywords',
      content: 'AI超级智能体,智能助手,专业问答,AI问答,专业建议,AI智能体'
    }
  ]
})

const router = useRouter()
const messages = ref([])
const connectionStatus = ref('disconnected')
let eventSource = null
const STORAGE_KEY_PREFIX = 'ai_super_agent_chat_history_'
const chatId = ref(localStorage.getItem('ai_super_agent_chat_id') || 'chat_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9))

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
  saveMessages()
}

// 保存消息到localStorage
const saveMessages = () => {
  try {
    localStorage.setItem(STORAGE_KEY_PREFIX + chatId.value, JSON.stringify(messages.value))
  } catch (e) {
    console.error('保存聊天记录失败:', e)
  }
}

// 从localStorage加载消息
const loadMessages = () => {
  try {
    const saved = localStorage.getItem(STORAGE_KEY_PREFIX + chatId.value)
    if (saved) {
      messages.value = JSON.parse(saved)
    }
  } catch (e) {
    console.error('加载聊天记录失败:', e)
  }
}

// 清除聊天记录
const clearChat = () => {
  if (confirm('确定要清除所有聊天记录吗？')) {
    messages.value = []
    localStorage.removeItem(STORAGE_KEY_PREFIX + chatId.value)
  }
}

const sendMessage = (message) => {
  addMessage(message, true, 'user-question')
  
  if (eventSource) {
    eventSource.close()
  }
  
  const aiMessageIndex = messages.value.length
  addMessage('', false, 'ai-answer')
  
  connectionStatus.value = 'connecting'
  
  eventSource = chatWithManus(message, chatId.value)
  
  eventSource.onmessage = (event) => {
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
      eventSource.close()
    }
  }
  
  eventSource.onerror = (error) => {
    console.error('SSE Error:', error)
    if (aiMessageIndex < messages.value.length) {
      messages.value[aiMessageIndex].content = cleanMarkdown(messages.value[aiMessageIndex].content)
      messages.value[aiMessageIndex].type = 'ai-error'
      saveMessages()
    }
    connectionStatus.value = 'error'
    eventSource.close()
  }
}

// 加载对话
const loadConversation = (newChatId) => {
  if (eventSource) {
    eventSource.close()
  }
  chatId.value = newChatId
  messages.value = []
  loadMessages()
  if (messages.value.length === 0) {
    addMessage('你好，我是AI超级智能体。我可以解答各类问题，提供专业建议，请问有什么可以帮助你的吗？', false)
  }
}

// 创建新对话
const createNewChat = (newChatId) => {
  if (eventSource) {
    eventSource.close()
  }
  chatId.value = newChatId
  messages.value = []
  addMessage('你好，我是AI超级智能体。我可以解答各类问题，提供专业建议，请问有什么可以帮助你的吗？', false)
}

// 返回主页
const goBack = () => {
  router.push('/')
}

// 页面加载时加载聊天记录
onMounted(() => {
  loadMessages()
  if (messages.value.length === 0) {
    addMessage('你好，我是AI超级智能体。我可以解答各类问题，提供专业建议，请问有什么可以帮助你的吗？', false)
  }
})

// 组件销毁前关闭SSE连接
onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
  }
  localStorage.setItem('ai_super_agent_chat_id', chatId.value)
})
</script>

<style scoped>
.super-agent-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: #f9fbff;
}

.main-layout {
  display: flex;
  flex: 1;
  min-height: calc(100vh - 56px);
}

.chat-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 16px 24px;
  background-color: #3f51b5;
  color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 10;
}

.back-button {
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: opacity 0.2s;
  justify-self: start;
}

.back-button:hover {
  opacity: 0.8;
}

.back-button:before {
  content: '←';
  margin-right: 8px;
}

.title {
  font-size: 20px;
  font-weight: bold;
  margin: 0;
  text-align: center;
  justify-self: center;
}

.clear-chat {
  font-size: 14px;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.8);
  justify-self: end;
  padding: 4px 12px;
  border-radius: 4px;
  transition: all 0.2s;
}

.clear-chat:hover {
  color: white;
  background-color: rgba(255, 255, 255, 0.2);
}

.content-wrapper {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.chat-area {
  flex: 1;
  padding: 16px;
  overflow: hidden;
  position: relative;
  min-height: calc(100vh - 56px - 180px);
  margin-bottom: 16px;
}

.footer-container {
  margin-top: auto;
}

@media (max-width: 768px) {
  .main-layout {
    flex-direction: column;
  }
  
  .header {
    padding: 12px 16px;
  }
  
  .title {
    font-size: 18px;
  }
  
  .chat-area {
    padding: 12px;
    min-height: calc(100vh - 48px - 160px);
    margin-bottom: 12px;
  }
}

@media (max-width: 480px) {
  .header {
    padding: 10px 12px;
  }
  
  .back-button {
    font-size: 14px;
  }
  
  .title {
    font-size: 16px;
  }
  
  .chat-area {
    padding: 8px;
    min-height: calc(100vh - 42px - 150px);
    margin-bottom: 8px;
  }
}
</style> 