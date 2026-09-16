# mihondesk 品牌资源

mihondesk 使用“展开书页与书签”图标：蓝色圆角底承托米白色书页，青色书签标记正在阅读的位置。图标不含文字，供窗口、任务栏、快捷方式和安装包共用。

## 文件

| 文件 | 用途 |
| --- | --- |
| `desktop-app/src/main/resources/icon.svg` | 应用图标的可编辑矢量源文件 |
| `desktop-app/src/main/resources/icon.png` | 512 × 512，应用窗口与通知使用 |
| `desktop-app/src/main/resources/icon.ico` | 16、24、32、48、64、96、128、256 像素，Windows 可执行文件与快捷方式使用 |
| `.github/assets/logo.png` | 与应用一致的 GitHub 标识 |
| `.github/assets/banner.svg` / `banner.png` | 仓库首页横幅 |
| `.github/assets/social-preview.png` | 1280 × 640，仓库分享预览图片 |

## 颜色与名称

- 项目名统一写作 `mihondesk`，安装程序和快捷方式也使用该名称。
- 图标底色：`#336CF5` → `#1739B8`。
- 书页：`#F6F1E4` / `#FFFFFF`；书签：`#54E5CF`。
- 仓库横幅底色：`#0C1632`。

## 导出

图形使用可编辑 SVG 绘制。PNG 与 ICO 由源文件导出，不依赖在线图像生成服务。准备 ImageMagick 7 后，在仓库根目录运行：

```powershell
.\scripts\export-brand-assets.ps1 -MagickPath '<ImageMagick 7 的 magick.exe 路径>'
```

修改图标后请重新导出，并检查透明边角和 16 / 32 / 48 像素下的识别效果。横幅文字使用 Segoe UI 与 Microsoft YaHei 字体，建议在 Windows 上导出。

本套图标与横幅属于 mihondesk 项目资源，沿用仓库的 Apache 2.0 许可。上游 Mihon 的名称与标识仍归其各自权利人所有；本项目为独立 Windows 移植项目。
