# SDA Android 转向实施蓝图

## 1. 这次转向的目标

项目接下来的目标不再是“尽量把网页登录修通”，而是：

**把当前 Android 应用逐步转成 SDA 的手机版本。**

目标链路是：

1. 协议级登录 Steam
2. 处理邮箱验证码 / 设备验证码挑战
3. 获取 `SteamID / AccessToken / RefreshToken`
4. 调用 `AddAuthenticator / FinalizeAddAuthenticator`
5. 本地保存 Android 端的 `maFile` 等价模型
6. 生成 Steam Guard 动态码
7. 拉取并批准交易 / 市场确认
8. 管理会话刷新与本地加密存储

---

## 2. 本地 SDA 源码里最值得直接对标的部分

当前工作区里已经有完整 SDA 项目：

- `SteamDesktopAuthenticator-master/Steam Desktop Authenticator/LoginForm.cs`
- `SteamDesktopAuthenticator-master/Steam Desktop Authenticator/UserFormAuthenticator.cs`
- `SteamDesktopAuthenticator-master/lib/SteamAuth/SteamAuth/AuthenticatorLinker.cs`
- `SteamDesktopAuthenticator-master/lib/SteamAuth/SteamAuth/SessionData.cs`
- `SteamDesktopAuthenticator-master/lib/SteamAuth/SteamAuth/SteamGuardAccount.cs`

此外，现在本地也已经有 `SteamKit-master`，可以直接阅读 SteamKit2 的认证源码：

- `SteamKit-master/SteamKit2/SteamKit2/Steam/Authentication/SteamAuthentication.cs`
- `SteamKit-master/SteamKit2/SteamKit2/Steam/Authentication/AuthSession.cs`
- `SteamKit-master/SteamKit2/SteamKit2/Steam/Authentication/CredentialsAuthSession.cs`
- `SteamKit-master/SteamKit2/SteamKit2/Steam/Authentication/IAuthenticator.cs`
- `SteamKit-master/Samples/000_Authentication/Program.cs`

这些文件基本定义了 SDA 的完整主干：

- `LoginForm.cs`
  - 账号密码登录
  - 调用 SteamKit2
  - 区分 Initial / Refresh / Import 三种登录模式
- `UserFormAuthenticator.cs`
  - 2FA 挑战应答
  - 邮箱码输入
  - 设备码自动生成
- `AuthenticatorLinker.cs`
  - 绑定手机令牌
  - 手机号、邮件确认、短信确认
  - `AddAuthenticator`
  - `FinalizeAddAuthenticator`
- `SessionData.cs`
  - `AccessToken / RefreshToken / SessionID`
  - JWT 过期判断
  - 刷新 access token
  - 构造移动端 cookie
- `SteamGuardAccount.cs`
  - `shared_secret / identity_secret / device_id`
  - 生成动态码
  - 生成确认签名
  - 获取和批准 confirmations

SteamKit 源码补充确认了下面几个关键事实：

- `BeginAuthSessionViaCredentialsAsync(...)` 内部会先调用 `GetPasswordRSAPublicKey`，再用 RSA 公钥加密密码后发起认证
- `PollingWaitForResultAsync()` 才是真正统一处理 2FA 与轮询结果的中枢
- `IAuthenticator` 的 3 个挑战入口分别是：
  - `GetDeviceCodeAsync(...)`
  - `GetEmailCodeAsync(...)`
  - `AcceptDeviceConfirmationAsync()`
- SteamKit sample 明确展示了：
  - 登录请求可以带 `guardData`
  - 登录结果可能返回 `newGuardData`
  - 成功后会拿到 `AccessToken / RefreshToken`

这说明我们 Android 侧的协议登录抽象，不能只设计成“用户名密码换 session”，还必须预留：

- 2FA challenge/response
- `guardData`
- `newGuardData`
- 轮询式认证完成

---

## 3. Android 侧对应关系

当前 Android 项目里，已有一部分能力，但状态仍然是拆开的：

### 已有基础

- `TokenRecord`
  - 保存 `shared_secret / identity_secret / revocation_code / device_id / token_gid`
- `SteamSessionRecord`
  - 保存 Steam 会话材料
- `DefaultSteamConfirmationSyncManager`
  - 拉取/批准/拒绝 confirmations
- `DefaultSteamSessionValidationSyncManager`
  - 校验当前网页会话
- `DefaultSteamTimeSyncManager`
  - 对齐 Steam 时间

