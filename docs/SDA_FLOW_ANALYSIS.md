# Steam Desktop Authenticator 流程分析

## 概览

本文档总结了 Steam Desktop Authenticator，简称 SDA，是如何完整打通下面几条核心流程的：

- 登录 Steam
- 获取并持久化 `maFile`
- 生成 Steam Guard 动态码
- 拉取并批准或拒绝交易确认
- 管理并刷新登录令牌

需要先说明一点：主程序项目本身主要是 WinForms 界面外壳，真正负责 Steam 手机令牌逻辑、确认签名、会话令牌管理的核心实现，主要都在 `lib/SteamAuth` 子模块里。

## 项目结构

- `Steam Desktop Authenticator/`
  - WinForms UI
  - `manifest.json` 管理
  - `maFile` 导入
  - 本地加密逻辑
- `lib/SteamAuth/SteamAuth/`
  - Steam 手机令牌核心逻辑
  - 会话和 token 管理
  - 交易确认签名与请求
  - 新手机令牌绑定流程

## 核心原理

SDA 之所以能完整模拟 Steam 手机令牌，是因为它本地保存了完整的一组关键数据：

- `shared_secret`
  - 用来生成 Steam Guard 登录动态码
- `identity_secret`
  - 用来生成交易确认和市场确认所需的签名
- `device_id`
  - 用来把本地客户端伪装成一个固定的移动设备
- `Session`
  - 内含 `AccessToken`、`RefreshToken`、`SteamID`、`SessionID`

`.maFile` 本质上就是一个 `SteamGuardAccount` 对象的 JSON 持久化结果，里面保存了上面这些关键字段。

## 1. Steam 登录流程

### 入口

登录主入口在：

- `Steam Desktop Authenticator/LoginForm.cs`

### 登录方式

SDA 并不是通过抓浏览器 Cookie 来登录 Steam，而是直接使用 `SteamKit2.Authentication` 发起一次移动端登录流程。

实际流程如下：

1. 创建 `SteamClient`
2. 连接 Steam
3. 调用 `BeginAuthSessionViaCredentialsAsync`
4. 指定登录平台为移动端
   - `PlatformType = MobileApp`
   - `ClientOSType = Android9`
5. 提供自定义认证器对象 `UserFormAuthenticator`
6. 调用 `PollingWaitForResultAsync` 轮询等待登录结果

登录成功后，SDA 会创建一个 `SessionData` 对象，里面保存：

- `SteamID`
- `AccessToken`
- `RefreshToken`

这套会话数据后续会用于：

- 刷新访问令牌
- 伪造移动端 Cookie
- 访问 Steam 社区确认接口

### 登录中的二次验证

`UserFormAuthenticator` 是登录过程中的挑战应答层，负责处理 Steam 要求的额外验证码：

- 邮箱验证码
  - 通过 `InputForm` 弹窗手动输入
- 设备验证码
  - 直接调用当前 `SteamGuardAccount` 生成 Steam Guard 动态码

这也是为什么：

- 已经有 `maFile` 的账户可以重新登录并刷新会话
- 一个还没有被 SDA 绑定过的全新账户，无法通过 SDA 自己生成设备码

## 2. SDA 是如何“获取 maFile”的

### 关键澄清

SDA 不是从 Steam 上“下载一个现成的 `maFile`”。

它真正做的是：

1. 先登录 Steam
2. 调用 Steam 的手机令牌绑定接口
3. 从 Steam 返回结果里拿到手机令牌密钥数据
4. 组装成 `SteamGuardAccount`
5. 本地序列化保存为 `.maFile`

换句话说，`maFile` 是 SDA 自己根据 Steam 返回的手机令牌数据在本地生成出来的。

### 绑定流程

核心类在：

- `lib/SteamAuth/SteamAuth/AuthenticatorLinker.cs`

`LoginForm` 在初次绑定时会创建：

- `AuthenticatorLinker linker = new AuthenticatorLinker(sessionData);`

随后调用：

- `AddAuthenticator()`

这个方法会向 Steam 发送请求：

- `ITwoFactorService/AddAuthenticator`

如果成功，Steam 会返回完整的手机令牌数据，包括：

- `shared_secret`
- `identity_secret`
- `revocation_code`
- `serial_number`
- `token_gid`

SDA 把返回结果存入：

- `linker.LinkedAccount`

随后再补充：

- `DeviceID`
- `Session`

这就形成了完整可用的 `SteamGuardAccount` 对象。

### 绑定前置条件：手机号与邮箱

