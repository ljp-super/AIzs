<template>
  <div class="company-policy-container">
    <div class="main-layout">
      <ConversationSidebar 
        :current-chat-id="chatId"
        agent-type="COMPANY_POLICY"
        :current-messages="messages"
        @select-chat="loadConversation"
        @new-chat="createNewChat"
      />
      
      <div class="chat-content">
        <div class="header">
          <div class="back-button" @click="goBack">返回</div>
          <h1 class="title">公司规章制度问答</h1>
          <div class="clear-chat" @click="clearChat">清除记录</div>
        </div>
        
        <div class="content-wrapper">
          <div class="chat-area">
            <ChatRoom 
              :messages="messages" 
              :connection-status="connectionStatus"
              ai-type="company"
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
import { chatWithCompanyPolicy } from '../api'

useHead({
  title: '公司规章制度问答 - AI超级智能体应用平台',
  meta: [
    {
      name: 'description',
      content: '公司规章制度智能问答助手，快速查询请假制度、考勤规定等公司政策'
    },
    {
      name: 'keywords',
      content: '公司规章制度,企业政策,请假制度,考勤制度,AI问答,智能助手'
    }
  ]
})

const router = useRouter()
const messages = ref([])
const connectionStatus = ref('disconnected')
let eventSource = null
const STORAGE_KEY_PREFIX = 'ai_company_policy_chat_history_'
const chatId = ref(localStorage.getItem('ai_company_policy_chat_id') || 'chat_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9))

const addMessage = (content, isUser, type = '') => {
  messages.value.push({
    content,
    isUser,
    type,
    time: new Date().getTime()
  })
  saveMessages()
}

const saveMessages = () => {
  try {
    localStorage.setItem(STORAGE_KEY_PREFIX + chatId.value, JSON.stringify(messages.value))
  } catch (e) {
    console.error('保存聊天记录失败:', e)
  }
}

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

const clearChat = () => {
  if (confirm('确定要清除所有聊天记录吗？')) {
    messages.value = []
    localStorage.removeItem(STORAGE_KEY_PREFIX + chatId.value)
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

const sendMessage = (message) => {
  addMessage(message, true, 'user-question')
  
  if (eventSource) {
    eventSource.close()
  }
  
  const aiMessageIndex = messages.value.length
  addMessage('', false, 'ai-answer')
  
  connectionStatus.value = 'connecting'
  
  eventSource = chatWithCompanyPolicy(message, chatId.value)
  
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
    connectionStatus.value = 'error'
    eventSource.close()
    
    if (aiMessageIndex < messages.value.length) {
      messages.value[aiMessageIndex].content = cleanMarkdown(messages.value[aiMessageIndex].content)
      messages.value[aiMessageIndex].type = 'ai-error'
      saveMessages()
    }
  }
}

const loadConversation = (newChatId) => {
  if (eventSource) {
    eventSource.close()
  }
  chatId.value = newChatId
  messages.value = []
  loadMessages()
  if (messages.value.length === 0) {
    addMessage('你好，我是公司规章制度智能问答助手。请问你想了解哪些公司政策？比如：请假制度、考勤规定、加班政策等。', false)
  }
}

const createNewChat = (newChatId) => {
  if (eventSource) {
    eventSource.close()
  }
  chatId.value = newChatId
  messages.value = []
  addMessage('你好，我是公司规章制度智能问答助手。请问你想了解哪些公司政策？比如：请假制度、考勤规定、加班政策等。', false)
}

const goBack = () => {
  router.push('/')
}

onMounted(() => {
  loadMessages()
  if (messages.value.length === 0) {
    addMessage('你好，我是公司规章制度智能问答助手。请问你想了解哪些公司政策？比如：请假制度、考勤规定、加班政策等。', false)
  }
})

onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
  }
  localStorage.setItem('ai_company_policy_chat_id', chatId.value)
})
</script>

<style scoped>
.company-policy-container {
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
  background-color: #1976d2;
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
