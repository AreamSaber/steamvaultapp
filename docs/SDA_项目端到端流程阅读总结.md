# SteamDesktopAuthenticator（SDA）端到端流程阅读总结

> 基于仓库：`https://github.com/Jessecar96/SteamDesktopAuthenticator`（主程序） + `lib/SteamAuth`（子模块核心库）  
> 目标：串起「登录拿到会话/令牌 → 绑定生成 maFile → 令牌(TOTP)/时间管理 → 交易/市场确认获取与批准/拒绝 → 本地加密存储与导入」整条链路，并标出关键类/文件。

---

## 0. 项目分层与关键入口

### 0.1 SDA 主程序（WinForms UI）
目录：`Steam Desktop Authenticator/`

- 程序入口：`Program.cs` → `MainForm.cs`
- 登录/绑定引导：`LoginForm.cs`
- confirmations UI：
  - 列表窗体：`ConfirmationFormWeb.cs`
  - 弹窗提示：`TradePopupForm.cs`
- maFile 与配置/加密：
  - `Manifest.cs`（manifest.json、maFiles 目录、账号列表加载/保存）
  - `FileEncryptor.cs`（PBKDF2 + AES-CBC 加解密）

### 0.2 核心 Steam 交互库（子模块）
目录：`lib/SteamAuth/SteamAuth/`

- 会话/令牌与 cookie：`SessionData.cs`
- 绑定 Mobile Authenticator：`AuthenticatorLinker.cs`
- TOTP 生成与 confirmations：`SteamGuardAccount.cs`、`Confirmation.cs`
- Steam 时间对齐：`TimeAligner.cs`
- Web 请求封装：`SteamWeb.cs`、`CookieAwareWebClient.cs`
- 常量：`APIEndpoints.cs`

### 0.3 关键依赖
- `SteamKit2`：用于现代登录（token-based 的 BeginAuthSession/PollingWaitForResult）

---

## 1. 流程 A：登录 Steam（含邮箱码/令牌码）→ 获取 Access/Refresh Token

### 1.1 调用链（UI → SteamKit2）
1. `MainForm.btnSteamLogin_Click` 打开 `LoginForm`
2. `LoginForm.btnSteamLogin_Click`：
   - 创建并连接：`SteamClient steamClient = new SteamClient(); steamClient.Connect();`
   - 开始认证会话：
     - `steamClient.Authentication.BeginAuthSessionViaCredentialsAsync(new AuthSessionDetails { ... })`
     - 关键参数：
       - `PlatformType = k_EAuthTokenPlatformType_MobileApp`
       - `ClientOSType = Android9`
       - `Authenticator = new UserFormAuthenticator(this.account)`
   - 等待结果：`authSession.PollingWaitForResultAsync()`
   - 组装会话对象 `SessionData`：
     - `SteamID`
     - `AccessToken`
     - `RefreshToken`

### 1.2 二步验证接入方式（邮箱码 / Steam Guard code）
文件：`UserFormAuthenticator.cs`（实现 `SteamKit2.Authentication.IAuthenticator`）

- `GetEmailCodeAsync(...)`：弹出 `InputForm` 让用户输入邮箱验证码
- `GetDeviceCodeAsync(...)`：
  - 若已有 SDA 账号（`account != null`），则调用 `account.GenerateSteamGuardCodeAsync()` 生成 Steam Guard code
  - 若 `account == null`（新增绑定阶段），则提示该账号已有 authenticator/无法用 SDA 生成 device code

> 说明：仓库中存在 `CaptchaForm` 等旧登录时代遗留 UI，但当前主登录链路以 SteamKit2 新认证 API 为核心。

---

## 2. 流程 B：绑定 SDA 作为 Mobile Authenticator → 生成/获得 maFile 核心数据

登录成功后，`LoginForm.cs` 继续执行“绑定移动令牌”流程，产出包含 `shared_secret / identity_secret / device_id / revocation_code` 等字段的 `SteamGuardAccount`（即 maFile 序列化对象）。

### 2.1 AddAuthenticator（第一阶段：请求生成 secrets）
调用链：

- `LoginForm.cs`：
  - `AuthenticatorLinker linker = new AuthenticatorLinker(sessionData);`
  - 循环调用 `await linker.AddAuthenticator()` 直到返回 `AwaitingFinalization`

文件：`AuthenticatorLinker.cs::AddAuthenticator()`

