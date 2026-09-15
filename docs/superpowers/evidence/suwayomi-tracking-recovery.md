# T7 跟踪恢复、凭据迁移与新增跟踪器

工作树：`D:\my project\mihon-w\.worktrees\suwayomi`。未提交，Runtime/UI 由主代理接线。

## 实现

- Windows Credential Manager：JNA 直接调用 CredWriteW/CredReadW/CredDeleteW/CredFree，用户级 generic credential。每个 profile 有独立非秘密 UUID namespace，避免安装之间覆盖。
- preferences 旧 token 只有在写入并回读完全一致后删除；写/读失败保留旧账户。新登录写入失败仅在进程内保留 token，不新增明文，也不覆盖旧账户元数据。便携版换机器无法解密旧凭据时重新登录。
- 离线队列持久化 nextAttemptAt/retryCount/authenticationRequired；5 秒起、最多 1 小时指数退避，401 暂停直到该服务登录成功。429/5xx/网络异常保留待办。
- 读进度先更新本地并入队；后台开启时阅读不等待 HTTP。start 防止重复 worker，所有启动/联网/登录/定时/手动 drain 使用同一 Mutex。相同漫画/服务合并更高章节并保留退避，不会被旧请求完成误删新章节。
- 远端章节高于本地时暂停覆盖并发布 TrackConflict；可选本地覆盖或采用远端。正常远端落后属于待同步进度，直接上传。
- 默认 tracker 从 9 个补为 11 个：Hikka=10、MangaBaka=11，保留原有 9 个。新增服务支持验证 token 登录、恢复、搜索、查询绑定、更新/新增、退出。
- MangaBaka 遵循仓库 Android `/v1/my/profile`、`/v1/series/search`、`/v1/my/library/{id}`、POST/PUT、状态/隐私/100 分制/日期和 titles 优先级。
- Hikka 遵循仓库 Android `auth` header、`/user/me`、POST `/manga`、`/read/manga/{slug}`、UUID name hash 身份、秒级日期和 rereads；更新保留已有 note/volumes。

## Runtime/UI 接线

```kotlin
// trackerManager、repository、trackingQueue 与 service 都只创建一份
trackOnReadSyncService.start(applicationScope)
trackOnReadSyncService.onConnectivityChanged(isOnline)
// shutdown
trackOnReadSyncService.close()
```

默认 online=true，启动立即 drain；即使暂时没有系统联网事件，退避定时器仍能恢复。登录恢复 handler 已由 service 注册。

UI 收集 `authenticationRequired: StateFlow<Set<Long>>` 显示重新登录；收集 `conflicts: StateFlow<List<TrackConflict>>` 显示本地/远端值，调用 `resolveConflict(mangaId, trackerId, LOCAL_WINS/REMOTE_WINS)`。

## 验证

统一使用 `.superpowers/sdd/run-gradle.ps1` 的共享 Mutex。

- 红：TrackingRecoveryTest 在旧实现编译失败（不存在 CredentialStore / 可注入构造）。
- 绿：TrackingRecoveryTest、TrackOnReadSyncServiceTest、DesktopTrackerStoreTest、OfflineTrackingQueueTest 首批通过。
- 新增服务阶段发现 JSON helper 与 Shikimori 私有扩展同名，已统一 token 前缀消除冲突。
- 第二轮绿：DesktopAdditionalTrackerApiTest 2、TrackerAndQueueTest 3、TrackingQueueRecoveryTest 2、TrackingRecoveryTest 3，共 10 个，0 failed/0 skipped。
- Windows 原生凭据写入→回读（含中文）→删除→确认不存在：本机实际执行通过；使用一次性随机 test target 并 finally 清理。
- HTTP 契约为本机 HttpServer 实际 HTTP 请求，覆盖真实路径/方法/header/body 字段；不等同线上账户联调。
- 最终全 `mihon.desktop.track.*`：15 类、44 个测试，0 failures / 0 errors / 0 skipped；BUILD SUCCESSFUL in 27s。包含新增远端更高进度冲突采用远端测试。

## 外部门槛和实测范围

没有真实 MangaBaka/Hikka 账户 token，未对生产账户发起读写。桌面现提供已有合法 access token 输入；未冒用 Android OAuth client 或复制 Android client secret。

MangaBaka 自动 OAuth 需 MihonW 自己的注册 client/redirect（仓库 Android 为 PKCE）；Hikka 自动 OAuth 需注册应用 reference/secret/redirect。当前未取得这些配置，因此浏览器授权回调/交换与 refresh 生命周期未宣称完成。Hikka 官方说明 token 需定期使用续期，长期离线可能需要重新登录。

官方核对：[MangaBaka API](https://mangabaka.org/data/api)、[Hikka API](https://api.hikka.io/docs)、[Hikka OAuth](https://hikka.io/articles/hikka-oauth-ebbd59)。字段实现以当前 checkout Android MangaBakaApi/HikkaApi/DTO/Utils 为来源，不臆造生产接口。


补充：TrackingRecoveryTest 源文件统一 UTF-8 后单独重跑 3 项通过（18s），含 Windows 中文 credential native round trip。

