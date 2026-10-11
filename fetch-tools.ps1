# 下载打包所需的三件工具，放到仓库的 tools\ 目录
#   - build-tools 34（腾讯镜像，国内较快）
#   - platform 34（腾讯镜像）
#   - r8.jar（Google Maven 上的官方包，里面的 D8 用来把 Java 编译成 dex）
$ErrorActionPreference = "Stop"
$Root      = Split-Path -Parent $PSCommandPath
$dl        = Join-Path $Root "dl"
$tools     = Join-Path $Root "tools"
$R8Version = "9.5.23"

New-Item -ItemType Directory -Force -Path $dl, $tools | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$ProgressPreference = "SilentlyContinue"

$items = @(
  @{ url = "https://mirrors.cloud.tencent.com/AndroidSDK/build-tools_r34-windows.zip"; out = "bt.zip"; dir = "bt" },
  @{ url = "https://mirrors.cloud.tencent.com/AndroidSDK/platform-34-ext7_r03.zip";  out = "pf.zip"; dir = "pf" }
)
foreach ($i in $items) {
  $target = Join-Path $dl $i.out
  if (-not (Test-Path -LiteralPath $target)) {
    Write-Host ("下载 " + $i.url)
    Invoke-WebRequest -Uri $i.url -OutFile $target -TimeoutSec 600
  }
  $dest = Join-Path $tools $i.dir
  if (-not (Test-Path -LiteralPath $dest)) {
    Write-Host ("解压到 " + $dest)
    [IO.Compression.ZipFile]::ExtractToDirectory($target, $dest)
  }
}

$r8 = Join-Path $tools "r8.jar"
if (-not (Test-Path -LiteralPath $r8)) {
  $url = "https://dl.google.com/dl/android/maven2/com/android/tools/r8/" + $R8Version + "/r8-" + $R8Version + ".jar"
  Write-Host ("下载 r8.jar " + $R8Version + "（约 20MB，国内可能比较慢）")
  Invoke-WebRequest -Uri $url -OutFile $r8 -TimeoutSec 900
  if ((Get-Item -LiteralPath $r8).Length -lt 5MB) {
    Remove-Item -LiteralPath $r8 -Force
    throw "r8.jar 下载不完整，请重跑本脚本"
  }
}

Write-Host ("工具就绪：" + $tools)
Write-Host "如果 r8.jar 一直下不动，也可以直接把 Android Studio 里的 r8.jar 拷到 tools\ 下："
Write-Host "  <Android Studio 安装目录>\plugins\android\lib\r8.jar"