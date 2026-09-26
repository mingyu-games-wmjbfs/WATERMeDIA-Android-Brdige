# SPDX-License-Identifier: GPL-3.0-or-later
$ErrorActionPreference = 'Continue'
$root = 'D:\DSH\WATERMeDIA Android Bridge'
$dl = Join-Path $root 'vendor\downloads'
$jna = Join-Path $root 'vendor\jna'
New-Item -ItemType Directory -Force -Path $jna | Out-Null

$ref = 'v3_openjdk'
$base = "https://api.github.com/repos/PojavLauncherTeam/PojavLauncher/contents/app_pojavlauncher/src/main/jniLibs"
$abis = @('arm64-v8a','armeabi-v7a','x86_64','x86')

foreach ($abi in $abis) {
  $tmp = Join-Path $dl "jnidispatch-$abi.json"
  & curl.exe -sS -m 120 --ssl-no-revoke -H "User-Agent: dsh" -o $tmp "$base/$abi/libjnidispatch.so?ref=$ref"
  if (-not (Test-Path $tmp)) { Write-Host "[miss] $abi (no response)"; continue }
  try {
    # NOTE: no ConvertFrom-Json here - PS 5.1 caps JSON length at 2MB and the
    # base64 payload of libjnidispatch.so is close to that limit.
    $raw = [System.IO.File]::ReadAllText($tmp)
    if ($raw -match '"content"\s*:\s*"([^"]+)"') {
      # GitHub wraps the base64 payload with literal \n escape sequences
      $b64 = $Matches[1] -replace '\\n', '' -replace '\s', ''
      $bytes = [Convert]::FromBase64String($b64)
      $out = Join-Path $jna "libjnidispatch-$abi.so"
      [System.IO.File]::WriteAllBytes($out, $bytes)
      Write-Host "[ok  ] $abi -> $($bytes.Length) bytes"
    } else {
      Write-Host "[miss] $abi (no content field)"
    }
  } catch { Write-Host "[FAIL] $abi : $_" }
}

Write-Host "===== version strings inside jnidispatch ====="
foreach ($f in Get-ChildItem $jna -Filter *.so -ErrorAction SilentlyContinue) {
  $b = [System.IO.File]::ReadAllBytes($f.FullName)
  $cur = New-Object System.Text.StringBuilder
  $found = New-Object System.Collections.Generic.List[string]
  foreach ($x in $b) {
    if ($x -ge 32 -and $x -lt 127) { [void]$cur.Append([char]$x) }
    else { if ($cur.Length -ge 4) { [void]$found.Add($cur.ToString()) }; [void]$cur.Clear() }
  }
  $vers = $found | Where-Object { $_ -match '^\d+\.\d+\.\d+$' } | Sort-Object -Unique
  Write-Host "$($f.Name): versions=$($vers -join ',')"
}