- 调用接口：`ITwoFactorService/AddAuthenticator/v1/?access_token=...`
- 关键 body：
  - `steamid`
  - `authenticator_time`（对齐后的 Steam 时间）
  - `device_identifier`（随机生成的 `android:GUID`）
- 状态分支（典型）：
  - `Status == 2`：账号未绑定手机号
    - UI 收集手机号：`PhoneInputForm`
    - `IPhoneService/SetAccountPhoneNumber`
    - `IPhoneService/IsAccountWaitingForEmailConfirmation`（要求点邮件确认链接）
    - `IPhoneService/SendPhoneVerificationCode`（触发短信验证码）
  - `Status == 29`：账号已有 authenticator（直接报错退出）
  - `Status == 1`：成功进入待 finalize 阶段
    - `LinkedAccount = addAuthenticatorResponse.Response`（类型：`SteamGuardAccount`）
    - `LinkedAccount.DeviceID = this.DeviceID`
    - `LinkedAccount.Session = this.Session`

### 2.2 落盘（非常关键：Finalize 前先保存）
文件：`Manifest.cs::SaveAccount(...)`

- 在 finalize 之前先把 `linker.LinkedAccount` 立即写入 `{steamid}.maFile`（防止 finalize 前崩溃导致 secret 丢失）

### 2.3 FinalizeAddAuthenticator（第二阶段：短信码确认最终绑定）
文件：`AuthenticatorLinker.cs::FinalizeAddAuthenticator(smsCode)`

- 调用接口：`ITwoFactorService/FinalizeAddAuthenticator/v1/?access_token=...`
- 关键字段：
  - `activation_code`：短信验证码
  - `authenticator_code`：用 `shared_secret` 生成的 Steam Guard code
  - `authenticator_time`：对齐后的 Steam 时间
- 成功：
  - `LinkedAccount.FullyEnrolled = true`
  - 再次 `Manifest.SaveAccount(...)` 更新落盘状态

---

## 3. maFile / manifest 的保存、加载与加密

### 3.1 文件组织
位置：`<exe所在目录>/maFiles/`

- 每个账号一个文件：`{steamid}.maFile`
- 索引与设置：`manifest.json`（`Manifest.cs`）

`manifest.json` 的 entry（`ManifestEntry`）包含：

- `steamid`
- `filename`
- `encryption_iv`
- `encryption_salt`

以及全局字段：

- `encrypted`
- 定时检查与自动确认等设置项

### 3.2 maFile 加密（可选）
文件：`FileEncryptor.cs`

- PBKDF2：`Rfc2898DeriveBytes(password, salt, 50000)` 派生 32 字节 key
- AES（RijndaelManaged）：CBC + PKCS7，随机 IV（16字节）
- maFile 文件内容加密后写为 base64 字符串；salt/iv 写在 `manifest.json` 的对应 entry 里

### 3.3 加载账号列表
文件：`Manifest.cs::GetAllAccounts(passKey)`

- 若 `Encrypted=true`：读取 maFile → `FileEncryptor.DecryptData(passKey, salt, iv, fileText)` → JSON 反序列化为 `SteamGuardAccount`

### 3.4 导入 maFile（以及会话过期后的补登录）
文件：`ImportAccountForm.cs`

- 导入后如果：
  - `maFile.Session == null` 或 `SteamID == 0` 或 `IsAccessTokenExpired()`
  - 则打开 `LoginForm(LoginType.Import, maFile)` 重新登录拿到新的 `SessionData`，写回并保存

---

## 4. 令牌（Steam Guard code）与 Steam 时间对齐

### 4.1 时间对齐
文件：`TimeAligner.cs`

- 通过 `ITwoFactorService/QueryTime` 获取 `server_time`
- 缓存 `_timeDifference = server_time - local_unix_time`
- `GetSteamTime()/GetSteamTimeAsync()` 返回对齐后的时间戳

UI：`MainForm.timerSteamGuard_Tick`

- 周期性调用 `TimeAligner.GetSteamTimeAsync()`
- 以 `steamTime/30` 驱动 code 刷新与进度条

### 4.2 TOTP（Steam Guard code）生成
文件：`SteamGuardAccount.cs::GenerateSteamGuardCodeForTime(long time)`

- `time /= 30`
- HMAC-SHA1(key = `shared_secret`)
- 动态截断后映射到 Steam 自定义字符表，生成 5 位 code

---

## 5. 流程 C：交易/市场确认（confirmations）获取与批准/拒绝（含自动确认）

