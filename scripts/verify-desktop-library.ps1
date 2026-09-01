$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$temporaryBase = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$temporaryName = 'mihon-w-library-' + [guid]::NewGuid().ToString('N')
$temporaryRoot = Join-Path $temporaryBase $temporaryName
$originalJavaHome = $env:JAVA_HOME

function Invoke-Gradle {
    param([Parameter(Mandatory)][string[]]$Arguments)

    & .\gradlew.bat @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
    }
}

function Quote-ProcessArgument {
    param([Parameter(Mandatory)][string]$Value)
    if ($Value.Contains('"')) {
        throw 'Process arguments containing quotes are not supported by this verifier.'
    }
    return '"' + $Value + '"'
}

function Invoke-PackagedCommand {
    param(
        [Parameter(Mandatory)][string]$Launcher,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$Name
    )

    $standardOutput = Join-Path $temporaryRoot "$Name.stdout.log"
    $standardError = Join-Path $temporaryRoot "$Name.stderr.log"
    $argumentsJson = ConvertTo-Json -Compress -InputObject @($Arguments)
    $argumentsBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($argumentsJson))
    $runner = Join-Path $temporaryRoot 'packaged-command-runner.ps1'
    $pwsh = (Get-Command pwsh -ErrorAction Stop).Source
    $wrapperArguments = @(
        '-NoProfile',
        '-File',
        (Quote-ProcessArgument $runner),
        '-Launcher',
        (Quote-ProcessArgument $Launcher),
        '-StandardOutput',
        (Quote-ProcessArgument $standardOutput),
        '-StandardError',
        (Quote-ProcessArgument $standardError),
        '-ArgumentsBase64',
        (Quote-ProcessArgument $argumentsBase64)
    )
    $wrapper = Start-Process -FilePath $pwsh -ArgumentList $wrapperArguments -WindowStyle Hidden -PassThru
    if (-not $wrapper.WaitForExit(30 * 1000)) {
        & taskkill.exe /PID $wrapper.Id /T /F 2>&1 | Out-Null
        if (-not $wrapper.WaitForExit(5 * 1000)) {
            throw "Packaged command '$Name' timed out after 30 seconds and its process tree did not exit after termination."
        }
        throw "Packaged command '$Name' timed out after 30 seconds and was terminated."
    }
    if ($wrapper.ExitCode -ne 0) {
        $errorText = if (Test-Path -LiteralPath $standardError) {
            Get-Content -Raw -Encoding utf8 -LiteralPath $standardError
        } else {
            '<no stderr file>'
        }
        throw "Packaged command '$Name' failed with exit code $($wrapper.ExitCode). stderr: $errorText"
    }
    if (-not (Test-Path -LiteralPath $standardOutput)) {
        throw "Packaged command '$Name' did not create stdout evidence at $standardOutput"
    }
    return [pscustomobject]@{
        ExitCode = $wrapper.ExitCode
        StandardOutput = $standardOutput
        StandardError = $standardError
        Text = Get-Content -Raw -Encoding utf8 -LiteralPath $standardOutput
    }
}

function Read-CommandJson {
    param(
        [Parameter(Mandatory)][pscustomobject]$Result,
        [Parameter(Mandatory)][string]$Name
    )

    $lines = @($Result.Text -split "`r?`n" | Where-Object { $_.Trim().Length -gt 0 })
    if ($lines.Count -ne 1) {
        throw "Packaged command '$Name' must emit exactly one nonempty JSON line, but emitted $($lines.Count)."
    }
    try {
        return $lines[0] | ConvertFrom-Json -ErrorAction Stop
    } catch {
        throw "Packaged command '$Name' emitted invalid JSON: $($lines[0])"
    }
}

