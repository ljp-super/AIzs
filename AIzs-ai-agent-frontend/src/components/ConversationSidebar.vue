<template>
  <div class="conversation-sidebar">
    <div class="sidebar-header">
      <h2 class="sidebar-title">对话</h2>
      <button class="new-chat-btn" @click="createNewChat">
        <span class="plus-icon">+</span>
        新对话
      </button>
    </div>
    
    <div class="conversation-list">
      <div 
        v-for="conv in conversations" 
        :key="conv.id"
        class="conversation-item"
        :class="{ active: conv.id === currentChatId }"
        @click="selectConversation(conv)"
      >
        <div class="conv-preview">
          <div class="conv-avatar">
            <span>{{ getAvatarChar(conv.agentType) }}</span>
          </div>
          <div class="conv-info">
            <div class="conv-name">{{ conv.name }}</div>
            <div class="conv-preview-text">{{ conv.preview }}</div>
          </div>
        </div>
        <button class="delete-btn" @click.stop="deleteConversation(conv.id)">
          <span>×</span>
        </button>
      </div>
    </div>
    
    <div v-if="conversations.length === 0" class="empty-state">
      <div class="empty-icon">💬</div>
      <div class="empty-text">暂无对话</div>
      <button class="empty-btn" @click="createNewChat">创建新对话</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'

const props = defineProps({
  currentChatId: {
    type: String,
    default: ''
  },
  agentType: {
    type: String,
    required: true
  },
  currentMessages: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['select-chat', 'new-chat'])

const conversations = ref([])
const STORAGE_KEY_PREFIX = 'ai_conversations_'

const loadConversations = () => {
  try {
    const key = STORAGE_KEY_PREFIX + props.agentType
    const saved = localStorage.getItem(key)
    if (saved) {
      conversations.value = JSON.parse(saved)
    }
  } catch (e) {
    console.error('加载对话列表失败:', e)
  }
}

const saveConversations = () => {
  try {
    const key = STORAGE_KEY_PREFIX + props.agentType
    localStorage.setItem(key, JSON.stringify(conversations.value))
  } catch (e) {
    console.error('保存对话列表失败:', e)
  }
}

const getAvatarChar = (type) => {
  const chars = {
    LOVE_APP: '恋',
    MANUS: '智',
    COMPANY_POLICY: '规',
    PDF_APP: '文'
  }
  return chars[type] || 'AI'
}

const createNewChat = () => {
  const newId = 'chat_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)
  const now = new Date()
  const newConv = {
    id: newId,
    name: getDefaultName(now),
    preview: '开始新对话',
    agentType: props.agentType,
    createdAt: now.getTime(),
    updatedAt: now.getTime()
  }
  conversations.value.unshift(newConv)
  saveConversations()
  emit('new-chat', newId)
}

const getDefaultName = (date) => {
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hour = date.getHours().toString().padStart(2, '0')
  const minute = date.getMinutes().toString().padStart(2, '0')
  const typeNames = {
    LOVE_APP: '恋爱大师',
    MANUS: '超级智能体',
    COMPANY_POLICY: '公司规章'
  }
  return `${typeNames[props.agentType]} ${month}/${day} ${hour}:${minute}`
}

const selectConversation = (conv) => {
  emit('select-chat', conv.id)
}

const deleteConversation = (id) => {
  if (confirm('确定要删除这个对话吗？')) {
    conversations.value = conversations.value.filter(c => c.id !== id)
    localStorage.removeItem('ai_' + props.agentType.toLowerCase() + '_chat_history_' + id)
    saveConversations()
    if (id === props.currentChatId && conversations.value.length > 0) {
      emit('select-chat', conversations.value[0].id)
    }
  }
}

const updateCurrentConversation = () => {
  if (!props.currentChatId || props.currentMessages.length === 0) return
  
  const conv = conversations.value.find(c => c.id === props.currentChatId)
  if (conv) {
    const lastMsg = props.currentMessages[props.currentMessages.length - 1]
    conv.preview = lastMsg.content.slice(0, 30) + (lastMsg.content.length > 30 ? '...' : '')
    conv.updatedAt = Date.now()
    
    const index = conversations.value.indexOf(conv)
    if (index > 0) {
      conversations.value.splice(index, 1)
      conversations.value.unshift(conv)
    }
    
    saveConversations()
  } else {
    const now = new Date()
    const lastMsg = props.currentMessages[props.currentMessages.length - 1]
    const newConv = {
      id: props.currentChatId,
      name: getDefaultName(now),
      preview: lastMsg.content.slice(0, 30) + (lastMsg.content.length > 30 ? '...' : ''),
      agentType: props.agentType,
      createdAt: now.getTime(),
      updatedAt: now.getTime()
    }
    conversations.value.unshift(newConv)
    saveConversations()
  }
}

watch(() => props.currentMessages.length, () => {
  updateCurrentConversation()
})

onMounted(() => {
  loadConversations()
})
</script>

<style scoped>
.conversation-sidebar {
  width: 280px;
  background-color: #f8f9fa;
  border-right: 1px solid #e0e0e0;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #e0e0e0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sidebar-title {
  font-size: 18px;
  font-weight: bold;
  margin: 0;
  color: #333;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background-color: #007bff;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.new-chat-btn:hover {
  background-color: #0069d9;
}

.plus-icon {
  font-size: 16px;
  font-weight: bold;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conversation-item {
  display: flex;
  align-items: center;
  padding: 12px;
  margin-bottom: 4px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s;
  position: relative;
}

.conversation-item:hover {
  background-color: #e9ecef;
}

.conversation-item.active {
  background-color: #007bff;
}

.conversation-item.active .conv-name,
.conversation-item.active .conv-preview-text {
  color: white;
}

.conv-preview {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.conv-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background-color: #e0e0e0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: bold;
  color: #666;
  flex-shrink: 0;
}

.conversation-item.active .conv-avatar {
  background-color: rgba(255, 255, 255, 0.3);
  color: white;
}

.conv-info {
  flex: 1;
  min-width: 0;
}

.conv-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-preview-text {
  font-size: 12px;
  color: #888;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.delete-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: none;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 18px;
  opacity: 0;
  transition: opacity 0.2s, background-color 0.2s;
  flex-shrink: 0;
}

.conversation-item:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  background-color: #dc3545;
  color: white;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-text {
  font-size: 16px;
  color: #888;
  margin-bottom: 20px;
}

.empty-btn {
  padding: 10px 24px;
  background-color: #007bff;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.empty-btn:hover {
  background-color: #0069d9;
}

@media (max-width: 768px) {
  .conversation-sidebar {
    width: 240px;
  }
  
  .conv-preview-text {
    display: none;
  }
}
</style>