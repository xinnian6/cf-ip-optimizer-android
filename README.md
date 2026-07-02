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

## 打包方法

这台机器当前没有 Android/Gradle 构建工具，所以我先把源码项目建好了。你可以这样打包：

1. 安装 Android Studio。
2. 打开目录：

```text
C:\Users\10791\Desktop\你好\cf_ip_optimizer_android
```

3. 等 Android Studio 同步 Gradle。
4. 手机打开 USB 调试，点击 Run 安装。
5. 或者菜单选择 `Build -> Build APK(s)` 生成 APK。

## 推荐手机端参数

```text
超时秒：8
并发：16 或 32
候选：100
复测：2
下载MiB：2
最低Mbps：80
真实节点测速：勾选
```

判断结果时只看成功项：

```text
成功2次/共2次
Mbps 大于 80 或 100
错误为空
```

手机端结果和电脑端不同是正常的，因为手机网络、Wi-Fi、运营商、VPN 内核都不一样。最终给手机用的 IP，最好直接在手机端测出来。
