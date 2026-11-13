---
doc_id: ext-private-install-review
title: 扩展私有安装改造 · 审查与修改建议
repo: C:/Downloads/Archive/mihon-main
baseline_snapshot: "C:/Downloads/Archive/mihon-main (2)/mihon-main"
baseline_version: upstream v0.21.3 (versionCode 38)
target_version: 0.21.3Beta3 (versionCode 42, applicationId app.mihon.beta)
requirement: 扩展改为安装到应用私有目录并从该目录加载，不再交给系统安装器
verdict: 需求已实现（安装链路唯一入口，无系统安装器调用）；本文档只列待修项
generated_at: 2026-10-27
verified_by: ":app:compileDebugKotlin → BUILD SUCCESSFUL (1m50s, JDK 21)"
status: B1–B4 已实施 + P2-4/P2-7 方案 B 已落地（P2-6 待测量、P3-12 待实测），见 §8
---

# 扩展私有安装改造 · 审查与修改建议

## 0. 给 AI 的阅读约定

- 每条问题是一个固定结构的块，字段键固定为：`severity / type / location / current / trigger / impact / fix / accept`。
  - 有多方案时用 `fix_A` / `fix_B` 与 `accept_A` / `accept_B`（如 P2-4、P2-7）。
  - 需要先实测的条目额外带 `problem / status / verify / open_questions`（如 P3-12）。
  - **键名不要改**，便于解析与批量执行。
- 代码块的 `path:line` 指向**当前工作区**（非快照）。`-` 为现状，`+` 为建议，未标注的行表示上下文不变。
- 只允许改 §3 中列出位置；§5 是明确的"不要动"清单，避免顺手重构。
- 按 §4 的批次顺序执行，每批结束跑一次编译命令（见 §6）。
- 若某条标注 `blocked_questions`，先问人，不要自行假设。

## 1. 判定结果（已完成部分，勿重复修改）

安装链路的唯一入口已收敛，无系统安装器调用：

```
ExtensionsViewModel.installExtension / updateExtension
  → ExtensionManager.installExtension / updateExtension
  → ExtensionInstaller.downloadAndInstall            (下载 + 用商店 signingKey 验签)
  → ExtensionInstaller.deployExtension
  → ExtensionLoader.installPrivateExtensionFile      (inspect → .part → 只读 → validate → 替换)
  → ExtensionInstallReceiver.notifyAdded/notifyReplaced（触发重新加载）
```

已核实、**不需要再改**的点：

| 检查项 | 结论 | 证据 |
|---|---|---|
| 是否还有 `ACTION_INSTALL_PACKAGE` / `PackageInstaller` / Shizuku 调用 | 无 | 全仓 grep，仅剩应用自更新的 `APK_MIME` 与系统卸载 intent |
| Legacy / PackageInstaller / Shizuku 安装器 | 已删除 | `ExtensionInstallActivity`、`ExtensionInstallService`、`extension/installer/`、`mihon/app/shizuku/`、`DelegateLastClassLoaderCompat` 均不存在 |
| 清单/依赖残留 | 已清理 | Manifest 去掉 activity/service/ShizukuProvider/`REQUEST_DELETE_PACKAGES`/`UPDATE_PACKAGES_WITHOUT_USER_ACTION`；`libs.versions.toml` 与 `app/build.gradle.kts` 去掉 shizuku |
| 安装方式设置项与权限引导 | 已移除 | `BasePreferences.extensionInstaller` 删除；`SettingsAdvancedScreen` 的 installer 下拉、`PermissionStep` 与 `ExtensionsScreen` 的权限横幅删除 |
| 私有 dex 在 Android 14+ 可加载 | 是 | `ExtensionLoader.kt:168` 在 validate 之前 `setReadOnly()`；加载侧 `:268` 兜底 |
| 老用户（系统已装）插件是否继续可用 | 是 | `loadExtensions` 仍枚举 `getInstalledPackages`，`selectExtensionPackage` 取版本更高者 |
| 私有 apk 是否会进云备份 | 否 | Manifest `android:allowBackup="false"` |
| dex 加载是否在主线程 | 否 | `@AppCoroutineScope` 绑定 `Dispatchers.IO`（`core/metro/.../AppCoroutineScope.kt` KDoc） |
| release 下 R8 是否吃掉扩展 ABI | 已处理 | `-dontobfuscate` + `proguard-rules.pro:21-31` keep 扩展依赖的包 |

## 2. 问题总览