### 5.1 会话/令牌管理（Access 刷新 + cookie 组装）
文件：`SessionData.cs`

- 过期判断：`IsAccessTokenExpired()` / `IsRefreshTokenExpired()`（解析 JWT payload 的 `exp`）
- 刷新 access token：
  - `RefreshAccessToken()` → `IAuthenticationService/GenerateAccessTokenForApp/v1/`
- 构造 steamcommunity cookie：`GetCookies()`
  - `steamLoginSecure = "{steamid}%7C%7C{accessToken}"`
  - `sessionid`（随机生成）
  - `mobileClient/android` 等

### 5.2 拉取 confirmations 列表
调用入口：

- `ConfirmationFormWeb.LoadData()`（列表窗体打开/刷新时）
- `MainForm.timerTradesPopup_Tick()`（定时检查）

通用步骤：

1. refresh token 过期：提示用户“login again”
2. access token 过期：`Session.RefreshAccessToken()`
3. 调用：`SteamGuardAccount.FetchConfirmationsAsync()`

文件：`SteamGuardAccount.cs`

- URL：`https://steamcommunity.com/mobileconf/getlist?...`
- Query 由 `GenerateConfirmationQueryParams(tag)` 生成，关键参数：
  - `p`：`device_id`
  - `a`：`steamid`
  - `t`：Steam 时间
  - `k`：使用 `identity_secret` + `(time, tag)` 的 HMAC-SHA1 生成，再 base64 + urlencode
  - `tag`：默认 `"conf"`
- 返回解析：`ConfirmationsResponse` / `Confirmation`（见 `Confirmation.cs`）

### 5.3 批准/拒绝
文件：`SteamGuardAccount.cs`

- 单个：
  - `AcceptConfirmation(conf)` / `DenyConfirmation(conf)`
  - GET `https://steamcommunity.com/mobileconf/ajaxop?op=allow|cancel&...&cid=...&ck=...`
  - tag 会映射为：`accept` / `reject`

- 批量：
  - `AcceptMultipleConfirmations(confs)`
  - POST `https://steamcommunity.com/mobileconf/multiajaxop`
  - body：`op=allow|cancel&...&cid[]=...&ck[]=...`

### 5.4 自动确认与弹窗逻辑
文件：`MainForm.timerTradesPopup_Tick`

- 可配置是否检查全部账号：`manifest.CheckAllAccounts`
- 若开启自动确认：
  - `manifest.AutoConfirmMarketTransactions`
  - `manifest.AutoConfirmTrades`
  - 则将匹配类型的 confirmations 按账号分组后执行 `AcceptMultipleConfirmations(...)`
- 其余 confirmations 聚合后交由 `TradePopupForm` 弹出提示

---

## 6. 最关键的数据结构（建议重点关注）

### 6.1 `SteamGuardAccount`（maFile 的核心序列化对象）
文件：`SteamGuardAccount.cs`

- `shared_secret`：生成 Steam Guard code（TOTP）
- `identity_secret`：生成 confirmations 请求参数 `k`（确认 hash）
- `device_id`：confirmations 参数 `p`
- `revocation_code`：移除 authenticator 用
- `fully_enrolled`：是否 finalize 成功
- `Session`：`SessionData`（包含 `SteamID/access_token/refresh_token/sessionid` 等；会被 JSON 序列化保存进 maFile）

### 6.2 `SessionData`（会话与令牌）
文件：`SessionData.cs`

- `AccessToken`：用于 steamcommunity cookie（`steamLoginSecure`）以及部分 Web API
- `RefreshToken`：用于刷新 access token
- `GetCookies()`：统一生成访问 steamcommunity 所需 cookie 集

---

## 7. 一句话“全链路”摘要

SDA 用 SteamKit2 完成账号登录与 2FA（邮箱码/已有 SDA 令牌码）→ 获得 `AccessToken/RefreshToken` 后调用 `ITwoFactorService` 绑定生成 `SteamGuardAccount(shared_secret/identity_secret/device_id/...)` 并落盘为 maFile（可选 PBKDF2+AES 加密）→ 通过 `TimeAligner` 对齐时间生成 TOTP → 用 `SessionData` 组装 cookie 与 `identity_secret` 生成确认 hash 拉取 steamcommunity `/mobileconf/getlist` 列表，并通过 `/ajaxop` 或 `/multiajaxop` 执行批准/拒绝（支持定时检查与自动确认）。

