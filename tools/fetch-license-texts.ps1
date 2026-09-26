# SPDX-License-Identifier: GPL-3.0-or-later
# Fetches the licence texts that must accompany the binaries shipped inside the mod jar.
$ErrorActionPreference = 'Continue'
$root = 'D:\DSH\WATERMeDIA Android Bridge'
$dl = Join-Path $root 'vendor\licenses'
New-Item -ItemType Directory -Force -Path $dl | Out-Null

$want = @(
  @{ file = 'GPL-3.0.txt';   url = 'https://www.gnu.org/licenses/gpl-3.0.txt';        marker = 'GNU GENERAL PUBLIC LICENSE' },
  @{ file = 'LGPL-2.1.txt';  url = 'https://www.gnu.org/licenses/lgpl-2.1.txt';       marker = 'GNU LESSER GENERAL PUBLIC LICENSE' },
  @{ file = 'Apache-2.0.txt'; url = 'https://www.apache.org/licenses/LICENSE-2.0.txt'; marker = 'Apache License' },
  @{ file = 'LLVM-exception.txt'; url = 'https://raw.githubusercontent.com/llvm/llvm-project/main/LICENSE.TXT'; marker = 'LLVM' }
)

foreach ($item in $want) {
  $out = Join-Path $dl $item.file
  if ((Test-Path $out) -and ((Get-Item $out).Length -gt 2000)) {
    Write-Host "[skip] $($item.file) ($((Get-Item $out).Length) bytes)"
    continue
  }
  & curl.exe -sSL --fail --retry 3 --retry-delay 2 --connect-timeout 30 -o $out $item.url
  if ($LASTEXITCODE -ne 0) { Write-Host "[FAIL] $($item.url)"; continue }
  $text = Get-Content $out -Raw -ErrorAction SilentlyContinue
  $ok = $text -and ($text -match [regex]::Escape($item.marker))
  Write-Host ("[{0}] {1,-18} {2,8} bytes" -f $(if ($ok) { 'ok  ' } else { 'warn' }), $item.file, (Get-Item $out).Length)
}

Write-Host ''
Write-Host '=== already present (VLC / PojavLauncher) ==='
Get-ChildItem $dl -Filter *.txt | Select-Object Name, Length | Format-Table -AutoSize
