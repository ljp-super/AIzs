# AIzs - AI 超级智能体项目

基于 Spring AI + LangChain4j 构建的 AI 智能体应用，提供 AI 恋爱大师、公司政策问答、PDF 聊天等功能，支持 RAG 知识库、Tool Calling、MCP 服务和自主规划智能体。

## 功能特性

### AI 恋爱大师应用
- 多轮对话支持
- 对话记忆持久化
- RAG 知识库检索
- 工具调用与 MCP 服务集成
- 结构化输出（恋爱报告）

### AI 超级智能体 YuManus
- 基于 ReAct 模式的自主规划智能体
- 支持网页搜索、资源下载、PDF 生成等工具
- 自主推理和行动，直到完成目标

### 工具集
- 联网搜索（WebSearchTool）
- 文件操作（FileOperationTool）
- 网页抓取（WebScrapingTool）
- 资源下载（ResourceDownloadTool）
- 终端操作（TerminalOperationTool）
- PDF 生成（PDFGenerationTool）
- 任务终止（TerminateTool）

### MCP 服务
- 图片搜索 MCP 服务

## 技术栈

- **Java 17 + Spring Boot 3**
- **Spring AI 1.0.0** - AI 开发框架
- **LangChain4j** - AI 开发工具库
- **RAG** - 检索增强生成
- **PgVector** - 向量数据库
- **Tool Calling** - 工具调用
- **MCP** - 模型上下文协议
- **ReAct Agent** - 智能体构建
- **SSE** - 服务端事件推送
- **Vue 3 + Vite** - 前端框架

## 项目结构

```
AIzs/
├── src/main/java/com/yupi/yuaiagent/
│   ├── agent/          # 智能体核心模块
│   ├── app/            # 应用模块（恋爱大师、政策问答、PDF聊天）
│   ├── config/         # 配置类
│   ├── controller/     # REST API 控制器
│   ├── rag/            # RAG 知识库模块
│   ├── tools/          # 工具实现
│   ├── service/        # 业务服务
│   ├── memory/         # 记忆系统
│   └── ...
├── src/main/resources/
│   ├── document/       # 知识库文档
│   ├── application.yml # 应用配置
│   └── mcp-servers.json # MCP 服务器配置
├── AIzs-ai-agent-frontend/ # 前端项目
├── AIzs-image-search-mcp-server/ # 图片搜索 MCP 服务
└── data/               # 数据目录
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+ (前端)
- MySQL / H2 (数据库)

### 后端运行

```bash
# 进入项目目录
cd AIzs

# 编译运行
mvn spring-boot:run
```

后端服务启动后访问：http://localhost:8123/api/

### 前端运行

```bash
# 进入前端目录
cd AIzs-ai-agent-frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端服务启动后访问：http://localhost:3000/

### MCP 服务器运行

```bash
# 进入 MCP 服务器目录
cd AIzs-image-search-mcp-server

# 编译运行
mvn spring-boot:run
```

## 配置说明

### 数据库配置

在 `application.yml` 中配置数据源：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/yu_ai_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
```

### AI 模型配置

在 `application.yml` 中配置 AI 模型：

```yaml
spring:
  ai:
    dashscope:
      api-key: your_api_key
```

### MCP 服务配置

在 `mcp-servers.json` 中配置 MCP 服务器。

## API 接口

### 健康检查
```
GET /api/health
```

### AI 对话
```
POST /api/ai/chat
```

### RAG 知识库问答
```
POST /api/rag/query
```

## 核心模块

### 智能体模块
- `BaseAgent` - 智能体基类
- `ReActAgent` - ReAct 模式智能体
- `ToolCallAgent` - 工具调用智能体
- `YuManus` - 自主规划智能体

### RAG 模块
- `LoveAppDocumentLoader` - 文档加载器
- `MyTokenTextSplitter` - 文本分割器
- `MyKeywordEnricher` - 关键词增强器
- `MultiQueryExpander` - 多查询扩展器
- `Reranker` - 结果重排序器

### 工具模块
- 支持多种工具的注册和调用
- 工具选择器自动选择合适的工具

## 架构设计

项目采用分层架构设计：

1. **Controller 层** - REST API 入口
2. **Service 层** - 业务逻辑处理
3. **Agent 层** - 智能体核心逻辑
4. **Tool 层** - 工具调用层
5. **RAG 层** - 知识库检索层
6. **Memory 层** - 对话记忆管理

## 许可证

MIT License