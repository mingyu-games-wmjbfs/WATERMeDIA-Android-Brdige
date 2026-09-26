# SPDX-License-Identifier: GPL-3.0-or-later
$ErrorActionPreference = 'Continue'
$root = 'D:\DSH\WATERMeDIA Android Bridge'
$dl = Join-Path $root 'vendor\downloads'
New-Item -ItemType Directory -Force -Path $dl | Out-Null

function Get-File($url, $out) {
  if ((Test-Path $out) -and ((Get-Item $out).Length -gt 1024)) {
    Write-Host "[skip] $out ($((Get-Item $out).Length) bytes)"
    return $true
  }
  Write-Host "[get ] $url"
  & curl.exe -sSL --fail --retry 3 --retry-delay 2 -o $out $url
  if ($LASTEXITCODE -ne 0) { Write-Host "[FAIL] $url"; return $false }
  $len = (Get-Item $out).Length
  Write-Host "[ok  ] $out ($len bytes)"
  return $true
}

Write-Host "===== WATERMeDIA core ====="
Get-File 'https://cdn.modrinth.com/data/G922NeHS/versions/yezkkXsZ/watermedia-2.1.36.jar'          "$dl\watermedia-2.1.36.jar"
Get-File 'https://cdn.modrinth.com/data/G922NeHS/versions/yezkkXsZ/watermedia-2.1.36-sources.jar'  "$dl\watermedia-2.1.36-sources.jar"
Get-File 'https://cdn.modrinth.com/data/G922NeHS/versions/fB0LmHnR/watermedia-2.1.37-sources.jar'  "$dl\watermedia-2.1.37-sources.jar"

Write-Host "===== WATERMeDIA Binaries (neoforge 1.21.1) ====="
$binJson = "$dl\watermedia-binaries-versions.json"
Get-File 'https://api.modrinth.com/v2/project/watermedia-binaries/version?loaders=%5B%22neoforge%22%5D' $binJson | Out-Null
try {
  $vers = Get-Content $binJson -Raw | ConvertFrom-Json
  foreach ($v in $vers) {
    if ($v.game_versions -contains '1.21.1') {
      $f = $v.files | Where-Object { $_.primary -eq $true } | Select-Object -First 1
      if ($f) {
        Write-Host "[info] binaries version $($v.version_number) -> $($f.filename)"
        Get-File $f.url "$dl\$($f.filename)" | Out-Null
        break
      }
    }
  }
} catch { Write-Host "[WARN] binaries parse failed: $_" }

Write-Host "===== VLC Android (libvlc-all AAR) ====="
$meta = "$dl\libvlc-all-maven-metadata.xml"
Get-File 'https://repo1.maven.org/maven2/org/videolan/android/libvlc-all/maven-metadata.xml' $meta | Out-Null
$latest = $null
if (Test-Path $meta) {
  [xml]$x = Get-Content $meta -Raw
  $latest = $x.metadata.versioning.release
  if (-not $latest) { $latest = $x.metadata.versioning.latest }
  Write-Host "[info] libvlc-all latest = $latest"
  Write-Host "[info] all versions: $($x.metadata.versioning.versions.version -join ', ')"
}
if ($latest) {
  Get-File "https://repo1.maven.org/maven2/org/videolan/android/libvlc-all/$latest/libvlc-all-$latest.aar" "$dl\libvlc-all-$latest.aar" | Out-Null
}

Write-Host "===== DONE ====="
Get-ChildItem $dl | Select-Object Name, Length | Format-Table -AutoSize
