param(
    [string] $BaseUrl = "http://localhost:8080",
    [string] $Operator = "admin"
)

$ErrorActionPreference = "Stop"

function Invoke-DemoPost {
    param([string] $Path)
    Write-Host "POST $Path"
    curl.exe -sS -X POST "$BaseUrl$Path" -H "X-Operator: $Operator" -H "X-Trace-Id: demo-v2-script"
    Write-Host "`n"
}

function Invoke-DemoGet {
    param([string] $Path)
    Write-Host "GET $Path"
    curl.exe -sS "$BaseUrl$Path" -H "X-Operator: $Operator"
    Write-Host "`n"
}

Invoke-DemoPost "/demo/order/object-payload?orderNo=ORD-OBJ-001&buyerId=USER-123&address=Shanghai"
Invoke-DemoPost "/demo/order/duplicate?orderNo=ORD-DUP-001&buyerId=USER-123"
Invoke-DemoPost "/demo/order?orderNo=ORD-RETRY-001&buyerId=USER-123"
Invoke-DemoPost "/demo/order/non-retryable?orderNo=ORD-BAD-001&buyerId=USER-123"

Start-Sleep -Seconds 3

Invoke-DemoGet "/api/reliable-task/tasks"
Invoke-DemoGet "/api/reliable-task/tasks/stats"
Invoke-DemoGet "/api/reliable-task/workers"
Invoke-DemoGet "/api/reliable-task/workers/stale"
Invoke-DemoGet "/actuator/metrics/reliable_task_pending_total"

Write-Host "Batch preview example:"
curl.exe -sS -X POST "$BaseUrl/api/reliable-task/tasks/batch/preview" `
  -H "Content-Type: application/json" `
  -H "X-Operator: $Operator" `
  -H "X-Trace-Id: demo-v2-batch" `
  -d '{"status":5,"limit":10,"dryRun":true}'
Write-Host "`n"
