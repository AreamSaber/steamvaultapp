# SteamDesktopAuthenticator (SDA) 项目完整源码分析报告

> **项目地址**: [https://github.com/Jessecar96/SteamDesktopAuthenticator](https://github.com/Jessecar96/SteamDesktopAuthenticator)
>
> **语言**: C# (.NET 8) | **UI 框架**: WinForms | **协议**: Steam Mobile Authenticator
>
> **核心依赖**: SteamKit2 (登录)、geel9/SteamAuth (验证逻辑)、Newtonsoft.Json (序列化)

---

## 目录

- [一、分析思路与方法论](#一分析思路与方法论)
  - [1.1 分析目标](#11-分析目标)
  - [1.2 探究路径](#12-探究路径)
  - [1.3 关键发现](#13-关键发现)
- [二、项目架构总览](#二项目架构总览)
  - [2.1 代码结构](#21-代码结构)
  - [2.2 分层设计](#22-分层设计)
  - [2.3 依赖关系](#23-依赖关系)
- [三、核心流程一：Steam 登录](#三核心流程一steam-登录)
  - [3.1 登录架构](#31-登录架构)
  - [3.2 三种登录模式](#32-三种登录模式)
  - [3.3 完整登录链路](#33-完整登录链路)
  - [3.4 二次验证回调 (UserFormAuthenticator)](#34-二次验证回调-userformauthenticator)
- [四、核心流程二：验证器绑定与 maFile 生成](#四核心流程二验证器绑定与-mafile-生成)
  - [4.1 绑定流程 (AuthenticatorLinker)](#41-绑定流程-authenticatorlinker)
  - [4.2 两步绑定详解](#42-两步绑定详解)
  - [4.3 maFile 文件结构](#43-mafile-文件结构)
  - [4.4 maFile 加密机制 (FileEncryptor)](#44-mafile-加密机制-fileencryptor)
  - [4.5 Manifest 管理体系](#45-manifest-管理体系)
- [五、核心流程三：TOTP 验证码生成](#五核心流程三totp-验证码生成)
  - [5.1 算法实现](#51-算法实现)
  - [5.2 时间同步 (TimeAligner)](#52-时间同步-timealigner)
  - [5.3 Steam 字符集](#53-steam-字符集)
- [六、核心流程四：交易确认 (Trade Confirmations)](#六核心流程四交易确认-trade-confirmations)
  - [6.1 确认签名机制](#61-确认签名机制)
  - [6.2 获取确认列表](#62-获取确认列表)
  - [6.3 确认操作](#63-确认操作)
  - [6.4 确认类型](#64-确认类型)
  - [6.5 UI 层处理](#65-ui-层处理)
- [七、核心流程五：Token 与会话管理](#七核心流程五token-与会话管理)
  - [7.1 Token 体系](#71-token-体系)
  - [7.2 Token 刷新机制](#72-token-刷新机制)
  - [7.3 JWT 过期检测](#73-jwt-过期检测)
  - [7.4 Cookie 生成](#74-cookie-生成)
- [八、HTTP 通信层](#八http-通信层)
  - [8.1 移动客户端模拟](#81-移动客户端模拟)
  - [8.2 请求流程](#82-请求流程)
  - [8.3 Cookie 管理](#83-cookie-管理)
- [九、为什么 SDA 能完全打通所有流程](#九为什么-sda-能完全打通所有流程)
  - [9.1 协议还原精确](#91-协议还原精确)
  - [9.2 密码学实现正确](#92-密码学实现正确)
  - [9.3 Token 生命周期管理完善](#93-token-生命周期管理完善)
  - [9.4 容错设计周到](#94-容错设计周到)
  - [9.5 本地存储安全](#95-本地存储安全)
- [十、完整数据流总结](#十完整数据流总结)
- [附录：核心文件索引](#附录核心文件索引)

---

## 一、分析思路与方法论

### 1.1 分析目标

完整阅读 SteamDesktopAuthenticator 项目源码，理解其三大核心功能的实现方式：

1. **Steam 登录** + **maFile 文件获取** — 如何通过 SteamKit2 登录并绑定验证器
2. **交易确认批准** — 如何获取、签名、批准/拒绝交易确认
3. **令牌管理** — 如何管理 AccessToken、RefreshToken、Session 的完整生命周期

### 1.2 探究路径

分析按照以下路径展开，从外到内逐层深入：

```
第一层：项目结构 & README
  └── 理解项目组成（主项目 + SteamAuth 子模块）
      │
第二层：主项目 UI 层源码
  └── LoginForm.cs、MainForm.cs、ConfirmationFormWeb.cs
  └── Manifest.cs、FileEncryptor.cs
      │
第三层：SteamAuth 核心库源码
  └── SteamGuardAccount.cs（核心中枢）
  └── AuthenticatorLinker.cs（验证器绑定）
  └── SessionData.cs（Token 管理）
  └── Confirmation.cs（确认模型）
  └── TimeAligner.cs（时间同步）
  └── SteamWeb.cs（HTTP 通信）
      │
第四层：密码学 & 协议细节
  └── TOTP 算法（HMAC-SHA1 + 动态截取）
  └── 确认签名（HMAC-SHA1 + IdentitySecret）
  └── AES-256-CBC 加密（PBKDF2 密钥派生）
  └── JWT Token 解析与刷新
      │
第五层：整体架构串联
  └── 数据流梳理、容错机制、安全设计
```

### 1.3 关键发现

| 发现 | 说明 |
|------|------|
| **子模块架构** | 核心验证逻辑在 `geel9/SteamAuth` 子模块中，主项目只负责 UI 和文件管理 |
| **无 UserLogin.cs** | 早期版本有独立的登录类，当前版本已重构为基于 SteamKit2 的 OAuth Token 认证 |
| **无 RSA.cs** | RSA 加密逻辑已移除，登录密码加密由 SteamKit2 内部处理 |
| **移动客户端伪装** | 所有请求模拟 Android Steam App（User-Agent、Cookie、DeviceID） |
| **双密钥体系** | `SharedSecret` 用于 TOTP 验证码，`IdentitySecret` 用于确认操作签名 |

---

## 二、项目架构总览

### 2.1 代码结构

```
SteamDesktopAuthenticator/
│
├── Steam Desktop Authenticator/          ← 主项目（WinForms UI + 业务逻辑）
│   ├── Program.cs                        ← 程序入口（单实例检测、命令行参数）
│   ├── MainForm.cs                       ← 主窗体（验证码显示、账户管理）
│   ├── LoginForm.cs                      ← Steam 登录表单（核心）
│   ├── UserFormAuthenticator.cs          ← SteamKit2 认证回调（核心）
│   ├── Manifest.cs                       ← maFile 索引管理（核心）
│   ├── FileEncryptor.cs                  ← AES-256-CBC 加密引擎
│   ├── ConfirmationFormWeb.cs            ← 交易确认 Web 界面
│   ├── TradePopupForm.cs                 ← 交易确认系统托盘弹窗
│   ├── ImportAccountForm.cs              ← 账户导入
│   ├── CaptchaForm.cs                    ← 验证码输入
│   ├── PhoneInputForm.cs                 ← 手机号输入
│   ├── SettingsForm.cs                   ← 设置
│   ├── WelcomeForm.cs                    ← 欢迎/引导页
│   └── ...
│
├── lib/
│   └── SteamAuth/                        ← 核心验证逻辑库（git submodule）
│       └── SteamAuth/
│           ├── SteamGuardAccount.cs      ← 验证账户核心类（中枢）
│           ├── AuthenticatorLinker.cs    ← 验证器绑定流程
│           ├── SessionData.cs            ← Token/会话管理
│           ├── Confirmation.cs           ← 确认数据模型
│           ├── TimeAligner.cs            ← 时间同步
│           ├── SteamWeb.cs               ← HTTP 请求封装
│           ├── APIEndpoints.cs           ← API 端点常量
│           ├── CookieAwareWebClient.cs   ← Cookie 管理
│           └── Util.cs                   ← 工具类
│
└── SteamDesktopAuthenticator.sln         ← 解决方案文件
```

### 2.2 分层设计

```
┌─────────────────────────────────────────────────────────────┐
│                     表现层 (UI Layer)                        │
│  MainForm / LoginForm / ConfirmationFormWeb / TradePopup    │
├─────────────────────────────────────────────────────────────┤
│                   业务逻辑层 (Business Logic)                │
│  Manifest / FileEncryptor / ImportAccount / Settings        │
├─────────────────────────────────────────────────────────────┤
│                   协议层 (Protocol Layer)                    │
│  SteamGuardAccount / AuthenticatorLinker / SessionData      │
│  Confirmation / TimeAligner                                 │
├─────────────────────────────────────────────────────────────┤
│                   通信层 (Transport Layer)                   │
│  SteamWeb / CookieAwareWebClient / APIEndpoints             │
├─────────────────────────────────────────────────────────────┤
│                   外部依赖 (External)                         │
│  SteamKit2 (登录) / Newtonsoft.Json (序列化)                 │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 依赖关系

| 依赖 | 版本 | 用途 |
|------|------|------|
| SteamKit2 | - | Steam 协议实现（登录、认证） |
| geel9/SteamAuth | commit 01c0c9b | 验证器核心逻辑（TOTP、确认、绑定） |
| Newtonsoft.Json | 13.0.3 | JSON 序列化/反序列化 |
| .NET | 8.0 | 运行时框架 |

---

## 三、核心流程一：Steam 登录

### 3.1 登录架构

SDA 的登录基于 **SteamKit2** 库，该库实现了完整的 Steam 认证协议。登录流程在 `LoginForm.cs` 中实现，二次验证回调在 `UserFormAuthenticator.cs` 中处理。

```
┌──────────────┐     ┌───────────────────┐     ┌─────────────────────┐
│  LoginForm   │────→│    SteamKit2      │────→│  Steam 服务器        │
│  (UI 输入)   │     │  (协议实现)        │     │  (认证服务)          │
└──────────────┘     └───────────────────┘     └─────────────────────┘
                            │
                     ┌──────▼──────┐
                     │UserFormAuth │
                     │(二次验证回调)│
                     └─────────────┘
```

### 3.2 三种登录模式

```csharp
public enum LoginType { Initial, Refresh, Import }
```

| 模式 | 触发场景 | 登录后行为 |
|------|----------|-----------|
| **Initial** | 首次添加新账户 | 绑定验证器 → 生成 maFile |
| **Refresh** | 刷新已有账户的会话 | 更新 SessionData → 保存到现有 maFile |
| **Import** | 导入已有 maFile | 仅获取 SessionData，不绑定验证器 |

### 3.3 完整登录链路

```
用户输入账号密码
    │
    ▼
① SteamClient.Connect()
    │  连接 Steam 服务器（CM 服务器）
    │
    ▼
② Authentication.BeginAuthSessionViaCredentialsAsync()
    │  参数: username, password, platformType = MobileApp
    │  ⚠️ 平台类型设为 MobileApp，模拟手机登录
    │
    ▼
③ UserFormAuthenticator (IAuthenticator 接口实现)
    │
    ├── GetDeviceCodeAsync()
    │   ├── 如果账户已有 maFile:
    │   │   └── account.GenerateSteamGuardCodeAsync() 自动生成 TOTP 码
    │   │       └── 码错？等待 30 秒（下一个时间步长）后自动重试
    │   └── 如果账户无 maFile:
    │       └── 弹出输入框让用户手动输入验证码
    │
    ├── GetEmailCodeAsync()
    │   └── 弹出输入框让用户输入邮件验证码
    │
    └── AcceptDeviceConfirmationAsync()
        └── 返回 false（不自动确认，需用户手动操作）
    │
    ▼
④ authSession.PollingWaitForResultAsync()
    │  轮询等待认证结果
    │
    ▼
⑤ 构建 SessionData
    │  {
    │    SteamID: ulong,         // 64 位 Steam ID
    │    AccessToken: string,    // JWT 访问令牌
    │    RefreshToken: string,   // JWT 刷新令牌
    │    SessionID: string       // 32 位随机 hex
    │  }
    │
    ▼
⑥ 根据 LoginType 执行后续操作
    ├── Initial → 进入验证器绑定流程（见第四章）
    ├── Refresh → 更新现有 maFile 中的 Session
    └── Import  → 仅保存 SessionData
```

### 3.4 二次验证回调 (UserFormAuthenticator)

`UserFormAuthenticator` 实现了 SteamKit2 的 `IAuthenticator` 接口，是登录过程中处理二次验证的关键组件：

```csharp
internal class UserFormAuthenticator : IAuthenticator
{
    private SteamGuardAccount account;

    // 如果账户已绑验证器，自动生成 TOTP 码
    public async Task<string> GetDeviceCodeAsync()
    {
        if (account != null)
        {
            string code = await account.GenerateSteamGuardCodeAsync();
            // 如果码错误，等待 30 秒后重试（下一个时间步长）
            return code;
        }
        // 无账户则弹出输入框
        return await ShowInputBoxAsync("请输入验证码");
    }

    // 邮件验证码
    public async Task<string> GetEmailCodeAsync(string email)
    {
        return await ShowInputBoxAsync($"请输入发送到 {email} 的验证码");
    }
}
```

**关键设计**：如果已有 maFile（即 `SharedSecret`），二次验证完全自动化，用户无需手动输入任何验证码。

---

## 四、核心流程二：验证器绑定与 maFile 生成

### 4.1 绑定流程 (AuthenticatorLinker)

`AuthenticatorLinker` 类负责将新的移动验证器绑定到 Steam 账户，是 maFile 生成的核心。

```
┌──────────────────────────────────────────────────────────┐
│                  AuthenticatorLinker                      │
│                                                          │
│  属性:                                                    │
│  ├── Session: SessionData        ← 登录后的会话          │
│  ├── PhoneNumber: string         ← 用户手机号            │
│  ├── DeviceID: string            ← "android:{GUID}"     │
│  └── LinkedAccount: SteamGuardAccount ← 绑定后的账户数据 │
│                                                          │
│  方法:                                                    │
│  ├── AddAuthenticator()          ← 第一步：初始化绑定    │
│  └── FinalizeAddAuthenticator()  ← 第二步：SMS 确认完成  │
└──────────────────────────────────────────────────────────┘
```

### 4.2 两步绑定详解

#### 第一步：AddAuthenticator()

```
调用 ITwoFactorService/AddAuthenticator API
    │
    ├── 参数:
    │   ├── steamid              ← 用户 SteamID
    │   ├── authenticator_type   ← 1 (移动验证器)
    │   ├── authenticator_time   ← 当前 Steam 时间
    │   ├── device_identifier    ← "android:{GUID}"
    │   └── sms_phone_id         ← 手机号标识
    │
    └── 返回状态处理:
        │
        ├── Status 2: 账户无手机号
        │   └── 弹出 PhoneInputForm → 用户输入手机号
        │       └── IPhoneService/SetAccountPhoneNumber API
        │
        ├── 邮箱未确认
        │   └── 等待用户确认邮箱
        │       └── ISteamUserAuth/IsAccountWaitingForEmailConfirmation API
        │
        ├── Status 29: 验证器已存在
        │   └── 提示错误，终止流程
        │
        ├── Status 1: 成功 → AwaitingFinalization
        │   └── ⚠️ 此时 LinkedAccount 已包含 SharedSecret 等关键数据
        │       ⚠️ 必须在此步和 Finalize 之间保存！
        │
        └── 其他错误 → GeneralFailure
```

#### 第二步：FinalizeAddAuthenticator(smsCode)

```
用户输入收到的 SMS 验证码
    │
    ▼
循环调用（最多 10 次重试）:
    │
    ├── 调用 ITwoFactorService/FinalizeAddAuthenticator API
    │   参数:
    │   ├── steamid              ← 用户 SteamID
    │   ├── authenticator_code   ← 当前 TOTP 验证码（自动生成）
    │   ├── authenticator_time   ← 当前 Steam 时间
    │   ├── activation_code      ← SMS 验证码（用户输入）
    │   └── validate_sms_code    ← 1（验证 SMS）
    │
    └── 状态处理:
        ├── Status 89: SMS 码错误 → 提示用户重新输入
        ├── Status 88: 时间偏差 → 等待后重试（最多 10 次）
        ├── WantMore: 需要继续等待
        └── Success: 绑定完成！
            │
            ▼
        Manifest.SaveAccount() → 写入 {SteamID}.maFile
```

**容错设计**：Finalize 最多重试 10 次，专门处理本地时间与 Steam 服务器时间偏差的问题（Status 88）。每次重试都会重新生成 TOTP 码并使用最新的 Steam 时间。

### 4.3 maFile 文件结构

maFile 是 JSON 文件，文件名格式为 `{SteamID}.maFile`，存储在 `maFiles/` 目录下：

```json
{
    "shared_secret": "Base64编码的共享密钥（用于TOTP验证码生成）",
    "serial_number": "设备序列号",
    "revocation_code": "R12345（撤销验证器时使用）",
    "uri": "otpauth://totp/Steam:username?secret=...&issuer=Steam",
    "server_time": 1234567890,
    "account_name": "steam用户名",
    "token_gid": "Token GID",
    "identity_secret": "Base64编码的身份密钥（用于确认操作签名）",
    "secret_1": "额外密钥",
    "status": 1,
    "device_id": "android:xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "fully_enrolled": true,
    "Session": {
        "SteamID": 76561198xxxxxxxx,
        "AccessToken": "eyJhbGciOi...（JWT访问令牌）",
        "RefreshToken": "eyJhbGciOi...（JWT刷新令牌）",
        "SessionID": "a1b2c3d4e5f6...（32位随机hex）"
    }
}
```

**关键密钥说明**：

| 密钥 | 编码 | 用途 | 重要性 |
|------|------|------|--------|
| `shared_secret` | Base64 | TOTP 验证码生成的 HMAC-SHA1 密钥 | ⭐⭐⭐ 最重要 |
| `identity_secret` | Base64 | 交易确认操作的 HMAC-SHA1 签名密钥 | ⭐⭐⭐ 最重要 |
| `revocation_code` | 明文 | 撤销验证器时的凭证 | ⭐⭐ 重要 |
| `device_id` | 明文 | 设备标识，格式 `android:{GUID}` | ⭐ 一般 |
| `Session` | JSON | 包含 AccessToken/RefreshToken | ⭐⭐ 重要（可重新登录获取） |

### 4.4 maFile 加密机制 (FileEncryptor)

SDA 提供可选的 AES-256-CBC 加密保护 maFile，防止本地文件被直接读取。

```
加密方案:
┌─────────────────────────────────────────────────┐
│  密钥派生: PBKDF2 (RFC2898)                      │
│  ├── 输入: 用户密码 + 8字节随机盐                │
│  ├── 迭代次数: 50,000                            │
│  ├── 算法: HMAC-SHA1                            │
│  └── 输出: 32 字节 AES 密钥                     │
│                                                  │
│  加密算法: AES-256-CBC                           │
│  ├── 密钥: 32 字节（PBKDF2 派生）                │
│  ├── IV: 16 字节随机                            │
│  ├── 填充: PKCS7                                │
│  └── 输出: Base64 编码密文                       │
└─────────────────────────────────────────────────┘
```

**加密后的 maFile 示例**：

```json
{
    "shared_secret": "Base64编码的AES密文...",
    "identity_secret": "Base64编码的AES密文...",
    "...": "所有字段都被加密"
}
```

**加密参数存储位置**：盐值和 IV 存储在 `manifest.json` 的 `entries` 数组中，每个账户独立：

```json
{
    "encrypted": true,
    "entries": [{
        "filename": "76561198xxx.maFile",
        "steamid": 76561198xxx,
        "encryption_iv": "Base64编码的16字节IV",
        "encryption_salt": "Base64编码的8字节盐"
    }]
}
```

### 4.5 Manifest 管理体系

`Manifest` 类是所有 maFile 的索引和管理中心：

```
maFiles/
├── manifest.json          ← Manifest 索引文件
├── 76561198xxx.maFile     ← 账户1的验证数据
├── 76561199yyy.maFile     ← 账户2的验证数据
└── ...
```

**Manifest 核心方法**：

| 方法 | 功能 |
|------|------|
| `GetManifest()` | 加载/获取 manifest 单例 |
| `GenerateNewManifest()` | 生成新 manifest，扫描现有 maFile |
| `GetAllAccounts()` | 加载所有账户（自动解密） |
| `SaveAccount()` | 保存账户到 maFile |
| `RemoveAccount()` | 移除账户及对应 maFile |
| `ChangeEncryptionKey()` | 更换加密密钥（重新加密所有 maFile） |
| `PromptForPassKey()` | 弹出密码输入框 |

---

## 五、核心流程三：TOTP 验证码生成

### 5.1 算法实现

SDA 的 TOTP（基于时间的一次性密码）验证码生成完全遵循 RFC 6238 标准，使用 Steam 自定义字符集。

```
GenerateSteamGuardCodeForTime(time)
    │
    ├── ① Base64 解码 SharedSecret → 20 字节密钥
    │
    ├── ② 时间转换: time /= 30 → 30 秒时间步长
    │   └── 转为 8 字节大端序数组 (big-endian)
    │
    ├── ③ HMAC-SHA1 计算
    │   ├── 密钥: SharedSecret (20 字节)
    │   ├── 数据: 时间数组 (8 字节)
    │   └── 输出: 20 字节哈希值
    │
    ├── ④ 动态截取 (Dynamic Truncation, RFC 4226)
    │   ├── offset = hash[19] & 0x0F        ← 取最后一个字节的低4位
    │   ├── codePoint =                      ← 拼接4字节为31位整数
    │   │   (hash[offset]   & 0x7F) << 24   ← 最高位清零（确保正数）
    │   │ | (hash[offset+1] & 0xFF) << 16
    │   │ | (hash[offset+2] & 0xFF) << 8
    │   │ | (hash[offset+3] & 0xFF)
    │   └── 结果: 0 ~ 2^31-1 范围的整数
    │
    └── ⑤ 转换为 Steam 字符集（连续取模5次 → 5位验证码）
        字符集: 2 3 4 5 6 7 8 9 B C D F G H J K M N P Q R T V W X Y
        │
        ├── codeArray[0] = steamGuardCodeTranslations[codePoint % 26]
        ├── codePoint /= 26
        ├── codeArray[1] = steamGuardCodeTranslations[codePoint % 26]
        ├── codePoint /= 26
        ├── ...（重复5次）
        └── 输出: "X7B2K" (5位验证码)
```

### 5.2 时间同步 (TimeAligner)

TOTP 码依赖准确的时间，`TimeAligner` 确保本地时间与 Steam 服务器同步：

```
首次调用 GetSteamTime()
    │
    ├── ① 记录本地时间: currentTime = GetSystemUnixTime()
    │
    ├── ② 向 Steam API 查询服务器时间
    │   └── POST https://api.steampowered.com/ITwoFactorService/QueryTime/v0001
    │       参数: steamid=0
    │       响应: { "response": { "server_time": 1234567890 } }
    │
    ├── ③ 计算时差: timeDifference = serverTime - currentTime
    │
    └── ④ 缓存时差（静态变量，只需对齐一次）
        └── 后续: steamTime = localTime + timeDifference
```

**容错**：网络异常时静默失败，使用本地时间（可能导致验证码不正确，但不会崩溃）。

### 5.3 Steam 字符集

Steam 使用自定义的 26 字符集（排除了容易混淆的字符）：

```
标准 TOTP: 0-9 (10个数字)
Steam TOTP: 2 3 4 5 6 7 8 9 B C D F G H J K M N P Q R T V W X Y (26个字符)

排除的字符: 0, 1, A, E, I, L, O, S, U, Z
排除原因:   容易与数字或其他字母混淆
  0 ↔ O, 1 ↔ I ↔ L, A ↔ 4, E ↔ 3, S ↔ 5, U ↔ V, Z ↔ 2
```

---

## 六、核心流程四：交易确认 (Trade Confirmations)

### 6.1 确认签名机制

所有交易确认操作都需要使用 `IdentitySecret` 生成 HMAC-SHA1 签名，防止请求伪造：

```
_generateConfirmationHashForTime(time, tag)
    │
    ├── ① Base64 解码 IdentitySecret → 密钥
    │
    ├── ② 拼接签名数据
    │   ├── 前 8 字节: 时间戳（大端序）
    │   └── 后 N 字节: tag 字符串（如 "conf", "details", "allow", "cancel"）
    │
    ├── ③ HMAC-SHA1(IdentitySecret, 数据) → 20 字节哈希
    │
    └── ④ Base64 编码 + URL 编码 → 签名参数 "k"
```

**tag 值与用途**：

| tag | 用途 |
|-----|------|
| `conf` | 获取确认列表 |
| `details` | 获取确认详情 |
| `allow` | 接受确认 |
| `cancel` | 拒绝确认 |
| `revoke` | 撤销确认（批量操作） |

### 6.2 获取确认列表

```
FetchConfirmationsAsync()
    │
    ├── ① 生成确认 URL:
    │   https://steamcommunity.com/mobileconf/getlist?
    │   ├── p = {DeviceID}           ← 设备 ID
    │   ├── a = {SteamID}            ← Steam ID
    │   ├── k = {HMAC签名}           ← IdentitySecret 签名
    │   ├── t = {时间戳}             ← Steam 时间
    │   ├── m = react                ← 移动端标识
    │   └── tag = conf               ← 操作类型
    │
    ├── ② 发送 GET 请求（携带 Cookie）
    │   ├── steamLoginSecure: {SteamID}||{AccessToken}
    │   ├── sessionid: {SessionID}
    │   ├── mobileClient: android
    │   └── mobileClientVersion: 777777 3.6.1
    │
    └── ③ 解析 JSON 响应 → Confirmation[] 数组
```

### 6.3 确认操作

#### 单个确认操作

```
AcceptConfirmation(conf) / DenyConfirmation(conf)
    │
    ├── 生成操作 URL:
    │   https://steamcommunity.com/mobileconf/ajaxop?
    │   ├── op = allow (接受) / cancel (拒绝)
    │   ├── cid = {conf.ID}          ← 确认 ID
    │   ├── ck = {conf.Key}          ← 确认的 nonce
    │   ├── k = {HMAC签名}           ← IdentitySecret 签名（tag="allow"/"cancel"）
    │   ├── t = {时间戳}
    │   ├── p = {DeviceID}
    │   ├── a = {SteamID}
    │   └── m = react
    │
    └── GET 请求 → 检查响应中的 success 字段
```

#### 批量确认操作

```
AcceptMultipleConfirmations(confs) / DenyMultipleConfirmations(confs)
    │
    ├── 生成批量操作 URL:
    │   https://steamcommunity.com/mobileconf/multiajaxop?
    │   ├── op = allow (接受) / cancel (拒绝)
    │   ├── cid = {id1},{id2},{id3}  ← 逗号分隔的确认 ID
    │   ├── ck = {key1},{key2},{key3} ← 逗号分隔的 nonce
    │   ├── k = {HMAC签名}           ← tag = "revoke"
    │   └── ...其他参数同上
    │
    └── GET 请求 → 检查响应中的 success 字段
```

### 6.4 确认类型

```csharp
public enum EMobileConfirmationType
{
    Invalid = 0,           // 无效
    Test = 1,              // 测试
    Trade = 2,             // 交易报价
    MarketListing = 3,     // 市场挂单
    FeatureOptOut = 4,     // 功能退出
    PhoneNumberChange = 5, // 手机号变更
    AccountRecovery = 6    // 账户恢复
}
```

### 6.5 UI 层处理

#### ConfirmationFormWeb（确认列表界面）

```
LoadData()
    │
    ├── ① 检查 RefreshToken 是否过期
    │   └── 过期 → 提示用户重新登录
    │
    ├── ② 检查 AccessToken 是否过期
    │   └── 过期 → Session.RefreshAccessToken() 自动刷新
    │
    ├── ③ FetchConfirmationsAsync() 获取确认列表
    │
    └── ④ 为每个确认创建 UI 面板
        ├── 图标（交易/市场/手机号等）
        ├── 标题（Headline）
        ├── 摘要（Summary 列表）
        ├── 接受按钮 → AcceptConfirmation()
        └── 拒绝按钮 → DenyConfirmation()
```

#### TradePopupForm（系统托盘弹窗）

```
新确认到达时自动弹出
    │
    ├── 显示确认信息
    ├── 双击确认机制（防止误操作）
    │   ├── 第一次点击: 高亮按钮
    │   └── 第二次点击: 执行操作
    └── 处理完队列后自动隐藏
```

---

## 七、核心流程五：Token 与会话管理

### 7.1 Token 体系

SDA 使用 Steam 的 OAuth Token 体系管理会话：

```
SessionData
    │
    ├── SteamID (ulong)          ← 64 位 Steam ID（固定不变）
    ├── AccessToken (JWT)        ← API 访问令牌（短期有效，约 1 小时）
    ├── RefreshToken (JWT)       ← 刷新令牌（长期有效，约 90 天）
    └── SessionID (string)       ← 32 位随机 hex 会话 ID
```

**Token 关系图**：

```
用户登录
    │
    ├──→ AccessToken (短期) ──→ 用于 API 请求认证
    │         │                    │
    │         │ 过期               │ 每次请求携带
    │         ▼                    │
    │    RefreshAccessToken()      │
    │         │                    │
    │         └── 需要 RefreshToken │
    │                              │
    └──→ RefreshToken (长期) ──────┘
              │
              │ 过期（约90天）
              ▼
         需要用户重新登录
```

### 7.2 Token 刷新机制

```
RefreshAccessToken()
    │
    ├── POST https://api.steampowered.com/IAuthenticationService/GenerateAccessTokenForApp/v1/
    │
    ├── 请求参数:
    │   ├── refresh_token = {RefreshToken}
    │   └── steamid = {SteamID}
    │
    └── 响应: { "response": { "access_token": "新的JWT..." } }
        └── 更新 this.AccessToken
```

**刷新时机**：每次需要使用 AccessToken 时（如获取确认列表），先检查是否过期，过期则自动刷新。

### 7.3 JWT 过期检测

SDA 手动解码 JWT Token 检查过期时间，无需外部 JWT 库：

```
IsTokenExpired(token)
    │
    ├── ① 分割 JWT: header.payload.signature
    │   └── 只需要中间的 payload 部分
    │
    ├── ② Base64Url 解码 payload
    │   ├── 替换: '-' → '+', '_' → '/'
    │   ├── 补齐: 末尾填充 '=' 至长度为4的倍数
    │   └── Base64 解码 → JSON 字符串
    │
    ├── ③ JSON 反序列化 → 提取 exp 字段
    │   └── { "exp": 1234567890, "iat": ..., ... }
    │
    └── ④ 比较: 当前Unix时间 > exp → 已过期
```

### 7.4 Cookie 生成

所有 Steam 社区请求都需要携带特定的 Cookie，模拟 Android 客户端：

```
GetCookies()
    │
    ├── steamLoginSecure = {SteamID}||{AccessToken}
    │   └── 例: "76561198xxx%7C%7CeyJhbGciOi..."
    │   └── %7C%7C 是 "||" 的 URL 编码
    │
    ├── sessionid = 随机32位hex
    │   └── 例: "a1b2c3d4e5f6789012345678abcdef90"
    │
    ├── mobileClient = android
    │
    └── mobileClientVersion = 777777 3.6.1
        └── 模拟 Steam Android App 版本号
```

---

## 八、HTTP 通信层

### 8.1 移动客户端模拟

整个 SteamAuth 库的核心策略是 **完美模拟 Android Steam 客户端**，使 Steam 服务器无法区分请求来自真实手机还是桌面应用：

| 参数 | 值 | 目的 |
|------|-----|------|
| User-Agent | `okhttp/3.12.12` | 模拟 Android HTTP 库 |
| mobileClient | `android` | 声明为移动客户端 |
| mobileClientVersion | `777777 3.6.1` | 模拟 Steam App 版本 |
| DeviceID | `android:{GUID}` | 模拟 Android 设备 |
| 登录平台 | `MobileApp` | SteamKit2 登录时声明为手机 |

### 8.2 请求流程

```
SteamWeb (静态工具类)
    │
    ├── GETRequest(url, cookies)
    │   └── CookieAwareWebClient.DownloadStringTaskAsync(url)
    │
    └── POSTRequest(url, cookies, body)
        └── CookieAwareWebClient.UploadValuesTaskAsync(url, "POST", body)
            │
            ▼
    CookieAwareWebClient (继承 System.Net.WebClient)
        ├── 注入 CookieContainer（请求时）
        └── 捕获响应 Cookie（响应时）
            │
            ▼
    所有请求自动携带:
        ├── Header: User-Agent = okhttp/3.12.12
        └── Cookie: steamLoginSecure + sessionid + mobileClient + mobileClientVersion
```

### 8.3 Cookie 管理

`CookieAwareWebClient` 继承自 `System.Net.WebClient`，解决了默认 WebClient 不支持 Cookie 的问题：

```csharp
public class CookieAwareWebClient : WebClient
{
    public CookieContainer CookieContainer { get; set; }

    protected override WebRequest GetWebRequest(Uri address)
    {
        var request = (HttpWebRequest)base.GetWebRequest(address);
        request.CookieContainer = CookieContainer;  // 注入 Cookie
        return request;
    }

    protected override WebResponse GetWebResponse(WebRequest request)
    {
        var response = (HttpWebResponse)base.GetWebResponse(request);
        this.ResponseCookies = response.Cookies;    // 捕获响应 Cookie
        return response;
    }
}
```

---

## 九、为什么 SDA 能完全打通所有流程

### 9.1 协议还原精确

SDA 对 Steam 移动验证协议的还原非常精确：

| 还原点 | 实现方式 |
|--------|---------|
| 登录协议 | 使用 SteamKit2（成熟的 Steam 协议库），平台声明为 `MobileApp` |
| HTTP 请求 | User-Agent、Cookie、DeviceID 全部伪装为 Android 客户端 |
| API 端点 | 所有端点与 Steam 官方移动客户端一致 |
| 数据格式 | JSON 序列化/反序列化与 Steam API 完全兼容 |

### 9.2 密码学实现正确

| 算法 | 用途 | 标准 |
|------|------|------|
| HMAC-SHA1 + TOTP | 验证码生成 | RFC 6238 / RFC 4226 |
| HMAC-SHA1 + IdentitySecret | 确认操作签名 | Steam 自定义协议 |
| AES-256-CBC + PBKDF2 | maFile 加密 | RFC 2898 / NIST SP 800-38A |
| JWT | Token 格式 | RFC 7519 |

### 9.3 Token 生命周期管理完善

```
┌──────────┐    过期(约1h)    ┌──────────────────┐
│AccessToken│ ──────────────→ │RefreshAccessToken │
└──────────┘                 └──────────────────┘
                                    │ 需要
                                    ▼
                             ┌──────────────┐
                             │ RefreshToken │
                             └──────────────┘
                                    │ 过期(约90天)
                                    ▼
                             ┌──────────────┐
                             │ 重新登录      │
                             └──────────────┘
```

- **自动刷新**：AccessToken 过期时自动通过 RefreshToken 获取新令牌
- **过期检测**：JWT 手动解码检查 `exp` 字段
- **优雅降级**：RefreshToken 过期时提示用户重新登录，而非崩溃

### 9.4 容错设计周到

| 场景 | 处理方式 |
|------|---------|
| Finalize 时间偏差 (Status 88) | 最多重试 10 次，每次使用最新 Steam 时间 |
| TOTP 验证码错误 | 等待 30 秒（下一个时间步长）后自动重试 |
| AccessToken 过期 | 自动调用 RefreshAccessToken() |
| RefreshToken 过期 | 提示用户重新登录 |
| 时间同步失败 | 静默失败，使用本地时间（可能不准确但不崩溃） |
| 网络请求失败 | 捕获异常，显示错误信息 |
| 加密密码错误 | 返回 null，提示用户重新输入 |

### 9.5 本地存储安全

| 安全措施 | 说明 |
|---------|------|
| AES-256-CBC 加密 | maFile 可选加密，防止本地文件被直接读取 |
| PBKDF2 密钥派生 | 50,000 次迭代，防止暴力破解密码 |
| 随机盐值 | 每个账户独立盐值，防止彩虹表攻击 |
| 随机 IV | 每次加密使用新 IV，相同明文产生不同密文 |
| 撤销码保存 | `RevocationCode` 保存在 maFile 中，可随时撤销验证器 |

---

## 十、完整数据流总结

```
┌─────────────────────────────────────────────────────────────────────┐
│                        SDA 完整数据流                                │
│                                                                     │
│  ┌──────────┐     ┌──────────────┐     ┌────────────────────┐      │
│  │ 用户登录  │────→│ 获取 Session  │────→│ 绑定验证器          │      │
│  │ 密码+2FA │     │ Token 体系    │     │ AddAuth+Finalize   │      │
│  └──────────┘     └──────┬───────┘     └────────┬───────────┘      │
│                          │                      │                   │
│                          ▼                      ▼                   │
│                   ┌──────────────┐     ┌────────────────────┐      │
│                   │ 保存 maFile   │     │ 生成 TOTP 验证码    │      │
│                   │ JSON+AES加密  │     │ 30秒周期自动刷新    │      │
│                   └──────────────┘     └────────┬───────────┘      │
│                                                 │                   │
│                          ┌──────────────────────┘                   │
│                          ▼                                          │
│                   ┌────────────────────┐     ┌────────────────┐    │
│                   │ 获取交易确认列表    │────→│ 接受/拒绝确认   │    │
│                   │ HMAC签名请求       │     │ HMAC签名操作   │    │
│                   └────────────────────┘     └────────────────┘    │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │ 底层支撑:                                                     │  │
│  │ • TimeAligner: 时间同步（确保TOTP准确）                       │  │
│  │ • SteamWeb: HTTP通信（模拟Android客户端）                     │  │
│  │ • SessionData: Token管理（自动刷新、过期检测）                 │  │
│  │ • FileEncryptor: 本地加密（AES-256-CBC + PBKDF2）            │  │
│  └──────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 附录：核心文件索引

### 主项目文件

| 文件 | 核心职责 | 关键类/方法 |
|------|---------|------------|
| `LoginForm.cs` | Steam 登录 | `LoginType` 枚举、登录流程、验证器绑定触发 |
| `UserFormAuthenticator.cs` | 二次验证回调 | `GetDeviceCodeAsync()`、`GetEmailCodeAsync()` |
| `MainForm.cs` | 主界面 | 验证码显示、账户列表、定时器 |
| `Manifest.cs` | maFile 管理 | `SaveAccount()`、`GetAllAccounts()`、`ChangeEncryptionKey()` |
| `FileEncryptor.cs` | 加密引擎 | `EncryptData()`、`DecryptData()`、PBKDF2+AES-256-CBC |
| `ConfirmationFormWeb.cs` | 确认界面 | `LoadData()`、接受/拒绝按钮事件 |
| `TradePopupForm.cs` | 确认弹窗 | 双击确认机制、队列处理 |
| `ImportAccountForm.cs` | 账户导入 | 导入加密/未加密 maFile |
| `Program.cs` | 程序入口 | 单实例检测、命令行参数 |

### SteamAuth 库文件

| 文件 | 核心职责 | 关键类/方法 |
|------|---------|------------|
| `SteamGuardAccount.cs` | 验证账户中枢 | `GenerateSteamGuardCode()`、`FetchConfirmations()`、`AcceptConfirmation()` |
| `AuthenticatorLinker.cs` | 验证器绑定 | `AddAuthenticator()`、`FinalizeAddAuthenticator()` |
| `SessionData.cs` | Token/会话管理 | `RefreshAccessToken()`、`IsTokenExpired()`、`GetCookies()` |
| `Confirmation.cs` | 确认数据模型 | `Confirmation` 类、`EMobileConfirmationType` 枚举 |
| `TimeAligner.cs` | 时间同步 | `GetSteamTime()`、`AlignTime()` |
| `SteamWeb.cs` | HTTP 通信 | `GETRequest()`、`POSTRequest()` |
| `APIEndpoints.cs` | API 端点 | `STEAMAPI_BASE`、`COMMUNITY_BASE`、`TWO_FACTOR_TIME_QUERY` |
| `CookieAwareWebClient.cs` | Cookie 管理 | 继承 WebClient，注入/捕获 Cookie |
| `Util.cs` | 工具 | `GetSystemUnixTime()` |

### 关键 API 端点

| API | 用途 |
|-----|------|
| `ITwoFactorService/AddAuthenticator` | 初始化验证器绑定 |
| `ITwoFactorService/FinalizeAddAuthenticator` | 完成验证器绑定（SMS 确认） |
| `ITwoFactorService/QueryTime` | 查询 Steam 服务器时间 |
| `IAuthenticationService/GenerateAccessTokenForApp` | 刷新 AccessToken |
| `IMobileAuthService/GetWGToken` | 获取移动验证 Token |
| `steamcommunity.com/mobileconf/getlist` | 获取确认列表 |
| `steamcommunity.com/mobileconf/ajaxop` | 单个确认操作 |
| `steamcommunity.com/mobileconf/multiajaxop` | 批量确认操作 |
| `IPhoneService/SetAccountPhoneNumber` | 设置手机号 |
| `ISteamUserAuth/IsAccountWaitingForEmailConfirmation` | 检查邮箱确认状态 |
| `IPhoneService/SendPhoneVerificationCode` | 发送手机验证码 |

---

> **分析完成时间**: 2026-04-08
>
> **项目版本**: SteamDesktopAuthenticator v1.0.15 (最新)
>
> **SteamAuth 子模块**: geel9/SteamAuth @ commit 01c0c9b
