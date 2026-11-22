Set-Location $PSScriptRoot
if (-not $env:AI_SERVICE_API_KEY) { $env:AI_SERVICE_API_KEY = 'local-dev-ai-key-0123456789' }
& "$PSScriptRoot\.venv\Scripts\python.exe" app.py