| ID | 严重度 | 类型 | 位置（文件:行） | 一句话 | 建议批次 |
|---|---|---|---|---|---|
| P1-1 | 高 | 逻辑 | `ExtensionLoader.kt:151` | 降级/签名基线只比私有副本，忽略系统同包名 | B1 |
| P1-2 | 高 | 可靠性 | `ExtensionLoader.kt:174-188` | 两步 rename 有崩溃丢失窗口，且无恢复逻辑 | B1 |
| P1-3 | 高 | 并发 | `ExtensionInstaller.kt:61,130` + `ExtensionLoader.kt:140` | 同包并发安装共用固定临时名/`.part` | B1 |
| P2-4 | 中 | 一致性 | `ExtensionInstaller.kt:139` + `ExtensionManager.kt:434` | shared+private 同名长期并存；卸载路径互相干扰 | B2 |
| P2-5 | 中 | 可维护性 | `ChildFirstPathClassLoader.kt:49,51-58` | 依赖 `getSystemClassLoader()`；`runCatching` 吞 `LinkageError` | B2 |
| P2-6 | 中 | 性能 | `ExtensionLoader.kt:238` | 私有 dex 每次冷启动重新校验，插件多时变慢 | B3（先测量） |
| P2-7 | 中 | 语义 | `ExtensionLoader.kt:512-521` | "关闭插件"并未跳过实例化，与注释/文案不符 | B3 |
| P2-8 | 中 | 体验 | `InstallStep.kt:9` + `ExtensionInstaller.kt:93,121` | 安装失败原因只进 logcat | B3 |
| P3-9 | 低 | 清理 | `ExtensionsScreen.kt:436`、`ExtensionDetailsScreen.kt:287`、`strings.xml` | 所有插件都显示 "Private"；安装方式相关字符串成死资源 | B4 |
| P3-10 | 低 | 清理 | `ExtensionInstaller.kt:61-125` | 取消/异常时 `cacheDir/extension_*.apk` 残留 | B1（顺手） |
| P3-11 | 低 | 性能 | `ExtensionsViewModel.kt:164-172` | 全部更新时并发 dex 校验，内存/CPU 峰值 | B3 |
| P3-12 | 待验证 | 安全 | `ExtensionLoader.kt:226,70-131` | 私有安装不经过系统安装器的整包校验 | 实测后再定 |

## 3. 问题详述与修复

### P1-1 降级/签名基线只看私有副本

- **severity**: high
- **type**: logic
- **location**: `app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt:151`
- **current**:

```kotlin
val installed = (inspect(context, target) as? InspectResult.Ok)?.inspection
if (installed != null) {
    check(incoming.versionCode >= installed.versionCode) { ... }
    check(incoming.signatures.containsAll(installed.signatures)) { ... }
}
```

- **trigger**: 同包名扩展同时存在"系统装的旧版本 → 后来升级成私有"或"系统里版本更高"的情况。典型场景：用户此前用官方版装过 v1.5（isShared=true），本仓库商店只提供 v1.4。
- **impact**: 安装返回 `InstallStep.Installed`，但 `selectExtensionPackage`（`:592`）在加载时取版本更高的 shared → 实际生效版本没变，用户看到"装完了还是旧版"，且私有 v1.4 白占空间；反之也可能出现"永远装不上某个版本"。
- **fix**: 基线改为"当前实际生效的包"（含 shared），并把"是系统包"的信息带进错误文案。

```kotlin
// ExtensionLoader.kt
// 1) 把 inspect 拆成两段，复用从 PackageInfo 构造 Inspection 的逻辑
fun inspect(context: Context, file: File): InspectResult {
    if (!file.isFile) return InspectResult.Rejected("The extension file doesn't exist")

    val pkgInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, PACKAGE_FLAGS)
        ?.also { it.applicationInfo?.fixBasePaths(file.absolutePath) }
        ?: return InspectResult.Rejected("The file isn't a readable APK")

    return inspect(context, pkgInfo)
}

fun inspect(context: Context, pkgInfo: PackageInfo): InspectResult {
    // ← 原 inspect() 中 getPackageArchiveInfo 之后的全部逻辑，一行不改地搬进来
}
```

```kotlin
// ExtensionLoader.kt 新增：当前实际生效的那份包信息，口径与 selectExtensionPackage 一致
private fun effectiveInstalled(
    context: Context,
    dir: File,
    pkgName: String,
): Pair<PackageInfo?, Boolean> {   // second = 是否来自系统安装
    val privatePkg = File(dir, "$pkgName.$PRIVATE_EXTENSION_EXTENSION")
        .takeIf { it.isFile }
        ?.let { context.packageManager.getPackageArchiveInfo(it.absolutePath, PACKAGE_FLAGS) }
    val sharedPkg = runCatching {
        context.packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
    }.getOrNull()

    return when {
        sharedPkg == null && privatePkg == null -> null to false
        sharedPkg == null -> privatePkg to false
        privatePkg == null -> sharedPkg to true
        PackageInfoCompat.getLongVersionCode(sharedPkg) >=
            PackageInfoCompat.getLongVersionCode(privatePkg) -> sharedPkg to true
        else -> privatePkg to false
    }
}
```

```kotlin
// ExtensionLoader.kt:151 附近
-        val installed = (inspect(context, target) as? InspectResult.Ok)?.inspection
+        // 用户实际运行的是 shared / private 里版本更高的那份，降级与签名都必须以它为准
+        val (installedPkg, installedIsShared) = effectiveInstalled(context, dir, incoming.pkgName)
+        val installed = installedPkg?.let { (inspect(context, it) as? InspectResult.Ok)?.inspection }
         if (installed != null) {
             check(incoming.versionCode >= installed.versionCode) {
-                "The installed version is newer, and downgrading isn't allowed"
+                if (installedIsShared) {
+                    "A newer copy is installed by the system; uninstall it before installing this version"
+                } else {
+                    "The installed version is newer, and downgrading isn't allowed"
+                }
             }
```

> 不要写成 `getExtensionPackageInfoFromPkgName(...).isShared`：`PackageInfo` 没有 `isShared` 字段，shared 性只存在于 `ExtensionInfo` 这个内部包装里。上面的 helper 同时返回"哪一份"和"是否来自系统"。
>
> `dir` 即 `getPrivateExtensionDir(context)`，在该函数内已计算好，可直接传入。

