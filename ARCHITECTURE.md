# ARCHITECTURE.md
# 系统架构地图 — JeecgBoot 二次开发项目
#
# 用途：AI 每次会话先读本文件定位代码，避免全量扫描。
# 维护：每次新增模块后更新对应章节。

---

## 一、整体架构

```
┌─────────────────────────────────────────────────────┐
│                    前端（jeecgboot-vue3）              │
│   Vue3 + Vite + Ant Design Vue + Pinia               │
│   开发端口：3100   代理后端：/jeecgboot/api → :8080   │
└───────────────────┬─────────────────────────────────┘
                    │ HTTP / WebSocket
┌───────────────────▼─────────────────────────────────┐
│                   后端（jeecg-boot）                  │
│   Spring Boot 单体模式  端口：8080                    │
│   JWT 鉴权 + Shiro 权限 + MyBatis-Plus ORM           │
└───────────────────┬─────────────────────────────────┘
                    │
        ┌───────────┴──────────┐
        ▼                      ▼
   MySQL / PostgreSQL        Redis
   业务数据持久化              会话缓存 / 字典缓存
```

---

## 二、后端 Maven 模块结构

```
jeecg-boot/
├── jeecg-boot-base-core/          # 公共基础层（被所有模块依赖）
│   └── org.jeecg.common/
│       ├── api/                   # 公共接口定义（VO/DTO）
│       ├── aspect/                # AOP 切面（日志、权限注解）
│       ├── constant/              # 全局常量 & 枚举
│       ├── exception/             # 全局异常处理
│       ├── system/base/           # 基类 Controller/Service/Entity
│       └── util/                  # 工具类（加密/OSS/SQL解析等）
│
├── jeecg-module-system/           # 系统核心模块
│   ├── jeecg-system-api/          # 接口层（供其他模块调用）
│   │   ├── jeecg-system-local-api/   # 本地调用接口
│   │   └── jeecg-system-cloud-api/   # 微服务调用接口
│   ├── jeecg-system-biz/          # 业务实现层（主要开发区域）
│   │   └── org.jeecg.modules/
│   │       ├── system/            # 核心系统功能（用户/角色/菜单/权限）
│   │       ├── message/           # 消息中心（WebSocket 实时推送）
│   │       ├── monitor/           # 服务监控
│   │       ├── oss/               # 文件存储（OSS）
│   │       ├── quartz/            # 定时任务
│   │       ├── openapi/           # 对外开放接口
│   │       ├── airag/             # AI RAG 功能
│   │       ├── cas/               # CAS 单点登录
│   │       └── ngalain/           # Ng-Alain 适配
│   └── jeecg-system-start/        # 启动入口（application.yml 在这里）
│
├── jeecg-boot-module/             # 扩展业务模块
│   ├── jeecg-module-demo/         # 示例代码（新功能参考此模块写法）
│   └── jeecg-boot-module-airag/   # AI RAG 独立模块
│
└── jeecg-server-cloud/            # 微服务模式（单体模式不用关注）
    ├── jeecg-cloud-gateway/
    └── jeecg-visual/
```

---

## 三、后端核心分层（以 system 模块为例）

```
org.jeecg.modules.system/
├── controller/        # 接口层：接收请求，调 Service，返回 Result
├── service/           # 业务接口定义
│   └── impl/          # 业务实现（核心逻辑在这里）
├── mapper/            # MyBatis-Plus Mapper 接口
│   └── xml/           # 动态 SQL（复杂查询在这里）
├── entity/            # 数据库映射实体（@TableName）
├── model/             # VO（视图对象）/ DTO（传输对象）
└── vo/                # 响应视图对象
```

**新增业务模块时，复制 demo 模块结构：**
`jeecg-boot-module/jeecg-module-demo/src/main/java/org/jeecg/modules/demo/`

---

## 四、前端目录结构（jeecgboot-vue3/src/）

