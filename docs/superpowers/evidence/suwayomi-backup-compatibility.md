# T8 备份兼容与恢复验证

工作目录：`D:\my project\mihon-w\.worktrees\suwayomi`。2026-09-15。

## 来源与测试边界

对照 Suwayomi-Server `d10e000e1fdcac6f3c84d002f0b459c90c1b00f3` 的 `server/src/main/kotlin/suwayomi/tachidesk/manga/impl/backup/proto/models/Backup*.kt` 和 `handlers/BackupCategoryHandler.kt`，以及仓库 Android `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/`。

`desktop-library-data/src/test/kotlin/mihon/desktop/library/backup/suwayomifixture/` 的六个实体模型来自上述 Suwayomi 官方快照；仅迁移 package、删除环境 import，把空 JSON byte 常量内联。顶层 `Backup` 保留公共字段及 9000 meta，省略文件名 helper/serverSettings；UpdateStrategy 是同顺序 enum。测试使用这些独立模型的 Kotlin serialization protobuf serializer 生成 GZIP 样本，再由产品导入、SQLite 保存、产品导出，最后由独立模型解码。

这是**按官方 serializer 生成的合成兼容样本**，不是真实用户备份，也不是已运行 Suwayomi 服务导出的备份。当前没有用户脱敏 `.tachibk`。实际 Android/Suwayomi 应用打开本次导出仍未验证。Suwayomi 私有 meta、serverSettings、SyncYomi 分类 uid/version 不属于当前可迁移桌面数据，不承诺无损保留。Android 公共偏好仅迁移 SupportedPreferencePolicy 白名单，其他设置在导入报告明确跳过。

## 本次修复

- Suwayomi 不写分类字段 3 id，多个分类的缺省 id=0 不再错误触发“重复分类 id”；非零重复 id 仍拒绝。缺省 id 不参与 Android 分类偏好重映射，避免错误映射到最后一个分类。
- 原子导出使用同目录独占随机临时文件，成功时 ATOMIC_MOVE，失败也清理本次临时文件；不删除已有目标。文件系统不支持原子替换时显式失败，不降级为有截断窗口的覆盖。
- SQLite 实现的全部导出快照在一个事务内读取，防止漫画/章节/历史来自不同时间点。非事务测试 repository 仍可导出。
- 桌面合并可能形成相同 category sortOrder。导出发现碰撞时按 sortOrder、id 生成唯一 wire order，并同步漫画分类引用；不丢关联，原本唯一的顺序值保持。
- 自动/手动备份使用 Mutex 串行，文件名增加 UUID，同秒多次操作各自保留恢复点。取消异常向上传播；成功前不更新上次备份时间。
- scheduler 的 `lastResult` 提供完成路径、时间、失败原因、保留清理警告；只有成功后执行保留清理，并优先保留刚写出的备份，即使旧文件时间戳在未来。

## 执行验证

所有 Gradle 调用均经过共享 Mutex wrapper，不直接调用 gradlew：

```powershell
& .superpowers/sdd/run-gradle.ps1 -GradleArguments @(':desktop-library-data:test','--tests','mihon.desktop.library.backup.*',':desktop-app:test','--tests','mihon.desktop.backup.DesktopBackupSchedulerTest')
```

红测证据：独立模型两分类样本因重复 id=0 拒绝；原子替换失败遗留 tmp；导出未包事务；同秒备份覆盖前一个；桌面分类 order 碰撞导致导出后无法通过校验。上述失败均实际执行观察后修复。

已有回归涵盖：章节阅读进度/书签/历史/tracking/漫画 source ID、分类及偏好 remap、重复导入不重复记录且不回退进度、合并策略、checkpoint 失败全部表回滚、损坏 GZIP/非法 protobuf/限制校验。独立模型样本刻意不提供 backupSources，证明缺失源说明仍保留 source ID 与漫画。

## 导入导出 API（供集成或外部真实备份验证）

```kotlin
DesktopLibraryDatabaseFactory.open(databasePath).use { repository ->
    val codec = AndroidBackupCodec()
    val importer = AndroidBackupImporter(codec, AndroidBackupValidator(), repository)
    val first = importer.import(inputTachibkPath, System.currentTimeMillis())
    val second = importer.import(inputTachibkPath, System.currentTimeMillis())
    check(second.counts.mangaInserted == 0L)
    AndroidBackupExporter(repository, codec).export(outputTachibkPath)
    AndroidBackupValidator().validate(codec.decode(outputTachibkPath))
    check(repository.checkIntegrity() == listOf("ok"))
    // first/second 返回 ImportReport，repository.latestImportReport() 可再读取持久化报告。
}
```

对外部样本应用上述 API 应使用新的隔离 databasePath。通过本地往返不等于对方真实应用打开成功；导出的 outputTachibkPath 仍需 Android/Suwayomi 的实际导入确认。

## 最终结果

最后联合运行 BUILD SUCCESSFUL（1m 50s）：desktop-library-data backup 32 tests / 0 failures；DesktopBackupSchedulerTest 5 tests / 0 failures。期间曾被其他任务尚未落盘的 profile/background 类型阻塞，相关类型完成后本次联合构建与测试通过。改动范围 git diff --check 通过。未运行实际 Android/Suwayomi 应用，也未宣称完成该外部端到端验证。

## 当前Android编码器交叉验证

2026-09-15 root通过共享wrapper运行 `:app:testDebugUnitTest --tests '*DesktopBackupImportContractTest' --tests '*DesktopBackupFixtureWriterTest' -PmihonPlan2FixtureDir=app/build/plan2-fixtures :reader-core:extremeImageTest`，session 96178，BUILD SUCCESSFUL（4m16s）。Android编码器3项全部通过，极大图片2项全部通过。

`DesktopBackupImportContractTest`直接使用本仓库Android的Backup serializer写入gzip protobuf，经过桌面codec/importer/实际SQLite关闭重开后比较漫画、章节、分类、书签、进度、历史、跟踪及偏好，并覆盖旧字段回退和全部偏好类型。

生成样本：`app/build/plan2-fixtures/android-generated.tachibk`，499 bytes，SHA256 `5dc104039a1170d53e2bde40833b0ebedaac9d909ae8dc2ffc57742965bbc7d5`。它由当前Android代码编码，仍为构造的非空测试数据，不冒充真实用户备份或Android实机导入验收。
