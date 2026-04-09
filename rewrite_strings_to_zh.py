import re

with open("app/src/main/res/values/strings_add_account.xml", "r") as f:
    content = f.read()

replacements = {
    r'<string name="route_title_steam_add_authenticator">.*?</string>': r'<string name="route_title_steam_add_authenticator">添加账号</string>',
    r'<string name="route_short_steam_add_authenticator">.*?</string>': r'<string name="route_short_steam_add_authenticator">登录</string>',
    r'<string name="route_title_steam_authenticator_binding">.*?</string>': r'<string name="route_title_steam_authenticator_binding">绑定准备</string>',
    r'<string name="route_short_steam_authenticator_binding">.*?</string>': r'<string name="route_short_steam_authenticator_binding">绑定</string>',
    
    r'<string name="import_login_path_title">.*?</string>': r'<string name="import_login_path_title">Steam 登录 (推荐)</string>',
    r'<string name="import_login_path_description">.*?</string>': r'<string name="import_login_path_description">直接登录您的 Steam 账号并绑定新的手机令牌。</string>',
    r'<string name="import_login_path_action">.*?</string>': r'<string name="import_login_path_action">登录并添加令牌</string>',
    r'<string name="import_login_path_browser_fallback_action">.*?</string>': r'<string name="import_login_path_browser_fallback_action">备用：网页登录</string>',
    
    r'<string name="import_fallback_title">.*?</string>': r'<string name="import_fallback_title">手动导入</string>',
    r'<string name="import_fallback_description">.*?</string>': r'<string name="import_fallback_description">通过 maFile、JSON 或 URI 导入已有令牌备份。</string>',
    r'<string name="import_fallback_note">.*?</string>': r'<string name="import_fallback_note">如果您已经保存了令牌的密钥文件，请使用此选项。</string>',
    
    r'<string name="steam_add_authenticator_title">.*?</string>': r'<string name="steam_add_authenticator_title">登录 Steam</string>',
    r'<string name="steam_add_authenticator_overview_title">.*?</string>': r'<string name="steam_add_authenticator_overview_title">流程概览</string>',
    r'<string name="steam_add_authenticator_overview_description">.*?</string>': r'<string name="steam_add_authenticator_overview_description">按照提示步骤添加新的安全令牌。</string>',
    r'<string name="steam_add_authenticator_protocol_title">.*?</string>': r'<string name="steam_add_authenticator_protocol_title">账号登录</string>',
    r'<string name="steam_add_authenticator_protocol_description">.*?</string>': r'<string name="steam_add_authenticator_protocol_description">输入您的 Steam 账号密码以继续。</string>',
    r'<string name="steam_add_authenticator_protocol_username_label">.*?</string>': r'<string name="steam_add_authenticator_protocol_username_label">Steam 账号</string>',
    r'<string name="steam_add_authenticator_protocol_password_label">.*?</string>': r'<string name="steam_add_authenticator_protocol_password_label">密码</string>',
    r'<string name="steam_add_authenticator_protocol_note">.*?</string>': r'<string name="steam_add_authenticator_protocol_note">您的凭据仅会发送至官方 Steam 服务器。</string>',
    r'<string name="steam_add_authenticator_protocol_action">.*?</string>': r'<string name="steam_add_authenticator_protocol_action">登录</string>',
    r'<string name="steam_add_authenticator_protocol_action_loading">.*?</string>': r'<string name="steam_add_authenticator_protocol_action_loading">登录中...</string>',
    
    r'<string name="steam_add_authenticator_protocol_qr_action">.*?</string>': r'<string name="steam_add_authenticator_protocol_qr_action">扫码登录</string>',
    r'<string name="steam_add_authenticator_protocol_qr_action_loading">.*?</string>': r'<string name="steam_add_authenticator_protocol_qr_action_loading">等待扫码批准...</string>',
    r'<string name="steam_add_authenticator_protocol_qr_preview_title">.*?</string>': r'<string name="steam_add_authenticator_protocol_qr_preview_title">Steam 扫码登录</string>',
    r'<string name="steam_add_authenticator_protocol_qr_preview_description">.*?</string>': r'<string name="steam_add_authenticator_protocol_qr_preview_description">请使用已登录的 Steam 手机 App 扫描此二维码。</string>',
    r'<string name="steam_add_authenticator_protocol_qr_cancel_action">.*?</string>': r'<string name="steam_add_authenticator_protocol_qr_cancel_action">取消扫码</string>',
    
    r'<string name="steam_add_authenticator_challenge_email_title">.*?</string>': r'<string name="steam_add_authenticator_challenge_email_title">需要邮箱验证</string>',
    r'<string name="steam_add_authenticator_challenge_device_code_title">.*?</string>': r'<string name="steam_add_authenticator_challenge_device_code_title">需要 Steam 令牌</string>',
    r'<string name="steam_add_authenticator_challenge_device_confirmation_title">.*?</string>': r'<string name="steam_add_authenticator_challenge_device_confirmation_title">在手机上批准登录</string>',
    
    r'<string name="steam_add_authenticator_challenge_code_label">.*?</string>': r'<string name="steam_add_authenticator_challenge_code_label">验证码</string>',
    r'<string name="steam_add_authenticator_challenge_submit_action">.*?</string>': r'<string name="steam_add_authenticator_challenge_submit_action">提交</string>',
    r'<string name="steam_add_authenticator_challenge_cancel_action">.*?</string>': r'<string name="steam_add_authenticator_challenge_cancel_action">取消</string>',
    
    r'<string name="import_form_title">.*?</string>': r'<string name="import_form_title">导入令牌配置</string>',
    r'<string name="import_label_raw_payload">.*?</string>': r'<string name="import_label_raw_payload">配置文本 (JSON/URI)</string>',
    r'<string name="import_placeholder_raw_payload">.*?</string>': r'<string name="import_placeholder_raw_payload">在此粘贴 maFile、JSON 或 otpauth URI</string>',
    r'<string name="import_label_manual_account_name">.*?</string>': r'<string name="import_label_manual_account_name">账号名称</string>',
    r'<string name="import_label_manual_shared_secret">.*?</string>': r'<string name="import_label_manual_shared_secret">Shared Secret</string>',
    r'<string name="import_action_idle">.*?</string>': r'<string name="import_action_idle">保存令牌</string>',
    r'<string name="import_action_loading">.*?</string>': r'<string name="import_action_loading">保存中...</string>',
}

for pattern, replacement in replacements.items():
    content = re.sub(pattern, replacement, content)

with open("app/src/main/res/values/strings_add_account.xml", "w") as f:
    f.write(content)

