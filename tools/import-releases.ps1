$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$workspace = Split-Path $root -Parent
$versions = @('1.12.2', '1.17.1', '1.19.4', '1.20.1', '1.21', '1.21.1', '1.21.4', '1.21.6', '1.21.11', '26.1.2', '26.2', '26.3')
$records = foreach ($mc in $versions) {
    $legacy = $mc -eq '1.12.2'
    $version = if ($legacy) { '1.0.20.2' } elseif ($mc -eq '1.21') { '1.0.21' } else { '1.0.20.1' }
    $suffix = if ($legacy) { '-liteloader' } else { '' }
    $tag = "v$version-mc$mc$suffix"
    $release = (& gh api "repos/darealshekel/MMMod/releases/tags/$tag") | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0) { throw "Cannot inspect $tag" }
    $name = "mmm-$version+$mc$suffix-source.zip"
    $asset = $release.assets | Where-Object name -eq $name
    $zip = if ($legacy) { Join-Path $workspace "release-assets/forge-fix-1.0.20.2/$name" } else { Join-Path $workspace "release-assets/sync-safety-1.0.20.1/$name" }
    $id = "$mc-" + $(if ($legacy) { 'liteloader' } else { 'fabric' })
    $dest = Join-Path $root "versions/$id"
    if (Test-Path -LiteralPath $dest) { throw "Refusing to overwrite $dest" }
    New-Item -ItemType Directory -Path $dest -Force | Out-Null
    if ($mc -eq '1.21') {
        $zip = Join-Path $root 'build/import-1.21.zip'
        New-Item -ItemType Directory -Path (Split-Path $zip -Parent) -Force | Out-Null
        & git -C (Join-Path $workspace '.mmmod-reliability-publish') archive --format=zip "--output=$zip" $tag
        if ($LASTEXITCODE -ne 0) { throw 'Cannot archive 1.21' }
    } else {
        if (!$asset -or !$asset.digest) { throw "Missing verified source asset for $tag" }
        $hash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
        if ("sha256:$hash" -ne $asset.digest) { throw "Source hash mismatch: $tag" }
    }
    Expand-Archive -LiteralPath $zip -DestinationPath $dest
    $props = Join-Path $dest 'gradle.properties'
    if (!$legacy -and !((Get-Content $props) -contains "minecraft_version=$mc")) { throw "Wrong Minecraft source: $tag" }
    [ordered]@{ id=$id; minecraft=$mc; version=$version; tag=$tag; sourceAsset=$name; sha256=(Get-FileHash $zip).Hash.ToLowerInvariant(); sourceUrl=$asset.browser_download_url; releaseUrl=$release.html_url }
}
$records | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $root 'versions.json') -Encoding utf8
