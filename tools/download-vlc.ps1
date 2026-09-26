# SPDX-License-Identifier: GPL-3.0-or-later
$ErrorActionPreference = 'Continue'
$root = 'D:\DSH\WATERMeDIA Android Bridge'
$dl = Join-Path $root 'vendor\downloads'
$tools = Join-Path $root 'vendor\tools'
New-Item -ItemType Directory -Force -Path $dl, $tools | Out-Null

function Get-File($url, $out) {
  if ((Test-Path $out) -and ((Get-Item $out).Length -gt 1024)) {
    Write-Host "[skip] $out ($((Get-Item $out).Length) bytes)"
    return $true
  }
  Write-Host "[get ] $url"
  & curl.exe -L --fail --retry 3 --retry-delay 3 --connect-timeout 30 -o $out $url
  if ($LASTEXITCODE -ne 0) { Write-Host "[FAIL] exit=$LASTEXITCODE $url"; return $false }
  Write-Host "[ok  ] $out ($((Get-Item $out).Length) bytes)"
  return $true
}

Write-Host "===== decompiler (vineflower) ====="
Get-File 'https://repo1.maven.org/maven2/org/vineflower/vineflower/1.10.1/vineflower-1.10.1.jar' "$tools\vineflower-1.10.1.jar" | Out-Null

Write-Host "===== VLC Android native libs (libvlc-all) ====="
$ver = '3.7.6'
Get-File "https://repo1.maven.org/maven2/org/videolan/android/libvlc-all/$ver/libvlc-all-$ver.aar" "$dl\libvlc-all-$ver.aar" | Out-Null

Write-Host "===== DONE ====="
Get-ChildItem $dl, $tools | Select-Object Name, Length | Format-Table -AutoSize
