# SPDX-License-Identifier: GPL-3.0-or-later
$ErrorActionPreference = 'Continue'
$root = 'D:\DSH\WATERMeDIA Android Bridge'
$dl = Join-Path $root 'vendor\downloads'
New-Item -ItemType Directory -Force -Path $dl | Out-Null

$ver = '3.7.1'
foreach ($abi in @('arm64-v8a', 'armeabi-v7a', 'x86_64')) {
  $url = "https://get.videolan.org/vlc-android/$ver/VLC-Android-$ver-$abi.apk"
  $out = "$dl\VLC-Android-$ver-$abi.apk"
  if ((Test-Path $out) -and ((Get-Item $out).Length -gt 1MB)) {
    Write-Host "[skip] $out ($((Get-Item $out).Length) bytes)"
    continue
  }
  Write-Host "[get ] $url"
  & curl.exe -L --fail --retry 3 --retry-delay 3 --connect-timeout 30 -o $out $url
  if ($LASTEXITCODE -ne 0) { Write-Host "[FAIL] $url" } else { Write-Host "[ok  ] $out ($((Get-Item $out).Length) bytes)" }
}
Write-Host "===== DONE ====="
Get-ChildItem $dl -Filter *.apk | Select-Object Name, Length | Format-Table -AutoSize
