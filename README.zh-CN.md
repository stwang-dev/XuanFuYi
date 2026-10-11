# 悬浮译 · XuanFuYi

[English](README.md) | 中文

一个给鸿蒙 / 安卓平板用的悬浮翻译小工具，为在 Zotero 里读外文文献而做。

用笔选中文字，点「拷贝」，再点屏幕上的悬浮球，译文就会浮在 Zotero 上层，可以直接粘回笔记里。

![截图](docs/screenshot.jpg)

- 不需要账号，不收集任何数据。应用不会改动 Zotero 的内容，只读你刚复制的那段文字。
- 两种翻译引擎：免费公开接口（不用配置），或者任何 OpenAI 兼容服务（质量更好，用自己的密钥）。
- 密钥只存在本机，安装包里不含任何密钥。

## 安装

下载 `dist/悬浮译.apk`，拷到平板安装即可。支持安卓 8.0 及以上。

鸿蒙还需要额外放行几项权限，悬浮球才正常。完整步骤见 [安装与使用说明.md](安装与使用说明.md)，要点：

- 悬浮窗（显示在其他应用上层），悬浮球必需
- 后台弹出界面（华为 / 鸿蒙特有）
- 应用启动管理改成手动：允许自启动、关联启动、后台活动
- 电池优化设为「不受限制」
- 允许通知

## 怎么用

1. 用笔在 Zotero 里选中一个词、一句话或一整段。
2. 点弹出菜单里的「拷贝」。
3. 点屏幕上的蓝色「译」悬浮球。
4. 弹出气泡卡片：原文加译文，下面有「拷贝译文 / 重译 / 关闭」。

小技巧：

- 拖动悬浮球换位置；长按悬浮球打开设置页。
- 悬浮球大小、气泡宽度、译文字号都能在设置里调。
- 也可以通过系统的「共享」把文字发过来。
## 翻译引擎

免费公开接口（默认）：先用 MyMemory，失败时用 Google 的公开接口兜底。不用注册，但句子长了容易失败，质量一般。

OpenAI 兼容接口：任何支持 OpenAI 接口格式的服务都能用。

| 填写项 | 例子 |
| --- | --- |
| Base URL | https://api.deepseek.com/v1 或 https://tokendance.space/gateway/v1 |
| 密钥 | 你自己在那个平台申请的密钥 |
| 模型 | deepseek-chat、glm-4.5-air、qwen3.5-flash |

Base URL 填到版本号那一层就行，后面的 /chat/completions 应用会自己接上。

译文是边生成边显示的，通常一秒左右就出第一个字，气泡底部还会显示这一句花了多少秒。翻译选小尺寸的 flash 类模型最快；名字里带 max、pro 的，以及会先思考一轮的模型，慢好几倍但质量提升有限。

## 自己打包

需要 Windows 加 PowerShell 5.1 以上，以及 JDK 8 以上（脚本从 JAVA_HOME 里找 java / javac）。不需要 Android Studio，不需要 Gradle，也不用装 SDK。

```powershell
# 1) 下载安卓打包工具（约 120MB）
powershell -ExecutionPolicy Bypass -File fetch-tools.ps1

# 2) 打包，产物在 dist\悬浮译.apk
powershell -ExecutionPolicy Bypass -File build.ps1
```

没设 JAVA_HOME 的话：

```powershell
$env:JAVA_HOME = "C:\develop\jdk-21"
```

已经有 Android SDK 的话，可以直接指过去，不用下载：

```powershell
build.ps1 -BuildTools "D:\sdk\build-tools\34.0.0" -AndroidJar "D:\sdk\platforms\android-34\android.jar" -R8Jar "D:\sdk\r8.jar"
```

签名：默认用 keystore/xuanfuyi.keystore（别名 lingo，密码 123456），没有会自动生成，而且被 .gitignore 排除，只存在你本机，务必留好。安卓只接受同一个签名的覆盖安装，密钥丢了或者换了，老用户必须先卸载才能装新版。正式发布请换成自己的密钥，用 -KsPath、-KsAlias、-KsPass 指定。

## 目录结构

- app/AndroidManifest.xml，权限与组件声明
- app/res/，应用名与图标
- app/src/com/lingo/bubble/Prefs.java，设置项存取
- app/src/com/lingo/bubble/Translator.java，翻译引擎（免费接口加 OpenAI 兼容，SSE 流式）
- app/src/com/lingo/bubble/BubbleService.java，前台服务：悬浮球与译文气泡
- app/src/com/lingo/bubble/ClipActivity.java，透明页面，后台读不到剪贴板时走这条路
- app/src/com/lingo/bubble/ShareActivity.java，接收「共享」过来的文字
- app/src/com/lingo/bubble/MainActivity.java，设置页
- app/src/com/lingo/bubble/BootReceiver.java，可选的开机自启
- dist/，预编译 APK
- 安装与使用说明.md，详细的中文安装与使用教程

## 隐私

你选中的文字只会发给你选择的引擎：公开翻译接口，或者（用 OpenAI 兼容接口时）Base URL 指向的那个服务商或中转。没有统计，没有埋点，没有账号。

## 已知限制

- 翻译方向自动判断：含中文、日文、韩文的译成英文，其余译成中文。
- 单次请求上限 4000 字符。
- 免费接口偶尔会限流，或者对长文本失败。
- 鸿蒙对后台应用管得很严。悬浮球总是消失的话，回去检查应用启动管理那几项。

## 开源协议

MIT，见 [LICENSE](LICENSE)。