- **accept**:
  1. 造一个 shared v1.5 + 商店 v1.4 的环境，点安装 → 必须失败且提示指向系统版本，`filesDir/exts/` 下不产生文件。
  2. shared v1.5 + 商店 v1.6 → 安装成功，加载版本为 1.6。
  3. 仅 private v1.5 + 商店 v1.5（重装同版本）→ 允许（`>=`）。

### P1-2 两步 rename 的崩溃丢失窗口

- **severity**: high
- **type**: reliability
- **location**: `app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt:174-188`（`previous` 变量定义在 `:149`）
- **current**:

```kotlin
previous.delete()
if (target.exists() && !target.renameTo(previous)) {
    throw IllegalStateException("Unable to stage the extension update")
}
try {
    if (!part.renameTo(target)) {
        throw IllegalStateException("Unable to install the extension")
    }
    target.setReadOnly()
    previous.delete()
} catch (e: Exception) {
    target.delete()
    if (previous.exists()) previous.renameTo(target)
    throw e
}
```

- **trigger**: 第一次 rename 完成、第二次 rename 之前进程被杀（系统回收、用户划掉、崩溃、断电）。
- **impact**: `target` 不存在、只剩 `pkg.ext.previous`，且**没有任何恢复逻辑** → 该插件在列表里静默消失，直到用户重装。原来的 `part + 原子替换` 设计本意是"要么旧要么新"，被这两步退化成三态。
- **fix**: 单次 rename 即可。POSIX `rename(2)` 在同一目录内对已存在的目标是**原子替换**，Java `File.renameTo` 在 Android 上映射到它。

```kotlin
// ExtensionLoader.kt:174-188 全段替换
-        previous.delete()
-        if (target.exists() && !target.renameTo(previous)) { ... }
-        try { ... } catch (e: Exception) { ... }
+        // rename(2) 在同一目录内是原子替换：要么旧文件，要么新文件，不存在中间态，
+        // 因此不需要 .previous 暂存，也不需要回滚分支
+        if (!part.renameTo(target)) {
+            throw IllegalStateException("Unable to install the extension")
+        }
+        target.setReadOnly()
```

同时删除 `:149` 的 `private val previous = ...`。

- **also_required**: 对历史上已经产生的孤儿文件做一次回收，在 `loadExtensions` 枚举私有目录之前调用：

```kotlin
// ExtensionLoader.kt 新增
private fun recoverStagedExtensions(dir: File) {
    dir.listFiles()?.forEach { file ->
        val name = file.name
        when {
            name.endsWith(".$PRIVATE_EXTENSION_EXTENSION.part") -> file.delete()
            name.endsWith(".$PRIVATE_EXTENSION_EXTENSION.previous") -> {
                val target = File(dir, name.removeSuffix(".previous"))
                if (target.exists()) {
                    file.delete()
                } else if (!file.renameTo(target)) {
                    logcat(LogPriority.WARN) { "Unable to recover ${file.name}" }
                }
            }
        }
    }
}

// loadExtensions 内，`val privateExtPkgs = getPrivateExtensionDir(context)` 之前
val privateDir = getPrivateExtensionDir(context)
recoverStagedExtensions(privateDir)
```

- **accept**:
  1. 断点/`kill -9` 打在 rename 前后任意时刻，重启后插件要么是旧版要么是新版，不会消失。
  2. 手工放一个 `xxx.ext.previous`（无对应 `.ext`）→ 启动后恢复成 `.ext` 并正常加载。
  3. 手工放一个 `xxx.ext.part` → 启动后被清掉。

### P1-3 同包并发安装共用固定临时名

- **severity**: high
- **type**: concurrency
- **location**: `app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionInstaller.kt:61,130`；`ExtensionLoader.kt:140-198`
- **current**:

```kotlin
// ExtensionInstaller.kt:61
val tmpFile = File(context.cacheDir, "extension_${extension.pkgName}.apk")

// ExtensionInstaller.kt:130
fun cancelInstall(pkgName: String) {
    activeJobs.remove(pkgName)?.cancel()   // 只 cancel，不 join
}
```
`ExtensionLoader` 里 `.part` 名也是固定的：`File(dir, "${target.name}.part")`（`:148`）。

- **trigger**: 用户从两个商店对同一 `pkgName` 连点安装；或"全部更新"与手动更新撞车；或取消后立刻重装。
- **impact**: `cancel()` 是异步的，旧协程可能仍停在 `runInterruptible` 的 `copyTo` 里；随后新协程 `part.delete()` + 新建输出流，两条流写同一 `.part`/同一 cache 文件 → 产出损坏 apk（多数情况被 `inspect` 拦下报 Error，但存在校验通过而内容错位的窗口），最坏情况是替换掉一个可用版本。
- **fix**: 唯一临时名 + 按包名互斥（互斥放在 `installPrivateExtensionFile` 内部，覆盖 copy → validate → rename）。

```kotlin
// ExtensionInstaller.kt:61
-            val tmpFile = File(context.cacheDir, "extension_${extension.pkgName}.apk")
+            // 每次安装用独立文件名：cancel() 是异步的，旧任务可能还在写
+            val tmpFile = File(
+                context.cacheDir,
+                "extension_${extension.pkgName}_${System.nanoTime()}.apk",
+            )
```

