import axios from 'axios'

// 根据环境变量设置 API 基础 URL
const API_BASE_URL = process.env.NODE_ENV === 'production' 
 ? '/api' // 生产环境使用相对路径，适用于前后端部署在同一域名下
 : 'http://localhost:8123/api' // 开发环境指向本地后端服务

// 创建axios实例
const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
})

// 封装SSE连接
export const connectSSE = (url, params, onMessage, onError) => {
  // 构建带参数的URL
  const queryString = Object.keys(params)
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')
  
  const fullUrl = `${API_BASE_URL}${url}?${queryString}`
  
  // 创建EventSource
  const eventSource = new EventSource(fullUrl)
  
  eventSource.onmessage = event => {
    let data = event.data
    
    // 检查是否是特殊标记
    if (data === '[DONE]') {
      if (onMessage) onMessage('[DONE]')
    } else {
      // 处理普通消息
      if (onMessage) onMessage(data)
    }
  }
  
  eventSource.onerror = error => {
    if (onError) onError(error)
    eventSource.close()
  }
  
  // 返回eventSource实例，以便后续可以关闭连接
  return eventSource
}

// AI恋爱大师聊天
export const chatWithLoveApp = (message, chatId) => {
  return connectSSE('/ai/love_app/chat/sse', { message, chatId })
}

// AI超级智能体聊天
export const chatWithManus = (message, chatId) => {
  return connectSSE('/ai/manus/chat', { message, chatId })
}

// 公司规章制度问答
export const chatWithCompanyPolicy = (message, chatId) => {
  return connectSSE('/ai/company_policy/chat/sse', { message, chatId })
}

// PDF问答
export const chatWithPdf = (message, chatId) => {
  return connectSSE('/ai/pdf/chat/sse', { message, chatId })
}

// 上传PDF文件
export const uploadPdf = (chatId, file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/ai/pdf/upload/${chatId}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 检查是否有上传的文件
export const checkPdfFile = (chatId) => {
  return request.get(`/ai/pdf/has_file/${chatId}`)
}

export default {
  chatWithLoveApp,
  chatWithManus,
  chatWithCompanyPolicy,
  chatWithPdf,
  uploadPdf,
  checkPdfFile
} 