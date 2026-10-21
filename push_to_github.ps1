# 一键把本项目推送到 GitHub（Windows PowerShell）
# 用法：在本文件夹空白处 Shift+右键 →「在此处打开 PowerShell 窗口」，然后运行：
#   powershell -ExecutionPolicy Bypass -File .\push_to_github.ps1
# 首次推送前请先在 https://github.com/new 创建一个空仓库（名称与下面的 $repo 一致，不要勾选 README）。

$ErrorActionPreference = "Stop"
$user = "Jankin2020"
$repo = "learn_en_ja"

Set-Location $PSScriptRoot
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Host "没有找到 git，请先安装：https://git-scm.com/download/win （一路默认即可），装完重新打开 PowerShell 再运行。" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path ".git")) { git init -b main | Out-Null }

# GitHub Actions 自动构建配置：从 ci\build.yml 放到 .github\workflows\
if (Test-Path "ci\build.yml") {
    New-Item -ItemType Directory -Force ".github\workflows" | Out-Null
    Copy-Item "ci\build.yml" ".github\workflows\build.yml" -Force
}
if (-not (git config user.name))  { git config user.name  $user }
if (-not (git config user.email)) { git config user.email "$user@users.noreply.github.com" }

git add -A
$changes = git status --porcelain
if ($changes) {
    $msg = Read-Host "提交说明（直接回车 = Update VoxCoach）"
    if (-not $msg) { $msg = "Update VoxCoach" }
    git commit -m $msg | Out-Null
}

$remotes = git remote
if (-not ($remotes -contains "origin")) { git remote add origin "https://github.com/$user/$repo.git" }

# 优先使用 Git Credential Manager（浏览器登录）；没有的话会要求输入用户名 + 令牌
if (-not (git config --global credential.helper)) { git config --global credential.helper manager 2>$null }

Write-Host "正在推送到 https://github.com/$user/$repo ..." -ForegroundColor Cyan
Write-Host "如果要求输入 Password：请粘贴 GitHub 个人访问令牌（ghp_ 开头），不是登录密码；粘贴时屏幕不显示是正常的。" -ForegroundColor Yellow
git push -u origin main
if ($LASTEXITCODE -ne 0) {
    Write-Host "推送失败。请到 https://github.com/settings/tokens/new 生成令牌（勾选 repo 和 workflow），然后重新运行本脚本。" -ForegroundColor Red
    exit 1
}

$tag = Read-Host "要发布一个可下载的 APK 版本吗？输入版本号如 v1.2（直接回车跳过）"
if ($tag) {
    git tag $tag
    git push origin $tag
    Write-Host "已推送标签 $tag，几分钟后在 https://github.com/$user/$repo/releases 下载 APK" -ForegroundColor Green
}
Write-Host "完成！" -ForegroundColor Green
