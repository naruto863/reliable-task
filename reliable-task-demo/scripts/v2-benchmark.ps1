param(
    [string] $BaseUrl = "http://localhost:8080",
    [int] $TotalTasks = 1000,
    [int] $Concurrency = 20
)

$ErrorActionPreference = "Stop"

$started = Get-Date
$jobs = New-Object System.Collections.Generic.List[object]

for ($i = 1; $i -le $TotalTasks; $i++) {
    $orderNo = "BENCH-{0:D6}" -f $i
    $scriptBlock = {
        param($BaseUrl, $OrderNo)
        $url = "$BaseUrl/demo/order/object-payload?orderNo=$OrderNo&buyerId=BENCH&address=Benchmark"
        curl.exe -sS -X POST $url | Out-Null
    }
    $jobs.Add((Start-Job -ScriptBlock $scriptBlock -ArgumentList $BaseUrl, $orderNo))

    while (($jobs | Where-Object { $_.State -eq "Running" }).Count -ge $Concurrency) {
        Start-Sleep -Milliseconds 100
    }
}

$jobs | Wait-Job | Out-Null
$jobs | Receive-Job | Out-Null
$jobs | Remove-Job

$finished = Get-Date
$elapsedSeconds = [Math]::Max(($finished - $started).TotalSeconds, 0.001)
$submitTps = [Math]::Round($TotalTasks / $elapsedSeconds, 2)

[PSCustomObject]@{
    totalTasks = $TotalTasks
    concurrency = $Concurrency
    started = $started.ToString("s")
    finished = $finished.ToString("s")
    elapsedSeconds = [Math]::Round($elapsedSeconds, 2)
    submitTps = $submitTps
} | ConvertTo-Json

Write-Host "Query runtime stats:"
curl.exe -sS "$BaseUrl/api/reliable-task/tasks/stats" -H "X-Operator: admin"
Write-Host "`nQuery Micrometer pending gauge:"
curl.exe -sS "$BaseUrl/actuator/metrics/reliable_task_pending_total"
Write-Host "`n"
