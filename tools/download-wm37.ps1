# SPDX-License-Identifier: GPL-3.0-or-later
# Fetches the WATERMeDIA 2.1.37 binary so the mixin injection point can be verified
# against the exact bytecode the mod runs on the device.  The Modrinth CDN resets
# long downloads, so this resumes and validates the archive.
$ErrorActionPreference = 'Continue'
$root = 'D:\DSH\WATERMeDIA Android Bridge'
$dl = Join-Path $root 'vendor\downloads'
New-Item -ItemType Directory -Force -Path $dl | Out-Null

$url = 'https://cdn.modrinth.com/data/G922NeHS/versions/fB0LmHnR/watermedia-2.1.37.jar'
$out = Join-Path $dl 'watermedia-2.1.37.jar'
$expected = 37046674   # size published on Modrinth

function Test-Jar([string]$path) {
  if (-not (Test-Path $path)) { return $false }
  $entries = & tar -tf $path 2>$null
  return ($LASTEXITCODE -eq 0) -and ($entries -contains 'META-INF/MANIFEST.MF')
}

if ((Test-Jar $out) -and ((Get-Item $out).Length -eq $expected)) {
  Write-Host "[skip] $out already complete ($((Get-Item $out).Length) bytes)"
} else {
  for ($attempt = 1; $attempt -le 6; $attempt++) {
    $have = if (Test-Path $out) { (Get-Item $out).Length } else { 0 }
    Write-Host "[get ] attempt $attempt, resuming from $have bytes"
    & curl.exe -sSL --retry 5 --retry-delay 3 --retry-all-errors --connect-timeout 30 -C - -o $out $url
    if ((Test-Jar $out) -and ((Get-Item $out).Length -eq $expected)) {
      Write-Host "[ok  ] $out ($((Get-Item $out).Length) bytes)"
      break
    }
    Write-Host "[warn] incomplete: $((Get-Item $out -ErrorAction SilentlyContinue).Length) bytes"
  }
}

if ((Test-Jar $out) -and ((Get-Item $out).Length -eq $expected)) {
  Write-Host ""
  Write-Host "verifying RenderAPI bytecode in 2.1.37:"
  $stage = Join-Path $root 'build\wm37'
  if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
  New-Item -ItemType Directory -Force -Path $stage | Out-Null
  & tar -xf $out -C $stage 'org/watermedia/api/render/RenderAPI.class' 'videolan/win-x64.zip' 2>$null
  $jp = 'C:\Users\明余游戏\AppData\Roaming\.minecraft\runtime\java-runtime-delta\bin\javap.exe'
  & $jp -p -c -classpath $stage org.watermedia.api.render.RenderAPI 2>&1 |
    Select-String -Pattern 'createByteBuffer|memAlignedAlloc|allocateDirect' | ForEach-Object { $_.Line.Trim() }
  Write-Host "windows VLC zip still shipped: $(Test-Path (Join-Path $stage 'videolan\win-x64.zip'))"
} else {
  Write-Host "[FAIL] watermedia-2.1.37.jar could not be downloaded completely"
}
