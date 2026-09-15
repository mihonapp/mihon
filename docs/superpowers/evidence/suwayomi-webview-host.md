# T4 独立按需浏览器验证

## 已实现

- 新模块 `desktop-webview-host`，独立 **JBRSDK 21.0.10+1-1163.110-jcef**，JCEF 137.0.17.1107+ge397b56。主程序构建/运行时版本未被更改。
- JCEF API 从该独立 runtime 的 jcef.jmod 提取为仅编译依赖，运行时加载 JBR 自带 jcef 模块；不是打开系统默认浏览器。
- 每次 open 创建独立进程、随机 sessionId、sourceId、32 字节能力 token、独立 profile。父进程 stdin 命令与 localhost broker 验证身份，强制使用创建会话时的 extensionId/sourceId。
- HTTP(S) 资源通过 CEF resource handler 转至父进程 DesktopNetworkHelper，沿用域许可、代理、取消、Cookie。浏览器默认网络通过拒绝代理阻断，未支持的 WebSocket 不旁路直连。
- 传入请求头，浏览器 user-agent 与 `network.userAgentFor(url)` 一致。现有 Cookie 注入；JS/原生 Cookie 导回 DesktopCookieStore 后可供普通图片 HTTP 请求复用。
- JS 通过 CefMessageRouter 返回结果，支持 Promise。启动、加载、查询均有超时；取消查询关闭进程。关闭/崩溃销毁子孙进程、停 broker、清理专属 profile；可再次 open。
- 网页弹出窗口被阻止。当前支持 HTTP(S) 资源，不声称已实现 WebSocket/本地文件上传/所有 Android WebView API。

## API / root 接线

```kotlin
val webViews = DesktopWebViewManager(
    browserRuntime = browserBundle.resolve("runtime"),
    hostDistribution = browserBundle.resolve("app"),
    cacheDirectory = cacheRoot.resolve("webview"),
    network = sharedDesktopNetworkHelper,
    cookies = sharedDesktopCookieStore,
)
val session = webViews.open(sourceId, extensionId, url, headers)
session.awaitLoaded()
val resultJson = session.evaluate("document.title")
// 用户完成登录/验证后，导回并重试原来的图片/章节请求
session.exportCookies()
session.close()
// 应用退出
webViews.close()
```

`session.events` 是 BrowserEvent(type,value) Flow。构造 manager 不启动进程。UI/Runtime 由 root 接线。扩展兼容路径已增加 WebViewIpc 契约、BrokeredHttpClient.webView、WebViewBridge、Android WebView/WebSettings/WebViewClient/ValueCallback/CookieManager、Handler/Looper/Rx1 AndroidSchedulers 和 Coroutines MainDispatcherFactory；WindowsExtensionProcessManager 的 onWebView 校验及转发由 native-isolation 代理协作加入。Runtime 传 `onWebView = webViews::handleExtensionRequest`。

## 可构建发行物

共享 Gradle wrapper 支持：

- `:desktop-webview-host:test`
- `:desktop-webview-host:installDist` → `desktop-webview-host/build/install/desktop-webview-host`
- `:desktop-webview-host:stageBrowserRuntime` → `desktop-webview-host/build/browser-runtime`
- `:desktop-webview-host:browserBundle` → `desktop-webview-host/build/distributions/mihonw-browser-host-jbr21-win-x64.zip`，根下 `app/`、`runtime/`。

runtime stage 保留原有 legal/conf/native，排除 jmods/include/src.zip。独立 runtime 当前 608 文件、712754284 bytes（约680 MiB）。这是可选浏览器组件的增量，不应合入主Java17 runtime。

固定输入 SHA256：

- 原 JBR tar.gz（610233172 bytes）：`37f8cd307f79283392d1eef9eb4782838c2b5688cdc35ec26c029cf0fd82fbcb`
- java.exe：`a12e179a473066b19355a86240f001388663e9c751e03447099989bacfddf430`
- libcef.dll：`3c56f4adcf6f0c9f6e964e300ad3bd455cadb0ab1f9f1e6914dfd310299db828`
- release：`d0be7a20d9fbb03fe027b95eb83d86c73e9402153842a1f9045e6616fd1f9ccd`

