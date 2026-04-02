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
    "message": "你的问题"
  }
  ```
- **响应**: 
  ```json
  {
    "message": "AI 回复",
    "timestamp": 1234567890000
  }
  ```

## 环境变量

| 变量名 | 描述 | 示例值 |
|-------|------|-------|
| DEEPSEEK_API_KEY | DeepSeek API 密钥 | sk-xxxxxxxxxxxxxxxxxxxxxxxx |

## 前端优化

- 图片懒加载
- CSS 变量提升可维护性
- JavaScript 代码优化
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