如果 Steam 返回当前账户没有绑定且验证过手机号，SDA 会中断在前置条件流程上，依次处理：

- 询问用户手机号
- 必要时确定国家代码
- 让 Steam 发送确认邮件
- 等用户在邮件里完成确认
- 再让 Steam 发送短信验证码

只有这些条件满足后，才会继续进入手机令牌正式绑定。

## 3. 为什么 SDA 会在最终确认前先保存一次

这是整个项目里非常关键的一步。

在 `AddAuthenticator()` 成功之后，SDA 会立刻把 `LinkedAccount` 写入本地 `.maFile`，哪怕这时候短信最终确认还没有完成。

这么做的原因是：

- 如果 Steam 已经把核心密钥发下来了
- 但程序在最终确认前崩溃或者被关闭
- 而本地又没有及时保存这些数据

用户就有可能丢失唯一的手机令牌密钥，从而把自己锁在账号外面。

所以 SDA 的实际流程是：

1. 调用 `AddAuthenticator`
2. 本地立即保存 `.maFile`
3. 提示用户抄写撤销码 `revocation_code`
4. 让用户输入短信验证码
5. 调用 `FinalizeAddAuthenticator`
6. 成功后再保存一次，并把 `FullyEnrolled = true`

这也是 `SteamAuth` README 里特别强调必须先保存 `LinkedAccount` 的原因。

## 4. `maFile` 的存储结构

### 账户文件

每个账户会写入：

- `maFiles/<steamid>.maFile`

文件内容是 `SteamGuardAccount` 的 JSON 序列化结果。

### manifest 文件

SDA 还会维护一个：

- `maFiles/manifest.json`

它保存的是：

- 当前是否启用加密
- 账户条目列表
- 每个账户的文件名
- 每个账户对应的加密元数据
  - `encryption_salt`
  - `encryption_iv`

### manifest 的作用

`manifest.json` 本身不是手机令牌。

它只是一个索引文件加加密元数据文件，用来告诉 SDA：

- 某个账户对应哪个 `.maFile`
- 这个 `.maFile` 是否需要解密
- 解密所需的盐和 IV 是什么

真正的账户秘密数据仍然在每个 `.maFile` 里。

## 5. 导入已有 `maFile`

### 入口

导入逻辑在：

- `Steam Desktop Authenticator/ImportAccountForm.cs`

### 导入方式

SDA 支持两种情况：

- 未加密 `.maFile`
- 已加密 `.maFile`

如果导入的是加密文件，SDA 会尝试从该文件所在目录读取原始的 `manifest.json`，从中找出：

- 对应文件名
- `encryption_salt`
- `encryption_iv`

随后调用本地的 `FileEncryptor.DecryptData()` 解密，再反序列化成 `SteamGuardAccount`。

### 导入后的会话修复

如果导入后的账户存在下面任一情况：

- `Session` 为空
- `SteamID` 非法
- `AccessToken` 已过期

SDA 会打开：

- `LoginForm(LoginType.Import, maFile)`

这一步只是重新登录以补齐或刷新会话数据，并不会重新绑定手机令牌。

这点很重要：

- 初次绑定流程负责生成新的手机令牌密钥
- 导入流程只负责恢复已有手机令牌，并补一份可用的登录会话

## 6. Steam Guard 动态码生成

### 核心类

- `lib/SteamAuth/SteamAuth/SteamGuardAccount.cs`

### 算法

`GenerateSteamGuardCodeForTime()` 的主要步骤是：

1. Base64 解码 `shared_secret`
2. 获取对齐后的 Steam 时间
3. 以 30 秒为一个时间片
4. 用 HMAC-SHA1 计算摘要
5. 再映射成 Steam 使用的 5 位字符集

最终生成的就是界面上显示的 Steam Guard 动态码。

### 时间对齐

时间同步逻辑在：

- `lib/SteamAuth/SteamAuth/TimeAligner.cs`

它会调用：

- `ITwoFactorService/QueryTime`

将本地 UTC 时间和 Steam 服务器时间对齐，避免本机时钟误差导致动态码失效。

`MainForm` 会定时刷新当前账户的动态码和倒计时进度条。

## 7. 会话与 Token 管理

### 核心类

- `lib/SteamAuth/SteamAuth/SessionData.cs`

### 保存的数据

`SessionData` 内部主要保存：

- `SteamID`
- `AccessToken`
- `RefreshToken`
- `SessionID`

### 过期判断

SDA 会把 token 当作 JWT 解析，读取其中的 `exp` 字段判断是否过期。

相关方法包括：

