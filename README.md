<div align="center">

<a href="https://mihon.app">
    <img src="./.github/assets/logo.png" alt="Mihon logo" title="Mihon logo" width="80"/>
</a>

# Mihon [App](#)

### Full-featured reader
Discover and read manga, webtoons, comics, and more – easier than ever on your Android device.

[![Discord server](https://img.shields.io/discord/1195734228319617024.svg?label=&labelColor=6A7EC2&color=7389D8&logo=discord&logoColor=FFFFFF)](https://discord.gg/mihon)
[![GitHub downloads](https://img.shields.io/github/downloads/mihonapp/mihon/total?label=downloads&labelColor=27303D&color=0D1117&logo=github&logoColor=FFFFFF&style=flat)](https://mihon.app/download)

[![CI](https://img.shields.io/github/actions/workflow/status/mihonapp/mihon/build.yml?labelColor=27303D)](https://github.com/mihonapp/mihon/actions/workflows/build_push.yml)
[![License: Apache-2.0](https://img.shields.io/github/license/mihonapp/mihon?labelColor=27303D&color=0877d2)](/LICENSE)
[![Translation status](https://img.shields.io/weblate/progress/mihon?labelColor=27303D&color=946300)](https://hosted.weblate.org/engage/mihon/)

## Download

[![Mihon Stable](https://img.shields.io/github/release/mihonapp/mihon.svg?maxAge=3600&label=Stable&labelColor=06599d&color=043b69)](https://mihon.app/download)
[![Mihon Beta](https://img.shields.io/github/v/release/mihonapp/mihon-preview.svg?maxAge=3600&label=Beta&labelColor=2c2c47&color=1c1c39)](https://mihon.app/download)

*Requires Android 8.0 or higher.*

## Features

<div align="left">

* Local reading of content.
* A configurable reader with multiple viewers, reading directions and other settings.
* Tracker support: [MangaBaka](https://mangabaka.org), [MyAnimeList](https://myanimelist.net/), [AniList](https://anilist.co/), [Kitsu](https://kitsu.app/), [MangaUpdates](https://mangaupdates.com), [Shikimori](https://shikimori.one), [Bangumi](https://bgm.tv/), and [Hikka](https://hikka.io/) support.
* Categories to organize your library.
* Light and dark themes.
* Schedule updating your library for new chapters.
* Create backups locally to read offline or to your desired cloud service.
* Bidirectional **WebDAV sync** for extension stores, installed extensions, library entries, categories, reading progress, history, and tracking.
* Plus much more...

### WebDAV Sync (mihon-webdav)

This fork ([WguguW/mihon-webdav](https://github.com/WguguW/mihon-webdav)) adds built-in **WebDAV bidirectional synchronization** on top of Mihon:

* **Extensions**: Syncs configured extension stores (`Extension Stores`) and records installed extensions; detects missing extensions on other devices and supports one-tap individual or batch installation.
* **Library & Reading Progress**: Syncs library manga, categories, read chapters, page progress, reading history, and tracking records.
* **3-Way Smart Merge**: Uses a local baseline snapshot so single-side changes (including unfavoriting, marking unread, and removing bookmarks) propagate cleanly across devices; concurrent reading progress conflicts resolve by latest read timestamp with additive reading duration.
* **Flexible Triggers**: Manual sync, periodic background sync (with optional Wi-Fi-only constraint), automatic debounced sync on exiting the reader, and optional sync on app startup.
* **Setup**: Open **More → Settings → Data and storage → WebDAV sync**, enter your WebDAV server URL, username, password, and remote directory, then tap **Test connection** and **Sync now**.
* **APK Download**: Download the signed ARM64 release APK from [Releases](https://github.com/WguguW/mihon-webdav/releases).

#### WebDAV 同步（中文说明）

本项目（[WguguW/mihon-webdav](https://github.com/WguguW/mihon-webdav)）基于 Mihon 新增了内置 **WebDAV 双向智能同步** 功能：

* **插件同步**：同步已配置的插件仓库源（Extension Stores）并记录已安装插件清单；在其他设备同步后自动检测缺失插件，支持逐个或一键批量安装。
* **书架漫画与阅读进度**：同步收藏漫画、分类、已读章节、页码进度、阅读历史与追踪记录。
* **三方智能合并（3-Way Merge）**：基于本地基线快照合并变更，支持取消收藏、标为未读、取消书签等跨设备同步；双端并发阅读冲突时按最新阅读时间自动合并，阅读时长按增量叠加。
* **多种触发方式**：支持手动立即同步、定时后台同步（可限制仅 Wi-Fi）、退出阅读器自动同步及启动应用自动同步。
* **配置入口**：进入 **更多 → 设置 → 数据和存储 → WebDAV 同步**，填写 WebDAV 服务器地址、用户名、密码和远程目录，点击 **测试连接** 与 **立即同步** 即可。
* **安装包下载**：前往 [Releases](https://github.com/WguguW/mihon-webdav/releases) 页面下载 ARM64 正式版 APK。

</div>

## Contributing

[Code of conduct](./CODE_OF_CONDUCT.md) · [Contributing guide](./CONTRIBUTING.md)

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Before reporting a new issue, take a look at the [FAQ](https://mihon.app/docs/faq/general), the [changelog](https://mihon.app/changelogs/) and the already opened [issues](https://github.com/mihonapp/mihon/issues); if you got any questions, join our [Discord server](https://discord.gg/mihon).


### Repositories

[![mihonapp/website - GitHub](https://github-stats-extended.vercel.app/api/pin/?username=mihonapp&repo=website&bg_color=161B22&text_color=c9d1d9&title_color=0877d2&icon_color=0877d2&border_radius=8&hide_border=true&description_lines_count=2)](https://github.com/mihonapp/website/)
[![mihonapp/bitmap.kt - GitHub](https://github-stats-extended.vercel.app/api/pin/?username=mihonapp&repo=bitmap.kt&bg_color=161B22&text_color=c9d1d9&title_color=0877d2&icon_color=0877d2&border_radius=8&hide_border=true&description_lines_count=2)](https://github.com/mihonapp/bitmap.kt/)

### Credits

Thank you to all the people who have contributed!

<a href="https://github.com/mihonapp/mihon/graphs/contributors">
    <img src="https://contrib.rocks/image?repo=mihonapp/mihon" alt="Mihon app contributors" title="Mihon app contributors" width="800"/>
</a>

### Disclaimer

The developer(s) of this application does not have any affiliation with the content providers available, and this application hosts zero content.

### License

<pre>
Copyright © 2015 Javier Tomás
Copyright © 2024 Mihon Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
</pre>

</div>