```kotlin
// ExtensionLoader.kt
private val packageLocks = ConcurrentHashMap<String, Any>()
private fun packageLock(pkgName: String): Any = packageLocks.getOrPut(pkgName) { Any() }

fun installPrivateExtensionFile(context: Context, file: File) {
    val incoming = when (val result = inspect(context, file)) {
        is InspectResult.Ok -> result.inspection
        is InspectResult.Rejected -> throw IllegalStateException(result.reason)
    }

+    // 同一包名的安装串行化：临时文件、.part、目标文件都是按包名共享的
+    synchronized(packageLock(incoming.pkgName)) {
+        deployPrivateExtensionFile(context, file, incoming)
+    }
-    ...原函数体...
}
+
+private fun deployPrivateExtensionFile(
+    context: Context,
+    file: File,
+    incoming: ExtensionInspection,
+) {
+    // 原函数体中 incoming 之后的部分，直到 notifyAdded/notifyReplaced 为止
+}
```
> `notifyAdded/notifyReplaced` 留在锁内，保证两个并发安装的广播顺序与落盘顺序一致。`synchronized` 阻塞的是 IO 线程，可接受。
> 需要新增 `import java.util.concurrent.ConcurrentHashMap`（`ExtensionLoader.kt` 当前没有）。若不想改函数结构，也可把 `packageLock` 定义在 `ExtensionInstaller` 里、只把加锁放在 `deployExtension` 外层——但那样仍无法防住"两个进程内入口"（例如商店删除联动卸载），所以放在 `ExtensionLoader` 更彻底。

- **accept**:
  1. 两个线程/两个商店同时装同一包名 10 次，全部成功或明确失败，`filesDir/exts/` 下始终只有一个 `.ext`，无 `.part` 残留。
  2. 取消后立刻重装能成功。

### P2-4 shared + private 同名的长期并存

- **severity**: medium
- **type**: consistency
- **location**: `app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionInstaller.kt:139-149`；`app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt:434-438`
- **current**:

```kotlin
// ExtensionInstaller.uninstallApk
if (context.isPackageInstalled(pkgName)) {
    startActivity(ACTION_UNINSTALL_PACKAGE)   // 交给系统卸载器
} else {
    ExtensionLoader.uninstallPrivateExtension(context, pkgName)
    ExtensionInstallReceiver.notifyRemoved(context, pkgName)
}
```
```kotlin
// ExtensionManager.onPackageUninstalled —— 对任意被卸载的包名都会执行
override suspend fun onPackageUninstalled(pkgName: String) = loadMutex.withLock {
    ExtensionLoader.uninstallPrivateExtension(context, pkgName)
    unregisterExtension(pkgName)
    updatePendingUpdatesCount()
}
```

- **trigger**: 从官方版升级上来的用户，系统里留有旧扩展包；之后任何一次更新都会产生同名私有副本，两者长期并存。
- **impact**:
  1. 卸载走系统卸载器（用户可取消）；取消后私有副本仍在、shared 仍在，"卸载没生效"。
  2. 系统里那份旧包永不清理，长期双份占用。
  3. `onPackageUninstalled` 对**任意**被卸载的应用都删同名私有文件，属于越权副作用（例如用户从系统设置里卸掉同名系统扩展，私有副本被连带删掉；行为上多数可接受，但不受控）。
- **fix_A**（最小改动，推荐先做）:

```kotlin
// ExtensionInstaller.kt:139
fun uninstallApk(pkgName: String) {
    if (context.isPackageInstalled(pkgName)) {
+        // 系统里还留着旧版本：私有副本先删掉，剩下这份交给系统卸载器，
+        // 卸载完成由 ACTION_PACKAGE_REMOVED 触发刷新
+        ExtensionLoader.uninstallPrivateExtension(context, pkgName)
        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, "package:$pkgName".toUri())
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } else {
        ExtensionLoader.uninstallPrivateExtension(context, pkgName)
        ExtensionInstallReceiver.notifyRemoved(context, pkgName)
    }
}
```

```kotlin
// ExtensionManager.kt:434
override suspend fun onPackageUninstalled(pkgName: String) = loadMutex.withLock {
-    ExtensionLoader.uninstallPrivateExtension(context, pkgName)
+    // 只清理我们确实部署过的包，别对任意被卸载的应用动手
+    if (pkgName in loadedExtensionMapFlow.value || pkgName in notLoadedExtensionMapFlow.value) {
+        ExtensionLoader.uninstallPrivateExtension(context, pkgName)
+    }
    unregisterExtension(pkgName)
    updatePendingUpdatesCount()
}
```

- **fix_B**（可选，后续做）: 一次性迁移/提示。检测到 `isShared == true` 的扩展时，在详情页给一条"这是系统安装的旧版本，卸载后可改为应用内安装"。不要做静默自动卸载（会弹系统卸载框，打断用户）。
- **accept**:
  1. shared+private 并存时点卸载 → 系统卸载框取消 → 私有副本已删，`getPrivateExtensionDir` 无残留；再次加载得到 shared 版本。
  2. 并存时确认卸载 → 两份都没了，列表刷新为未安装。
  3. 卸载一个与扩展无关的应用 → `filesDir/exts/` 内容不变。

### P2-5 类加载器的两处隐含依赖

- **severity**: medium
- **type**: maintainability / correctness
- **location**: `app/src/main/java/mihon/data/dalvik/ChildFirstPathClassLoader.kt:49,51-58`（KDoc 在 `:12-28`、`:40-43`）
- **current**:

