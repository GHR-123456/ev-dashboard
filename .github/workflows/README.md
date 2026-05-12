# 离线地图自动发布管线

> 工作流文件:`build-tiles.yml`
> 目标:把"切片 → 打包 → 发布 → 客户端拉取"这条链全自动化,日常运营 0 操作。

## 数据流

```
GitHub Actions(每月 1 号 04:00 UTC)
   │
   ├─ 从 Geofabrik 下 china-latest.osm.pbf(~1.5 GB)
   ├─ osmium 按 bbox 裁出指定区域(shanghai/beijing/... 各自硬编码 bbox)
   ├─ 用 tilemaker 切成 OpenMapTiles schema 矢量瓦片(tiles.mbtiles)
   ├─ 打包 tiles.mbtiles + style.json → pkg.zip
   ├─ 计算 SHA-256 → manifest.json
   └─ 发布 GitHub Release(tag = 当天日期,例 2026.05.12)
        │
        └─ Release assets:
             pkg.zip          (~ 数十 MB ~ 400 MB,看区域)
             manifest.json    (固定字段,客户端比对版本用)

Android 客户端(WorkManager,每 7 天)
   │
   ├─ GET <MAP_MANIFEST_URL>
   ├─ 比对 latestVersion vs 本地已装版本
   ├─ 不一致 → 下载 packageUrl → SHA-256 校验 → 解压到空闲槽
   └─ 写 DataStore activeSlot,下一帧 MapView 自动切样式
```

## 首次部署(一次性,约 15 分钟)

### 1. 推到 GitHub

仓库还不是 git repo,初始化并 push:

```bash
cd C:/Users/guo19/ev-dashboard
git init
git add .
git commit -m "init"
gh repo create ev-dashboard --public --source=. --remote=origin --push
```

> 仓库**必须 public**,否则 Release 资源链接需登录令牌,App 端拉不到。
> 想保持私有的话需要走 Release 链接 + 短令牌,实现成本高,暂不支持。

### 2. 第一次手动跑 workflow

GitHub 不会因为 push 触发月度 cron,首次必须手动触发:

1. 进仓库的 `Actions` Tab
2. 左侧选 `Build & Publish Offline Map Tiles`
3. 右上 `Run workflow`,参数:
   - **region**:留空(默认 `shanghai`)或下拉选 beijing/guangzhou/shenzhen/hangzhou/chengdu/guangdong/china
   - **runner_size**:`small`(免费 ubuntu-latest;`large` 要绑卡 + Team 套餐)
4. 跑 15–40 分钟(看区域),完成后看 `Releases` Tab,应该多了一个 tag 是当天日期的 release

### 3. 把客户端的 MANIFEST URL 切过去

编辑 `app/build.gradle.kts`:

```kotlin
// 把这行
buildConfigField(
    "String",
    "MAP_MANIFEST_URL",
    "\"https://cdn.example.com/maps/cn/manifest.json\""
)
// 改成(把 <OWNER>/<REPO> 换成你的)
buildConfigField(
    "String",
    "MAP_MANIFEST_URL",
    "\"https://github.com/<OWNER>/<REPO>/releases/latest/download/manifest.json\""
)
```

`latest` 是 GitHub Release 的固定别名,永远指向最新一个 release,不需要每月手动改 URL。

重新装一次 APK,首启会自动拉新包(等 Wi-Fi)。

### 4. 端到端验证

车机端进 **Settings → 离线地图包 → 立即检查更新**:
- 看到进度条爬到 100%(下载 → 校验 → 解压)
- 版本号刷新成 release tag(`2026.05.12` 格式)
- 切到导航页,瓦片应该正常加载

## 日常运营(0 操作)

- 每月 1 号 04:00 UTC(北京时间 12:00)自动跑一次
- 客户端 WorkManager 每 7 天后台拉一次 manifest,发现新版本就走自动下载
- 用户在 7 天周期前等不及,可以在 Settings 主动点"立即检查"

## 手动触发选项

| 参数 | 选项 | 用途 |
|---|---|---|
| `region` | `shanghai`(默认)/`beijing`/`guangzhou`/`shenzhen`/`hangzhou`/`chengdu`/`guangdong`/`china` | 选哪个城市/省/全国,各自的 bbox 在 yml 里硬编码 |
| `runner_size` | `small`(默认)/`large` | small = `ubuntu-latest`(免费, 4c/16G/14GB 盘);large = `ubuntu-latest-16-cores`(**付费 + 须 Team 套餐**,通常不必选) |

