param(
    [Parameter(Mandatory)][string]$Version,
    [ValidateSet('baseline','after')][string]$Phase = 'after'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$project = Join-Path $root "versions/$Version"
$classesRoot = Join-Path $project 'build/classes/java/main'
if (!(Test-Path $classesRoot)) { throw "No compiled classes: $Version" }
$out = Join-Path $root 'build/verification'
New-Item -ItemType Directory -Path $out -Force | Out-Null
$classes = [ordered]@{}
Get-ChildItem -LiteralPath $classesRoot -File -Recurse | Where-Object Extension -eq '.class' | Sort-Object FullName | ForEach-Object {
    $key = [IO.Path]::GetRelativePath($classesRoot, $_.FullName).Replace('\','/')
    $classes[$key] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
}
$classes | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $out "$Phase-$Version-classes.json") -Encoding utf8
$tests = 0; $skips = 0; $failures = 0; $errors = 0
$testDest = Join-Path $out "$Phase-$Version-tests"
New-Item -ItemType Directory -Path $testDest -Force | Out-Null
Get-ChildItem -LiteralPath (Join-Path $project 'build/test-results/test') -Filter 'TEST-*.xml' | ForEach-Object {
    [xml]$doc = Get-Content -LiteralPath $_.FullName -Raw
    $tests += [int]$doc.testsuite.tests
    $skips += [int]$doc.testsuite.skipped
    $failures += [int]$doc.testsuite.failures
    $errors += [int]$doc.testsuite.errors
    Copy-Item -LiteralPath $_.FullName -Destination $testDest -Force
}
[ordered]@{ id=$Version; phase=$Phase; classes=$classes.Count; tests=$tests; skipped=$skips; failures=$failures; errors=$errors } | ConvertTo-Json | Tee-Object -FilePath (Join-Path $out "$Phase-$Version-result.json")
if ($tests -eq 0 -or $failures -gt 0 -or $errors -gt 0) { throw "Tests not passing: $Version" }
