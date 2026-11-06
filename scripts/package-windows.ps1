# =====================================================================
# iskolarPath — Windows packaging
#
#   .\scripts\package-windows.ps1 [-Version 1.0.0] [-Jdk C:\dev\jdk-21]
#
# Produces, under target/packaging/:
#   iskolarPath-Setup-<version>.exe   single-file installer (NSIS)
#   iskolarPath-Setup-<version>.exe.sha256
#   app-image/iskolarPath/            runnable folder used for smoke tests
#
# Prereqs: Maven on PATH and a JDK 21 with jlink/jpackage (via -Jdk or
# JAVA_HOME). The JavaFX jmods and a portable NSIS are downloaded
# automatically on first run. jpackage itself only produces the
# app-image here; modern jpackage needs WiX for Windows installers, so
# the single-file setup EXE is wrapped with NSIS (no admin, per-user).
# =====================================================================
param(
    [string]$Version = "1.0.0",
    [string]$Jdk = $env:JAVA_HOME,
    [string]$JavaFxJmods = "$env:TEMP\javafx-jmods-21.0.8\javafx-jmods-21.0.8",
    [string]$Nsis = "",   # optional path to makensis.exe (auto-detected/downloaded otherwise)
    [switch]$SkipMaven,
    [switch]$SkipJlink
)

$ErrorActionPreference = "Stop"

$JavaFxVersion = "21.0.8"
$ApiUrl        = "https://iskolarpath-api.onrender.com"

$root    = Split-Path -Parent $PSScriptRoot            # repo root
$dist    = Join-Path $root "target\dist"
$out     = Join-Path $root "target\packaging"
$runtime = Join-Path $out "runtime"

Write-Host "== iskolarPath packaging (version $Version) =="

# ---- 1. JDK 21 -------------------------------------------------------
if (-not $Jdk -or -not (Test-Path (Join-Path $Jdk "bin\jpackage.exe"))) {
    throw "JDK 21 not found at '$Jdk'. Install Temurin 21 and pass -Jdk (or set JAVA_HOME)."
}
$jlink   = Join-Path $Jdk "bin\jlink.exe"
$jpackage = Join-Path $Jdk "bin\jpackage.exe"
Write-Host "JDK: $Jdk"

# ---- 2. JavaFX jmods --------------------------------------------------
# The Gluon jmods zip unpacks to <dir>\javafx-jmods-<ver>\*.jmod — the
# -JavaFxJmods parameter is that directory itself.
if (-not (Test-Path (Join-Path $JavaFxJmods "javafx.controls.jmod"))) {
    $zip = "$env:TEMP\openjfx-$JavaFxVersion-jmods.zip"
    if (-not (Test-Path $zip)) {
        $url = "https://download2.gluonhq.com/openjfx/$JavaFxVersion/openjfx-$JavaFxVersion`_windows-x64_bin-jmods.zip"
        Write-Host "Downloading JavaFX jmods: $url"
        $ProgressPreference = "SilentlyContinue"
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
    }
    Expand-Archive -Path $zip -DestinationPath (Split-Path -Parent $JavaFxJmods) -Force
}
Write-Host "JavaFX jmods: $JavaFxJmods"

# ---- 3. Build the app jar --------------------------------------------
if (-not $SkipMaven) {
    Write-Host "Building jar with Maven..."
    & mvn -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }
}
$mainJar = Get-ChildItem -Path $dist -Filter "iskolar-app-*.jar" | Select-Object -First 1
if (-not $mainJar) { throw "No iskolar-app-*.jar found in $dist" }
Write-Host "App jar: $($mainJar.Name)"

# ---- 4. Trimmed Java 21 + JavaFX runtime via jlink --------------------
if (-not $SkipJlink) {
    if (Test-Path $runtime) { Remove-Item -Recurse -Force $runtime }
    Write-Host "Creating trimmed runtime image..."
    & $jlink `
        --module-path "$(Join-Path $Jdk 'jmods');$JavaFxJmods" `
        --add-modules "java.base,java.desktop,java.sql,java.logging,java.naming,java.management,java.net.http,java.prefs,javafx.controls,javafx.fxml,javafx.graphics" `
        --bind-services --strip-debug --no-header-files --no-man-pages --compress zip-2 `
        --output $runtime
    if ($LASTEXITCODE -ne 0) { throw "jlink failed" }
}
Write-Host "Runtime image: $runtime"

