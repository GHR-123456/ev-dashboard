# NavMap 出厂资源(D 方案)

当前采用 **D 方案**:APK 不附带 `seed.mbtiles`,首次启动后由 WorkManager 等 Wi-Fi 联网下载完整离线包。
本目录只需保留 `style.json`。

| 文件 | 用途 | 状态 |
|---|---|---|
| `style.json` | MapLibre 样式,被打进 APK 也被打进 pkg.zip | **必须存在** |
| `seed.mbtiles` | 已不再使用 | **请勿放入** |

## 启动行为

`MapBootstrapper.boot()` 流程:

1. `ensureSeed()` 检查 `filesDir/maps/` 是否已有可用包
2. 若无:`enqueueAutoFirstDownload()` 入队一次性下载(`UNMETERED + KEEP`)
3. 同时挂 7 天周期任务,后续自动追新

首启后地图页(`NavMapScreen`)显示带进度的 Placeholder UI,等下载完成自动切瓦片。

## style.json 修改注意

样式文件被两端各引用一次:

1. **APK 端**:本目录这份(实际只用于回退,正常路径走槽位里的)
2. **GitHub Actions 打包端**:工作流第 116 行 `cp app/src/main/assets/maps/style.json style.json` 把它打进 pkg.zip

改完务必重新跑一次 `Actions → Build & Publish Offline Map Tiles`,否则线上 release 的 pkg.zip 里还是旧 style。

## 离线包来源

不再手工准备,见 `.github/workflows/README.md`。
