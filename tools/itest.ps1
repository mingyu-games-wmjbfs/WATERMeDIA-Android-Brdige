# SPDX-License-Identifier: GPL-3.0-or-later
# Compiles and runs the desktop integration harness.  Two modes:
#   install  - exercises the real extraction code and the WATERMeDIA side contract
#   discover - loads WATERMeDIA's own Windows VLC through the configuration hook
$ErrorActionPreference = 'Stop'
$root = 'D:\DSH\WATERMeDIA Android Bridge'
$mc = 'D:\tools\PCL2\.minecraft'
$libs = Join-Path $mc 'libraries'

function Find-Java {
  $candidates = @()
  if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\javac.exe') }
  $runtimeRoot = Join-Path $env:APPDATA '.minecraft\runtime'
  foreach ($name in @('java-runtime-delta', 'java-runtime-epsilon', 'java-runtime-beta')) {
    $candidates += (Join-Path $runtimeRoot "$name\bin\javac.exe")
  }
  $onPath = Get-Command javac.exe -ErrorAction SilentlyContinue
  if ($onPath) { $candidates += $onPath.Source }
  foreach ($candidate in $candidates) { if ($candidate -and (Test-Path $candidate)) { return $candidate } }
  throw 'no javac found'
}

$javac = Find-Java
$java = Join-Path (Split-Path $javac) 'java.exe'
# Verify against the newest WATERMeDIA of the supported generation available locally:
# the mixin injection point is checked against its real bytecode and the discover mode
# extracts its bundled Windows VLC.
$waterMedia = @('watermedia-2.1.37.jar', 'watermedia-2.1.36.jar') |
  ForEach-Object { Join-Path $root "vendor\downloads\$_" } |
  Where-Object { Test-Path $_ } |
  Select-Object -First 1
if (-not $waterMedia) { throw 'no watermedia jar in vendor/downloads - run tools/download-deps.ps1' }
Write-Host "verifying against $waterMedia"

$classpath = @(
  (Join-Path $root 'build\classes'),
  $waterMedia,
  (Join-Path $libs 'net\java\dev\jna\jna\5.14.0\jna-5.14.0.jar'),
  (Join-Path $libs 'net\java\dev\jna\jna-platform\5.14.0\jna-platform-5.14.0.jar'),
  # WATERMeDIA's JarTool needs Gson, which Minecraft itself puts on the classpath
  (Join-Path $libs 'com\google\code\gson\gson\2.10.1\gson-2.10.1.jar'),
  (Join-Path $libs 'org\apache\logging\log4j\log4j-api\2.22.1\log4j-api-2.22.1.jar'),
  (Join-Path $libs 'org\apache\logging\log4j\log4j-core\2.22.1\log4j-core-2.22.1.jar')
) -join ';'

$itest = Join-Path $root 'build\itest'
if (Test-Path $itest) { Remove-Item $itest -Recurse -Force }
New-Item -ItemType Directory -Force -Path $itest | Out-Null
& $javac -encoding UTF-8 -proc:none -classpath $classpath -d $itest (Join-Path $root 'tools\itest\BridgeHarness.java')
if ($LASTEXITCODE -ne 0) { throw 'harness compilation failed' }

$modClasses = Join-Path $root 'build\classes'
$modJar = (Get-ChildItem (Join-Path $root 'dist') -Filter '*neoforge.jar' | Select-Object -First 1).FullName
if (-not $modJar) { throw 'no built mod jar in dist/ - run tools/build.ps1 first' }
$deps = @(
  $waterMedia,
  (Join-Path $libs 'net\java\dev\jna\jna\5.14.0\jna-5.14.0.jar'),
  (Join-Path $libs 'net\java\dev\jna\jna-platform\5.14.0\jna-platform-5.14.0.jar'),
  (Join-Path $libs 'com\google\code\gson\gson\2.10.1\gson-2.10.1.jar'),
  (Join-Path $libs 'org\apache\logging\log4j\log4j-api\2.22.1\log4j-api-2.22.1.jar'),
  (Join-Path $libs 'org\apache\logging\log4j\log4j-core\2.22.1\log4j-core-2.22.1.jar')
) -join ';'

$runDir = Join-Path $root 'build\itest-run'

function Invoke-Harness([string]$label, [string]$modPath, [string[]]$harnessArgs) {
  Write-Host ''
  Write-Host "################ $label ################"
  # the JVM's own output must be routed to the host, otherwise it lands in this
  # function's return value together with the exit code.  VLC itself writes
  # diagnostics to stderr, which must not abort the script.
  $previous = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  & $java -cp "$itest;$modPath;$deps" BridgeHarness @harnessArgs 2>&1 | ForEach-Object { Write-Host $_ }
  $code = $LASTEXITCODE
  $ErrorActionPreference = $previous
  return $code
}

$exits = @()
# contract checks against the compiled classes
$exits += Invoke-Harness 'mode=install (classes)' $modClasses @('install', (Join-Path $runDir 'game-install'))
# the same checks against the packaged jar: proves the payload really is inside it
$exits += Invoke-Harness 'mode=install (packaged jar)' $modJar @('install', (Join-Path $runDir 'game-install-jar'))
# the real discovery chain, using WATERMeDIA's own Windows VLC
$exits += Invoke-Harness 'mode=discover' $modClasses @('discover', (Join-Path $runDir 'game-discover'), $waterMedia)

Write-Host ''
Write-Host ('exit codes: ' + ($exits -join ', '))
if ($exits | Where-Object { $_ -ne 0 }) { throw 'integration harness reported failures' }
Write-Host 'all integration checks passed'