## 实测

- 首次窗口已初始化但页面仍为 about:blank：定位为 native browser 异步创建，修复为 onAfterCreated 后 loadURL；不将 ready 事件视为页面完成。
- 真实 host smoke：本地父 broker 提供 HTTPS origin 页面，JCEF 页面 JS 返回 answer=42、title，document.cookie 写入后经原生 visitor 导出；请求有指定 X-Test header；关闭 exit=0。首次成功页面就绪 **1134ms**。
- 第一轮父进程集成红：Windows Path.resolve("lib/*") 不接受星号，修复为目录字符串加 classpath 通配符。
- 第一轮父进程集成绿：host测试、installDist、stageBrowserRuntime、DesktopWebViewManagerTest（2测试，无skip）通过，真实网络 helper Cookie 往返、进程关闭。
- staged runtime 的 user-agent / 超时取消回归已通过（40s）；后续真实 Android WebView API → parent manager → JCEF → CookieManager.setCookie → document.cookie/evaluateJavascript → destroy 全链也通过（47s）。
- WebViewCompatibilityTest 2 项：原 source/package 跨 Handler 和 Dispatchers.Main 保持、load/evaluate/cookie/destroy action契约。DesktopWebViewManagerTest 2项含上述真实场景，均无skip。
- 独立副本 smoke 重跑：1215ms 页面就绪，进程树 RSS 424169472 bytes（约404.5 MiB），退出0。
- 新增 Windows kill-on-close Job Object 绑定（2GiB、最多32子进程，初始化stdin前绑定），另有waitFor监视，不依赖stdout EOF才能清理；强杀JVM后Chromium子孙全部退出回归已通过（共享wrapper，1m10s）。

没有真实验证码站点或扩展阅读样本账户，尚未验证全站兼容；上述证据限真实本地页面/父网络/Cookie/生命周期，不代表“所有 WebView 图源完整阅读”验收。

## 上游依据

- [Suwayomi CEFManager](https://github.com/Suwayomi/Suwayomi-Server/blob/d10e000e1fdcac6f3c84d002f0b459c90c1b00f3/server/src/main/kotlin/suwayomi/tachidesk/server/util/CEFManager.kt)：JBR/JCEF runtime、JCefAppConfig 与原生初始化。
- [Suwayomi KcefWebViewProvider](https://github.com/Suwayomi/Suwayomi-Server/blob/d10e000e1fdcac6f3c84d002f0b459c90c1b00f3/AndroidCompat/src/main/java/xyz/nulldev/androidcompat/webkit/KcefWebViewProvider.kt)：CEF resource handler、额外 headers 与 JS message router。
- [JCEF Maven 文档](https://github.com/jcefmaven/jcefmaven)：Windows JCEF 独立组件的可用性参考；最终实现使用本机固定 JBR 内置 JCEF，不引入 jcefmaven 下载器。

## 真实 APK 范围

T3代理提供官方 MangaFire 1.6.34 APK，SHA256 `6cfabb4ca49dbcad33688c89876b18ae61d62842f373abbc9139568711145b0b`。
其当前 runWebViewBlocking 使用 interceptRequest/jsBridge/loadData 运行自动 shape captcha solver；未执行该分支，不能把本地页面验证记成该APK完整阅读。当前通用兼容桥已验证 loadUrl/onPageFinished/evaluateJavascript/CookieManager/destroy；高级 loadData/jsBridge/resource replacement DSL仍需按具体非验证码样本继续补齐，当前不宣称覆盖这些API。

父UI稳定字段 `session.state: StateFlow<BrowserEvent>` 只发布 starting/ready/loaded/error/closed，events提供结果消息；JCEF JFrame是真实可交互窗口。