```
src/
├── api/                   # 后端接口定义
│   ├── sys/               # 系统接口（登录/用户/权限）
│   ├── demo/              # 示例接口（新功能参考）
│   └── common/            # 公共接口（文件上传等）
├── views/                 # 页面视图（按业务模块分目录）
│   ├── system/            # 系统管理页面（用户/角色/菜单等）
│   ├── monitor/           # 监控页面
│   ├── dashboard/         # 首页工作台
│   ├── openapi/           # 开放接口管理
│   ├── report/            # 报表
│   ├── super/airag/       # AI RAG 页面
│   └── demo/              # 示例页面（新功能参考）
├── components/            # 全局公共组件
│   ├── Table/             # JeecgBoot 增强表格
│   ├── Form/              # 增强表单
│   ├── Modal/             # 弹窗
│   ├── jeecg/JVxeTable/   # JVxe 可编辑表格
│   └── jeecg/OnLine/      # 在线表单组件
├── store/                 # Pinia 状态管理
│   └── modules/           # 按模块拆分（user/permission/app等）
├── router/                # 路由配置
│   ├── routes/modules/    # 静态路由（系统级页面）
│   └── guard/             # 路由守卫（权限控制）
├── hooks/                 # 组合式 API Hooks
│   ├── jeecg/             # JeecgBoot 专用 Hooks
│   └── web/               # 通用 Web Hooks
└── utils/                 # 工具函数
    ├── auth/              # Token 处理
    ├── dict/              # 数据字典
    └── http/axios/        # Axios 封装（请求拦截/响应处理）
```

---

## 五、新增业务页面的标准文件清单

新增一个业务模块（如"报名管理"）需创建以下文件：

**后端：**
```
jeecg-boot-module/jeecg-module-<模块名>/src/main/java/org/jeecg/modules/<模块名>/
├── controller/<模块名>Controller.java
├── service/I<模块名>Service.java
├── service/impl/<模块名>ServiceImpl.java
├── mapper/<模块名>Mapper.java
├── mapper/xml/<模块名>Mapper.xml
├── entity/<模块名>.java
└── vo/<模块名>VO.java（如需）
```

**前端：**
```
src/views/<模块名>/
├── index.vue              # 列表页
└── components/
    └── <模块名>Modal.vue  # 新增/编辑弹窗
src/api/<模块名>.ts        # 接口定义
```

**系统配置（不要漏！）：**
- 菜单管理 → 新增菜单
- 权限管理 → 配置按钮权限编码
- 路由注册（静态路由需手动加，动态路由系统自动加载）

---

## 六、关键配置文件位置

| 配置项 | 文件路径 |
|--------|---------|
| 后端启动配置 | `jeecg-boot/jeecg-module-system/jeecg-system-start/src/main/resources/application.yml` |
| 后端 Docker 配置 | `jeecg-boot/jeecg-module-system/jeecg-system-start/src/main/resources/application-docker.yml` |
| 前端环境变量 | `jeecgboot-vue3/.env` / `.env.development` / `.env.production` |
| 前端 Vite 配置 | `jeecgboot-vue3/vite.config.ts` |
| 前端路由守卫 | `jeecgboot-vue3/src/router/guard/` |
| 数据库脚本 | `jeecg-boot/db/` |

---

## 七、前后端接口约定

- **接口前缀**：`/jeecgboot/sys/`（系统）、`/jeecgboot/<模块名>/`（业务）
- **统一响应**：`Result<T>` 封装，`code=200` 成功，`code=500` 失败
- **分页参数**：`pageNo`、`pageSize`（MyBatis-Plus 分页插件）
- **认证方式**：请求头 `X-Access-Token: <JWT token>`
- **文件上传**：统一走 `/jeecgboot/sys/common/upload`

---

## 八、本项目自定义模块（此处填写业务开发新增的模块）

> ⚠️ 每次新增业务模块后，在此处追加记录。

| 模块名 | 后端路径 | 前端路径 | 说明 |
|--------|---------|---------|------|
| 待添加 | - | - | - |
