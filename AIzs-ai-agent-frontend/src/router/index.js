import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: {
      title: '首页 - AI超级智能体应用平台',
      description: 'AI超级智能体应用平台提供AI恋爱大师和AI超级智能体服务，满足您的各种AI对话需求'
    }
  },
  {
    path: '/love-master',
    name: 'LoveMaster',
    component: () => import('../views/LoveMaster.vue'),
    meta: {
      title: 'AI恋爱大师 - AI超级智能体应用平台',
      description: 'AI恋爱大师是AI超级智能体应用平台的专业情感顾问，帮你解答各种恋爱问题，提供情感建议'
    }
  },
  {
    path: '/super-agent',
    name: 'SuperAgent',
    component: () => import('../views/SuperAgent.vue'),
    meta: {
      title: 'AI超级智能体 - AI超级智能体应用平台',
      description: 'AI超级智能体是AI超级智能体应用平台的全能助手，能解答各类专业问题，提供精准建议和解决方案'
    }
  },
  {
    path: '/company-policy',
    name: 'CompanyPolicy',
    component: () => import('../views/CompanyPolicy.vue'),
    meta: {
      title: '公司规章制度问答 - AI超级智能体应用平台',
      description: '公司规章制度智能问答助手，快速查询请假制度、考勤规定等公司政策'
    }
  },
  {
    path: '/pdf-chat',
    name: 'PdfChat',
    component: () => import('../views/PdfChat.vue'),
    meta: {
      title: 'PDF文档问答 - AI超级智能体应用平台',
      description: 'PDF文档问答助手，上传PDF文件后可进行智能问答'
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局导航守卫，设置文档标题
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title
  }
  next()
})

export default router 