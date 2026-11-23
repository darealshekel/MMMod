$ErrorActionPreference = 'Stop'
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
$version = Join-Path $repository 'versions/1.21.11-fabric'
$output = Join-Path $version 'build/achievement-port-checks'
$pure = Join-Path $output 'pure'
$client = Join-Path $output 'client'
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
$javac = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/javac.exe' } else { 'javac' }
$javap = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/javap.exe' } else { 'javap' }
New-Item -ItemType Directory -Force -Path $pure, $client | Out-Null

# Reuse Loom's already-resolved dependencies without running Gradle or touching its outputs.
$remap = (Get-Content -Raw (Join-Path $version '.gradle/loom-cache/remapClasspath.txt')).Trim() -split ';'
$modulePath = $remap | Where-Object { $_ -match '[\\/]modules-2[\\/]files-2.1[\\/]' } | Select-Object -First 1
$moduleCache = ($modulePath -split '[\\/]modules-2[\\/]files-2.1[\\/]')[0] + '/modules-2/files-2.1'
function Dependency($group, $artifact, $versionNumber) {
    $directory = Join-Path $moduleCache "$group/$artifact/$versionNumber"
    $jar = Get-ChildItem -LiteralPath $directory -Filter "$artifact-$versionNumber.jar" -Recurse | Select-Object -First 1
    if (-not $jar) { throw "Missing cached dependency $group/$artifact/$versionNumber" }
    $jar.FullName
}
function Compile($destination, $classpath, $release, $sources) {
    $arguments = @('--release', "$release", '-encoding', 'UTF-8', '-proc:none', '-d', $destination, '-classpath', $classpath) + $sources
    $argfile = Join-Path $destination 'javac.args'
    [IO.File]::WriteAllLines($argfile, ($arguments | ForEach-Object { '"' + $_.Replace('\', '/') + '"' }))
    & $javac "@$argfile"
    if ($LASTEXITCODE -ne 0) { throw "Compilation failed: $destination" }
}
function RunTests($classpath, $classes) {
    & $java "-Dmmm.sources=$version/src/main/java" '-cp' $classpath RunAchievementTests @classes
    if ($LASTEXITCODE -ne 0) { throw 'Achievement tests failed' }
}

$junit = @(
    (Dependency 'org.junit.jupiter' 'junit-jupiter-api' '5.11.4'),
    (Dependency 'org.junit.jupiter' 'junit-jupiter-engine' '5.11.4'),
    (Dependency 'org.junit.platform' 'junit-platform-commons' '1.11.4'),
    (Dependency 'org.junit.platform' 'junit-platform-engine' '1.11.4'),
    (Dependency 'org.junit.platform' 'junit-platform-launcher' '1.11.4'),
    (Dependency 'org.opentest4j' 'opentest4j' '1.3.0'),
    (Dependency 'org.apiguardian' 'apiguardian-api' '1.1.2')
)
$manifest = Get-Content -Raw (Join-Path $version 'source-set.json') | ConvertFrom-Json
$portable = @('AchievementCardState', 'AchievementBadgeCatalog', 'MmmAdvancementDefinition', 'MmmAdvancementSnapshot') |
    ForEach-Object { Join-Path $repository "core/src/main/java/com/mmm/advancement/$_.java" }
$portable += Join-Path $repository $manifest.main.'com/mmm/tracker/CollectionProgress.java'
Compile $pure '.' 16 $portable
$pureTests = @('AchievementCardStateTest', 'AchievementBadgeCatalogTest') |
    ForEach-Object { Join-Path $repository "shared-tests/src/test/java/com/mmm/advancement/$_.java" }
Compile $pure ((@($pure) + $junit) -join ';') 21 ($pureTests + (Join-Path $PSScriptRoot 'RunAchievementTests.java'))
RunTests ((@($pure) + $junit) -join ';') @('com.mmm.advancement.AchievementCardStateTest', 'com.mmm.advancement.AchievementBadgeCatalogTest')

$minecraft = Get-ChildItem (Join-Path $version '.gradle/loom-cache/minecraftMaven') -Recurse -Filter '*.jar' |
    Where-Object { $_.Name -notmatch 'sources' } | Select-Object -First 1 -ExpandProperty FullName
$devJar = Get-ChildItem (Join-Path $version 'build/devlibs') -Filter '*-dev.jar' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
if (-not $minecraft -or -not $devJar) { throw 'Run the parent build once to populate the named Minecraft cache and dev jar' }
$namedMods = Get-ChildItem (Join-Path $version '.gradle/loom-cache/remapped_mods') -Recurse -Filter '*.jar' |
    Where-Object { $_.Name -notmatch 'sources' } | Select-Object -ExpandProperty FullName
$libraries = $remap | Where-Object { $_ -notmatch '[\\/](net.fabricmc.fabric-api|minecraftMaven)[\\/]' }
$classpath = (@($client, $pure, $minecraft, $devJar) + $junit + $namedMods + $libraries + @(
    (Dependency 'io.github.llamalad7' 'mixinextras-fabric' '0.5.4'),
    (Dependency 'net.fabricmc' 'sponge-mixin' '0.17.3+mixin.0.8.7')
)) -join ';'
$sources = @(
    'src/main/java/com/mmm/advancement/AchievementBadges.java',
    'src/main/java/com/mmm/advancement/AchievementCardRenderer.java',
    'src/main/java/com/mmm/mixin/AdvancementWidgetMixin.java',
    'src/smoke/java/com/mmm/smoke/SessionHoursBadgeSmoke.java',
    'src/test/java/com/mmm/advancement/AchievementBadgesTest.java'
) | ForEach-Object { Join-Path $version $_ }
Compile $client $classpath 21 $sources
RunTests ($classpath + ';' + (Join-Path $version 'src/main/resources')) @('com.mmm.advancement.AchievementBadgesTest')
$bytecode = (& $javap -classpath $minecraft -c -p net.minecraft.client.gui.screen.advancement.AdvancementWidget) -join "`n"
$calls = [regex]::Matches($bytecode, 'DrawContext\.drawItemWithoutEntity:\(Lnet/minecraft/item/ItemStack;II\)V').Count
if ($calls -ne 2) { throw "Expected two badge interception points, found $calls" }
'PASS: Java 16 portable core, focused JUnit tests, 1.21.11 adapters and smoke compiled, two widget interception points. No Gradle invocation.'
