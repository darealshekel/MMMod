$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = Split-Path $PSScriptRoot -Parent
$versions = Get-Content (Join-Path $root 'versions.json') -Raw | ConvertFrom-Json
$release = (Get-Content (Join-Path $root 'release.properties') | Where-Object {$_ -match '^mod_version='}) -replace '^mod_version=', ''
$out = Join-Path $root "release-artifacts/$release"
New-Item -ItemType Directory -Path $out -Force | Out-Null
function Read-ZipText($zip, [string]$name) {
    $entry = $zip.GetEntry($name)
    if (!$entry) { throw "Missing artifact entry: $name" }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}
$manifest = foreach ($v in $versions) {
    $legacy = $v.id.EndsWith('-liteloader')
    $fullVersion = "$release+$($v.minecraft)" + $(if ($legacy) {'-liteloader'} else {''})
    $name = "mmm-$fullVersion" + $(if ($legacy) {'.litemod'} else {'.jar'})
    $project = Join-Path $root "versions/$($v.id)"
    $artifact = Join-Path $project "build/libs/$name"
    if (!(Test-Path -LiteralPath $artifact)) { throw "Missing artifact $name" }
    $baseline = Get-Content (Join-Path $root "build/verification/baseline-$($v.id)-classes.json") -Raw | ConvertFrom-Json -AsHashtable
    $classesDir = Join-Path $project 'build/classes/java/main'
    $classes = @(Get-ChildItem $classesDir -Filter '*.class' -Recurse -File)
    if ($classes.Count -ne $baseline.Count) { throw "Class count changed: $($v.id)" }
    $changes = @()
    foreach ($file in $classes) {
        $logical = [IO.Path]::GetRelativePath($classesDir, $file.FullName).Replace('\','/')
        $hash = (Get-FileHash -LiteralPath $file.FullName).Hash
        if (!$baseline.ContainsKey($logical)) { throw "Unexpected class: $logical" }
        if ($hash -ne $baseline[$logical]) { $changes += $logical }
    }
    if (@($changes | Where-Object {!($legacy -and $_ -eq 'com/mmm/Reference.class')}).Count) { throw "Gameplay bytecode changed: $($v.id): $changes" }
    $tests = 0; $skipped = 0; $failures = 0
    Get-ChildItem (Join-Path $project 'build/test-results/test') -Filter 'TEST-*.xml' | ForEach-Object {
        [xml]$xml = Get-Content $_.FullName -Raw
        $tests += [int]$xml.testsuite.tests
        $skipped += [int]$xml.testsuite.skipped
        $failures += [int]$xml.testsuite.failures + [int]$xml.testsuite.errors
    }
    if ($tests -eq 0 -or $failures -ne 0) { throw "Tests not passing: $($v.id)" }
    $zip = [IO.Compression.ZipFile]::OpenRead($artifact)
    try {
        $metadata = Read-ZipText $zip $(if ($legacy) {'litemod.json'} else {'fabric.mod.json'}) | ConvertFrom-Json
        if ($metadata.version -ne $fullVersion) { throw "Incorrect embedded version in $name" }
        if ($legacy -and $metadata.mcversion -ne $v.minecraft) { throw "Incorrect Minecraft version in $name" }
        if (@($zip.Entries | Group-Object FullName | Where-Object Count -gt 1).Count) { throw "Duplicate archive entries: $name" }
        if ($zip.Entries.FullName -match 'com/mmm/smoke/|RuntimeProbe|mmm_smoke') { throw "Test-only code in release: $name" }
        if ($legacy) {
            $refmap = Read-ZipText $zip 'mmm.refmap.json' | ConvertFrom-Json
            if (!$refmap.data.searge -or !$refmap.data.notch) { throw 'Missing Forge/LiteLoader mapping contexts' }
            foreach ($entry in $zip.Entries | Where-Object FullName -like '*.class') {
                $reader = [IO.BinaryReader]::new($entry.Open())
                try {
                    $header = $reader.ReadBytes(8)
                    if ($header.Length -ne 8 -or ($header[6] * 256 + $header[7]) -gt 52) { throw "Not Java 8: $($entry.FullName)" }
                } finally { $reader.Dispose() }
            }
        }
    } finally { $zip.Dispose() }
    Copy-Item -LiteralPath $artifact -Destination (Join-Path $out $name) -Force
    [ordered]@{id=$v.id; minecraft=$v.minecraft; version=$fullVersion; filename=$name; sha256=(Get-FileHash $artifact).Hash.ToLowerInvariant(); bytes=(Get-Item $artifact).Length; classes=$classes.Count; intentionalClassChanges=$changes; tests=$tests; passed=$tests-$skipped; skipped=$skipped; failures=$failures}
}
$manifest | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $out 'manifest.json') -Encoding utf8
$manifest | ForEach-Object { [pscustomobject]$_ } | Format-Table id,passed,skipped,classes,filename -AutoSize
