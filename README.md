# 图书管理系统

基于 Vue 3 和 Spring Boot 的图书管理系统。读者可查询馆藏、提交借阅申请和反馈、查看进度和确认归还。管理员可维护馆藏、处理借阅和读者反馈。

## 功能

| 入口 | 功能 |
| --- | --- |
| `/` | 首页，展示馆藏库存、读者人数和借阅统计。 |
| `/books` | 书籍管理，提供搜索、筛选、详情、借阅申请和归还登记。 |
| `/register`、`/login` | 注册读者账号、登录。 |
| `/profile` | 个人中心，展示账号资料和个人借阅情况，可登记归还及查看全馆动态。`/loans` 仍可访问。 |
| `/about`、`/contact` | 查看借阅流程和常见问题，登录后可向管理员提交反馈。 |
| `/admin` | 管理员登录，维护馆藏，审批申请，登记归还，查看和处理读者反馈。 |

申请进入待审批状态。批准时扣减可借数量，归还时恢复。待审批和未归还的记录会阻止图书下架，下架后仍保留借阅历史。

## 技术栈

- 前端使用 Vue 3、Vue Router、Element Plus、Axios 和 Vite。
- 后端使用 Java 17+、Spring Boot、Spring JDBC 和 MySQL 8。
- 云端部署使用 Docker Compose 和 Nginx。

## 腾讯云 Ubuntu 24.04 LTS 部署

### 准备服务器和源码