# ---- 5. jpackage: app-image (what the installer will wrap) -----------
New-Item -ItemType Directory -Force -Path $out | Out-Null
$imageDir = Join-Path $out "app-image"
if (Test-Path $imageDir) { Remove-Item -Recurse -Force $imageDir }
Write-Host "Building app-image..."
& $jpackage `
    --type app-image `
    --name "iskolarPath" `
    --app-version $Version `
    --input $dist `
    --main-jar $mainJar.Name `
    --main-class "com.iskolar.Launcher" `
    --icon (Join-Path $root "packaging\iskolarPath.ico") `
    --vendor "iskolarPath" `
    --description "Discover, match, and track your scholarship journey." `
    --runtime-image $runtime `
    --java-options "-Diskolar.apiUrl=$ApiUrl" `
    --dest $imageDir
if ($LASTEXITCODE -ne 0) { throw "jpackage (app-image) failed" }
Write-Host "App image: $(Join-Path $imageDir 'iskolarPath')"

# ---- 6. NSIS: single-file installer ----------------------------------
function Find-Makensis {
    if ($Nsis) {
        if (Test-Path $Nsis) { return (Resolve-Path $Nsis).Path }
        throw "makensis.exe not found at '$Nsis'"
    }
    $cmd = Get-Command makensis.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        "$env:ProgramFiles(x86)\NSIS\makensis.exe",
        "$env:ProgramFiles\NSIS\makensis.exe",
        "$env:LOCALAPPDATA\Programs\NSIS\makensis.exe"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { return $c }
    }
    # Portable download (no admin, no installer). curl.exe is used because
    # SourceForge sometimes serves a truncated body to Invoke-WebRequest.
    $zip = "$env:TEMP\nsis-3.11.zip"
    if (-not (Test-Path $zip) -or (Get-Item $zip).Length -lt 1MB) {
        Write-Host "Downloading portable NSIS 3.11..."
        & curl.exe -L --retry 3 -o $zip "https://downloads.sourceforge.net/project/nsis/NSIS%203/3.11/nsis-3.11.zip"
        if ($LASTEXITCODE -ne 0 -or (Get-Item $zip).Length -lt 1MB) {
            throw "NSIS download failed"
        }
    }
    $nsisDir = "$env:TEMP\nsis"
    if (-not (Test-Path "$nsisDir\makensis.exe")) {
        if (Test-Path $nsisDir) { Remove-Item -Recurse -Force $nsisDir }
        New-Item -ItemType Directory -Force -Path $nsisDir | Out-Null
        & tar.exe -xf $zip -C $nsisDir   # bsdtar handles the zip without PS 5.1 quirks
        if ($LASTEXITCODE -ne 0) { throw "NSIS extraction failed" }
    }
    $found = Get-ChildItem -Path $nsisDir -Recurse -Filter "makensis.exe" | Select-Object -First 1
    if (-not $found) { throw "makensis.exe not found in the portable NSIS archive" }
    return $found.FullName
}

$makensis = Find-Makensis
$setupExe = Join-Path $out "iskolarPath-Setup-$Version.exe"
Write-Host "Building installer with NSIS ($makensis)..."
& $makensis "/DVERSION=$Version" "/DBUILDROOT=$root" (Join-Path $root "scripts\installer.nsi")
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $setupExe)) { throw "NSIS build failed" }

# ---- 7. Checksum ------------------------------------------------------
$hash = (Get-FileHash -Path $setupExe -Algorithm SHA256).Hash.ToLower()
Set-Content -Path "$setupExe.sha256" -Value "$hash  iskolarPath-Setup-$Version.exe"
Write-Host "SHA-256: $hash"

$imageSize = "{0:N1} MB" -f ((Get-ChildItem -Recurse (Join-Path $imageDir "iskolarPath") | Measure-Object Length -Sum).Sum / 1MB)
$exeSize   = "{0:N1} MB" -f ((Get-Item $setupExe).Length / 1MB)
Write-Host ""
Write-Host "Done. Installer: $exeSize | app-image: $imageSize"