**省钱组合**:`shanghai + small` → 0 成本,15–25 分钟出包
**全国包**:`china + small` → 0 成本但风险:全国 PBF 1.5 GB,tilemaker 切全国大约要 12+ GB 内存,标准 runner 16 GB 内存**可能 OOM**。失败的话只能换 large。

## 新增城市

`build-tiles.yml` 的 `case "${REGION}"` 块加一行 bbox 即可。
bbox 格式:`经度min,纬度min,经度max,纬度max`(WGS84)。可以从 https://bboxfinder.com/ 拉范围。

例如加南京:
```yaml
nanjing)      BBOX="118.36,31.74,119.23,32.61" ;;
```
然后在 workflow_dispatch.inputs.region.options 里加上 `- nanjing` 让它出现在下拉里。

## 容量与时间预估

无论选哪个 region,都会**先下整个 china-latest.osm.pbf(1.5 GB)**,然后 osmium 按 bbox 裁出指定区域。
切片阶段才真正吃 CPU 和内存。

| 区域 | 下载耗时 | osmium 裁剪 | tilemaker 切片 | pkg.zip | 总耗时 |
|---|---|---|---|---|---|
| 单城市 | 3–5 分钟 | <1 分钟 | 5–10 分钟 | 30–80 MB | **~15 分钟** |
| 单省 | 同上 | 1–2 分钟 | 20–30 分钟 | 100–180 MB | ~30 分钟 |
| 全国 | 同上 | 跳过 | 60–90 分钟 | 350–450 MB | ~75 分钟,**OOM 风险** |

> 免费 runner 单 job 上限 6 小时,但内存只有 16 GB。全国切片可能 OOM,失败就只能上 large。

## 故障排查

### 1. `tilemaker: command not found`

工作流第一次跑时会从源码编译 tilemaker(`/tmp/tilemaker`),依赖 lua5.1 + boost + sqlite3 + libshp。
依赖是 `apt-get install` 装的,几乎不会失败。如果失败,看 `Install tools` step 的日志,缺啥补啥。

### 2. `wget: 404` / 下载 PBF 失败

Geofabrik 的 `china-latest.osm.pbf` 偶尔会因为镜像同步、CDN 抖动 503。重试一次基本就过。
如果反复失败,看 https://download.geofabrik.de/asia/china.html 确认 URL 有没有变。

### 3. `osmium extract: unknown region`

只支持 yml 里 case 块列出的 region。新增请见上面 "新增城市" 一节。

### 3. `pkg.zip` 找不到 `style.json`

工作流第 116 行 `cp app/src/main/assets/maps/style.json style.json`。
确保该文件在仓库里(本仓库已就位)。若误删,从 `git history` 还原:
`git checkout HEAD~1 -- app/src/main/assets/maps/style.json`

### 4. Release 创建成功但客户端拉不下来

- 仓库是 private?→ 必须 public,见 §"首次部署 1"
- `MAP_MANIFEST_URL` 没改?→ App 还在打 `cdn.example.com`,看 logcat `MapUpdateApi` tag
- 客户端不在 Wi-Fi?→ WorkManager 约束是 UNMETERED,流量下不工作。Settings 主动"立即检查"才走 metered

### 5. 想取消月度自动跑

注释掉 `build-tiles.yml` 第 17–18 行的 `schedule:` 块即可,保留 `workflow_dispatch` 让你手动触发。

## 相关代码索引

| 关注点 | 文件 |
|---|---|
| 自动构建流水线 | `.github/workflows/build-tiles.yml` |
| 客户端拉清单 | `app/src/main/java/com/evdash/app/data/map/remote/MapUpdateApi.kt` |
| 周期任务与首启自动下载 | `app/src/main/java/com/evdash/app/data/map/MapRepository.kt` |
| 启动钩子 | `app/src/main/java/com/evdash/app/data/map/MapBootstrapper.kt` |
| 下载/解压/切槽 worker | `app/src/main/java/com/evdash/app/worker/MapUpdateWorker.kt` |
| 下载进度 UI | `app/src/main/java/com/evdash/app/ui/navmap/NavMapScreen.kt`(Placeholder) |
| Manifest URL 配置 | `app/build.gradle.kts`(buildConfigField `MAP_MANIFEST_URL`) |