- `IsAccessTokenExpired()`
- `IsRefreshTokenExpired()`
- `IsRefreshTokenAboutToExpire()`

### AccessToken 刷新

当 `AccessToken` 过期但 `RefreshToken` 还有效时，SDA 会调用：

- `IAuthenticationService/GenerateAccessTokenForApp`

刷新得到新的：

- `AccessToken`
- 有时也会返回新的 `RefreshToken`

### 什么时候必须重新登录

如果 `RefreshToken` 也过期了，那么 SDA 就无法静默恢复会话。

这时它会打开：

- `LoginForm(LoginType.Refresh, account)`

让用户重新输入账号密码并完成认证。

这套逻辑被用于：

- 周期性交易确认检查
- 用户手动点击“Login again”
- 某些导入场景下的会话修复

## 8. 交易确认为什么能工作

### 它和登录动态码不是一回事

Steam Guard 登录动态码依赖：

- `shared_secret`

而交易确认、市场确认依赖的是另一套机制：

- `identity_secret`
- `device_id`
- 当前时间
- 移动端 Cookie

所以确认批准并不是“把 5 位动态码再提交一次”这么简单，而是一套独立的手机确认协议。

### 拉取确认列表

核心方法：

- `FetchConfirmations()`
- `FetchConfirmationsAsync()`

这些方法访问的是：

- `/mobileconf/getlist`

在请求之前，SDA 会先生成一组移动端确认参数：

- `p = device_id`
- `a = steamid`
- `k = 确认签名`
- `t = 当前 Steam 时间`
- `m = react`
- `tag = conf` 或其他动作标签

### 确认签名

`SteamGuardAccount` 会使用：

- `identity_secret`
- 当前时间
- 动作标签，例如 `conf`、`accept`、`reject`

通过 HMAC-SHA1 生成确认签名，再做 URL 编码。

这正是 SDA 能像官方移动端一样批准交易的核心原因。

## 9. SDA 是如何伪造 Steam 手机端 Cookie 的

### 核心逻辑

`SessionData.GetCookies()` 会为下面两个域名构造 Cookie 容器：

- `steamcommunity.com`
- `store.steampowered.com`

写入的 Cookie 包括：

- `steamLoginSecure`
- `sessionid`
- `mobileClient = android`
- `mobileClientVersion = 777777 3.6.4`

### `steamLoginSecure` 的来源

SDA 并不是从浏览器里复制这个 Cookie，而是自己拼出来：

- `steamid||accessToken`

随后再做 URL 转义。

Steam 社区接口会把这套 Cookie 视为来自手机客户端的已登录请求，这就是 SDA 能访问手机确认接口的关键。

## 10. 批准与拒绝确认

### 单条确认

方法：

- `AcceptConfirmation(conf)`
- `DenyConfirmation(conf)`

接口：

- `/mobileconf/ajaxop`

请求参数包含：

- 操作类型
- 已签名的确认参数
- 确认 ID
- 确认 nonce/key

### 批量确认

方法：

- `AcceptMultipleConfirmations(confs)`
- `DenyMultipleConfirmations(confs)`

接口：

- `/mobileconf/multiajaxop`

SDA 的自动批准功能就是依赖这条批量接口实现的。

## 11. 界面层的确认流程

### 网页式确认窗口

文件：

- `Steam Desktop Authenticator/ConfirmationFormWeb.cs`

流程如下：

1. 先检查 `RefreshToken` 是否还有效
2. 如果 `AccessToken` 过期则先刷新
3. 拉取确认列表
4. 为每条确认渲染 Accept 和 Cancel 按钮
5. 点击按钮后调用 `AcceptConfirmation` 或 `DenyConfirmation`
6. 再重新加载列表

### 弹窗式确认

文件：

- `Steam Desktop Authenticator/TradePopupForm.cs`

这是一个更轻量的弹窗确认界面，支持：

- 连续两次确认后才执行 Accept
- 连续两次确认后才执行 Deny

底层仍然调用同一套 `SteamGuardAccount` 的确认接口。

## 12. 自动轮询确认

### 入口

自动检查逻辑在：

- `MainForm.timerTradesPopup_Tick`

### 实际流程

1. 先检查当前是否已有确认轮询正在进行
2. 根据设置决定是只检查当前账户，还是检查全部账户
3. 对每个账户依次：
   - 检查 `RefreshToken`
   - 必要时刷新 `AccessToken`
   - 拉取确认列表
4. 把确认分成两类：
   - 需要人工弹窗处理的
   - 可以自动批准的
