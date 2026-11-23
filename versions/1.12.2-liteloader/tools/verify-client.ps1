param(
    [string]$PrismRoot = "$env:APPDATA/PrismLauncher",
    [int]$TimeoutSeconds = 60,
    [switch]$TransportProbe,
    [switch]$RuntimeProbe,
    [switch]$AchievementProbe,
    [switch]$AfkProbe,
    [switch]$HudProbe,
    [switch]$WithForge,
    [switch]$PackagedBoardProbe,
    [switch]$JarFeasibility,
    [string]$ModArtifact = '',
    [string]$CompatibilityModsPath = ''
)
$ErrorActionPreference = 'Stop'
if ($JarFeasibility -and !$PackagedBoardProbe) { throw 'Jar discovery must run in an isolated packaged board test.' }
if ($AchievementProbe -or $AfkProbe -or $HudProbe) { $RuntimeProbe = [switch]::new($true) }
$root = Split-Path $PSScriptRoot -Parent
$variant = if ($WithForge) { 'forge' } else { 'lite' }
$gameVariant = if ($HudProbe) { "hud-$variant" } else { $variant }
if ($PackagedBoardProbe) {
    if ($RuntimeProbe -or $CompatibilityModsPath -or $TransportProbe) { throw 'Packaged board test requires an isolated release + test driver.' }
    $scope = if ($JarFeasibility) { 'jar-discovery' } else { 'board' }
    $gameVariant = "$scope-$variant-$([Guid]::NewGuid().ToString('N'))"
}
$game = Join-Path $root "build/review-$gameVariant-game"
$natives = Join-Path $root 'build/smoke-natives'
$smokeHome = Join-Path $root "build/review-$gameVariant-home"
$appdata = Join-Path $root "build/review-$gameVariant-appdata"
foreach ($directory in @($game, $natives, $smokeHome, $appdata, "$game/mods")) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}
$release = (Get-Content (Join-Path $root '../../release.properties') | Where-Object { $_ -match '^mod_version=' }) -replace '^mod_version=', ''
$mod = Join-Path $root "build/libs/mmm-$release+1.12.2-liteloader-development.jar"
if ($ModArtifact) { $mod = $ModArtifact }
if (!(Test-Path -LiteralPath $mod)) { throw 'Run verifyDevelopmentArtifact first.' }
$installedName = if ($JarFeasibility) { [IO.Path]::ChangeExtension((Split-Path -Leaf $mod), '.jar') } else { 'mmm-development.litemod' }
$installedMod = Join-Path "$game/mods" $installedName
Copy-Item -LiteralPath $mod -Destination $installedMod -Force
if ($PackagedBoardProbe) {
    $probe = Join-Path $root 'build/probe/mmm-board-probe.litemod'
    if (!$ModArtifact -or !($ModArtifact.EndsWith('.litemod')) -or !(Test-Path -LiteralPath $probe)) {
        throw 'Specify the final .litemod and build packagedBoardProbe first.'
    }
    Copy-Item -LiteralPath $probe -Destination "$game/mods/mmm-board-probe.litemod"
    $sha = (Get-FileHash -LiteralPath $mod -Algorithm SHA256).Hash.ToLowerInvariant()
    if ((Get-FileHash -LiteralPath $installedMod).Hash -ne $sha) { throw 'Packaged artifact copy differs.' }
}
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
$forgeLibraries = @()
if ($WithForge) {
    $forge = Get-Content -Raw "$PrismRoot/meta/net.minecraftforge/14.23.5.2859.json" | ConvertFrom-Json
    $forgeLibraries = @($forge.libraries)
}
$classpath = [Collections.Generic.List[string]]::new()
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($library in $forgeLibraries + @($minecraft.libraries) + @($loader.libraries) + @($lwjgl.libraries)) {
    if ($WithForge -and $library.name -eq 'org.ow2.asm:asm-all:5.2') { continue }
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
# At GUI scale 3 the packaged check gets a real one-column, paginated Tools grid.
$windowWidth = if ($PackagedBoardProbe) { '1200' } else { '1280' }
$arguments = @(
    '-Xmx1G', "-Djava.library.path=$natives", "-Duser.home=$smokeHome", "-Dmmm.runtimeProbe=$($RuntimeProbe.IsPresent.ToString().ToLowerInvariant())",
    "-Dmmm.achievementProbe=$($AchievementProbe.IsPresent.ToString().ToLowerInvariant())",
    "-Dmmm.afkProbe=$($AfkProbe.IsPresent.ToString().ToLowerInvariant())",
    "-Dmmm.hudProbe=$($HudProbe.IsPresent.ToString().ToLowerInvariant())",
    "-Dmmm.boardProbe=$($PackagedBoardProbe.IsPresent.ToString().ToLowerInvariant())",
    "-Dmmm.boardProbeForge=$($WithForge.IsPresent.ToString().ToLowerInvariant())",
    '-Dlog4j2.formatMsgNoLookups=true', '-cp', ($classpath -join ';'),
    'net.minecraft.launchwrapper.Launch', '--tweakClass', 'com.mumfrey.liteloader.launch.LiteLoaderTweaker',
    '--gameDir', $game, '--assetsDir', "$PrismRoot/assets", '--assetIndex', '1.12',
    '--username', 'MMMPortTest', '--version', '1.12.2', '--accessToken', '0', '--userType', 'legacy', '--width', $windowWidth, '--height', '720'
)
if ($WithForge) {
    $arguments += @('--tweakClass', 'net.minecraftforge.fml.common.launcher.FMLTweaker')
}
if ($PackagedBoardProbe) {
    # No live services, real account, or launcher credentials in packaged verification.
    $arguments = @('-Dhttp.proxyHost=127.0.0.1', '-Dhttp.proxyPort=1', '-Dhttps.proxyHost=127.0.0.1', '-Dhttps.proxyPort=1',
        '-DsocksProxyHost=127.0.0.1', '-DsocksProxyPort=1', '-Dfml.ignorePatchDiscrepancies=false') + $arguments
}
if ($TransportProbe) {
    $classpath.Insert(0, $mod)
    $classpath.Insert(0, (Join-Path $root 'build/java8-probe'))
    $arguments = @('-Xmx256M', "-Duser.home=$smokeHome", '-cp', ($classpath -join ';'),
        'com.mmm.liteloader.probe.Java8TransportProbe', $game)
}
$logPrefix = if ($TransportProbe) { 'java8-transport' } elseif ($RuntimeProbe) { 'runtime' } else { 'smoke' }
if ($AchievementProbe) { $logPrefix = 'achievements' }
if ($AfkProbe) { $logPrefix = 'afk' }
if ($HudProbe) { $logPrefix = 'hud' }
if ($CompatibilityModsPath) { $logPrefix += '-compat' }
$logPrefix = if ($PackagedBoardProbe) { "review-$gameVariant" } else { "review-$variant-$logPrefix" }
$stdout = Join-Path $root "build/$logPrefix-stdout.log"
$stderr = Join-Path $root "build/$logPrefix-stderr.log"
$previousAppdata = $env:APPDATA
$process = $null
$timedOut = $false
try {
    $env:APPDATA = $appdata
    $process = Start-Process -FilePath $java -ArgumentList ($arguments | ForEach-Object { '"' + $_ + '"' }) -WorkingDirectory $game -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    if (!$process.WaitForExit($TimeoutSeconds * 1000)) {
        $timedOut = $true
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
if ($PackagedBoardProbe) {
    $process.Refresh()
    if ($timedOut -or $process.ExitCode -ne 0 -or !(Select-String -LiteralPath $stdout -SimpleMatch 'MMM_BOARD_PROBE_OK' -Quiet) -or
            (Select-String -LiteralPath $stdout -SimpleMatch 'MMM_BOARD_PROBE_FAILED' -Quiet)) { throw "Packaged $variant failed (exit $($process.ExitCode)); see $stdout" }
    if ((Get-FileHash -LiteralPath $installedMod).Hash -ne $sha) { throw 'Tested artifact changed.' }
    $loadedFrom = (Select-String -LiteralPath $stdout -SimpleMatch 'MMM_BOARD_SOURCE' | Select-Object -Last 1).Line
    if (!$loadedFrom -or !$loadedFrom.Contains("/mods/$installedName!/com/mmm/liteloader/LiteModMMM.class")) {
        throw "Implementation did not load from $installedName in $game."
    }
    $mode = if ($WithForge) { 'liteloader+forge' } else { 'liteloader' }
    [pscustomobject]@{
        mode = $mode
        sha256 = $sha
        exitCode = $process.ExitCode
        gameDirectory = $game
        loadedArtifact = $installedMod
        probe = (Join-Path $game 'mods/mmm-board-probe.litemod')
        stdout = $stdout
        stderr = $stderr
        screenshots = @(Get-ChildItem "$game/screenshots/board-*.png" | ForEach-Object FullName)
        shareExport = @(Get-ChildItem "$game/screenshots/mmm/*.png" | ForEach-Object FullName)
        loadedFrom = $loadedFrom
    }
}
if ($TransportProbe -and !(Select-String -LiteralPath $stdout -SimpleMatch 'JAVA8_TRANSPORT_OK' -Quiet)) {
    throw 'Java 8 packaged HTTP transport probe did not pass.'
}
if ($RuntimeProbe -and !(Select-String -LiteralPath $stdout -SimpleMatch 'MMM_RUNTIME_PROBE_OK' -Quiet)) {
    throw 'The isolated in-world runtime probe did not finish successfully. Inspect the runtime logs.'
}
if ($RuntimeProbe -and ($timedOut -or $process.ExitCode -ne 0 -or
        (Select-String -LiteralPath $stdout -SimpleMatch 'MMM_RUNTIME_PROBE_FAILED' -Quiet))) {
    throw 'The runtime probe failed or did not exit cleanly. A completion marker alone is not a passing result.'
}
if ($AchievementProbe -and !(Select-String -LiteralPath $stdout -SimpleMatch 'MMM_ACHIEVEMENT_PROBE_OK' -Quiet)) {
    throw 'The isolated achievement runtime probe did not finish successfully.'
}
if ($HudProbe -and !(Select-String -LiteralPath $stdout -SimpleMatch 'MMM_HUD_PROBE_OK' -Quiet)) {
    throw 'The isolated HUD framebuffer checks did not pass.'
}
