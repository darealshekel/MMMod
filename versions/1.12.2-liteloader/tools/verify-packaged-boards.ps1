param(
    [string]$PrismRoot = "$env:APPDATA/PrismLauncher",
    [int]$TimeoutSeconds = 150,
    [switch]$JarFeasibility
)
$ErrorActionPreference = 'Stop'
$versionRoot = Split-Path $PSScriptRoot -Parent
$repo = (Resolve-Path (Join-Path $versionRoot '../..')).Path
$release = (Get-Content "$repo/release.properties" | Where-Object { $_ -match '^mod_version=' }) -replace '^mod_version=', ''
if ($release -notmatch '^\d+\.\d+\.\d+$') { throw 'Expected a release version.' }
$artifact = (Resolve-Path "$versionRoot/build/libs/mmm-$release+1.12.2-liteloader.litemod").Path
$sha = (Get-FileHash $artifact).Hash.ToLowerInvariant()
if ($JarFeasibility) {
    $primary = Get-Content -Raw "$repo/build/verification/$release/1.12.2-liteloader-packaged.json" | ConvertFrom-Json
    if ($primary.status -ne 'pass' -or $primary.sha256 -ne $sha -or
        ($primary.modes -join ',') -ne 'liteloader,liteloader+forge') { throw 'Verify the final primary .litemod in both modes first.' }
}
$runs = @(foreach ($forge in @($false, $true)) {
    & "$PSScriptRoot/verify-client.ps1" -PrismRoot $PrismRoot -TimeoutSeconds $TimeoutSeconds -PackagedBoardProbe -WithForge:$forge -ModArtifact $artifact -JarFeasibility:$JarFeasibility |
        ForEach-Object {
            if ($_ -is [string]) { Write-Host $_ }
            else { $_ }
        }
})
if ($runs.Count -ne 2 -or $runs[0].mode -ne 'liteloader' -or $runs[1].mode -ne 'liteloader+forge') { throw 'Missing dual-mode evidence.' }
foreach ($run in $runs) {
    if (!(Select-String -LiteralPath $run.stdout -SimpleMatch 'MMM_BOARD_KEYBOARD_OK' -Quiet) -or
        !(Select-String -LiteralPath $run.stdout -SimpleMatch 'MMM_BOARD_FOCUS_PIXELS' -Quiet)) { throw "Missing keyboard evidence for $($run.mode)." }
    $markers = @(Select-String -LiteralPath $run.stdout -SimpleMatch "MMM_BOARD_COLOR_PIXELS")
    $body = @(Select-String -LiteralPath $run.stdout -SimpleMatch "MMM_BOARD_BODY_PIXELS")
    if ($body.Count -lt 10) { throw "Missing rendered body evidence for $($run.mode)." }
    $run | Add-Member -NotePropertyName bodyChecks -NotePropertyValue @($body | ForEach-Object Line)
    $routes = @(Select-String -LiteralPath $run.stdout -SimpleMatch "MMM_BOARD_ROUTE_OK")
    $tools = @(Select-String -LiteralPath $run.stdout -SimpleMatch "MMM_BOARD_TOOL_OK")
    if ($tools.Count -ne 12) { throw "Missing tool click evidence for $($run.mode)." }
    $toolKeys = @(Select-String -LiteralPath $run.stdout -SimpleMatch "MMM_BOARD_TOOL_KEYS_OK ")
    if ($toolKeys.Count -ne 2) { throw "Missing Tools grid keyboard runs for $($run.mode)." }
    $pager = @(Select-String -LiteralPath $run.stdout -SimpleMatch "MMM_BOARD_PAGER_OK ")
    if ($pager.Count -ne 2) { throw "Missing Settings pager runs for $($run.mode)." }
    foreach ($size in @('wide','compact')) {
        if (!($pager.Line -cmatch "MMM_BOARD_PAGER_OK $size forward=(?<count>[1-9][0-9]*) back=\k<count>$")) {
            throw "Missing matched $size single-click Next/Previous counts for $($run.mode)."
        }
        $movement = if ($size -eq 'compact') { 'columns=1 pagedBothWays=true' } else { 'columns=2 pagedBothWays=false' }
        if (!($toolKeys.Line -cmatch "MMM_BOARD_TOOL_KEYS_OK $size $movement enter=Leaderboards$") -or
            !(Test-Path -LiteralPath (Join-Path $run.gameDirectory "screenshots/board-tool-keys-$size.png"))) {
            throw "Missing $size Tools arrow, focus or activation evidence for $($run.mode)."
        }
        if (!(Select-String -LiteralPath $run.stdout -Pattern "MMM_BOARD_INLINE_OK $size categories=[1-9][0-9]* controls=[1-9][0-9]*$" -Quiet) -or
            !(Select-String -LiteralPath $run.stdout -SimpleMatch "MMM_BOARD_EVERYDAY_CLICK_OK $size Mining Tracker" -Quiet) -or
            !(Select-String -LiteralPath $run.stdout -SimpleMatch "MMM_BOARD_INLINE_PIXELS $size Everyday settings ink=" -Quiet) -or
            !(Select-String -LiteralPath $run.stdout -SimpleMatch "MMM_BOARD_INLINE_PIXELS $size HUD ink=" -Quiet) -or
            !(Test-Path -LiteralPath (Join-Path $run.gameDirectory "screenshots/board-launcher-$size.png")) -or
            !(Test-Path -LiteralPath (Join-Path $run.gameDirectory "screenshots/board-inline-hud-$size.png"))) {
            throw "Missing $size inline category and return-path verification for $($run.mode)."
        }
        foreach ($name in @('Hotkeys','Projects','History','Summary','Scoreboard','Leaderboards')) {
            if (!($tools.Line -match "MMM_BOARD_TOOL_OK $size $name$") -or
                !(Test-Path -LiteralPath (Join-Path $run.gameDirectory "screenshots/board-tool-$size-$($name.ToUpperInvariant()).png"))) {
                throw "Missing $size $name board evidence for $($run.mode)."
            }
        }
    }
    $run | Add-Member -NotePropertyName toolClicks -NotePropertyValue @($tools | ForEach-Object Line)
    $run | Add-Member -NotePropertyName toolArrowKeys -NotePropertyValue @($toolKeys | ForEach-Object Line)
    $run | Add-Member -NotePropertyName pagerClicks -NotePropertyValue @($pager | ForEach-Object Line)
    $run | Add-Member -NotePropertyName inlineSettings -NotePropertyValue @((Select-String -LiteralPath $run.stdout -Pattern 'MMM_BOARD_(INLINE_OK|INLINE_PIXELS|EVERYDAY_CLICK_OK)').Line)
    if ($markers.Count -ne 2 -or $routes.Count -ne 29 -or
        !(Select-String -LiteralPath $run.stdout -SimpleMatch "MMM_BOARD_EDITORS_OK count=29" -Quiet)) { throw "Missing editor evidence for $($run.mode)." }
    foreach ($name in @("color-compact", "color-wide", "share") + @(0..28 | ForEach-Object { "route-$_" })) {
        if (!(Test-Path -LiteralPath (Join-Path $run.gameDirectory "screenshots/board-$name.png"))) { throw "Missing $name evidence for $($run.mode)." }
    }
    $run | Add-Member -NotePropertyName picker -NotePropertyValue @($markers | ForEach-Object Line)
    $run | Add-Member -NotePropertyName routes -NotePropertyValue @($routes | ForEach-Object Line)
    if ($run.exitCode -ne 0 -or $run.sha256 -ne $sha -or (Get-FileHash $run.loadedArtifact).Hash -ne $sha -or
        $run.screenshots.Count -lt 5 -or !$run.shareExport -or !$run.loadedFrom) { throw "Incomplete evidence for $($run.mode)." }
}
if ((Get-FileHash $artifact).Hash -ne $sha) { throw 'Release artifact changed during verification.' }
foreach ($run in $runs) {
    $file = Join-Path $run.gameDirectory "board-verification.json"
    $run | Add-Member -NotePropertyName receipt -NotePropertyValue $file
    $run | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $file -Encoding ascii
}
$dir = New-Item -ItemType Directory -Force "$repo/build/verification/$release"
$name = if ($JarFeasibility) { '1.12.2-liteloader-jar-discovery.json' } else { '1.12.2-liteloader-packaged.json' }
$receipt = Join-Path $dir.FullName $name
[ordered]@{
    status = 'pass'; loader = 'liteloader'; wrapper = '1.12.2-liteloader'; version = $release
    sha256 = $sha; artifact = $artifact; testedExtension = [IO.Path]::GetExtension($runs[0].loadedArtifact); modes = @('liteloader', 'liteloader+forge')
    scope = 'screens'; suite = 'legacy-inline-settings-v1'; legacyEditorRoutes = 'pass'; colorPicker = 'pass'; keyboardFocus = 'pass'; exitCode = 0; verifiedUtc = [DateTime]::UtcNow.ToString('o'); runs = $runs
} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $receipt -Encoding ascii
Write-Output "Verified $sha in both modes. Receipt: $receipt"
