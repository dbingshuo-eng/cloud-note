# 云笺 Cloud Note

一个基于微信小程序、Spring Boot、MySQL、Redis 与 MinIO 的个人文件云盘项目。

本仓库为核心源码发布版，仅保留项目运行所需的小程序、后端生产代码、数据库结构和 Maven 配置。

## 目录

- `cloud-miniapp`：微信小程序源码。
- `cloud-server`：Spring Boot 后端与数据库脚本。

## 本地启动

准备 MySQL、Redis 和 MinIO 后，配置 `cloud-server/src/main/resources/application.yml` 中的环境变量，再在 `cloud-server` 目录执行：

```powershell
mvn spring-boot:run
```

使用微信开发者工具打开 `cloud-miniapp`，并按本机后端地址修改 `cloud-miniapp/utils/config.js`。