Push-Location $repoRoot
try {
    $jbr = 'C:\Program Files\Android\Android Studio\jbr'
    $javaVersionOutput = if (Get-Command java -ErrorAction SilentlyContinue) { (& java -version 2>&1) -join "`n" } else { '' }
    $javaMajorMatch = [regex]::Match($javaVersionOutput, 'version "(?<major>\d+)')
    if (-not $javaMajorMatch.Success -or [int]$javaMajorMatch.Groups['major'].Value -lt 21) {
        if (-not (Test-Path -LiteralPath (Join-Path $jbr 'bin\java.exe'))) {
            throw 'Gradle requires JDK 21 or newer, and Android Studio JBR was not found.'
        }
        $env:JAVA_HOME = $jbr
        Write-Host "Using Gradle JDK at $jbr"
    }

    New-Item -ItemType Directory -Path $temporaryRoot -ErrorAction Stop | Out-Null
    $runnerSource = @'
param(
    [Parameter(Mandatory)][string]$Launcher,
    [Parameter(Mandatory)][string]$StandardOutput,
    [Parameter(Mandatory)][string]$StandardError,
    [Parameter(Mandatory)][string]$ArgumentsBase64
)
$ErrorActionPreference = 'Stop'
$json = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($ArgumentsBase64))
$commandArguments = @($json | ConvertFrom-Json)
$quotedArguments = @($commandArguments | ForEach-Object { '"' + ([string]$_).Replace('"', '\"') + '"' })
$process = Start-Process -FilePath $Launcher -ArgumentList $quotedArguments -WindowStyle Hidden -Wait -PassThru `
    -RedirectStandardOutput $StandardOutput -RedirectStandardError $StandardError
exit $process.ExitCode
'@
    Set-Content -LiteralPath (Join-Path $temporaryRoot 'packaged-command-runner.ps1') -Value $runnerSource -Encoding utf8

    & .\scripts\verify-desktop-foundation.ps1
    if ($LASTEXITCODE -ne 0) {
        throw "Desktop foundation verifier failed with exit code $LASTEXITCODE"
    }

    Invoke-Gradle @(
        ':desktop-library-data:test',
        ':app:testDebugUnitTest',
        '--tests',
        'eu.kanade.tachiyomi.data.backup.DesktopBackupImportContractTest',
        ':desktop-app:test',
        'verifySqlDelightMigration',
        ':desktop-app:createDistributable'
    )

    $fixtureDirectoryProperty = 'app/build/plan2-fixtures'
    Invoke-Gradle @(
        ':app:testDebugUnitTest',
        '--tests',
        'eu.kanade.tachiyomi.data.backup.DesktopBackupFixtureWriterTest',
        "-PmihonPlan2FixtureDir=$fixtureDirectoryProperty"
    )
    $fixture = Join-Path $repoRoot 'app\build\plan2-fixtures\android-generated.tachibk'
    $fixtureChecksum = "$fixture.sha256"
    if (-not (Test-Path -LiteralPath $fixture) -or -not (Test-Path -LiteralPath $fixtureChecksum)) {
        throw 'Android fixture writer did not create both required fixture files.'
    }
    $expectedChecksum = (Get-Content -Raw -LiteralPath $fixtureChecksum).Trim()
    if ($expectedChecksum -cnotmatch '^[0-9a-f]{64}$') {
        throw "Android fixture checksum must be lowercase 64-hex SHA-256, but was '$expectedChecksum'."
    }
    $actualChecksum = (Get-FileHash -Algorithm SHA256 -LiteralPath $fixture).Hash.ToLowerInvariant()
    if ($actualChecksum -cne $expectedChecksum) {
        throw "Android fixture SHA-256 mismatch: expected $expectedChecksum but was $actualChecksum"
    }
    Write-Host "Android fixture SHA-256=$actualChecksum"

    $launcher = Join-Path $repoRoot 'desktop-app\build\compose\binaries\main\app\MihonW\MihonW.exe'
    if (-not (Test-Path -LiteralPath $launcher)) {
        throw "Packaged launcher was not created at $launcher"
    }
    $runtimeRelease = Join-Path $repoRoot 'desktop-app\build\compose\binaries\main\app\MihonW\runtime\release'
    $runtimeReleaseContents = Get-Content -Raw -LiteralPath $runtimeRelease
    $javaVersionMatch = [regex]::Match($runtimeReleaseContents, '(?m)^JAVA_VERSION="(?<version>[^"]+)"\r?$')
    if (-not $javaVersionMatch.Success) {
        throw 'Packaged Java runtime metadata did not contain JAVA_VERSION'
    }
    $javaVersion = $javaVersionMatch.Groups['version'].Value
    if (-not [regex]::IsMatch($javaVersion, '^17(?:\.|$)')) {
        throw "Packaged Java runtime must be Java 17, but JAVA_VERSION was $javaVersion"
    }
    Write-Host "Packaged runtime JAVA_VERSION=$javaVersion"

    $longSegments = 1..3 | ForEach-Object { '路径段' + ('很长' * 30) + $_ }
    $sourceParent = $temporaryRoot
    foreach ($segment in $longSegments) {
        $sourceParent = Join-Path $sourceParent $segment
    }
    $localTitle = '跨平台本地漫画_验证'
    $localManga = Join-Path $sourceParent $localTitle
    $chapterDirectory = Join-Path $localManga '第 01 话_目录章节'
    New-Item -ItemType Directory -Path $chapterDirectory -Force | Out-Null
    [IO.File]::WriteAllBytes((Join-Path $chapterDirectory '001.jpg'), [byte[]](0xFF, 0xD8, 0xFF, 0xD9))
    @('.cbz', '.rar', '.7z', '.epub') | ForEach-Object {
        [IO.File]::WriteAllBytes((Join-Path $localManga ("归档章节$_")), [byte[]](0x50, 0x4B, 0x03, 0x04))
    }
    if ($localManga.Length -le 260) {
        throw "Long-path fixture was not longer than 260 characters: $($localManga.Length)"
    }

    $backupResult = Invoke-PackagedCommand $launcher @("--import-backup=$fixture", "--data-dir=$temporaryRoot") 'import-backup'
    $backupJson = Read-CommandJson $backupResult 'import-backup'
    if ($backupResult.ExitCode -ne 0 -or $backupJson.status -cne 'SUCCEEDED') {
        throw "Packaged backup import did not exit 0 with SUCCEEDED status."
    }

    $localResult = Invoke-PackagedCommand $launcher @("--import-local=$localManga", "--data-dir=$temporaryRoot") 'import-local'
    $localJson = Read-CommandJson $localResult 'import-local'
    if ($localResult.ExitCode -ne 0 -or $localJson.status -cne 'SUCCEEDED') {
        throw "Packaged local import did not exit 0 with SUCCEEDED status."
    }

    $listResult = Invoke-PackagedCommand $launcher @('--list-library-json', "--data-dir=$temporaryRoot") 'list-library'
    $listJson = Read-CommandJson $listResult 'list-library'
    $backupItem = @($listJson.items) | Where-Object { $_.title -ceq '跨平台备份' }
    $localItem = @($listJson.items) | Where-Object { $_.title -ceq $localTitle }
    if (@($backupItem).Count -ne 1 -or [long]$backupItem.chapterCount -le 0) {
        throw 'Reopened packaged process did not persist 跨平台备份 with chapters.'
    }
    if (@($localItem).Count -ne 1 -or [long]$localItem.chapterCount -le 0) {
        throw "Reopened packaged process did not persist $localTitle with chapters."
    }
    Write-Host "Packaged processes persisted titles and chapters under $temporaryRoot"
    Write-Host 'Mihon W desktop library verification passed.'
} finally {
    Pop-Location
    if ($null -eq $originalJavaHome) {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    } else {
        $env:JAVA_HOME = $originalJavaHome
    }
    if (Test-Path -LiteralPath $temporaryRoot) {
        $resolvedTemporaryRoot = [System.IO.Path]::GetFullPath((Resolve-Path -LiteralPath $temporaryRoot).Path)
        $resolvedTemporaryBase = [System.IO.Path]::GetFullPath($temporaryBase)
        $expectedLeaf = Split-Path -Leaf $resolvedTemporaryRoot
        if (
            -not $resolvedTemporaryRoot.StartsWith($resolvedTemporaryBase, [StringComparison]::OrdinalIgnoreCase) -or
            (Split-Path -Parent $resolvedTemporaryRoot).TrimEnd('\', '/') -ne $resolvedTemporaryBase.TrimEnd('\', '/') -or
            $expectedLeaf -cnotmatch '^mihon-w-library-[0-9a-f]{32}$'
        ) {
            throw "Refusing to delete unexpected verification path: $resolvedTemporaryRoot"
        }
        Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force
    }
}
