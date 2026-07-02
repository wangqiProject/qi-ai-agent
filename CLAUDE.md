# CLAUDE.md

为 Claude Code (claude.ai/code) 提供仓库指引。

## 常用命令

```bash
# 编译
./mvnw clean compile

# 运行测试
./mvnw test

# 运行单个测试
./mvnw test -Dtest=QiAiAgentApplicationTests

# 启动应用
./mvnw spring-boot:run

# 指定 DeepSeek API Key 启动
DEEPSEEK_API_KEY=sk-xxx ./mvnw spring-boot:run
```

## 项目架构

Spring Boot 3.4.4 + JDK 17 聊天应用，通过 Spring AI 1.0.0 接入 DeepSeek 模型。

### 目录结构

```
src/main/java/com/qi/qiaiaagent/
├── QiAiAgentApplication.java          # @SpringBootApplication 启动类
├── chatmemory/
│   └── FileBasedChatMemory.java       # Kryo 文件持久化对话记忆（实现 ChatMemory 接口）
├── config/
│   ├── AppConfig.java                 # Bean 配置（FileBasedChatMemory）
│   └── CorsConfig.java                # 跨域配置（开发环境允许所有来源）
├── constant/
│   └── FileConstant.java              # 文件路径常量
└── controller/
    ├── ChatController.java            # /api/chat/sync + /api/chat/stream + /api/chat/history
    ├── ChatRequest.java               # 请求 DTO（message + sessionId）
    └── HealthController.java          # GET /api/health 健康检查

src/main/resources/
├── application.yml                    # DeepSeek 配置（API Key、模型参数）
└── static/                            # 嵌入式前端
    ├── index.html                     # 聊天界面（结构化消息 + Markdown 渲染）
    ├── css/style.css                  # 聊天气泡样式（Markdown 表格/代码/标题等）
    └── js/chat.js                     # 会话管理 + SSE 流式 + Markdown 渲染
```

### 核心设计

- **会话记忆**：前端生成 sessionId 存入 localStorage，每次请求携带；后端用 Kryo 序列化存储对话历史到 `tmp/chat-memory/{sessionId}.kryo`；每次对话将历史消息注入 Prompt 实现记忆
- **同步接口** (`POST /api/chat/sync`)：返回 `{"reply":"...", "sessionId":"..."}`
- **流式接口** (`POST /api/chat/stream`)：SSE 流式输出，使用 `Flux<ServerSentEvent<String>>`
- **历史接口** (`GET /api/chat/history?sessionId=xxx`)：返回 `[{role, content}, ...]` 用于页面刷新恢复
- **新建会话** (`POST /api/chat/session/new`)：返回新 `sessionId`
- **ChatRequest**：`record ChatRequest(String message, String sessionId)` 双字段 DTO
- **前端**：内嵌在 `static/` 目录，使用 `marked` 库渲染 Markdown；消息结构化展示（头像 + 角色标签 + 时间戳 + Markdown 正文）
- **API Key**：从 `DEEPSEEK_API_KEY` 环境变量读取，`application.yml` 中有占位回退值

### 依赖说明

- `spring-boot-starter-web` — 内嵌 Tomcat、REST 控制器
- `spring-ai-starter-model-deepseek` — Spring AI DeepSeek 自动配置
- `kryo 5.6.2` — 对话记忆文件序列化
- Lombok（可选，仅编译期）
- Spring AI 仓库：Spring Milestones、Spring Snapshots、Central Portal Snapshots
