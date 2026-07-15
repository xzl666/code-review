# Code Review 项目说明

AI 驱动的代码评审平台，后端基于 Spring Boot，前端基于 Vue 3 + Vite。

## 环境要求

- JDK 17
- Maven 3.6+
- Node.js 18+
- npm
- MySQL 8+

## 数据库配置

开发环境默认使用 `dev` profile，数据库连接配置位于 `src/main/resources/application-dev.yml`。

默认连接信息：

```text
host: 127.0.0.1
port: 3306
database: code_review
username: root
password: 1234
```

如需覆盖默认值，可在启动后端前设置环境变量：

```powershell
$env:CODE_REVIEW_DB_HOST = "127.0.0.1"
$env:CODE_REVIEW_DB_PORT = "3306"
$env:CODE_REVIEW_DB_NAME = "code_review"
$env:CODE_REVIEW_DB_USERNAME = "root"
$env:CODE_REVIEW_DB_PASSWORD = "1234"
```

初始化脚本位于 `src/main/resources/db/init.sql`。

AI 和 Gitee Token 等本地环境变量可参考 `scripts/local-env.template.ps1`。

## 启动后端

在项目根目录执行：

```powershell
mvn spring-boot:run
```

后端默认端口：

```text
http://localhost:8080
```

Swagger 地址：

```text
http://localhost:8080/swagger-ui.html
```

健康检查：

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/health/check" `
  -ContentType "application/json" `
  -Body "{}"
```

正常返回示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "status": "UP"
  }
}
```

## 启动前端

进入前端目录：

```powershell
cd frontend
```

首次启动前安装依赖：

```powershell
npm install
```

启动开发服务：

```powershell
npm run dev
```

前端默认端口：

```text
http://localhost:5173
```

前端开发服务已在 `frontend/vite.config.ts` 中配置代理，所有 `/api` 请求会转发到：

```text
http://localhost:8080
```

可通过前端代理验证后端连通性：

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:5173/api/health/check" `
  -ContentType "application/json" `
  -Body "{}"
```

## 常见问题

查看端口占用：

```powershell
Get-NetTCPConnection -LocalPort 8080,5173 -ErrorAction SilentlyContinue |
  Select-Object LocalAddress,LocalPort,State,OwningProcess
```

根据进程 ID 查看进程：

```powershell
Get-Process -Id <PID>
```

停止占用端口的进程：

```powershell
Stop-Process -Id <PID>
```

