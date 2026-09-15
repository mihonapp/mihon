# 网络与桌面集成证据

工作区：`D:\my project\mihon-w\.worktrees\suwayomi`，分支 `codex/suwayomi-evolution`。

## 网络行为

- IPC带扩展、source和请求身份；取消逐请求传播到回调和OkHttp Call，关闭会话唤醒所有等待者。
- 标准扩展OkHttp最后传输进入父进程，保留自定义拦截器与getImage顺序。自动Cookie按扩展隔离、持久化，遵循host/path/secure/expiry匹配。
- 重定向逐跳核对权限，跨域移除认证与Cookie头。二进制和重复响应头不经有损文本转换，超大body有上限。
- 总并发8、单host3、后台单host最多2；阅读优先于预取和下载，同时限制连续高优先请求，避免后台饿死。429/503的Retry-After暂停对应host，其他host可继续。
- 系统/直连/HTTP/SOCKS代理、UA和超时由设置持久化，新请求使用新策略。高级设置、下载恢复、更新进度、跟踪冲突和浏览器返回重试均接入现有页面。
- 预取协程携带可见性；可见页为READER，预取NORMAL，下载BACKGROUND。正在预取且被前台请求的页可在发起网络请求前提升优先级。

验证：CookieStore4、NetworkHelper5、NetworkSessionContract3、NetworkPolicyIntegration2、NetworkRequestScheduler3，以及IPC取消和host传输测试。session 43129的37项包含上述网络测试和UI/书库测试；不把重叠重复执行相加。

## 10,000部漫画的真实SQLite测试

首次红灯：书签筛选返回100项而预期10项，旧实现将书签/下载筛选直接视为true。

修复：查询聚合书签、下载资产、最后阅读和更新时间；日期排序使用真实字段；批量操作一次快照并在IO线程事务中执行，UI显示进度和错误。退出时先取消并等待UI工作，再关闭SQLite。

session 43129，LargeLibraryWorkflowTest实测：

| 指标 | 时间 |
|---|---:|
| 写入10,000漫画及章节等数据 | 738 ms |
| 初始书库查询/呈现状态 | 418 ms |
| 搜索 | 27 ms |
| 事务移除100部漫画 | 250 ms |

批量移除仅读取1次全库快照。测试核对搜索、下载/书签筛选、日期顺序及实际数据库移除。以上是测试机单次测量，不等于P95、绘制帧率或30分钟阅读内存验收。

## 当前范围

reader-core、数据层、SDK及桌面关键阅读/书库回归已通过。真实隔离扩展的端到端流程由独立测试记录。实际发行EXE、宽窄窗口、各缩放和连续阅读的最终证据仍在补齐。
