# AIzs - 企业级 AI 智能助手平台

基于 Spring AI 构建的企业级 AI 智能助手平台，集成 RAG 知识库检索、ReAct Agent、PDF 问答、MCP 工具调用等能力；采用 DeepSeek + 阿里云 DashScope 双模型智能路由与降级，内置 AI 安全防护层与全链路可观测性，提供高质量、低成本、安全合规的 AI 服务。

## 核心特性

### AI 工程化能力

- **CRAG 自我纠错 RAG**：查询重写 → 混合检索（向量 + BM25）粗召回 → 阿里云 gte-rerank 二次精排；CRAG 模块对初步检索结果做置信度评估，低质量时自动触发查询重写与二次检索，解决单一检索对复杂问句召回不足的问题。
- **双模型智能路由与降级**：自研 `RoutingChatModel` 依据输入长度将请求路由至 DeepSeek（主力，长上下文）或 DashScope（轻量降本），`FallbackChatModel` 在主模型异常时自动切换备用模型，保障高并发与限流场景下的可用性。
- **AI 安全防护层**：基于 Spring AI Advisor 实现 `InputSanitizerAdvisor`，零侵入业务集成 Prompt Injection 检测、PII 敏感信息脱敏（手机号/邮箱/身份证）、敏感词过滤三道防线。
- **全链路可观测性与 RAG 评估**：AgentTrace 持久化记录 ReAct Agent 多步推理链路，TokenUsage 按会话统计 API 成本；搭建 RAG 评估数据集与自动评分服务，将问答质量量化为关键词命中率指标，支撑回归测试。
- **SSE 流式与用户取消**：SSE 心跳保活解决长连接超时断开，支持用户主动取消流式输出与活跃连接查询。
- **Redis 会话记忆持久化**：基于 Redis 的 ChatMemory 持久化，解决会话记忆重启丢失问题，Redis 不可用时自动降级内存。

### 应用能力

- **AI 恋爱大师**：多轮对话、RAG 知识库、工具调用、结构化输出（恋爱报告）
- **公司政策问答**：基于规章文档的 RAG 问答
- **PDF 聊天**：PDF 上传与基于文档内容的问答
- **YuManus 超级智能体**：基于 ReAct 模式的自主规划智能体，支持网页搜索、资源下载、PDF 生成等工具

## 技术栈

- **核心框架**：Spring Boot 3.4、Spring AI 1.0、Spring AI Alibaba、LangChain4j
- **AI 能力**：DeepSeek、阿里云 DashScope（通义千问 / text-embedding-v4 / gte-rerank）、MCP 协议
- **数据与存储**：MySQL、Redis、PGVector 向量数据库、Caffeine
- **工程能力**：Reactor（SSE 流式）、Spring Actuator、iText PDF、Hutool、Lombok
- **前端**：Vue 3 + Vite

## 项目结构

```
AIzs/
├── src/main/java/com/yupi/yuaiagent/
│   ├── advisor/       # Advisor 链（安全防护、日志）
│   ├── agent/         # ReAct 智能体（BaseAgent/YuManus）
│   ├── app/           # 应用（LoveApp/CompanyPolicyApp/PdfApp）
│   ├── config/        # 配置（模型路由/ChatMemory/SSE/数据源/向量库）
│   ├── controller/    # REST API
│   ├── entity/        # 实体（Conversation/TokenUsage）
│   ├── evaluation/    # RAG 评估服务
│   ├── mapper/        # 数据访问（AgentTrace/TokenUsage）
│   ├── rag/           # RAG 模块（CRAG/混合检索/重排序/查询重写）
│   ├── tools/         # 工具实现与注册
│   ├── trace/         # AgentTrace 与 TokenUsage 追踪
│   └── service/       # 业务服务
├── src/main/resources/
│   ├── document/                    # 知识库文档
│   ├── application.yml              # 应用配置（占位符）
│   ├── application-local.yml        # 本地配置（含 API Key，已 gitignore）
│   ├── rag-evaluation-dataset.json  # RAG 评估数据集
│   └── mcp-servers.json             # MCP 服务器配置
├── AIzs-ai-agent-frontend/          # 前端项目
└── AIzs-image-search-mcp-server/    # 图片搜索 MCP 服务
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8+ 与 Redis 5+（核心依赖）
- Node.js 18+（前端，可选）

### 配置 API Key

项目 API Key 已脱敏，需创建本地配置文件 `src/main/resources/application-local.yml`（已被 `.gitignore` 忽略，不会提交）：

```yaml
spring:
  ai:
    deepseek:
      api-key: your-deepseek-api-key
    dashscope:
      api-key: your-dashscope-api-key
search-api:
  api-key: your-serpapi-key
```

`spring.profiles.active: local` 启动时自动加载该文件覆盖占位符。

### 后端运行

```bash
mvn spring-boot:run
```

后端启动后访问：http://localhost:8123/api/

### 前端运行

```bash
cd AIzs-ai-agent-frontend
npm install
npm run dev
```

前端访问：http://localhost:3000/

## API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/ai/love_app/chat/sync` | GET | 恋爱大师同步对话 |
| `/api/ai/love_app/chat/sse` | GET | 恋爱大师 SSE 流式对话 |
| `/api/ai/love_app/chat/sse_emitter` | GET | 恋爱大师 SSE（含心跳保活） |
| `/api/ai/company_policy/chat/sync` | GET | 公司政策问答 |
| `/api/ai/pdf/chat/sse` | GET | PDF 文档问答（流式） |
| `/api/ai/pdf/upload/{chatId}` | POST | 上传 PDF 文件 |
| `/api/ai/manus/chat` | GET | YuManus 超级智能体（SSE） |
| `/api/ai/sse/cancel` | POST | 取消指定会话的 SSE 流 |
| `/api/ai/sse/active` | GET | 查询活跃 SSE 连接 |
| `/api/ai/rag-evaluation/run` | POST | 运行 RAG 评估 |
| `/api/actuator/health` | GET | 健康检查 |

## 核心模块

### RAG 模块（`rag/`）
- `CRAGService` - CRAG 自我纠错检索（质量评估 + 查询重写 + 二次检索）
- `HybridSearchService` - 向量 + BM25 混合检索
- `BM25Retriever` - BM25 关键词检索
- `Reranker` - 阿里云 gte-rerank 重排序
- `QueryRewriter` - 查询重写

### Agent 模块（`agent/`）
- `BaseAgent` - 智能体基类（多步推理循环）
- `ReActAgent` - ReAct 模式智能体
- `YuManus` - 自主规划超级智能体

### 配置模块（`config/`）
- `RoutingChatModel` - 双模型智能路由
- `FallbackChatModel` - 模型降级
- `RedisChatMemoryRepository` - Redis 会话记忆持久化
- `SseEmitterManager` - SSE 心跳保活与取消管理

### 安全模块（`advisor/`）
- `InputSanitizerAdvisor` - Prompt Injection 检测 + PII 脱敏 + 敏感词过滤

### 可观测性模块（`trace/`）
- `AgentTracer` - Agent 推理链路追踪
- `TokenUsageTracker` - Token 用量与成本统计

## 架构设计

项目采用分层架构：

1. **Controller 层** - REST API 与 SSE 流式入口
2. **App/Agent 层** - 应用编排与智能体推理
3. **Advisor 层** - 安全防护、记忆、日志等横切关注点
4. **RAG 层** - 检索增强生成管道
5. **Tool 层** - 工具调用与 MCP 集成
6. **Trace 层** - 可观测性与数据持久化

## 许可证

MIT License