5. 人工项弹出 `TradePopupForm`
6. 自动项走 `AcceptMultipleConfirmations`

这个行为由 `manifest.json` 中的设置项控制：

- `PeriodicChecking`
- `PeriodicCheckingInterval`
- `CheckAllAccounts`
- `AutoConfirmMarketTransactions`
- `AutoConfirmTrades`

## 13. 本地加密模型

### 相关文件

- `Steam Desktop Authenticator/FileEncryptor.cs`
- `Steam Desktop Authenticator/Manifest.cs`

### 加密流程

当启用加密后，SDA 对每个 `.maFile` 的处理流程是：

1. 先把账户对象序列化成 JSON
2. 生成随机 salt 和 IV
3. 用 PBKDF2 从用户口令导出密钥
4. 用 AES-CBC 加密 JSON
5. 把 salt 和 IV 写入 `manifest.json`
6. 把密文写入 `.maFile`

### 关键点

`manifest.json` 不保存手机令牌秘密本身，它只保存：

- 文件索引
- 加密状态
- 解密所需元数据

真正的账户秘密依然保存在每个 `.maFile` 里。

## 14. 移除手机令牌

SDA 也实现了从账户上移除手机令牌的流程。

方法：

- `SteamGuardAccount.DeactivateAuthenticator()`

接口：

- `ITwoFactorService/RemoveAuthenticator`

在 UI 层，SDA 会先要求用户输入当前生成出来的 Steam Guard 动态码，确认无误后才允许执行移除。

支持两种模式：

- 切回邮箱验证
- 完全移除 Steam Guard

## 15. 端到端流程总结

### 初次绑定路径

1. 用户打开 `LoginForm`
2. SDA 以移动端身份登录 Steam
3. 拿到 `AccessToken` 和 `RefreshToken`
4. 调用 `AddAuthenticator`
5. Steam 返回手机令牌密钥
6. SDA 创建 `SteamGuardAccount`
7. 立即保存 `.maFile`
8. 用户确认撤销码并输入短信验证码
9. SDA 调用 `FinalizeAddAuthenticator`
10. 再次保存账户，并标记为 `FullyEnrolled`

### 日常使用路径

1. SDA 从 `manifest.json` 和 `.maFile` 加载账户
2. 必要时先解密
3. 与 Steam 对齐时间
4. 生成当前 Steam Guard 动态码
5. 使用已保存的会话去访问确认接口
6. `AccessToken` 过期时尝试自动刷新
7. `RefreshToken` 失效时要求重新登录

### 导入路径

1. 用户选择已有 `.maFile`
2. SDA 如有必要先解密
3. 反序列化为 `SteamGuardAccount`
4. 如果会话失效，则重新登录补会话
5. 保存到本地 manifest 管理体系中

## 最终结论

SDA 之所以能把整套流程完全打通，是因为它同时掌握了两套关键状态：

- 手机令牌身份
  - `shared_secret`
  - `identity_secret`
  - `device_id`
- 登录会话身份
  - `AccessToken`
  - `RefreshToken`
  - `SteamID`
  - `SessionID`

前者负责：

- 生成 Steam Guard 动态码
- 生成交易确认签名

后者负责：

- 伪造移动端登录态
- 访问 Steam 社区确认接口
- 刷新过期会话

所以可以把它总结成一句话：

`maFile` 是手机令牌身份的本地持久化，`SessionData` 是移动端登录会话的本地持久化，SDA 把这两者组合在一起，因此登录、确认批准、动态码生成、会话刷新这几条链路才能全部工作。

## 建议重点阅读的文件

- `Steam Desktop Authenticator/LoginForm.cs`
- `Steam Desktop Authenticator/MainForm.cs`
- `Steam Desktop Authenticator/ImportAccountForm.cs`
- `Steam Desktop Authenticator/Manifest.cs`
- `Steam Desktop Authenticator/FileEncryptor.cs`
- `Steam Desktop Authenticator/ConfirmationFormWeb.cs`
- `Steam Desktop Authenticator/TradePopupForm.cs`
- `Steam Desktop Authenticator/UserFormAuthenticator.cs`
- `lib/SteamAuth/SteamAuth/SteamGuardAccount.cs`
- `lib/SteamAuth/SteamAuth/SessionData.cs`
- `lib/SteamAuth/SteamAuth/AuthenticatorLinker.cs`
- `lib/SteamAuth/SteamAuth/SteamWeb.cs`
- `lib/SteamAuth/SteamAuth/TimeAligner.cs`
- `lib/SteamAuth/SteamAuth/Confirmation.cs`
