# SPDX-License-Identifier: GPL-3.0-or-later
# Builds the mod jar with a plain JDK (no Gradle, no network): javac against the
# NeoForge/Minecraft jars that are already installed on this machine, then jar.
$ErrorActionPreference = 'Stop'
$root = 'D:\DSH\WATERMeDIA Android Bridge'
$mc = 'D:\tools\PCL2\.minecraft'
$version = '1.0.4'
$mcVersion = '1.21.1'
$neoVersion = '21.1.235'
$modId = 'watermedia_android_bridge'

function Find-Jdk {
  $candidates = @()
  if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\javac.exe') }
  $runtimeRoot = Join-Path $env:APPDATA '.minecraft\runtime'
  foreach ($name in @('java-runtime-delta', 'java-runtime-epsilon', 'java-runtime-beta')) {
    $candidates += (Join-Path $runtimeRoot "$name\bin\javac.exe")
  }
  $onPath = Get-Command javac.exe -ErrorAction SilentlyContinue
  if ($onPath) { $candidates += $onPath.Source }
  foreach ($candidate in $candidates) {
    if ($candidate -and (Test-Path $candidate)) { return $candidate }
  }
  throw 'no javac found - install a JDK 17+ or set JAVA_HOME'
}

$javac = Find-Jdk
$jarExe = Join-Path (Split-Path $javac) 'jar.exe'
Write-Host "javac: $javac"

# ------------------------------------------------------- licence hygiene (SPDX)
$missingSpdx = @()
foreach ($file in @(Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java) +
                   @(Get-ChildItem (Join-Path $root 'tools') -Recurse -Include *.java, *.ps1)) {
  if (-not (Select-String -Path $file.FullName -Pattern 'SPDX-License-Identifier: GPL-3.0-or-later' -List -Quiet)) {
    $missingSpdx += $file.FullName
  }
}
if ($missingSpdx.Count -gt 0) {
  throw "these sources are missing an SPDX licence header: $($missingSpdx -join ', ')"
}
Write-Host 'SPDX licence headers present on every source file'

# ---------------------------------------------------------------- classpath
$libs = Join-Path $mc 'libraries'
$classpath = New-Object System.Collections.Generic.List[string]
$classpath.Add((Join-Path $libs "net\neoforged\neoforge\$neoVersion\neoforge-$neoVersion-universal.jar"))
$classpath.Add((Join-Path $libs "net\neoforged\neoforge\$neoVersion\neoforge-$neoVersion-client.jar"))
$classpath.Add((Join-Path $libs 'net\neoforged\fancymodloader\loader\4.0.44\loader-4.0.44.jar'))
$classpath.Add((Join-Path $libs 'net\neoforged\bus\8.0.5\bus-8.0.5.jar'))
$classpath.Add((Join-Path $libs 'net\neoforged\mergetool\2.0.0\mergetool-2.0.0-api.jar'))
$classpath.Add((Join-Path $libs 'net\fabricmc\sponge-mixin\0.15.2+mixin.0.8.7\sponge-mixin-0.15.2+mixin.0.8.7.jar'))
$classpath.Add((Join-Path $libs 'org\lwjgl\lwjgl\3.3.3\lwjgl-3.3.3.jar'))
$classpath.Add((Join-Path $libs 'net\java\dev\jna\jna\5.14.0\jna-5.14.0.jar'))
$classpath.Add((Join-Path $libs 'org\apache\logging\log4j\log4j-api\2.22.1\log4j-api-2.22.1.jar'))
$classpath.Add((Join-Path $root 'vendor\downloads\watermedia-2.1.36.jar'))
$classpath.Add((Join-Path $libs 'net\minecraft\client\1.21.1-20240808.144430\client-1.21.1-20240808.144430-srg.jar'))

$existing = @()
foreach ($entry in $classpath) {
  if (Test-Path $entry) { $existing += $entry }
  else { Write-Host "[warn] missing compile dependency: $entry" }
}
$cp = $existing -join ';'

# ---------------------------------------------------------------- compile
$classes = Join-Path $root 'build\classes'
$distDir = Join-Path $root 'dist'
if (Test-Path $classes) { Remove-Item $classes -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classes, $distDir | Out-Null

$sources = @(Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
Write-Host "compiling $($sources.Count) source file(s) with --release 17"

# the workspace path contains spaces, so the sources are passed as real argv
# entries instead of through a javac argument file.
# -proc:none: the sponge-mixin annotation processor must not run (no mixins here,
# and it aborts javac when ASM is not on the processor path).
# -Xlint:-path: WATERMeDIA's manifest Class-Path names jars that only exist in a
# real installation, which is irrelevant for compilation.
$javacArgs = @('-encoding', 'UTF-8', '--release', '17', '-proc:none',
  '-Xlint:all', '-Xlint:-serial', '-Xlint:-path',
  '-classpath', $cp, '-d', $classes) + $sources
& $javac @javacArgs
if ($LASTEXITCODE -ne 0) { throw "compilation failed (exit $LASTEXITCODE)" }

Copy-Item (Join-Path $root 'src\main\resources\*') -Destination $classes -Recurse -Force
Write-Host "classes + resources staged in $classes"

# ---------------------------------------------------------------- package
$jarName = "$modId-$version+mc$mcVersion-neoforge.jar"
$jarPath = Join-Path $distDir $jarName
if (Test-Path $jarPath) { Remove-Item $jarPath -Force }
$manifest = Join-Path $root 'build\MANIFEST.MF'
Set-Content -Path $manifest -Encoding ASCII -Value @(
  'Manifest-Version: 1.0',
  'Implementation-Title: WATERMeDIA: Android Bridge',
  "Implementation-Version: $version"
)
& $jarExe --create --file $jarPath --manifest $manifest -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar creation failed (exit $LASTEXITCODE)" }

$sourceJar = Join-Path $distDir "$modId-$version-sources.jar"
if (Test-Path $sourceJar) { Remove-Item $sourceJar -Force }
# The source archive must not contain the ~127 MiB native payload; tools/pack-payload.ps1
# rebuilds it from the official VLC APKs.
$srcStage = Join-Path $root 'build\sources-stage'
if (Test-Path $srcStage) { Remove-Item $srcStage -Recurse -Force }
New-Item -ItemType Directory -Force -Path $srcStage | Out-Null
Copy-Item (Join-Path $root 'src\main\java') -Destination $srcStage -Recurse -Force
New-Item -ItemType Directory -Force -Path (Join-Path $srcStage 'resources') | Out-Null
Copy-Item (Join-Path $root 'src\main\resources\META-INF') -Destination (Join-Path $srcStage 'resources') -Recurse -Force
Copy-Item (Join-Path $root 'src\main\resources\pack.mcmeta') -Destination (Join-Path $srcStage 'resources') -Force
Copy-Item (Join-Path $root 'src\main\resources\watermedia_android_bridge.mixins.json') -Destination (Join-Path $srcStage 'resources') -Force
Copy-Item (Join-Path $root 'LICENSE') -Destination $srcStage -Force
& $jarExe --create --file $sourceJar -C $srcStage .
if ($LASTEXITCODE -ne 0) { throw "source jar creation failed (exit $LASTEXITCODE)" }

$size = (Get-Item $jarPath).Length
Write-Host ""
Write-Host ("built {0} ({1:N1} MiB)" -f $jarPath, ($size / 1MB))
Write-Host ("built {0}" -f $sourceJar)
