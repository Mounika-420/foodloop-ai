param([switch]$Stop)
$ErrorActionPreference = 'Continue'
$root = 'C:\projects\foodloop-ai'
$pidFile = Join-Path $env:TEMP 'foodloop-public.pids'
function StopAll {
    if (Test-Path $pidFile) {
        Get-Content $pidFile | ForEach-Object { Stop-Process -Id ([int]$_) -Force -ErrorAction SilentlyContinue }
        Remove-Item $pidFile -ErrorAction SilentlyContinue
    }
}
if ($Stop) { StopAll; Write-Host 'Public link stopped. Link ippo velai seiyaadhu.' -ForegroundColor Yellow; return }
StopAll

try { $h = (Invoke-RestMethod http://localhost:8080/actuator/health -TimeoutSec 5).status } catch { $h = 'DOWN' }
if ($h -ne 'UP') { Write-Host 'Backend DOWN. Mudhalla .\start-foodloop.ps1 run pannunga.' -ForegroundColor Red; return }

$cf = (Get-Command cloudflared -ErrorAction SilentlyContinue).Source
if (-not $cf) {
    $cf = Join-Path $env:USERPROFILE '.foodloop\cloudflared.exe'
    if (-not (Test-Path $cf)) {
        Write-Host 'cloudflared download aagudhu (oru thadava mattum)...' -ForegroundColor Cyan
        New-Item -ItemType Directory -Force -Path (Split-Path $cf) | Out-Null
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        try { Invoke-WebRequest -Uri 'https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe' -OutFile $cf -UseBasicParsing }
        catch { Write-Host "Download fail: $($_.Exception.Message)" -ForegroundColor Red; return }
    }
}

$py = (Get-Command python -ErrorAction SilentlyContinue).Source
if (-not $py) { Write-Host 'python kaanom' -ForegroundColor Red; return }
$proxy = Start-Process $py -ArgumentList "`"$root\deployment\demo-proxy.py`"", '5600', "`"$root\frontend`"" -WindowStyle Hidden -PassThru
for ($i = 0; $i -lt 10 -and -not (Get-NetTCPConnection -LocalPort 5600 -State Listen -ErrorAction SilentlyContinue); $i++) { Start-Sleep 1 }
if (-not (Get-NetTCPConnection -LocalPort 5600 -State Listen -ErrorAction SilentlyContinue)) { Write-Host 'Proxy start aagala (port 5600).' -ForegroundColor Red; Stop-Process -Id $proxy.Id -Force -ErrorAction SilentlyContinue; return }

$log = Join-Path $env:TEMP 'foodloop-tunnel.log'
Remove-Item $log -ErrorAction SilentlyContinue
$tun = Start-Process $cf -ArgumentList 'tunnel', '--url', 'http://localhost:5600', '--no-autoupdate' -RedirectStandardError $log -RedirectStandardOutput (Join-Path $env:TEMP 'foodloop-tunnel.out') -WindowStyle Hidden -PassThru
@($proxy.Id, $tun.Id) | Set-Content $pidFile

$url = $null
for ($i = 0; $i -lt 40 -and -not $url; $i++) {
    Start-Sleep 1
    if (Test-Path $log) { $m = Select-String -Path $log -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' | Select-Object -First 1; if ($m) { $url = $m.Matches[0].Value } }
}
if (-not $url) { Write-Host 'Tunnel link varala. Log:' -ForegroundColor Red; Get-Content $log -Tail 15; StopAll; return }

$ok = $false
for ($i = 0; $i -lt 10 -and -not $ok; $i++) {
    try { $ok = ((Invoke-RestMethod "$url/actuator/health" -TimeoutSec 8).status -eq 'UP') } catch { Start-Sleep 3 }
}
Set-Clipboard -Value $url
Write-Host "`nPublic link: $url" -ForegroundColor Green
Write-Host ('Self-test (link vazhiya backend health): ' + $(if ($ok) { 'OK' } else { 'innum ready aagala, 30 sec kazhichu link-ai thirandhu paarunga' }))
Write-Host 'Link clipboard-la copy aagiduchu. Nirutha:  .\public-demo.ps1 -Stop' -ForegroundColor Cyan