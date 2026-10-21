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

# 国内直连 github.com 经常被重置：如果系统开着代理而 git 没配，就让 git 访问 GitHub 时走同一个代理
if (-not (git config --global --get http.https://github.com.proxy) -and -not (git config --global --get http.proxy)) {
    $ie = Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -ErrorAction SilentlyContinue
    if ($ie -and $ie.ProxyEnable -eq 1 -and $ie.ProxyServer) {
        $ps = [string]$ie.ProxyServer
        if ($ps -match 'https=([^;]+)') { $ps = $Matches[1] } elseif ($ps -match 'http=([^;]+)') { $ps = $Matches[1] }
        if ($ps -notmatch '^[a-z]+://') { $ps = "http://$ps" }
        git config --global http.https://github.com.proxy $ps
        Write-Host "检测到系统代理 $ps，已让 git 访问 GitHub 时使用它" -ForegroundColor Cyan
    } else {
        Write-Host "提示：没检测到系统代理。如果推送连不上 GitHub，请先打开代理软件（Clash / v2rayN 等）的「系统代理」再运行。" -ForegroundColor Yellow
    }
}

Write-Host "正在推送到 https://github.com/$user/$repo ..." -ForegroundColor Cyan
Write-Host "如果要求输入 Password：请粘贴 GitHub 个人访问令牌（ghp_ 开头），不是登录密码；粘贴时屏幕不显示是正常的。" -ForegroundColor Yellow
git push -u origin main
if ($LASTEXITCODE -ne 0) {
    Write-Host "推送失败。看上面的英文报错：" -ForegroundColor Red
    Write-Host "  · Connection was reset / Couldn't connect / timed out → 网络问题：打开代理的系统代理，或手动设置：git config --global http.https://github.com.proxy http://127.0.0.1:端口" -ForegroundColor Red
    Write-Host "  · Authentication failed / Invalid username or token → 令牌问题：到 https://github.com/settings/tokens/new 重新生成（勾选 repo 和 workflow）" -ForegroundColor Red
    exit 1
}

$tag = Read-Host "要发布一个可下载的 APK 版本吗？输入版本号如 v1.2（直接回车跳过）"
if ($tag) {
    git tag $tag
    git push origin $tag
    Write-Host "已推送标签 $tag，几分钟后在 https://github.com/$user/$repo/releases 下载 APK" -ForegroundColor Green
}
Write-Host "完成！" -ForegroundColor Green