### 这次转向后的目标关系

| SDA | Android 目标 |
|---|---|
| `SteamGuardAccount` | `SteamGuardAccountSnapshot` |
| `SessionData` | 扩展后的 `SteamSessionRecord` |
| `AuthenticatorLinker` | 未来的协议级绑定服务 |
| `UserFormAuthenticator` | 未来的登录挑战处理层 |
| `Manifest + maFile` | Vault 内统一快照存储 |

---

## 4. 这一轮代码已经开始做的基础调整

为了让后续协议级登录能落地，当前仓库已经先补了两块基础模型：

### 4.1 `SteamSessionRecord` 开始向 SDA `SessionData` 靠拢

新增了：

- `accessToken`
- `refreshToken`
- `platform`

这意味着它不再只是“网页 cookie 会话”，后面可以逐步承接：

- SDA 式移动端 token 会话
- 网页降级会话
- 导入态会话

### 4.2 新增统一快照模型 `SteamGuardAccountSnapshot`

新模型把现在分散在：

- `TokenRecord`
- `SteamSessionRecord`

里的数据，收成一个更像 SDA `SteamGuardAccount + SessionData` 组合体的快照对象。

它的职责是：

- 统一表示一个 Steam 手机令牌账户
- 同时承载 authenticator secrets 和 session
- 为后面替换旧流程提供稳定中枢

### 4.3 协议登录抽象已经起骨架

当前仓库已经新增：

- `SteamMobileSession`
- `SteamProtocolLoginRequest`
- `SteamProtocolLoginResult`
- `SteamProtocolLoginChallenge`
- `SteamProtocolLoginRepository`

这里面已经预留了 SteamKit 认证语义最关键的几个点：

- `AccessToken / RefreshToken`
- `guardData`
- `newGuardData`
- challenge/response 式 2FA
- 登录模式区分（Initial / Refresh / Import 对齐 SDA）

---

## 5. 推荐实施顺序

### 阶段 A：先收拢状态

目标：

- 不急着替换所有页面
- 先把核心状态模型统一
- 让新旧流程都能映射到统一快照

这一阶段要完成：

- `SteamGuardAccountSnapshot`
- 扩展后的 `SteamSessionRecord`
- 旧 `TokenRecord / SessionRecord` 向新快照的映射

### 阶段 B：做协议级登录

目标：

- 替换当前“浏览器取材料”的主流程
- 直接拿到 `SteamID / AccessToken / RefreshToken`

这一阶段需要对标：

- `LoginForm.cs`
- `UserFormAuthenticator.cs`

### 阶段 C：做协议级绑定

目标：

- Android 端直接完成 `AddAuthenticator / FinalizeAddAuthenticator`
- 不再把“网页登录拿材料”当主线

这一阶段需要对标：

- `AuthenticatorLinker.cs`

### 阶段 D：统一确认、刷新、令牌详情

目标：

- 当前 confirmations、动态码、会话刷新全部切到统一核心模型
- 令牌详情展示直接以统一快照为数据源

这一阶段主要对标：

- `SteamGuardAccount.cs`
- `SessionData.cs`

---

## 6. 浏览器链路接下来怎么处理

浏览器链路不应该立刻删除，但角色要变化。

它后面应该只保留为：

- 导入/迁移工具
- 手工修复工具
- 开发调试兜底方案

它不应该再承担：

- 主登录链路
- 主绑定链路
- 全链路成败的核心依赖

---

## 7. 当前最重要的工程原则

后续所有改动都尽量遵守下面三条：

1. **新功能尽量挂到统一快照模型，不再继续把状态散落到页面局部状态里。**
2. **旧网页登录链路只做兼容，不再继续往里加核心能力。**
3. **所有协议级能力都优先对齐 SDA 的核心类职责，而不是对齐它的 WinForms UI。**

---

## 8. 下一步最值得立刻做的事

接下来的优先级建议是：

1. 继续扩展 `SteamSessionRecord`，让它能完整承载 SDA `SessionData`
2. 把 `TokenRecord + SteamSessionRecord` 的读取入口逐步收束为统一快照读取
3. 新建协议级登录抽象层，对标 `LoginForm + UserFormAuthenticator`
4. 再开始替换当前新增令牌流程

一句话说，这次转向的本质不是“继续修网页登录”，而是：

> **在 Android 端重建一套属于我们自己的 `SteamAuth` 核心层。**
