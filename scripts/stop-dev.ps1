# 停止开发环境：释放 8080 与 5173 端口
$ports = @(8080, 5173)
foreach ($port in $ports) {
    $connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($connections) {
        $connections | Select-Object -ExpandProperty OwningProcess -Unique | ForEach-Object {
            Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
            Write-Host "已停止端口 $port 上的进程 $_" -ForegroundColor Yellow
        }
    } else {
        Write-Host "端口 $port 无监听进程" -ForegroundColor Gray
    }
}
