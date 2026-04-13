# 系統架構

技術棧
層級	技術
框架	Spring Boot 3.2.4 + Java 17
安全	Spring Security + JWT (jjwt 0.12.5)
資料庫	PostgreSQL + Spring Data JPA
郵件	Spring Mail（開發模式下輸出到 console）
建置	Maven


# 架構分層

┌─────────────────────────────────────────────┐
│  Controller 層（HTTP 入口）                   │
│  AuthController  /api/auth/**               │
│  UserController  /api/users/**, /api/admin/**│
└───────────────┬─────────────────────────────┘
                │
┌───────────────▼─────────────────────────────┐
│  Service 層（業務邏輯）                        │
│  AuthService     → 認證核心流程               │
│  UserService     → 使用者管理                 │
│  EmailService    → 郵件發送                   │
│  TokenCleanupService → 定時清理過期 Token     │
└───────────────┬─────────────────────────────┘
                │
┌───────────────▼─────────────────────────────┐
│  Security 層                                 │
│  JwtService            → 產生/驗證 JWT        │
│  JwtAuthenticationFilter → 每次請求攔截驗證    │
│  UserDetailsServiceImpl → 載入使用者          │
└───────────────┬─────────────────────────────┘
                │
┌───────────────▼─────────────────────────────┐
│  Repository 層（資料存取）                     │
│  User / RefreshToken / BlacklistedToken      │
│  PasswordResetToken / EmailVerificationToken │
└─────────────────────────────────────────────┘


# 核心資料表（Entity）

Entity	用途
User	使用者帳號，含失敗次數、鎖定時間、角色
RefreshToken	多裝置 Refresh Token，含裝置資訊
BlacklistedToken	登出後的 Access Token JTI 黑名單
PasswordResetToken	密碼重設一次性 Token
EmailVerificationToken	Email 驗證一次性 Token
主要安全機制
JWT Access Token（15分鐘）+ Refresh Token Rotation（7天，每次刷新輪換）
JTI 黑名單：登出後 Access Token 仍在有效期內也會被拒絕
帳號鎖定：失敗 5 次後鎖定 30 分鐘
BCrypt 強度 12：約 400ms/次，抵抗暴力破解
防帳號枚舉：忘記密碼不論信箱存在與否回傳相同訊息
定時清理：每小時清除過期 Token，防止資料庫膨脹


# API 端點一覽

POST   /api/auth/register            → 註冊
GET    /api/auth/verify-email?token= → 驗證 Email
POST   /api/auth/resend-verification → 重發驗證信
POST   /api/auth/login               → 登入（回傳 JWT）
POST   /api/auth/refresh             → 刷新 Access Token
POST   /api/auth/logout              → 登出（單裝置）
POST   /api/auth/logout-all          → 登出所有裝置
POST   /api/auth/forgot-password     → 忘記密碼
POST   /api/auth/reset-password      → 重設密碼
GET    /api/users/me                 → 取得自身資料（需登入）
GET    /api/admin/users              → 所有使用者（ADMIN）
PATCH  /api/admin/users/{id}/disable → 停用帳號（ADMIN）
PATCH  /api/admin/users/{id}/enable  → 啟用帳號（ADMIN）
PATCH  /api/admin/users/{id}/unlock  → 解鎖帳號（ADMIN）


# 如何測試（開發環境）
1. 前置條件
安裝並啟動 PostgreSQL，建立資料庫：

CREATE DATABASE logindb;
修改 application.properties 中的密碼：

spring.datasource.password=你的密碼

2. 啟動應用程式

./mvnw spring-boot:run
Hibernate 會自動建立所有資料表（ddl-auto=update）。

3. 測試流程（用 curl 或 Postman）
   
步驟一：註冊
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","email":"test@example.com","password":"Test@1234"}'
   
步驟二：取得 Email 驗證連結

因為 app.email.dev-mode=true，驗證連結會印在 Spring Boot console log 中，格式如：
[DEV] Email 驗證連結 → http://localhost:8080/api/auth/verify-email?token=xxxxx
直接用瀏覽器或 curl 開啟該連結完成驗證。

步驟三：登入
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test@1234"}'
回傳 accessToken 和 refreshToken。

步驟四：呼叫受保護的 API
curl http://localhost:8080/api/users/me \
  -H "Authorization: Bearer <accessToken>"
  
步驟五：刷新 Token
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
  
步驟六：登出
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'

  
# 如何改為正式環境
需要修改以下幾個地方：

1. 資料庫設定

正式環境改為 validate（由 Flyway/Liquibase 管理 schema）
spring.jpa.hibernate.ddl-auto=validate
建議加入 Flyway 管理資料庫版本：在 pom.xml 加入依賴：
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>

2. JWT 金鑰
絕對不要將金鑰寫在 properties 檔案中。改用環境變數：

產生新金鑰
openssl rand -base64 64

jwt.secret=${JWT_SECRET}
部署時設定環境變數 JWT_SECRET=...。

3. 開啟真實 Email
修改 application.properties：

app.email.dev-mode=false
app.base-url=https://yourdomain.com

spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

4. CORS 設定
在 SecurityConfig.java 改為明確指定前端網域：

config.setAllowedOriginPatterns(List.of("https://yourdomain.com"));

5. 移除開發用路由
在 SecurityConfig.java 移除 H2 console：

private static final String[] PUBLIC_URLS = {
    "/api/auth/**",
    "/actuator/health"   // 移除 /h2-console/**
};
同時移除第 89 行的 headers.frameOptions(fo -> fo.disable())。

6. 帳號安全強化（建議）
security.max-failed-attempts=5
security.lock-duration-minutes=30
這兩個值可依需求調整，或改為環境變數。

7. 打包部署
./mvnw clean package -DskipTests
java -jar target/login-1.0.0.jar \
  --spring.datasource.password=$DB_PASSWORD \
  --jwt.secret=$JWT_SECRET \
  --spring.mail.username=$MAIL_USERNAME \
  --spring.mail.password=$MAIL_PASSWORD
或使用 Docker 部署，建議搭配 application-prod.properties profile 管理正式環境設定。
