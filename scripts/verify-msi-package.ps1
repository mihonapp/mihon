param([Parameter(Mandatory)][string]$MsiPath)
$ErrorActionPreference = 'Stop'
$expectedVersion = (Get-Content (Join-Path $PSScriptRoot '../desktop-version.txt') -Raw).Trim()
$installer = New-Object -ComObject WindowsInstaller.Installer
$database = $installer.OpenDatabase((Resolve-Path -LiteralPath $MsiPath).Path,0)
function Read-MsiTable([string]$Table) {
    $columnCount = switch ($Table) { 'CustomAction' { 4 }; 'InstallExecuteSequence' { 3 }; 'Property' { 2 }; 'File' { 8 }; default { throw 'Unsupported table' } }
    $view = $database.OpenView("SELECT * FROM $Table")
    [void]$view.Execute()
    try {
        while ($record = $view.Fetch()) {
            $cells = for ($i = 1; $i -le $columnCount; $i++) { $record.StringData($i) }
            ,$cells
        }
    } finally { [void]$view.Close() }
}
$actions = @(Read-MsiTable 'CustomAction')
$hook = @($actions | Where-Object { $_[0] -eq 'MihonRemoveBackgroundTasks' })
if ($hook.Count -ne 1 -or $hook[0][3] -notmatch 'remove-background-tasks.ps1') { throw 'MSI uninstall action missing' }
if (([int]$hook[0][1] -band 1024) -eq 0) { throw 'Uninstall action is not deferred' }
$sequence = @(Read-MsiTable 'InstallExecuteSequence')
$cleanup = $sequence | Where-Object { $_[0] -eq 'MihonRemoveBackgroundTasks' }
$remove = $sequence | Where-Object { $_[0] -eq 'RemoveFiles' }
if ([int]$cleanup[2] -ge [int]$remove[2] -or $cleanup[1] -notmatch 'REMOVE="ALL"') { throw 'Uninstall action sequence is invalid' }
$properties = @(Read-MsiTable 'Property')
$version = $properties | Where-Object { $_[0] -eq 'ProductVersion' }
if ($version[1] -ne $expectedVersion) { throw 'MSI version mismatch' }
$productName = $properties | Where-Object { $_[0] -eq 'ProductName' }
if ($productName[1] -ne 'mihondesk') { throw 'MSI product name mismatch' }
$files = @(Read-MsiTable 'File')
foreach ($required in @('mihondesk.exe','libcef.dll','CHROMIUM-CREDITS.html','remove-background-tasks.ps1')) {
    if (-not ($files | Where-Object { $_[2].EndsWith($required) })) { throw "MSI required resource missing: $required" }
}
Write-Output "PASS: MSI $expectedVersion contains browser, licenses and deferred uninstall hook before RemoveFiles; execution not tested"
