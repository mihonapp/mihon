[CmdletBinding(SupportsShouldProcess)]
param([Parameter(Mandatory)][string]$ExecutablePath)
$ErrorActionPreference = 'Stop'
$targetExecutable = [IO.Path]::GetFullPath($ExecutablePath)
Get-ScheduledTask -TaskName 'MihonW-*' -ErrorAction SilentlyContinue | ForEach-Object {
    $task = $_
    $owned = @($task.Actions | Where-Object {
        $_.Execute -and [IO.Path]::GetFullPath($_.Execute.Trim('"')).Equals($targetExecutable, [StringComparison]::OrdinalIgnoreCase)
    }).Count -gt 0
    if ($owned -and $PSCmdlet.ShouldProcess($task.TaskName, 'Remove background task; preserve profile data')) {
        Unregister-ScheduledTask -TaskName $task.TaskName -TaskPath $task.TaskPath -Confirm:$false
    }
}
