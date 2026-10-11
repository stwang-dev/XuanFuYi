# 悬浮译 · 一键打包脚本（Windows / PowerShell 5.1+）
#
# 用法（在仓库根目录执行）：
#   powershell -ExecutionPolicy Bypass -File build.ps1
#
# 需要 JDK 8 以上（脚本从 JAVA_HOME 取 java / javac / keytool / jar）。
# 安卓打包工具默认从 tools\ 目录找，先跑一次 fetch-tools.ps1 就会自动下载。
# 也可以手动指定：
#   build.ps1 -BuildTools "D:\sdk\build-tools\34.0.0" -AndroidJar "D:\sdk\platforms\android-34\android.jar" -R8Jar "D:\sdk\r8.jar"
param(
  [string]$Root        = "",
  [string]$JavaHome    = $env:JAVA_HOME,
  [string]$BuildTools  = "",
  [string]$AndroidJar  = "",
  [string]$R8Jar       = "",
  [string]$KsPath      = "",
  [string]$KsPass      = "123456",
  [string]$KsAlias     = "lingo",
  [int]   $VersionCode = 3,
  [string]$VersionName = "1.2",
  [string]$OutDir      = ""
)

$ErrorActionPreference = "Stop"
if (-not $Root)   { $Root   = $PSScriptRoot }
if (-not $OutDir) { $OutDir = Join-Path $Root "dist" }
if (-not $KsPath) { $KsPath = Join-Path $Root "keystore\xuanfuyi.keystore" }

function Find-First($paths) {
  foreach ($p in $paths) { if ($p -and (Test-Path -LiteralPath $p)) { return $p } }
  return ""
}

if (-not $BuildTools) {
  $BuildTools = Find-First @(
    (Join-Path $Root "tools\bt\android-14"),
    (Join-Path $Root "tools\build-tools"),
    (Join-Path $env:ANDROID_HOME "build-tools\34.0.0"),
    (Join-Path $env:ANDROID_SDK_ROOT "build-tools\34.0.0")
  )
}
if (-not $AndroidJar) {
  $AndroidJar = Find-First @(
    (Join-Path $Root "tools\pf\android-34\android.jar"),
    (Join-Path $Root "tools\android.jar"),
    (Join-Path $env:ANDROID_HOME "platforms\android-34\android.jar"),
    (Join-Path $env:ANDROID_SDK_ROOT "platforms\android-34\android.jar")
  )
}
if (-not $R8Jar) {
  $R8Jar = Find-First @(
    (Join-Path $Root "tools\r8.jar"),
    (Join-Path ${env:ProgramFiles} "Android\Android Studio\plugins\android\lib\r8.jar"),
    (Join-Path $env:LOCALAPPDATA "Programs\Android Studio\plugins\android\lib\r8.jar"),
    (Join-Path ${env:ProgramFiles(x86)} "Android\Android Studio\plugins\android\lib\r8.jar")
  )
}

if (-not $BuildTools) { throw "找不到 build-tools 目录。先跑一次 fetch-tools.ps1，或用 -BuildTools 指定。" }
if (-not $AndroidJar) { throw "找不到 android.jar。先跑一次 fetch-tools.ps1，或用 -AndroidJar 指定。" }
if (-not $R8Jar)      { throw "找不到 r8.jar（里面是 D8 编译器）。先跑一次 fetch-tools.ps1，或用 -R8Jar 指定。" }
if (-not $JavaHome)   { throw "没有设置 JAVA_HOME。例：`$env:JAVA_HOME = 'C:\develop\jdk-21'" }

$java    = Join-Path $JavaHome "bin\java.exe"
$javac   = Join-Path $JavaHome "bin\javac.exe"
$keytool = Join-Path $JavaHome "bin\keytool.exe"
$jarcmd  = Join-Path $JavaHome "bin\jar.exe"
foreach ($t in @($java, $javac, $keytool, $jarcmd)) {
  if (-not (Test-Path -LiteralPath $t)) { throw ("JAVA_HOME 里找不到 " + $t) }
}

$app   = Join-Path $Root "app"
$build = Join-Path $Root "build"
New-Item -ItemType Directory -Force -Path $build, $OutDir, (Split-Path -Parent $KsPath) | Out-Null
Remove-Item (Join-Path $build "gen"), (Join-Path $build "classes"), (Join-Path $build "dex") -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "[1/7] 编译资源"
& (Join-Path $BuildTools "aapt2.exe") compile --dir (Join-Path $app "res") -o (Join-Path $build "res.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "[2/7] 链接资源与清单"
& (Join-Path $BuildTools "aapt2.exe") link -o (Join-Path $build "app-unsigned.apk") -I $AndroidJar `
  --manifest (Join-Path $app "AndroidManifest.xml") (Join-Path $build "res.zip") `
  --java (Join-Path $build "gen") --min-sdk-version 26 --target-sdk-version 33 `
  --version-code $VersionCode --version-name $VersionName
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "[3/7] 编译 Java"
New-Item -ItemType Directory -Force -Path (Join-Path $build "classes") | Out-Null
$src = @((Join-Path $build "gen\com\lingo\bubble\R.java")) + (Get-ChildItem (Join-Path $app "src\com\lingo\bubble\*.java") | Select-Object -Expand FullName)
& $javac -encoding UTF-8 -source 8 -target 8 -nowarn -bootclasspath $AndroidJar -classpath $AndroidJar -d (Join-Path $build "classes") @src
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "[4/7] 生成 dex"
New-Item -ItemType Directory -Force -Path (Join-Path $build "dex") | Out-Null
& $jarcmd cf (Join-Path $build "classes.jar") -C (Join-Path $build "classes") .
& $java -cp $R8Jar com.android.tools.r8.D8 --release --min-api 26 --lib $AndroidJar --output (Join-Path $build "dex") (Join-Path $build "classes.jar")
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "[5/7] 组装 APK"
$apk = Join-Path $build "app-dexed.apk"
Copy-Item (Join-Path $build "app-unsigned.apk") $apk -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open($apk, "Update")
$dex = Join-Path $build "dex\classes.dex"
$entry = $zip.GetEntry("classes.dex")
if ($entry) { $entry.Delete() }
[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $dex, "classes.dex", "Optimal") | Out-Null
$zip.Dispose()

Write-Host "[6/7] 对齐"
$aligned = Join-Path $build "app-aligned.apk"
& (Join-Path $BuildTools "zipalign.exe") -f -p 4 $apk $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "[7/7] 签名"
if (-not (Test-Path -LiteralPath $KsPath)) {
  Write-Host ("首次打包，生成签名文件：" + $KsPath)
  & $keytool -genkeypair -keystore $KsPath -alias $KsAlias -keyalg RSA -keysize 2048 -validity 10950 `
    -storepass $KsPass -keypass $KsPass -dname "CN=XuanFuYi, O=Lingo, C=CN"
}
$final = Join-Path $OutDir "悬浮译.apk"
& (Join-Path $BuildTools "apksigner.bat") sign --v4-signing-enabled false --ks $KsPath --ks-key-alias $KsAlias `
  --ks-pass ("pass:" + $KsPass) --key-pass ("pass:" + $KsPass) --out $final $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }
& (Join-Path $BuildTools "apksigner.bat") verify --print-certs $final | Select-Object -First 6

Write-Host ("完成：" + $final + "  (" + [math]::Round((Get-Item $final).Length / 1KB) + " KB)")