```kotlin
private val systemClassLoader: ClassLoader? = getSystemClassLoader()

override fun loadClass(name: String?, resolve: Boolean): Class<*> {
    val loaded = findLoadedClass(name)
        ?: systemClassLoader?.runCatching { loadClass(name) }?.getOrNull()
        ?: runCatching { findClass(name) }.getOrNull()
        ?: super.loadClass(name, resolve)
    if (resolve) resolveClass(loaded)
    return loaded
}
```

- **problem**:
  1. Android 上 `ClassLoader.getSystemClassLoader()` 是 `PathClassLoader(java.class.path, BootClassLoader)`，而 `java.class.path` 在应用进程里通常是 `"."`，**效果上等价于 boot loader**——但这是实现细节。老实现用的是 `Any::class.java.classLoader`（明确的 BootClassLoader），语义更硬。
  2. `runCatching` 吞掉 `Throwable`，包括 `LinkageError` / `NoClassDefFoundError`。扩展缺依赖时会从"链接错误（指向真正缺失的类）"退化成"ClassNotFoundException（指向声明类）"，排障成本显著上升。
  3. KDoc 写的顺序是「system → 自己的 dex → 宿主」，与实际语义（"平台类优先，其次自己，最后宿主"）表述不一致。
- **fix**:

```kotlin
-    private val systemClassLoader: ClassLoader? = getSystemClassLoader()
+    // 平台类（java.*、android.*）由 BootClassLoader 提供；不要去拿 getSystemClassLoader()，
+    // 它指向 java.class.path，语义随实现变化
+    private val bootClassLoader: ClassLoader? = Any::class.java.classLoader

     override fun loadClass(name: String?, resolve: Boolean): Class<*> {
-        val loaded = findLoadedClass(name)
-            ?: systemClassLoader?.runCatching { loadClass(name) }?.getOrNull()
-            ?: runCatching { findClass(name) }.getOrNull()
-            ?: super.loadClass(name, resolve)
-        if (resolve) resolveClass(loaded)
-        return loaded
+        findLoadedClass(name)?.let {
+            if (resolve) resolveClass(it)
+            return it
+        }
+
+        if (bootClassLoader != null) {
+            try {
+                return bootClassLoader.loadClass(name)
+            } catch (_: ClassNotFoundException) {
+                // 平台类里没有，继续找插件自己的 dex
+            }
+        }
+
+        try {
+            val fromDex = findClass(name)
+            if (resolve) resolveClass(fromDex)
+            return fromDex
+        } catch (_: ClassNotFoundException) {
+            // 插件没自带这个类，交给宿主（parent-first 的最后一步）
+        }
+
+        return super.loadClass(name, resolve)
     }
```
`getResource` / `getResources` / `getResourceAsStream` 中的 `systemClassLoader` 同步改名为 `bootClassLoader`。

- **also**: 把 KDoc `:12-28` 与 `:40-43` 的"system"改为"平台类（BootClassLoader）"，与新实现一致。
- **accept**: 用一个依赖缺失的假扩展加载 → 异常信息里能看到真实缺失的类名（`NoClassDefFoundError: okhttp3/...` 而不是 `ClassNotFoundException: <声明类>`）。

### P2-6 私有 dex 的冷启动成本（先测量）

- **severity**: medium
- **type**: performance
- **location**: `ExtensionLoader.loadExtensions`（`:238`）
- **problem**: 私有 apk 在 `filesDir/exts` 且被设成只读，ART 无法在旁边落 oat/vdex，每次进程启动都要重新校验/解释执行。扩展数量多时 `loadExtensions` 耗时会明显高于从 `/data/app` 加载。
- **fix**: 先量化，再决定是否优化。
  1. 在 `loadExtensions` 前后打点（`SystemClock.elapsedRealtime()`），装 30+ 扩展对比冷启动耗时。
  2. 若确实明显：可选方案是把 apk 放到 `codeCacheDir/exts`（ART 可写同目录，能落 oat），代价是该目录可能被系统清理，需要缺失时自愈（重新下载/提示）；**不要**为了性能把 apk 放回可写状态（Android 14+ 会直接拒绝加载）。
- **accept**: 记录改造前后 `loadExtensions` 冷启动 P50/P90，作为是否继续优化的依据。

### P2-7 "关闭插件"并未跳过实例化

- **severity**: medium
- **type**: semantics
- **location**: `ExtensionLoader.loadExtension`（`:512-521`）
- **current**:

```kotlin
val isEnabled = pkgName !in disabledExtensions
if (alreadyLoaded != null && alreadyLoaded.versionCode == versionCode && ...) {
    return alreadyLoaded.copy(store = store, isEnabled = isEnabled)
}
// ↓ 继续 new classloader、Class.forName、newInstance() 全部 source
```
- **impact**: 注释写的是 "they're simply not loaded"，实际是"照样加载并实例化，只是不注册到 `AndroidSourceManager`"。用户如果为了省资源关掉插件，期望落空；文案与行为不一致。
- **fix_A**（改措辞，0 风险）: 把 `SourcePreferences.disabledExtensions` 的 KDoc（`SourcePreferences.kt:43`）和 `ExtensionManager.setExtensionEnabled` 的 KDoc（`:323-326`）、CHANGELOG 条目统一改成"仍会加载，但不再向应用注册它提供的图源"。
- **fix_B**（真跳过，需改模型）:
  1. `Extension.Installed` 接口加 `val isEnabled: Boolean`，`NotLoaded` 增加该字段（默认 `true`）；
  2. `NotLoaded.Reason` 增加 `data object Disabled`；
  3. `loadExtension` 在 `:512` 之后立即 `return notLoaded(Reason.Disabled)`（放在 `alreadyLoaded` 快路径之前，保证关掉后立刻释放）；
  4. UI：`ExtensionsScreen.kt:427-431` 的 reason 分支加 Disabled 的标签；`ExtensionDetailsScreen.kt:362` 的开关改读 `extension.isEnabled`（现在只读 `Extension.Loaded`）。
  > 选 B 时注意：`ExtensionsViewModel` 的分组会把 Disabled 归到"未加载"组，需确认这是想要的产品语义。
- **accept_A**: 文案与行为一致，无代码行为变化。
- **accept_B**: 关闭某插件后 `adb shell dumpsys meminfo` 中 dex mmap 减少、重启后该插件不出现在 `loadedExtensionMapFlow`。

### P2-8 安装失败原因只进 logcat

- **severity**: medium
- **type**: UX
- **location**: `app/src/main/java/eu/kanade/tachiyomi/extension/model/InstallStep.kt:9`；`ExtensionInstaller.kt:93,121`
- **current**: `data object Error`，失败原因（验签不符 / 拒绝降级 / validate 抛错）只写 `logcat`。
- **impact**: 用户只看到重试按钮，无法判断是"商店 key 不对"还是"版本比已装的低"。
- **fix**:

```kotlin
// InstallStep.kt
-    data object Error : InstallStep
+    data class Error(val message: String? = null) : InstallStep

     fun isCompleted(): Boolean {
-        return this == Installed || this == Error || this == Idle
+        return this == Installed || this is Error || this == Idle
     }
```
```kotlin
// ExtensionInstaller.kt:93
-                step.value = InstallStep.Error
+                step.value = InstallStep.Error(e.message ?: e::class.java.simpleName)
// ExtensionInstaller.kt:121
-            updateInstallStep(downloadId, InstallStep.Error)
+            updateInstallStep(downloadId, InstallStep.Error(e.message ?: e::class.java.simpleName))
```

> `ExtensionLoader.kt:682` 的 `Throwable.rootMessage` 是**文件级 private**，在 `ExtensionInstaller` 里不可见。想要"最深 cause 的信息"，把它挪到共享工具（如 `eu.kanade.tachiyomi.util.lang`）并改成 internal；只想先跑通就用上面的 `message`。

受影响的引用点（全仓仅 3 处，已确认）：
- `ExtensionsScreen.kt:497` → `installStep is InstallStep.Error -> ...`
- `ExtensionInstaller.kt:93 / :121` → 如上

可选：在 `ExtensionsTab.kt` 里对 `is InstallStep.Error` 弹一次 snackbar，把 `message` 显示出来。

- **accept**: 用错误商店 key 的扩展安装 → 界面提示可读原因，不再是裸 error。

### P3-9 "Private" 徽标与死字符串

- **severity**: low
- **type**: cleanup
- **location**: `ExtensionsScreen.kt:434-436`、`ExtensionDetailsScreen.kt:285-290`、`i18n/src/commonMain/moko-resources/{base,zh-rCN,zh-rTW}/strings.xml`
- **current**: 改造后所有扩展都是私有安装，`!isShared` 恒为真 → 每个插件都挂一个 "Private" 徽标，信息量为零。
- **fix**:
  1. 删除两处 `ext_installer_private` 徽标（列表 `ExtensionsScreen.kt:434-436`、详情 `ExtensionDetailsScreen.kt:285-290`）。
  2. 删除死字符串（三语都确认存在，`ext_installer_packageinstaller` / `ext_installer_shizuku` 只在 base 有）：
     `ext_installer_pref`、`ext_installer_legacy`、`ext_installer_shizuku_stopped`、`ext_installer_shizuku_unavailable_dialog`、`ext_permission_install_apps_warning`、`onboarding_permission_install_apps`、`onboarding_permission_install_apps_description`、`ext_installer_packageinstaller`、`ext_installer_shizuku`、`ext_installer_private`（删完徽标后即无引用）。
     校验方式：删前逐个 `grep -rn "<key>" --include=*.kt app/src presentation-core/src`，必须 0 命中。
  3. `CHANGELOG.md` 的「不再需要「安装未知应用」权限」需要限定为"安装插件时"：Manifest 仍保留 `REQUEST_INSTALL_PACKAGES`（`:21`）用于应用自更新。
- **accept**: `assembleDebug` 通过；插件列表无多余徽标；无悬空字符串引用。

### P3-10 临时文件清理

- **severity**: low
- **type**: cleanup
- **location**: `ExtensionInstaller.kt:61-125`
- **current**: `tmpFile.delete()` 只在 `deployExtension` 里调用；下载被取消（协程 cancel）或抛 `Error` 时残留 `cacheDir/extension_*.apk`。
- **fix**: 把删除收口到 job 的 `finally`，并去掉 `deployExtension` 里的删除：

```kotlin
val job = scope.launch {
    val tmpFile = File(context.cacheDir, "extension_${extension.pkgName}_${System.nanoTime()}.apk")
    try {
        ...
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        ...
    } finally {
        tmpFile.delete()
    }
}
```
- **accept**: 下载中途取消后 `cacheDir` 无 `extension_*.apk`。

