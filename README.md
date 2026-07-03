# CF 手机优选测速 Android 版

这是电脑端 `cf_ip_optimizer` 的安卓原生 MVP 版，用来在手机网络环境下直接测试 Cloudflare/Edgetunnel 入口 IP。

## 当前支持

- IP 数据源 URL 或直接粘贴 IP 列表
- `IP:端口#地区` 格式
- IPv4 CIDR，例如 `104.16.144.0/20#HK`
- TCP 延迟初筛
- TLS/SNI + WebSocket `101` 握手验证
- VLESS over WebSocket 真实 HTTP 下载测速
- `proxyip=` 组合测试，当前只支持 1 个 ProxyIP
- 生成 Edgetunnel 绑定节点：把当前优选 IP 固定到当前 ProxyIP，输出完整 `vless://` 链接
- 一键复制成功结果：

```text
219.76.13.183:443#香港 32.74ms
```

## 和电脑端的区别

安卓端是轻量版，先做手机上最关键的测速链路。下面这些电脑端功能暂时没有做：

- ProxyIP 稳定性批量排名
- 历史优选文件归档
- 回测历史优选
- CSV/JSON 导出
- 多 ProxyIP 组合矩阵

建议流程是：电脑端先筛 ProxyIP 排名，安卓端填其中一个 ProxyIP，在手机网络下测入口 IP。

## 打包方法（推荐：GitHub 云端自动打包）

本机没有 Android/Gradle/JDK 环境，所以直接用 GitHub Actions 在云端出 APK，不用在电脑上装任何构建工具。项目里已经配好 `.github/workflows/build-apk.yml`，推上去就会自动构建。

### 第一步：在 GitHub 建一个空仓库

1. 打开 <https://github.com/new>。
2. Repository name 填 `cf-ip-optimizer-android`（随意）。
3. 选 **Private**（私有即可，不用公开）。
4. 下面的 `Add a README / .gitignore / license` 全部**不要勾**，保持空仓库。
5. 点 **Create repository**，记下页面上给出的仓库地址，形如：

```text
https://github.com/你的用户名/cf-ip-optimizer-android.git
```

### 第二步：把本地项目推上去

在本项目目录打开终端（Git Bash / PowerShell 都行），依次执行。第一次用 git 需要先设置身份：

```bash
git config --global user.name "你的名字"
git config --global user.email "你的邮箱"
```

然后关联远端并推送（把 URL 换成你自己的）：

```bash
cd "C:\Users\10791\Desktop\你好\cf_ip_optimizer_android"
git remote add origin https://github.com/你的用户名/cf-ip-optimizer-android.git
git branch -M main
git push -u origin main
```

推送时会弹出 GitHub 登录，用浏览器授权或输入 Personal Access Token 即可。

### 第三步：等云端自动打包，下载 APK

1. 打开你的仓库页面，点顶部 **Actions** 标签。
2. 会看到一条名为 **Build APK** 的运行记录，点进去等它跑完（约 3-6 分钟，绿色对勾表示成功）。
3. 在这次运行页面最下方 **Artifacts** 区，下载 `cf-optimizer-debug-apk`。
4. 解压得到 `app-debug.apk`，传到手机安装（需在手机设置里允许安装未知来源应用）。

以后每次改了代码 `git push`，云端都会自动重新出一版新的 APK。也可以在 Actions 页面点 **Run workflow** 手动触发。

如果手机提示“签名不一致”“版本不兼容”或要求先卸载旧版，这是因为旧 APK 使用过临时 debug 签名。从 v1.5 开始 GitHub Actions 会缓存固定 debug 签名：这次先卸载旧版再安装一次，之后同一个仓库打出来的新 APK 就可以直接覆盖安装。

## 备选方法：本地 Android Studio 打包

如果你愿意在电脑装环境，也可以本地打包：

1. 安装 Android Studio。
2. 打开目录 `C:\Users\10791\Desktop\你好\cf_ip_optimizer_android`。
3. 等 Android Studio 自动同步 Gradle（它会自动补上 Gradle Wrapper）。
4. 手机开 USB 调试，点 Run 直接安装；或菜单 `Build -> Build APK(s)` 生成 APK。

## 推荐手机端参数

```text
超时秒：8
并发：16 或 32
候选：100
复测：2
下载MiB：2
最低Mbps：80
真实节点测速：勾选
真实下载URL：http://speedtest.tele2.net/10MB.zip
```

判断结果时只看成功项：

```text
成功2次/共2次
Mbps 大于 80 或 100
错误为空
```

手机端结果和电脑端不同是正常的，因为手机网络、Wi-Fi、运营商、VPN 内核都不一样。最终给手机用的 IP，最好直接在手机端测出来。
