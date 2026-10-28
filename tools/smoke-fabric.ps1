param(
    [Parameter(Mandatory)][string]$Version,
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$Gradle,
    [int]$TimeoutSeconds = 300,
    [string]$Mode = ''
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$known = Get-Content (Join-Path $root 'versions.json') -Raw | ConvertFrom-Json
if ($Version -notin $known.id -or !$Version.EndsWith('-fabric')) { throw 'Unknown Fabric adapter' }
if ($Mode -notin @('', 'reliabilitySmoke', 'miningFeaturesSmoke')) { throw 'Unknown smoke mode' }
$project = Join-Path $root "versions/$Version"
$out = Join-Path $root 'build/verification'
New-Item -ItemType Directory -Path $out -Force | Out-Null
$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome/bin;$env:PATH"
$env:JAVA_TOOL_OPTIONS = '-XX:ActiveProcessorCount=2'
$env:MMM_COLLECTIONS_LIVE = 'false'
$env:MMM_LEADERBOARD_LIVE = 'false'
$arguments = 'runClient -PsmokeTest --offline --no-daemon --console=plain --max-workers=1 "-Dorg.gradle.jvmargs=-Xmx1536m -XX:ActiveProcessorCount=2"'
if ($Mode) { $arguments += " -P$Mode" }
$stdout = Join-Path $out "runtime-$Version-stdout.log"
$stderr = Join-Path $out "runtime-$Version-stderr.log"
$started = Get-Date
$process = Start-Process -FilePath $env:COMSPEC -ArgumentList "/d /s /c `"`"$Gradle`" $arguments`"" -WorkingDirectory $project -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
if (!$process.WaitForExit($TimeoutSeconds * 1000)) {
    & taskkill /PID $process.Id /T /F | Out-Null
    throw "Runtime deadline reached for $Version; stopped only launched process tree"
}
$process.Refresh()
$sentinels = @(Get-ChildItem -LiteralPath $project -Recurse -Filter '*smoke*.txt' -ErrorAction SilentlyContinue | Where-Object {$_.LastWriteTime -ge $started})
$passed = @($sentinels | Where-Object {(Get-Content $_.FullName -Raw).StartsWith('PASS')})
$failed = @($sentinels | Where-Object {(Get-Content $_.FullName -Raw).StartsWith('FAIL')})
$logPass = Select-String -LiteralPath $stdout -Pattern 'MMM_(RELIABILITY_)?SMOKE_(PASS|26_)' -Quiet
[ordered]@{version=$Version; started=$started.ToUniversalTime().ToString('o'); exitCode=$process.ExitCode; passed=@($passed | ForEach-Object FullName); failed=@($failed | ForEach-Object FullName); logPass=$logPass; stdout=$stdout; stderr=$stderr} | ConvertTo-Json | Tee-Object -FilePath (Join-Path $out "runtime-$Version-result.json")
if ($process.ExitCode -ne 0 -or !$passed.Count -or $failed.Count -or !$logPass) { throw "Runtime verification failed: $Version" }
