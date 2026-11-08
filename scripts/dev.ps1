# 开发环境启动脚本
# 用法：在仓库根目录执行  powershell -ExecutionPolicy Bypass -File scripts\dev.ps1
# 说明：首次运行会先构建 agv-algorithm 并安装到本地仓库，否则后端无法解析该依赖。

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

Write-Host "==> 1/3 构建后端与算法包" -ForegroundColor Cyan
Push-Location $root
mvn -q -DskipTests install
if ($LASTEXITCODE -ne 0) {
    Pop-Location
    Write-Host "构建失败，请检查 Maven 与 JDK 17 环境。" -ForegroundColor Red
    exit 1
}
Pop-Location

if (-not (Test-Path "$root\agv-frontend\node_modules")) {
    Write-Host "==> 首次运行，安装前端依赖" -ForegroundColor Cyan
    Push-Location "$root\agv-frontend"
    npm install --no-audit --no-fund
    Pop-Location
}

Write-Host "==> 2/3 启动后端（H2 开发库，端口 8080）" -ForegroundColor Cyan
Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "cd '$root\agv-backend'; `$env:MAVEN_OPTS='-Dfile.encoding=UTF-8'; mvn spring-boot:run"
)

Start-Sleep -Seconds 12

Write-Host "==> 3/3 启动前端（端口 5173）" -ForegroundColor Cyan
Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "cd '$root\agv-frontend'; npm run dev"
)

Write-Host ""
Write-Host "后端: http://localhost:8080/api/health" -ForegroundColor Green
Write-Host "前端: http://localhost:5173" -ForegroundColor Green
Write-Host "账号: admin/admin123（管理员）  dispatcher/dispatcher123（调度人员）" -ForegroundColor Yellow
Write-Host "停止: powershell -ExecutionPolicy Bypass -File scripts\stop-dev.ps1" -ForegroundColor Yellow
