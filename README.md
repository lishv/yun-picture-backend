# ☁️ 绘灵云图片协作平台后端（DDD 架构版）

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.9-blue.svg)](https://baomidou.com/)
[![Sa-Token](https://img.shields.io/badge/Sa--Token-1.39.0-green.svg)](https://sa-token.com/)
[![Disruptor](https://img.shields.io/badge/LMAX%20Disruptor-3.4.2-red.svg)](https://lmax-exchange.github.io/disruptor/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-4.4+-FF6600.svg)](https://www.rabbitmq.com/)

一款面向个人与企业团队的高性能、现代化云端图片管理与协同创作平台后端服务。本项目采用 **DDD（领域驱动设计，Domain-Driven Design）** 四层架构思想重构，结合 **WebSocket 协同同步**、**LMAX Disruptor 环形并发队列**、**RabbitMQ 异步文件清理**、**二级缓存** 与 **AI 图像扩展**，具备高并发、高内聚、易扩展的企业级生产规范。http://www.huilingyun.asia/

---

## 📸 系统效果展示

### 1. 系统主页面与瀑布流画廊
支持基于颜色主色调、标签、分类的多维度检索，集成虚拟滚动与多端响应式瀑布流展示：
![系统主页面](./sql/主页面.png)

### 2. 多人实时协同编辑（团队空间）
支持团队空间内多协作者实时抢锁编辑、缩放/旋转动作毫秒级同步广播、状态全量回放与原子保存更新：
![协同编辑](./sql/协同编辑.gif)

### 3. AI绘图
支持根据用户要求重新绘制图像:

<img width="1091" height="569" alt="image" src="https://github.com/user-attachments/assets/e4541e9a-0a87-46d3-a805-f3cb6d3f1c8c" />


---

## 🌟 核心特色与技术亮点

### 1. 🏛️ DDD 领域驱动设计架构
工程打破传统三层 MVC 的贫血模型结构，按 DDD 战略与战术设计原则组织各领域边界：
- **用户接口层（Interfaces）**：极简薄控制器，专注于 HTTP 请求路由、参数映射、Swagger/Knife4j 展现与权限校验。
- **应用服务层（Application）**：负责跨聚合的业务用例编排（Use Case Orchestration），在领域事务提交完成后统筹外部通信、消息推送等副作用。
- **领域核心层（Domain）**：封装纯粹的业务实体（Entity）、值对象（Value Object）、聚合根与领域服务（Domain Service），维护核心商业逻辑与存储配额状态不变性。
- **基础设施层（Infrastructure）**：实现仓储接口（Repository Impl）、对象存储（腾讯云 COS）、AI 扩展接入（阿里云通义 AI、百度以图搜图等外部能力）。
- **共享与横切上下文（Shared）**：集中管理 Sa-Token 权限模型、WebSocket 会话与 Disruptor 环形缓冲区。

### 2. ⚡ 高性能多人协同编辑体系
- **WebSocket 状态机**：维护客户端握手鉴权、房间加入、抢锁编辑（`ENTER_EDIT`）、动作实时广播（`EDIT_ACTION`）、释放锁（`EXIT_EDIT`）与编辑完成（`COMPLETE_EDIT`）全生命周期。
- **LMAX Disruptor 环形缓冲区**：引入无锁 RingBuffer 消息队列对高频协同编辑事件进行异步削峰解耦，保证单图片内协同操作的时序确定性与极低延迟。
- **状态快照与后来者追赶**：维护图片维度的操作历史栈，新进入房间的协作者通过 `SYNC_STATE` 瞬间追平当前画布最新形态。

### 3. 🛡️ 细粒度空间权限控制体系
- 区分**公共图库**、**私有空间**与**团队空间**三大边界。
- 集成 **Sa-Token** 实现自定义编程式与注解式鉴权（`@SaSpaceCheckPermission`）。
- 团队空间支持空间管理员、编辑者、查看者多角色细粒度 RBAC 鉴权模型，拦截非法入侵与越权操作。

### 4. 🚀 二级缓存与高效列表查询
- **本地缓存（Caffeine）**：缓存热点公共图片查询结果，降低访问延迟。
- **分布式缓存（Redis）**：维护分布式 Session、图片列表缓存、邮箱验证码与缓存未命中限流计数。
- **复合游标分页**：使用 `createTime + id` 作为游标，支持首页瀑布流稳定加载。

### 5. 📨 RabbitMQ 异步清理 COS 图片文件
- 图片软删除、空间额度恢复与本地清理任务写入在同一个 MySQL 事务内完成。
- 事务提交后异步发送 `taskId`，通过发布确认、定时补偿、幂等消费、30 分钟延迟重试和死信队列处理异常。
- 最终失败任务记录错误原因，并提供超级管理员人工处理入口。

### 6. 🎨 智能 AI 扩展与图像检索能力
- **AI 智能扩图**：集成阿里云通义千问 AI 图像生成能力，支持边界智能外扩与重绘。
- **以图搜图与色彩检索**：结合主色调聚类算法与图搜图搜索引擎，支持基于色彩相似度算法和特征图像检索。

### 7. ✉️ 邮箱验证码
- 支持邮箱验证码注册和通过邮箱重置密码。
- 验证码存储于 Redis，5 分钟有效；同一邮箱、同一业务场景设置 60 秒发送冷却。

---

## 🏗️ 整体技术选型

| 技术 | 说明 | 官网 / 依赖版本 |
| :--- | :--- | :--- |
| **Spring Boot** | 核心脚手架框架 | 2.7.6 |
| **Java** | 运行环境 | JDK 17 LTS |
| **MyBatis-Plus** | ORM 数据持久层 | 3.5.9 |
| **Sa-Token** | 轻量级权限认证与 Session 共享 | 1.39.0 |
| **LMAX Disruptor** | 高性能无锁环形队列 | 3.4.2 |
| **RabbitMQ / Spring AMQP** | 图片文件异步清理、延迟重试与死信 | RabbitMQ 4.4+ / Spring AMQP 2.7.6 |
| **Caffeine / Redis**| 本地与分布式二级缓存、Session、验证码 | Caffeine 3.1.8 / Redis 6.0+ |
| **Spring Mail** | 邮箱验证码发送 | Spring Boot 依赖管理 |
| **Knife4j** | 接口文档管理与交互调试 | 4.4.0 (OpenAPI2) |
| **Tencent COS** | 云端对象存储与 WebP 压缩管道 | cos_api 5.6.227 |
| **Aliyun AI** | AI 智能扩图大模型服务 | HTTP 异步任务拉取 |

---

## 📂 工程目录结构

```text
yun-picture-backend-ddd
├── sql/                                  # 数据库 DDL 脚本与展示素材
│   ├── create_table.sql                  # 初始化建表脚本
│   ├── 主页面.png                         # 系统首页效果截图
│   └── 协同编辑.gif                       # 团队协同编辑动图
├── src/main/java/com/xiaoli/yunpicture
│   ├── application/                      # 【应用服务层】用例编排与跨领域协调
│   │   └── service/                      # ApplicationService 接口与实现
│   ├── domain/                           # 【领域核心层】业务规则、实体与仓储契约
│   │   ├── user/                         # 用户聚合（User、UserRoleEnum、UserRepository）
│   │   ├── picture/                      # 图片聚合（Picture、PictureDomainService）
│   │   └── space/                        # 空间聚合（Space、SpaceUser、SpaceTypeEnum）
│   ├── infrastructure/                   # 【基础设施层】技术底座与外部集成
│   │   ├── api/                          # COS 管理器、阿里云 AI、以图搜图接入
│   │   ├── config/                       # MyBatis-Plus、CORS、Json 配置
│   │   ├── manager/                      # 图片上传模板与邮件管理
│   │   ├── messaging/picture/            # RabbitMQ 图片清理、重试、补偿与死信
│   │   ├── mapper/                       # MyBatis 数据库映射器
│   │   ├── repository/                   # 领域仓储持久化实现
│   │   └── exception/                    # 全局业务异常体系与错误码
│   ├── interfaces/                       # 【用户接口层】对外 HTTP 控制器与 VO/DTO 组装
│   │   ├── controller/                   # UserController, PictureController 等
│   │   ├── dto/                          # 请求数据传输对象（Request DTO）
│   │   ├── vo/                           # 展现层响应对象（View Object）
│   │   └── assembler/                    # Entity / DTO / VO 装配转换器
│   ├── shared/                           # 【共享基础设施与通用服务】
│   │   ├── auth/                         # Sa-Token 自定义权限体系
│   │   └── websocket/                    # 协同编辑处理器与 Disruptor 环形无锁队列
│   └── YunPictureBackendApplication.java # Spring Boot 启动入口
└── pom.xml                               # 项目 Maven 构建依赖定义
```

---

## 🚀 快速启动指南

### 1. 基础环境准备
- **Java**: 17 或以上
- **MySQL**: 8.0 或以上
- **Redis**: 6.0 或以上
- **RabbitMQ**: 4.4 或以上
- **Maven**: 3.8 或以上

### 2. 初始化数据库
在本地或服务器 MySQL 执行工程下初始化脚本：
```bash
mysql -u root -p < sql/create_table.sql
```

### 3. 修改核心配置
打开 `src/main/resources/application.yml`（或对应环境配置），配置以下核心参数：
- **MySQL 数据库连接**：`url`、`username`、`password`
- **Redis 连接信息**：`host`、`port`、`password`
- **RabbitMQ**：`host`、`port`、`username`、`password`、`virtual-host`
- **腾讯云对象存储（COS）**：`secret-id`、`secret-key`、`bucket`、`region`、`host`
- **邮件服务**：`spring.mail` 下的 SMTP 地址、账号与授权码
- **阿里云 AI 扩图**：`apiKey`、`apiHost`

RabbitMQ 默认虚拟主机为 `/yun-picture`，Java 客户端端口为 `5672`。当前 30 分钟延迟重试使用 RabbitMQ 4.4 quorum queue 的 `x-opt-delivery-time` 能力。

### 4. 启动后端项目
- **在 IDE 中运行**：运行主启动类 `com.xiaoli.yunpicture.YunPictureBackendApplication`，并指定程序参数：
  ```text
  --spring.profiles.active=local
  ```
- **命令行 Maven 打包与运行**：
  ```bash
  mvn clean package -DskipTests
  java -jar target/yun-picture-backend-ddd-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
  ```

服务器部署时将 `local` 改为 `prod`。

### 5. 接口调试文档
项目启动后，访问 Knife4j 在线交互式文档进行接口测试：
- **Swagger / Knife4j 访问地址**：`http://localhost:8123/api/doc.html`
- **健康检查地址**：`http://localhost:8123/api/health`
- **WebSocket 协同编辑地址**：`ws://localhost:8123/api/ws/picture/edit?pictureId=图片ID`

---

## 🧹 图片文件清理任务状态

| 状态 | 含义 |
| :---: | :--- |
| 0 | 待发送，初始 MQ 发送失败时由定时任务补偿 |
| 1 | 已发送至正常队列，正在处理 |
| 2 | 首轮处理失败，等待 30 分钟延迟重试 |
| 3 | COS 文件清理完成 |
| 4 | 延迟重试后仍失败，等待管理员处理 |
| 5 | 管理员已确认人工处理完成 |

超级管理员可通过 `/api/picture/cleanup/failures/page` 查询失败任务，并在实际处理 COS 文件后调用 `/api/picture/cleanup/failures/resolve` 标记完成。
