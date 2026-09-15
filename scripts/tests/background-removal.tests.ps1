$ErrorActionPreference = 'Stop'
$global:mihonTestRemovedTasks = @()
function Get-ScheduledTask {
    param($TaskName, $ErrorAction)
    @(
        [pscustomobject]@{ TaskName='MihonW-owned';TaskPath='\';Actions=@([pscustomobject]@{Execute='C:\fixture\MihonW.exe'}) },
        [pscustomobject]@{ TaskName='MihonW-other';TaskPath='\';Actions=@([pscustomobject]@{Execute='C:\other\MihonW.exe'}) }
    )
}
function Unregister-ScheduledTask { param($TaskName,$TaskPath,[switch]$Confirm) $global:mihonTestRemovedTasks += $TaskName }
& (Join-Path $PSScriptRoot '../remove-background-tasks.ps1') -ExecutablePath 'C:\fixture\MihonW.exe'
if ($global:mihonTestRemovedTasks.Count -ne 1 -or $global:mihonTestRemovedTasks[0] -ne 'MihonW-owned') { throw 'Removal crossed installation boundary' }
Write-Output 'PASS: task removal confined to exact installation executable'

