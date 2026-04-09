# Steam Vault App

[English](README.en.md)

[![Android CI](https://github.com/AreamSaber/steamvaultapp/actions/workflows/android-ci.yml/badge.svg)](https://github.com/AreamSaber/steamvaultapp/actions/workflows/android-ci.yml)
[![License](https://img.shields.io/github/license/AreamSaber/steamvaultapp)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84)](https://developer.android.com/)

Steam Vault App 是一个原生 Android Steam Guard 管理工具，重点覆盖本地安全存储、手机令牌工作流、交易确认、扫码登录批准以及加密备份。

本项目与 Valve 无官方关联。它是一个面向个人账户管理和 Steam Guard 相关流程研究实现的独立客户端工具。

## 功能亮点

- 使用主密码保护本地加密 Vault
- 基于 Argon2id 的密钥派生与 Vault Key 包装存储
- 支持从 maFile、JSON、otpauth URI 或手动 `shared_secret` 导入 Steam 令牌
- 支持离线生成 Steam Guard 动态码
- 支持本地保存、修复和刷新 Steam 移动端会话
- 支持 Steam 协议登录与 Steam Guard 挑战处理
- 支持新增并完成 Steam 手机验证器绑定
- 支持拉取、批准和拒绝 Steam confirmations
- 支持选择账户来批准或拒绝 Steam 扫码登录请求
- 支持导出加密本地备份
- 支持上传和恢复加密 WebDAV 云备份

## 项目状态

仓库已经超过早期原型阶段，当前代码已覆盖：

- 原生 Android Compose 应用骨架
- 本地 Vault、解锁和安全设置流程
- Steam 令牌导入与详情页流程
- Steam Session 持久化与修复流程
- confirmations 与二维码批准流程
- 本地备份与 WebDAV 云备份流程

当前仍在继续收口的部分，主要集中在锁定/退出后的备份时机，以及少数 Steam Session 恢复边界。

## 安全模型

- 令牌敏感材料存放在加密 Vault 中，而不是项目明文文件
- `shared_secret`、`identity_secret`、access token、refresh token、maFile 和备份导出文件都不应提交到源码仓库
- 本地备份和云备份内容均为加密载荷，仓库中只保留应用代码和文档

## 技术栈

- Kotlin
- Jetpack Compose
- Android Keystore
- WorkManager
- OkHttp
- WebDAV

## 仓库结构

- `app/`：Android 应用源码
- `docs/`：产品、架构和实现文档
- `gradle/`：Gradle Wrapper 与版本目录

本地用于参考的 SDA、SteamKit 副本在当前工作区中已存在，但不会纳入 Git 跟踪。

## 构建方式

建议使用 Android Studio 打开工程，并确保 `local.properties` 指向有效的 Android SDK。

常用命令：

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:assembleDebug
```

## CI

GitHub Actions 当前提供一套基础 Android CI，在 `push` 和 `pull_request` 上运行：

- Debug Kotlin 编译
- Android 测试源码编译
- JVM 单元测试

需要模拟器或真机参与的测试，更适合在本地或专用设备环境里执行。

## 关键文档

- [产品规格](docs/PRODUCT_SPEC.md)
- [Android 开发任务清单](docs/ANDROID_DEVELOPMENT_TASKS.md)
- [Android 执行清单](docs/ANDROID_EXECUTION_CHECKLIST_P2_P4.md)
- [SDA 流程分析](docs/SDA_FLOW_ANALYSIS.md)
- [SDA 完整分析](docs/SDA_Complete_Analysis.md)

更多中文规划和实现记录可直接查看 [`docs/`](docs/) 目录。

## 致谢

本项目在架构思路、流程梳理和协议研究过程中，参考了以下上游项目：

- [SteamDesktopAuthenticator (SDA)](https://github.com/Jessecar96/SteamDesktopAuthenticator)
- [SteamKit](https://github.com/SteamRE/SteamKit)

## 安全注意事项

- 不要提交 `local.properties`
- 不要提交 `steam-vault-backup-*.json` 这类导出备份文件
- 不要提交真实 maFile 或任何包含 Steam 密钥材料的载荷
- 如果任何 Steam 密钥已经暴露，应视为泄露并及时在 Steam 侧进行轮换或撤销