### P3-11 全部更新时的并发 dex 校验

- **severity**: low
- **type**: performance
- **location**: `app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsViewModel.kt:164-172`
- **current**: `forEach(::updateExtension)`，每个插件各起一个协程 → N 个 dex 同时 `Class.forName` + `newInstance`。
- **fix**: 加并发上限（例如 3）：

```kotlin
val semaphore = Semaphore(3)
state.value.items.values.flatten()
    .map { it.extension }
    .filterIsInstance<Extension.Installed>()
    .filter { it.hasUpdate }
    .forEach { extension ->
        launch {
            semaphore.withPermit {
                extensionManager.updateExtension(extension).collectToInstallUpdate(extension)
            }
        }
    }
```
（`updateExtension` 仍需在 IO 上收集；`Semaphore` 来自 `kotlinx.coroutines.sync`，`launch` 需在 `viewModelScope` 内。）
- **accept**: 20 个更新同时进行时，内存峰值明显低于改造前，全部更新最终完成。

### P3-12 私有安装的签名完整性（待实测）

- **severity**: unknown (verify first)
- **type**: security
- **status**: blocked_questions
- **location**: `ExtensionLoader.getArchivePackageInfo`（`:226`）、`inspect`（`:70-131`）、`validate`（`:204`）
- **problem**: 系统安装器会做 APK v2/v3 整包摘要校验；私有安装不走它，只依赖 `PackageManager.getPackageArchiveInfo` 读到的签名信息 + 商店 `signingKey` 白名单。**若平台在 `getPackageArchiveInfo` 内部不做整包摘要校验**，那么"签名块保留、classes.dex 被替换"的 apk 有可能通过 `inspect` 与 `validate`。
- **verify**（先做这个，不要先改代码）:
  1. 取一个合法扩展 apk，用 `apksigner verify -v` 确认 v2/v3 通过；
  2. 直接改 `classes.dex` 的任意字节（不重签），在目标 Android 版本上调用 `getPackageArchiveInfo`，观察返回是否为 `null` / 签名是否仍能读出；
  3. 若返回 `null` → 平台已做校验，本条降级为"记录在案"；
  4. 若仍返回签名 → 需要在 `inspect` 后自行校验 v2 digest（或改为 `PackageInstaller` 的 `setStagedSession` 语义），成本较高，需排期。
- **open_questions**: 目标分发渠道（GitHub / F-Droid / Play）对这条风险的接受度；是否要求"签名块被篡改也必须拒绝"。

## 4. 建议实施批次

| 批次 | 内容 | 涉及文件 | 完成后必须 | 
|---|---|---|---|
| B1 | P1-1、P1-2、P1-3、P3-10 | `ExtensionLoader.kt`、`ExtensionInstaller.kt` | 编译 + 三组安装/升级/取消手测 |
| B2 | P2-4、P2-5 | `ExtensionInstaller.kt`、`ExtensionManager.kt`、`ChildFirstPathClassLoader.kt` | 编译 + shared/private 同名场景手测 |
| B3 | P2-6（测量）、P2-7、P2-8、P3-11 | `ExtensionLoader.kt`、`InstallStep.kt`、`ExtensionsScreen.kt`、`ExtensionsViewModel.kt`、`SourcePreferences.kt` | 编译 + 30 插件冷启动耗时记录 |
| B4 | P3-9 | `ExtensionsScreen.kt`、`ExtensionDetailsScreen.kt`、三语 `strings.xml`、`CHANGELOG.md` | 编译 + grep 死字符串 0 命中 |
| 待定 | P3-12 | — | 篡改样本实测结论 |

批次内条目相互独立，可按 ID 单独执行；**B1 的三条互相依赖（都改同一段代码），建议一次性改完再编译**。

## 5. 不要动的地方

以下是刻意设计，改动会破坏需求或引入回归：

1. `ExtensionLoader.loadExtensions` 里枚举 `getInstalledPackages` 的 shared 分支（`:251-260`）——这是老用户升级后不掉插件的唯一保障，不要因为"现在只装私有"就删掉。
2. `-dontobfuscate` 与 `proguard-rules.pro:21-31` 的 ABI keep——删掉会让 release 版扩展加载报 `NoClassDefFoundError`，而 debug 版看不出来。
3. `part.setReadOnly()`（`:168`）与加载侧 `it.setReadOnly()`（`:268`）——Android 14+ 加载可写 dex 会直接失败。
4. `Manifest:21` 的 `REQUEST_INSTALL_PACKAGES`——应用自更新还要用，不要跟着"插件不再需要权限"一起删。
5. `InstallStep.Installed` 之后由 `ExtensionInstallReceiver.notifyAdded/notifyReplaced` 触发重载这条链路——不要改成安装后同步 reload，会与 `loadMutex` 抢锁。
6. `selectExtensionPackage` 的"shared 优先、版本高者胜"（`:592-606`）——P1-1 是让**安装前**用同一口径做校验，不是改这里。

## 6. 验证环境

