# my-blog-backend

个人博客系统 —— 前后端分离 + Docker Compose 全链路部署。

- 在线体验：http://47.94.95.8
- 后端仓库（本仓库）：https://github.com/liushiqi-dev/my-blog-backend
- 前端仓库：https://github.com/liushiqi-dev/blog-frontend
- 接口文档：https://idcnwe6jg5.apifox.cn

## 技术栈

- **后端**：Java 17 · Spring Boot · Spring Security · MyBatis · Flyway
- **中间件**：MySQL 8.0 · Redis 7 · RabbitMQ
- **认证**：JWT（RS256 非对称签名）
- **前端**：Vue 3 · Vite · Element Plus（见前端仓库）
- **部署**：Docker · Docker Compose · Nginx

## 功能特性

- 用户注册 / 登录，BCrypt 密码加密存储，登录用 matches 校验
- JWT 无状态认证：私钥签名、过滤器公钥验签，身份还原至 SecurityContext
- 方法级权限控制：`@PreAuthorize("hasRole('ADMIN')")`，未登录返回 401 + 统一 JSON
- 文章 / 分类增删改查，草稿 / 发布状态管理
- 点赞：Redis + Lua 原子切换状态与计数，RabbitMQ 异步落库（手动 ACK + 幂等）
- 浏览量：Redis INCR 承载高频写，@Scheduled 定时 GETDEL 批量回写 MySQL
- 文章列表：Cache Aside 旁路缓存，随机过期时间防雪崩，SCAN 分批删除避免阻塞
- 统一 `Result` 返回结构，`GlobalExceptionHandler` 全局异常处理

## 仓库结构

```
├── blog-main/                # 后端源码（Spring Boot）
│   └── src/main/
│       ├── java/com/liushiqi/blogmain/
│       │   ├── controller    # 参数校验 + 调用 Service
│       │   ├── service       # 业务逻辑（impl 为实现类）
│       │   ├── mapper        # MyBatis 数据访问
│       │   ├── entity        # 数据库实体
│       │   ├── dto           # 请求/响应对象（request / response）
│       │   ├── vo            # 视图对象（面向前端的出参）
│       │   ├── config        # Security / Redis / RabbitMQ / Web 配置
│       │   ├── security      # JWT 过滤器与工具（RS256）
│       │   ├── mq            # 点赞消息与消费者
│       │   ├── task          # 浏览量定时落库任务
│       │   └── common        # Result 封装 / 异常处理 / Redis 工具
│       └── resources/
│           ├── db/migration  # Flyway 建表与迁移脚本
│           ├── scripts       # Lua 脚本（点赞原子操作）
│           └── mapper        # MyBatis XML
├── blog-frontend-dist/       # 前端编译产物 + Nginx 配置 + Dockerfile
└── docker-compose.yml        # 5 服务编排（mysql/redis/rabbitmq/backend/frontend）
```

## 快速开始（本地开发）

1. 准备 MySQL、Redis、RabbitMQ 环境
2. 复制 `application-dev.yaml.example` 为 `application-dev.yaml`，填入数据库与密钥配置
3. 启动：

```bash
cd blog-main
mvn spring-boot:run        # 默认 dev 配置，Flyway 自动建表
```

前端启动见前端仓库 README，通过 Vite 代理访问 `http://localhost:8080`。

## Docker Compose 部署

```bash
# 1. 后端打 jar（产物由 Dockerfile COPY 进镜像）
cd blog-main
mvn clean package -DskipTests

# 2. 前端编译产物放入 blog-frontend-dist/dist（见前端仓库）

# 3. 配置 .env（数据库密码、JWT 密钥等，已 gitignore）

# 4. 启动全部 5 个服务
cd ..
docker compose up -d --build
```

要点：

- `healthcheck` + `depends_on: service_healthy` 保证后端晚于 MySQL 就绪启动
- 敏感信息全部经 `.env` 环境变量注入，不入库
- `restart: always` 开机自启
