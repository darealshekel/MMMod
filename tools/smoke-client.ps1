param(
    [string]$PrismRoot = "$env:APPDATA/PrismLauncher",
    [int]$TimeoutSeconds = 60,
    [switch]$TransportProbe,
    [switch]$RuntimeProbe,
    [string]$CompatibilityModsPath = ''
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$game = Join-Path $root $(if ($CompatibilityModsPath) { 'build/smoke-compat-game' } else { 'build/smoke-game' })
$natives = Join-Path $root 'build/smoke-natives'
$smokeHome = Join-Path $root 'build/smoke-home'
$appdata = Join-Path $root 'build/smoke-appdata'
foreach ($directory in @($game, $natives, $smokeHome, $appdata, "$game/mods")) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}
$mod = Join-Path $root 'build/libs/mmm-1.0.20.1+1.12.2-liteloader-development.jar'
if (!(Test-Path -LiteralPath $mod)) { throw 'Run verifyDevelopmentArtifact first.' }
Copy-Item -LiteralPath $mod -Destination "$game/mods/mmm-development.litemod" -Force
if ($CompatibilityModsPath) {
    Get-ChildItem -LiteralPath $CompatibilityModsPath -File -Filter '*.litemod' |
        Where-Object { $_.Name -notlike 'mmm-*' } |
        ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination "$game/mods" -Force }
}

function MavenPath([string]$name, [string]$classifier = '') {
    $parts = $name.Split(':')
    if (!$classifier -and $parts.Length -gt 3) { $classifier = $parts[3] }
    $suffix = if ($classifier) { "-$classifier" } else { '' }
    return Join-Path "$PrismRoot/libraries" ($parts[0].Replace('.', '/') + '/' + $parts[1] + '/' + $parts[2] + '/' + $parts[1] + '-' + $parts[2] + $suffix + '.jar')
}
function Allowed($library) {
    if (!$library.rules) { return $true }
    $allow = $false
    foreach ($rule in $library.rules) {
        if ($rule.os.name -and $rule.os.name -ne 'windows') { continue }
        if ($rule.os.arch -and $rule.os.arch -notin @('x86_64', 'amd64')) { continue }
        if ($rule.os.version -and [Environment]::OSVersion.VersionString -notmatch $rule.os.version) { continue }
        $allow = $rule.action -eq 'allow'
    }
    return $allow
}
$minecraft = Get-Content -Raw "$PrismRoot/meta/net.minecraft/1.12.2.json" | ConvertFrom-Json
$loader = Get-Content -Raw "$PrismRoot/meta/com.mumfrey.liteloader/1.12.2-SNAPSHOT.json" | ConvertFrom-Json
$lwjgl = Get-Content -Raw "$PrismRoot/meta/org.lwjgl/2.9.4-nightly-20150209.json" | ConvertFrom-Json
$classpath = [Collections.Generic.List[string]]::new()
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($library in @($minecraft.libraries) + @($loader.libraries) + @($lwjgl.libraries)) {
    if (!(Allowed $library)) { continue }
    if ($library.natives.windows) {
        $nativeJar = MavenPath $library.name $library.natives.windows
        $zip = [IO.Compression.ZipFile]::OpenRead($nativeJar)
        try {
            foreach ($entry in $zip.Entries) {
                if ($entry.FullName.EndsWith('.dll')) {
                    $target = Join-Path $natives ([IO.Path]::GetFileName($entry.FullName))
                    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
                }
            }
        } finally { $zip.Dispose() }
    }
    if (!$library.natives -or ($library.downloads.artifact -and $library.downloads.artifact.size -gt 22)) {
        $path = MavenPath $library.name
        if (!(Test-Path -LiteralPath $path)) { throw "Missing installed Prism dependency: $path" }
        if (!$classpath.Contains($path)) { $classpath.Add($path) }
    }
}
$classpath.Add((MavenPath $minecraft.mainJar.name))
$java = Join-Path $PrismRoot 'java/jre-legacy/bin/java.exe'
$arguments = @(
    '-Xmx1G', "-Djava.library.path=$natives", "-Duser.home=$smokeHome", "-Dmmm.runtimeProbe=$($RuntimeProbe.IsPresent.ToString().ToLowerInvariant())",
    '-Dlog4j2.formatMsgNoLookups=true', '-cp', ($classpath -join ';'),
    'net.minecraft.launchwrapper.Launch', '--tweakClass', 'com.mumfrey.liteloader.launch.LiteLoaderTweaker',
    '--gameDir', $game, '--assetsDir', "$PrismRoot/assets", '--assetIndex', '1.12',
    '--username', 'MMMPortTest', '--version', '1.12.2', '--accessToken', '0', '--userType', 'legacy', '--width', '1280', '--height', '720'
)
if ($TransportProbe) {
    $classpath.Insert(0, $mod)
    $classpath.Insert(0, (Join-Path $root 'build/java8-probe'))
    $arguments = @('-Xmx256M', "-Duser.home=$smokeHome", '-cp', ($classpath -join ';'),
        'com.mmm.liteloader.probe.Java8TransportProbe', $game)
}
$logPrefix = if ($TransportProbe) { 'java8-transport' } elseif ($RuntimeProbe) { 'runtime' } else { 'smoke' }
if ($CompatibilityModsPath) { $logPrefix += '-compat' }
$stdout = Join-Path $root "build/$logPrefix-stdout.log"
$stderr = Join-Path $root "build/$logPrefix-stderr.log"
$previousAppdata = $env:APPDATA
$process = $null
try {
    $env:APPDATA = $appdata
    $process = Start-Process -FilePath $java -ArgumentList ($arguments | ForEach-Object { '"' + $_ + '"' }) -WorkingDirectory $game -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    if (!$process.WaitForExit($TimeoutSeconds * 1000)) {
        Stop-Process -Id $process.Id
        $process.WaitForExit()
        Write-Output 'Stopped the isolated startup check at its time limit. This does not establish feature parity.'
    } else {
        Write-Output "Java process exited: $($process.ExitCode)"
    }
} finally {
    $env:APPDATA = $previousAppdata
    if ($process -and !$process.HasExited) { Stop-Process -Id $process.Id }
}
Get-Content -LiteralPath $stdout -Tail 90
Get-Content -LiteralPath $stderr -Tail 40
if ($TransportProbe -and !(Select-String -LiteralPath $stdout -SimpleMatch 'JAVA8_TRANSPORT_OK' -Quiet)) {
    throw 'Java 8 packaged HTTP transport probe did not pass.'
}
if ($RuntimeProbe -and !(Select-String -LiteralPath $stdout -SimpleMatch 'MMM_RUNTIME_PROBE_OK' -Quiet)) {
    throw 'The isolated in-world runtime probe did not finish successfully. Inspect the runtime logs.'
}