在腾讯云 Ubuntu 24.04 LTS 实例上安装 Docker Engine、Buildx 和 Docker Compose 插件。Java、Node.js 和 MySQL 由容器提供。首次构建需要访问 Docker Hub、Maven Central 和 npm Registry。[Docker 官方安装说明](https://docs.docker.com/engine/install/ubuntu/)

可在服务器克隆包含本次改动的仓库。若改动还在本地工作区，可在 Windows 项目根目录执行以下 PowerShell 命令，打包当前源码。将 SSH 用户名和地址换成实际值。

```powershell
tar.exe -czf library-source.tar.gz compose.yaml .env.example backend/Dockerfile backend/.dockerignore backend/pom.xml backend/src frontend/Dockerfile frontend/.dockerignore frontend/package.json frontend/package-lock.json frontend/index.html frontend/vite.config.js frontend/nginx.conf frontend/src
if ($LASTEXITCODE -eq 0) { scp library-source.tar.gz ubuntu@服务器公网IP:~/ }
```

如果使用压缩包，在服务器解压到部署目录。

```bash
mkdir -p ~/library
tar -xzf ~/library-source.tar.gz -C ~/library
cd ~/library
```

### 启动服务

在服务器的项目根目录创建 `.env`。已有部署继续使用原来的 `.env`。

```bash
umask 077
cp -n .env.example .env
nano .env
```

首次部署分别填写 `MYSQL_ROOT_PASSWORD`、`DB_PASSWORD` 和 `ADMIN_PASSWORD`。可用 `openssl rand -hex 24` 分别生成。管理员密码至少 8 个字符，最多 72 个 UTF-8 字节。管理员用户名默认是 `admin`，也可通过 `ADMIN_USERNAME` 指定 3–32 位字母、数字或下划线。

```bash
sudo docker compose up --build --wait --wait-timeout 300
```

Compose 会等待数据库可查询、后端完成初始化以及前端 Nginx 就绪。默认 Web 入口是服务器上的 `127.0.0.1:8088`。MySQL 和 Spring Boot 通过容器网络连接，MySQL 数据保存在 `library_mysql_data` 卷中。

### 公网 HTTPS 访问

将域名解析到服务器公网地址。在腾讯云安全组放行站点的 TCP 80 和 443，并将 SSH 端口限制在管理来源。使用中国大陆实例通过域名提供公网服务时，按腾讯云要求办理备案。[腾讯云备案说明](https://cloud.tencent.com/document/product/243/19630/)

保留 `.env` 中的 `WEB_BIND=127.0.0.1` 和 `SESSION_COOKIE_SECURE=true`。安装主机 Nginx 和 Certbot。

```bash
sudo apt update
sudo apt install -y nginx certbot python3-certbot-nginx
```

将下面的配置写入服务器的 `/etc/nginx/sites-available/library`。把域名换成实际域名，端口应与 `WEB_PORT` 一致。

```nginx
server {
    listen 80;
    server_name library.example.com;

    location / {
        proxy_pass http://127.0.0.1:8088;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

首次启用站点时执行以下命令，并替换 Certbot 命令中的域名。

```bash
sudo ln -s /etc/nginx/sites-available/library /etc/nginx/sites-enabled/library
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx --redirect -d library.example.com
```

站点使用 `https://library.example.com/`，管理员入口是 `/admin`。如果暂时没有域名，可在服务器的 `.env` 中设 `SESSION_COOKIE_SECURE=false` 后重新运行 Compose 启动命令，将 Web 端口保持在回环地址，再从本地执行 `ssh -N -L 8088:127.0.0.1:8088 ubuntu@服务器公网IP`，通过 `http://localhost:8088/` 访问。启用公网 HTTPS 时将该值恢复为 `true` 并重新启动服务。

以后更新源码时，在同一部署目录重新运行 Compose 启动命令。停止服务可用 `sudo docker compose down`，它会保留数据库卷。现有管理员凭据保存在数据库里，修改 `ADMIN_PASSWORD` 不会重置已有管理员密码。

## 本地开发

准备 Java 17 或更高版本、Maven、Node.js 22 和 MySQL 8.0.16 或更高版本。以有建库和授权权限的 MySQL 账户执行以下 SQL，并替换示例数据库密码。

```sql
CREATE DATABASE IF NOT EXISTS library CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'library_app'@'localhost' IDENTIFIED BY '替换为数据库密码';
GRANT SELECT, INSERT, UPDATE, CREATE, REFERENCES ON library.* TO 'library_app'@'localhost';
```

从项目根目录启动后端。以下为 PowerShell 示例。

```powershell
$env:DB_USER = 'library_app'
$env:DB_PASSWORD = '与 MySQL 中设置的密码一致'
$env:ADMIN_PASSWORD = '替换为至少8个字符的管理员密码'
mvn.cmd -f backend/pom.xml spring-boot:run
```

在另一个终端启动前端。

```powershell
cd frontend
npm.cmd ci --cache (Join-Path $env:LOCALAPPDATA 'npm-cache')
if ($LASTEXITCODE -eq 0) { npm.cmd run dev }
```

依赖已安装时，可直接运行 `npm.cmd run dev`。需要重新运行 `npm ci` 时，先在正在运行 Vite 的终端按 Ctrl+C，以释放 Windows 上的 esbuild 文件。

打开 `http://localhost:5173/`，管理入口为 `http://localhost:5173/admin`。管理员默认用户名为 `admin`，可在首次启动前通过 `ADMIN_USERNAME` 指定。首次启动时后端建表并初始化管理员，馆藏由管理员添加。Vite 将 `/api` 代理到后端的 8080 端口，修改后端地址时可设置 `API_TARGET`。

“联系我们”默认展示 `2411557902@qq.com`。如需更换，可在部署用的 `.env` 中设置 `VITE_CONTACT_EMAIL` 并重建前端容器。本地开发时，在启动 Vite 的终端设置同名变量。

## 项目结构

| 路径 | 说明 |
| --- | --- |
| `frontend/src/components/` | 所有页面组件与复用组件。 |
| `frontend/src/api.js`、`frontend/src/router.js` | API 请求、会话恢复和路由。 |
| `backend/src/main/java/edu/library/` | REST API、账号鉴权、借阅与库存服务。 |
| `backend/src/main/resources/` | 应用配置和数据库建表脚本。 |
| `compose.yaml`、`.env.example` | 容器编排和部署环境变量模板。 |
