# 开发环境启动脚本
# 用法：在仓库根目录执行  powershell -ExecutionPolicy Bypass -File scripts\dev.ps1

$root = Split-Path -Parent $PSScriptRoot
Write-Host "==> 启动后端（H2 开发库，端口 8080）" -ForegroundColor Cyan

Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "cd '$root\\agv-backend'; `$env:MAVEN_OPTS='-Dfile.encoding=UTF-8'; mvn spring-boot:run"
)

Start-Sleep -Seconds 3

Write-Host "==> 启动前端（端口 5173）" -ForegroundColor Cyan
Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "cd '$root\\agv-frontend'; npm run dev"
)

Write-Host ""
Write-Host "后端: http://localhost:8080" -ForegroundColor Green
Write-Host "前端: http://localhost:5173" -ForegroundColor Green
Write-Host "账号: admin/admin123（管理员）  dispatcher/dispatcher123（调度人员）" -ForegroundColor Yellow
