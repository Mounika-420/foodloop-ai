param([switch]$NoAi)
$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
if (-not $env:FOODLOOP_DB_PASSWORD) { $env:FOODLOOP_DB_PASSWORD = [Environment]::GetEnvironmentVariable('FOODLOOP_DB_PASSWORD','User') }
if (-not $env:FOODLOOP_DB_PASSWORD -and (Test-Path "$env:USERPROFILE\.foodloop\db-password.txt")) { $env:FOODLOOP_DB_PASSWORD = (Get-Content "$env:USERPROFILE\.foodloop\db-password.txt" -Raw).Trim() }
if (-not $env:FOODLOOP_DB_PASSWORD) { Write-Host 'FOODLOOP_DB_PASSWORD set aagala (user environment variable venum).' -ForegroundColor Red; return }
$jwtFile = Join-Path $env:USERPROFILE '.foodloop\jwt-secret.txt'
if (-not $env:FOODLOOP_JWT_SECRET -and (Test-Path $jwtFile)) { $env:FOODLOOP_JWT_SECRET = (Get-Content $jwtFile -Raw).Trim() }
if (-not $env:FOODLOOP_JWT_SECRET) { Write-Host 'JWT secret file illa (%USERPROFILE%\.foodloop\jwt-secret.txt).' -ForegroundColor Red; return }
$b = 'http://localhost:8080'
function Listening($port) { [bool](Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) }
function BackendUp { try { (Invoke-RestMethod "$b/actuator/health" -TimeoutSec 3).status -eq 'UP' } catch { $false } }

if (-not $NoAi) {
    if (-not (Listening 5000)) {
        Write-Host 'AI service start aagudhu...' -ForegroundColor Cyan
        Start-Process powershell -WindowStyle Hidden -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File',"$root\ai-service\run-ai.ps1" -RedirectStandardOutput "$env:TEMP\foodloop-ai.out" -RedirectStandardError "$env:TEMP\foodloop-ai.log"
        for ($i = 0; $i -lt 40 -and -not (Listening 5000); $i++) { Start-Sleep 3 }
    }
    Write-Host ('AI service : ' + $(if (Listening 5000) { 'UP (port 5000)' } else { 'not running (rule-based matching will be used)' }))
}

if (-not (BackendUp)) {
    Write-Host 'Backend start aagudhu (1-2 min)...' -ForegroundColor Cyan
    $key = if ($NoAi) { '' } else { 'set AI_SERVICE_API_KEY=local-dev-ai-key-0123456789&& ' }
    $cmd = "cd /d $root\backend && set DB_USER=foodloop_app&& set DB_PASSWORD=$env:FOODLOOP_DB_PASSWORD&& set JWT_SECRET=$env:FOODLOOP_JWT_SECRET&& " + $key + "mvn spring-boot:run > $env:USERPROFILE\backend-run.log 2>&1"
    Start-Process cmd -WindowStyle Minimized -ArgumentList '/c', $cmd
    for ($i = 0; $i -lt 80 -and -not (BackendUp); $i++) { Start-Sleep 3 }
}
if (-not (BackendUp)) { Write-Host 'Backend UP aagala. Log:' -ForegroundColor Red; Get-Content "$env:USERPROFILE\backend-run.log" -Tail 20; return }
Write-Host 'Backend    : UP (port 8080)' -ForegroundColor Green

if (-not (Listening 5500)) {
    $py = Get-Command python -ErrorAction SilentlyContinue; if (-not $py) { $py = Get-Command py -ErrorAction SilentlyContinue }
    if ($py) { Start-Process $py.Source -WindowStyle Hidden -ArgumentList '-m','http.server','5500','--bind','127.0.0.1','--directory',"$root\frontend" }
    else { Start-Process cmd -WindowStyle Minimized -ArgumentList '/c',"cd /d $root\frontend && npx --yes http-server -p 5500 -a 127.0.0.1" }
    Start-Sleep 3
}
Write-Host 'Frontend   : http://127.0.0.1:5500/index.html' -ForegroundColor Green
Start-Process 'http://127.0.0.1:5500/index.html'
Write-Host "`nLogins (password Password@123): donor annapoorna@foodloop.test | NGO annadhanam@foodloop.test | admin admin@foodloop.test"