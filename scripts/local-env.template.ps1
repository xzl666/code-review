# Copy these commands into PowerShell after replacing the placeholder values.
# Restart the backend after setting user-level environment variables.

[Environment]::SetEnvironmentVariable("CODE_REVIEW_DEEPSEEK_API_KEY", "replace-with-deepseek-api-key", "User")
[Environment]::SetEnvironmentVariable("CODE_REVIEW_DEEPSEEK_BASE_URL", "https://zhenze-huhehaote.cmecloud.cn", "User")
[Environment]::SetEnvironmentVariable("CODE_REVIEW_DEEPSEEK_MODEL", "deepseek-v4-flash", "User")
[Environment]::SetEnvironmentVariable("CODE_REVIEW_GITEE_TOKEN", "replace-with-gitee-token", "User")

# Optional: make variables available in the current PowerShell session immediately.
$env:CODE_REVIEW_DEEPSEEK_API_KEY = [Environment]::GetEnvironmentVariable("CODE_REVIEW_DEEPSEEK_API_KEY", "User")
$env:CODE_REVIEW_DEEPSEEK_BASE_URL = [Environment]::GetEnvironmentVariable("CODE_REVIEW_DEEPSEEK_BASE_URL", "User")
$env:CODE_REVIEW_DEEPSEEK_MODEL = [Environment]::GetEnvironmentVariable("CODE_REVIEW_DEEPSEEK_MODEL", "User")
$env:CODE_REVIEW_GITEE_TOKEN = [Environment]::GetEnvironmentVariable("CODE_REVIEW_GITEE_TOKEN", "User")
