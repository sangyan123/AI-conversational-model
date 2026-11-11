# AI 对话助手

一个基于 Spring Boot 和 Vue 3 开发的 AI 对话助手应用，支持与 AI 进行实时聊天，具有良好的用户体验和响应式设计。

## 技术栈

### 后端
- Spring Boot 3.0+
- Maven 3.8+
- Java 17+
- DeepSeek API

### 前端
- Vue 3
- Vite
- JavaScript
- CSS3

## 项目结构

```
├── aichat-back/         # 后端项目
│   ├── src/              # 源代码
│   ├── pom.xml          # Maven 配置文件
│   └── ...
├── aichat-front/        # 前端项目
│   ├── aichat-front/     # 前端源码
│   │   ├── src/          # 源代码
│   │   ├── package.json  # 依赖配置
│   │   └── ...
│   └── ...
└── README.md            # 项目说明文件
```

## 功能特性

- 实时 AI 对话
- 多轮上下文记忆（前端携带最近 20 轮历史）
- 请求超时保护（前端 65s / 后端读取 60s），失败返回明确错误码
- 可选令牌鉴权 + 按 IP 限流
- 响应式设计，支持移动端、平板和桌面端
- 消息历史记录
- 加载状态动画
- 连接状态显示
- 平滑的动画效果
- 跨浏览器兼容性

## 安装与运行

### 后端

1. 进入后端目录
   ```bash
   cd aichat-back
   ```

2. 配置 API 密钥
   - 在系统环境变量中设置 `DEEPSEEK_API_KEY` 为你的 DeepSeek API 密钥

3. 构建项目
   ```bash
   mvn clean package
   ```

4. 运行项目
   ```bash
   mvn spring-boot:run
   ```

   后端服务默认运行在 `http://localhost:8080`

### 前端

1. 进入前端目录
   ```bash
   cd aichat-front/aichat-front
   ```

2. 安装依赖
   ```bash
   npm install
   ```

3. 运行开发服务器
   ```bash
   npm run dev
   ```

   前端应用默认运行在 `http://localhost:5173`

## API 接口

### 健康检查
- **URL**: `/api/chat/health`
- **方法**: GET
- **描述**: 检查后端服务是否正常运行

### 聊天接口
- **URL**: `/api/chat`
- **方法**: POST
- **请求体**: 
  ```json
  {
    "message": "你的问题",
    "history": [
      { "role": "user", "content": "上一轮用户输入" },
      { "role": "assistant", "content": "上一轮 AI 回复" }
    ]
  }
  ```
  `history` 可选，用于多轮上下文，最多 50 条；不传则为单轮问答。
- **响应**: 
  ```json
  {
    "message": "AI 回复",
    "timestamp": 1234567890000
  }
  ```
- **错误响应**: 失败时返回对应 HTTP 状态码和结构化错误体
  ```json
  {
    "success": false,
    "message": "AI 服务响应超时，请稍后重试。",
    "error": "Gateway Timeout",
    "status": 504,
    "timestamp": 1234567890000
  }
  ```

| 状态码 | 含义 |
|-------|------|
| 400 | 请求参数校验失败（消息为空、历史超 50 条等） |
| 401 | 已启用令牌鉴权但未携带或携带了错误令牌 |
| 429 | 触发本地限流，或上游 AI 服务限流 |
| 502 | 上游鉴权失败或返回异常 |
| 503 | 未配置 `DEEPSEEK_API_KEY` |
| 504 | 调用上游超时或网络不可达 |

## 环境变量

### 后端

| 变量名 | 描述 | 示例值 |
|-------|------|-------|
| DEEPSEEK_API_KEY | DeepSeek API 密钥（必填） | sk-xxxxxxxxxxxxxxxxxxxxxxxx |
| AICHAT_AUTH_TOKEN | 可选。设置后启用令牌鉴权，请求需带 `Authorization: Bearer <token>` | my-secret-token |

### 前端

| 变量名 | 描述 |
|-------|------|
| VITE_AICHAT_AUTH_TOKEN | 可选。后端启用 `AICHAT_AUTH_TOKEN` 时，这里填同样的值 |
| VITE_API_BASE_URL | 可选。后端地址，如 `https://api.example.com`。留空则用相对路径 `/api/chat` |
| VITE_DEV_API_TARGET | 仅开发期。Vite 代理目标，默认 `http://localhost:8080` |

写在 `aichat-front/aichat-front/.env.local` 中即可（该文件已被 git 忽略）。

## 前后端连接方式

前端默认用**相对路径** `/api/chat`，不写死后端地址：

- **开发环境**：Vite 把 `/api` 代理到 `VITE_DEV_API_TARGET`（默认 `http://localhost:8080`）。
  浏览器看到的是同源请求，因此开发时**不需要 CORS**。
- **生产环境**：需要反向代理把 `/api` 转发到后端（如 Nginx），
  或显式设置 `VITE_API_BASE_URL` 指向后端地址。

Nginx 反向代理示例：

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

若前后端分别部署在不同域名下，后端仍保留 CORS 白名单配置（`cors.allowed-origin-patterns`）。

## 鉴权与限流

- **令牌鉴权**：默认关闭，保持本地开发开箱即用。设置 `AICHAT_AUTH_TOKEN` 后启用，
  健康检查接口始终放行。这解决的是"谁能调用我的后端"，不是多用户体系。
- **限流**：默认开启，按 IP 每 60 秒 20 次，保护 DeepSeek 额度。
  可在 `application.yml` 的 `ratelimit` 下调整或关闭。

> 注意：当前限流是「每实例」计数。多实例部署若需要全局配额，应改用 Redis 计数器。

## 测试

```bash
cd aichat-back
mvn test
```

覆盖：多轮历史组装顺序、角色白名单过滤、上游异常映射（401/429/5xx/空结果）、
参数校验、限流与鉴权的过滤器行为。

## 前端优化

- 内联 SVG 图标，不依赖外部 CDN，离线可用
- CSS 变量提升可维护性
- 响应式布局
- 平滑动画效果
- 跨浏览器兼容性

## 浏览器支持

- Chrome (最新版)
- Firefox (最新版)
- Safari (最新版)
- Edge (最新版)

## 许可证

MIT

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

如有问题或建议，请联系项目维护者。
