[CmdletBinding()]
param(
    [Parameter(Mandatory)][int]$ProcessId,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [ValidateRange(1, 1440)][int]$DurationMinutes = 30,
    [ValidateRange(1, 60)][int]$IntervalSeconds = 5,
    [string]$JcmdPath
)
$ErrorActionPreference = 'Stop'
$rootProcess = Get-Process -Id $ProcessId -ErrorAction Stop
$expectedPath = $rootProcess.Path
if ([IO.Path]::GetFileName($expectedPath) -ne 'mihondesk.exe') { throw 'Target must be a running mihondesk executable' }
$outputRoot = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
$runName = 'process-memory-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')
$samplesPath = Join-Path $outputRoot "$runName.jsonl"
$heapPath = Join-Path $outputRoot "$runName-heap.txt"
$started = [DateTime]::UtcNow
$nextHeap = $started
$samples = [Collections.Generic.List[object]]::new()
while (([DateTime]::UtcNow - $started).TotalMinutes -lt $DurationMinutes) {
    $currentRoot = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if (-not $currentRoot -or $currentRoot.Path -ne $expectedPath) { break }
    $processRows = @(Get-CimInstance Win32_Process -Property ProcessId,ParentProcessId,Name)
    $treeIds = [Collections.Generic.HashSet[int]]::new()
    $null = $treeIds.Add($ProcessId)
    do {
        $added = $false
        foreach ($row in $processRows) {
            if ($treeIds.Contains([int]$row.ParentProcessId) -and $treeIds.Add([int]$row.ProcessId)) { $added = $true }
        }
    } while ($added)
    $members = @(foreach ($treeId in $treeIds) {
        $item = Get-Process -Id $treeId -ErrorAction SilentlyContinue
        if ($item) {
            [pscustomobject]@{
                processId = $treeId
                name = $item.ProcessName
                workingSetBytes = $item.WorkingSet64
                privateBytes = $item.PrivateMemorySize64
                cpuSeconds = $item.CPU
            }
        }
    })
    $sample = [pscustomobject]@{
        utc = [DateTime]::UtcNow.ToString('o')
        elapsedSeconds = [Math]::Round(([DateTime]::UtcNow - $started).TotalSeconds, 2)
        workingSetBytes = ($members | Measure-Object workingSetBytes -Sum).Sum
        privateBytes = ($members | Measure-Object privateBytes -Sum).Sum
        processes = $members
    }
    $samples.Add($sample)
    $sample | ConvertTo-Json -Depth 4 -Compress | Add-Content -LiteralPath $samplesPath -Encoding utf8
    if ($JcmdPath -and [DateTime]::UtcNow -ge $nextHeap) {
        foreach ($member in $members) {
            $candidate = Get-Process -Id $member.processId -ErrorAction SilentlyContinue
            if (-not $candidate) { continue }
            try { $isJvm = @($candidate.Modules | Where-Object ModuleName -eq 'jvm.dll').Count -gt 0 }
            catch { $isJvm = $false }
            if ($isJvm) {
                "UTC $([DateTime]::UtcNow.ToString('o')) JVM $($member.processId)" | Add-Content -LiteralPath $heapPath
                & $JcmdPath $member.processId GC.heap_info 2>&1 | Add-Content -LiteralPath $heapPath
            }
        }
        $nextHeap = [DateTime]::UtcNow.AddSeconds(60)
    }
    Start-Sleep -Seconds $IntervalSeconds
}
[pscustomobject]@{
    executable = $expectedPath
    requestedMinutes = $DurationMinutes
    sampledSeconds = [Math]::Round(([DateTime]::UtcNow - $started).TotalSeconds, 2)
    samples = $samples.Count
    maxWorkingSetBytes = ($samples | Measure-Object workingSetBytes -Maximum).Maximum
    maxPrivateBytes = ($samples | Measure-Object privateBytes -Maximum).Maximum
    samplesPath = $samplesPath
    heapPath = if ($JcmdPath) { $heapPath } else { $null }
    note = 'Process-tree memory sampling only; this does not prove active reading, frame times, or UI responsiveness.'
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputRoot "$runName-summary.json")
Get-Content -LiteralPath (Join-Path $outputRoot "$runName-summary.json")