```bash
# 必须用 JDK 21：metro 1.4.5 要求 JVM 21，机器默认 JAVA_HOME 是 jdk-17，会直接配置失败
cd "C:/Downloads/Archive/mihon-main"
JAVA_HOME="C:/Android/jdk/jdk-21" ./gradlew :app:compileDebugKotlin --offline

# 完整包（release 会跑 R8，才能验证 keep 规则）
JAVA_HOME="C:/Android/jdk/jdk-21" ./gradlew :app:assembleRelease --offline
```

- 已知坑：默认 `JAVA_HOME=C:\Android\jdk\jdk-17`，直接跑 `./gradlew` 会报 `Dependency requires at least JVM runtime version 21`。
- `local.properties` 的 `sdk.dir=C:\Android\Sdk`；`gradle/mihon.versions.toml` 为 minSdk 26 / targetSdk 36 / compileSdk 37.2（`CINNAMON_BUN` 分支在真机上不会走到，实际全部走 `ChildFirstPathClassLoader`）。

## 7. 待确认

1. P3-12 的签名完整性要求：是否必须在应用层拒绝"签名块有效但内容被改"的 apk？
2. P2-7 选 A（改文案）还是 B（真跳过实例化）？B 会改变"关闭的插件出现在未加载分组"的产品语义。
3. shared（系统安装）旧扩展是否要提供一次性迁移入口，还是只在详情页提示？

## 8. 实施记录（2026-10-27）

已完成 B1–B4 的代码改动，`:app:compileDebugKotlin` 通过。§3 保留原始分析与理由，实际落地以本表为准。

| ID | 状态 | 实际改动 |
|---|---|---|
| P1-1 | 已改 | `ExtensionLoader.inspect()` 拆成 `File` / `PackageInfo` 两个重载；新增 `effectiveInstalled()` 返回 `(PackageInfo, isShared)`；降级文案区分"系统安装的同名包更高" |
| P1-2 | 已改 | 删掉 `.previous` 两步 rename，改单次 `part.renameTo(target)`；新增 `recoverStagedExtensions()`，由 `loadExtensions` 在枚举前调用（回收 `.part`、恢复孤儿 `.previous`） |
| P1-3 | 已改 | 新增 `packageLocks` + `packageLock()`（`putIfAbsent`）；`installPrivateExtensionFile` 拆出 `deployPrivateExtensionFile()` 并用 `synchronized` 串行化；`ExtensionInstaller` 临时名加 `System.nanoTime()` |
| P2-4 | 已改（含 fix_B） | `uninstallApk` 在系统卸载分支先删私有副本；`onPackageUninstalled` 只对已知插件删文件；详情页对 `isShared` 的插件显示 `ext_shared_install_warning` 横幅，提示卸载后改为应用内安装，不做静默自动卸载 |
| P2-5 | 已改 | `ChildFirstPathClassLoader` 改用 `Any::class.java.classLoader`，只捕获 `ClassNotFoundException`，`getResource(s)` 同步改名 |
| P2-6 | 未做 | 需真机测 `loadExtensions` 冷启动耗时后再决定 |
| P2-7 | 已改（B） | `Extension.Installed` 新增 `isEnabled`；`NotLoaded` 增加同名字段与 `Reason.Disabled`；`loadExtension` 在创建 classloader 之前直接返回 Disabled，关闭即卸载；列表行开关对 `Installed` 通用，禁用项进入未加载分组；详情页切换关闭后自动返回列表；`CrashLogUtil` 忽略 Disabled；KDoc/文案同步为关闭即卸载 |
| P2-8 | 已改 | `InstallStep.Error(message: String? = null)`；下载失败与私有部署失败都带上原因；`ExtensionsScreen` 的判断改 `is InstallStep.Error` |
| P3-9 | 已改 | 删除 `ExtensionsScreen`/`ExtensionDetailsScreen` 两处 "Private" 徽标；删除三语共 10 个安装方式相关字符串；CHANGELOG 措辞限定为"安装插件时" |
| P3-10 | 已改 | `downloadAndInstall` 增加 `finally { tmpFile.delete() }`，`deployExtension` 不再负责删除 |
| P3-11 | 已改 | `updateAllExtensions` 用 `coroutineScope` + `Semaphore(3)` 限流，直接 collect 管理器返回的 flow |
| P3-12 | 待验证 | 需用篡改过 dex 的样本在目标机型上确认 `getPackageArchiveInfo` 是否拒绝 |

编译验证：

```bash
JAVA_HOME="C:/Android/jdk/jdk-21" ./gradlew :app:compileDebugKotlin --offline   # BUILD SUCCESSFUL
JAVA_HOME="C:/Android/jdk/jdk-21" ./gradlew :app:assembleRelease --offline      # BUILD SUCCESSFUL in 5m（5 个 APK）
```

### 方案 B 带来的可见行为变化（2026-10-27 追加）

- 关闭插件后，它从「已安装」分组移到「未加载」分组，行内显示灰色「已关闭」标签（不是错误红），开关仍在行内，可直接重新打开。
- 在插件详情页关闭开关会立即返回列表：插件已被卸载出内存，详情页没有内容可显示（`State.Uninstalled` 原本也会自动 pop）。
- 关闭的插件仍参与更新检查与「全部更新」，更新完成后保持关闭状态。
- 关闭的插件不再出现在崩溃日志的 `Problematic extensions` 段（`CrashLogUtil` 过滤 `Reason.Disabled`）。
- 「由系统安装」的插件在详情页顶部显示橙色警告横幅（`ext_shared_install_warning`），文案指向"卸载后改为应用内安装"，不会自动执行卸载。
