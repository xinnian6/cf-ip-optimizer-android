package com.cfoptimizer.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.service.quicksettings.TileService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class QuickRunService extends Service {
    private static final String PREFS = "cf_optimizer_mobile";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String SECURE_KEY_ALIAS = "cf_optimizer_secure_prefs_v1";
    private static final String SECURE_PREFIX = "secure_";
    private static final String ENC_PREFIX = "enc:v1:";
    private static final String CHANNEL_ID = "cf_optimizer_scan";
    private static final int NOTIFICATION_ID = 101;
    private static final int MIN_REAL_BYTES = 64 * 1024;
    private static final int BODY_SNIPPET_BYTES = 96;
    private static final String DEFAULT_REAL_URL = "http://speedtest.tele2.net/10MB.zip";
    private static final String OLD_REAL_URL = "http://cachefly.cachefly.net/10mb.test";
    private static final String BAD_REAL_URL = "http://cachefly.cachefly.net/100mb.test";
    private static final String USER_AGENT = "CFMobileOptimizer/1.31";

    private final SecureRandom random = new SecureRandom();
    private volatile boolean running = false;
    private SharedPreferences prefs;
    private Map<String, String> regionNames;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        regionNames = buildRegionNames();
        ensureNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) {
            notifyStatus("后台测速已经在运行", 3, true);
            return START_NOT_STICKY;
        }
        running = true;
        prefs.edit().putBoolean("quickRunRunning", true).apply();
        requestTileRefresh();
        startForeground(NOTIFICATION_ID, buildNotification("正在准备后台测速", 0, true));

        new Thread(() -> {
            try {
                runOnce();
            } catch (Exception e) {
                notifyStatus("后台测速失败：" + e.getMessage(), 100, false);
            } finally {
                running = false;
                prefs.edit().putBoolean("quickRunRunning", false).apply();
                requestTileRefresh();
                stopForeground(false);
                stopSelf();
            }
        }, "cf-quick-run").start();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void runOnce() throws Exception {
        Config config = readConfig();
        notifyStatus("正在测速 ProxyIP", 5, true);
        selectBestProxyIfAvailable(config);

        notifyStatus("正在解析 IP 数据源", 8, true);
        List<Target> targets = loadTargets(config.source, config.regions, true);
        if (targets.isEmpty()) throw new IllegalArgumentException("没有解析到符合区域的 IP");

        List<Result> tcpOk = runTcpScan(targets, config);
        tcpOk.sort((a, b) -> Double.compare(a.tcpMs, b.tcpMs));
        tcpOk = filterByMaxTcp(tcpOk, config);
        if (tcpOk.size() > config.candidates) {
            tcpOk = new ArrayList<>(tcpOk.subList(0, config.candidates));
        }
        if (tcpOk.isEmpty()) throw new IllegalArgumentException("TCP 初筛没有可连接地址");

        List<Result> checked = runWsScan(tcpOk, config);
        checked.sort(this::compareResults);
        String nodes = buildEdgetunnelBoundNodes(checked, config);
        if (nodes.isEmpty()) throw new IllegalArgumentException("没有可保存的绑定节点");

        notifyStatus("正在覆盖保存到 TextSpace", 96, true);
        String title = config.textspaceTitle.isEmpty() ? "Edgetunnel 绑定节点" : config.textspaceTitle;
        saveTextspaceNote(title, nodes, config);
        notifyStatus("后台测速完成，已覆盖保存：" + title, 100, false);
    }

    private Config readConfig() {
        Config c = new Config();
        c.source = pref("source", "https://zip.cm.edu.kg/all.txt");
        c.proxySource = securePref("proxySource", "");
        c.host = pref("host", "xinnian.us.ci").trim();
        c.path = securePref("path", "/").trim();
        c.uuid = securePref("uuid", "").trim();
        c.proxyip = firstToken(securePref("proxyip", ""));
        c.realUrl = prefRealUrl();
        c.timeoutMs = Math.max(500, (int) (parseDouble(pref("timeout", "8"), 8) * 1000));
        c.concurrency = clamp((int) parseDouble(pref("concurrency", "32"), 32), 1, 256);
        c.candidates = clamp((int) parseDouble(pref("candidates", "100"), 100), 1, 5000);
        c.maxTcpMs = Math.max(0, parseDouble(pref("maxTcpMs", "250"), 250));
        c.repeats = clamp((int) parseDouble(pref("repeats", "2"), 2), 1, 10);
        c.downloadBytes = Math.max(1, (int) (parseDouble(pref("downloadMb", "2"), 2) * 1024 * 1024));
        c.minSpeedMbps = Math.max(0, parseDouble(pref("minSpeed", "80"), 80));
        c.realCheck = !c.uuid.isEmpty();
        c.regions = selectedRegions();
        c.textspaceTitle = pref("textspaceTitle", "CF优选结果").trim();
        c.textspaceNoteId = pref("textspaceNoteId", "").trim();
        TextspaceSettings textspace = parseTextspaceSettings(true);
        c.textspaceBaseUrl = textspace.baseUrl;
        c.textspaceToken = textspace.token;
        if (c.host.isEmpty()) throw new IllegalArgumentException("请先填写 SNI/Host 域名");
        if (!c.uuid.isEmpty()) UUID.fromString(c.uuid);
        if (c.realCheck && !c.realUrl.toLowerCase(Locale.ROOT).startsWith("http://")) {
            throw new IllegalArgumentException("真实下载 URL 目前请使用 http:// 大文件地址");
        }
        return c;
    }

    private void selectBestProxyIfAvailable(Config config) throws Exception {
        if (config.proxySource.trim().isEmpty()) return;
        List<Target> proxies = loadTargets(config.proxySource, config.regions, true);
        if (proxies.isEmpty()) return;
        if (proxies.size() > config.candidates) proxies = evenSample(proxies, config.candidates);
        List<ProxyResult> results = runProxyTcpScan(proxies, config);
        results.sort((a, b) -> {
            int ok = Integer.compare(b.successes, a.successes);
            if (ok != 0) return ok;
            return Double.compare(a.bestMs, b.bestMs);
        });
        for (ProxyResult result : results) {
            if (result.successes <= 0) continue;
            config.proxyip = result.address();
            SharedPreferences.Editor editor = prefs.edit();
            putSecureString(editor, "proxyip", config.proxyip);
            editor.apply();
            notifyStatus("ProxyIP 第一名：" + config.proxyip, 12, true);
            return;
        }
    }

    private List<Result> runTcpScan(List<Target> targets, Config config) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(config.concurrency);
        List<Future<Result>> futures = new ArrayList<>();
        AtomicInteger done = new AtomicInteger();
        for (Target target : targets) {
            futures.add(pool.submit(() -> {
                Result r = new Result(target);
                long start = System.nanoTime();
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(target.host, target.port), config.timeoutMs);
                    r.tcpMs = elapsedMs(start);
                    r.tcpOk = true;
                } catch (Exception e) {
                    r.error = shortError(e);
                }
                int n = done.incrementAndGet();
                if (n % 20 == 0 || n == targets.size()) {
                    progress("TCP 初筛", n, targets.size(), 15, 45);
                }
                return r;
            }));
        }
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.MINUTES);

        List<Result> out = new ArrayList<>();
        for (Future<Result> f : futures) {
            try {
                Result r = f.get();
                if (r.tcpOk) out.add(r);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private List<ProxyResult> runProxyTcpScan(List<Target> proxies, Config config) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(config.concurrency);
        List<Future<ProxyResult>> futures = new ArrayList<>();
        AtomicInteger done = new AtomicInteger();
        for (Target proxy : proxies) {
            futures.add(pool.submit(() -> {
                ProxyResult r = new ProxyResult(proxy);
                for (int i = 0; i < config.repeats; i++) {
                    long start = System.nanoTime();
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(proxy.host, proxy.port), config.timeoutMs);
                        double ms = elapsedMs(start);
                        r.successes++;
                        r.bestMs = r.bestMs <= 0 ? ms : Math.min(r.bestMs, ms);
                    } catch (Exception e) {
                        r.error = shortError(e);
                    }
                }
                int n = done.incrementAndGet();
                progress("ProxyIP", n, proxies.size(), 5, 15);
                return r;
            }));
        }
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.MINUTES);

        List<ProxyResult> out = new ArrayList<>();
        for (Future<ProxyResult> f : futures) {
            try {
                out.add(f.get());
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private List<Result> runWsScan(List<Result> candidates, Config config) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(config.concurrency);
        List<Future<Result>> futures = new ArrayList<>();
        AtomicInteger done = new AtomicInteger();
        for (Result candidate : candidates) {
            futures.add(pool.submit(() -> {
                Result r = candidate.copy();
                for (int i = 0; i < config.repeats; i++) probeWs(r, config);
                r.handshakeOk = r.wsSuccesses >= 1;
                r.realOk = config.realCheck ? r.realSuccesses >= 1 : r.handshakeOk;
                r.fastOk = r.realOk && (!config.realCheck || r.speedMbps >= config.minSpeedMbps);
                int n = done.incrementAndGet();
                progress("WS / 真实测速", n, candidates.size(), 45, 95);
                return r;
            }));
        }
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.MINUTES);

        List<Result> out = new ArrayList<>();
        for (Future<Result> f : futures) {
            try {
                out.add(f.get());
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private void probeWs(Result r, Config config) {
        SSLSocket socket = null;
        try {
            SSLSocketFactory factory = (SSLSocketFactory) SSLContext.getDefault().getSocketFactory();
            socket = (SSLSocket) factory.createSocket();
            socket.setSoTimeout(config.timeoutMs);
            SSLParameters params = socket.getSSLParameters();
            params.setServerNames(Collections.singletonList(new SNIHostName(config.host)));
            socket.setSSLParameters(params);
            long tlsStart = System.nanoTime();
            socket.connect(new InetSocketAddress(r.host, r.port), config.timeoutMs);
            socket.startHandshake();
            r.tlsMs = elapsedMs(tlsStart);

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            String key = Base64.getEncoder().encodeToString(randomBytes(16));
            String path = pathWithProxy(config.path, config.proxyip);
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + config.host + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "User-Agent: " + USER_AGENT + "\r\n\r\n";
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String status = readLine(in);
            int http = parseStatus(status);
            r.wsStatus = http;
            while (true) {
                String line = readLine(in);
                if (line == null || line.length() == 0) break;
            }
            if (http == 101) {
                r.wsSuccesses++;
                r.error = "";
                if (config.realCheck) {
                    RealSpeed speed = vlessHttpSpeed(in, out, config);
                    r.httpStatus = speed.status;
                    r.bytesRead += speed.bytes;
                    if (speed.ok()) {
                        r.realSuccesses++;
                        r.speedMbps = r.speedMbps <= 0 ? speed.mbps : Math.min(r.speedMbps, speed.mbps);
                        r.error = "";
                    } else {
                        String hint = speed.bodySnippet.isEmpty() ? "" : " 内容:" + speed.bodySnippet;
                        r.error = speed.status > 0
                                ? "真实测速 HTTP " + speed.status + " bytes " + speed.bytes + hint
                                : "真实测速无数据";
                    }
                }
            } else {
                r.error = "WS HTTP " + http;
            }
        } catch (Exception e) {
            r.error = shortError(e);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private RealSpeed vlessHttpSpeed(InputStream in, OutputStream out, Config config) throws Exception {
        URL url = new URL(config.realUrl);
        byte[] payload = buildVlessPayload(config.uuid, url);
        writeWsFrame(out, payload);

        long deadline = System.currentTimeMillis() + config.timeoutMs;
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        ByteArrayOutputStream bodySample = new ByteArrayOutputStream();
        boolean stripped = false;
        boolean headersDone = false;
        int status = 0;
        int body = 0;
        long start = System.nanoTime();

        while (System.currentTimeMillis() < deadline && body < config.downloadBytes) {
            byte[] frame;
            try {
                frame = readWsFrame(in);
            } catch (IOException e) {
                break;
            }
            if (frame.length == 0) continue;
            if (!stripped) {
                frame = stripVlessHeader(frame);
                stripped = true;
                start = System.nanoTime();
                if (frame.length == 0) continue;
            }
            if (headersDone) {
                body += frame.length;
                rememberBodySample(bodySample, frame, 0, frame.length);
                continue;
            }
            response.write(frame);
            byte[] data = response.toByteArray();
            int headerEnd = indexOf(data, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            if (headerEnd < 0) continue;
            String head = new String(data, 0, headerEnd, StandardCharsets.ISO_8859_1);
            String first = head.split("\\r?\\n", 2)[0];
            status = parseStatus(first);
            int bodyStart = headerEnd + 4;
            int bodyLen = data.length - bodyStart;
            body += bodyLen;
            rememberBodySample(bodySample, data, bodyStart, bodyLen);
            headersDone = true;
        }
        double seconds = Math.max(elapsedMs(start) / 1000.0, 0.001);
        RealSpeed speed = new RealSpeed();
        speed.status = status;
        speed.bytes = body;
        speed.mbps = body * 8.0 / seconds / 1_000_000.0;
        speed.bodySnippet = sanitizeSnippet(bodySample.toByteArray());
        return speed;
    }

    private byte[] buildVlessPayload(String uuidText, URL target) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        UUID uuid = UUID.fromString(uuidText);
        ByteBuffer id = ByteBuffer.allocate(16);
        id.putLong(uuid.getMostSignificantBits());
        id.putLong(uuid.getLeastSignificantBits());
        out.write(0);
        out.write(id.array());
        out.write(0);
        out.write(1);
        int port = target.getPort() > 0 ? target.getPort() : 80;
        out.write((port >> 8) & 0xff);
        out.write(port & 0xff);
        String host = target.getHost();
        byte[] hostBytes = host.getBytes(StandardCharsets.US_ASCII);
        out.write(2);
        out.write(hostBytes.length);
        out.write(hostBytes);
        String path = target.getFile().isEmpty() ? "/" : target.getFile();
        String request = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + host + "\r\n"
                + "User-Agent: " + USER_AGENT + "\r\n"
                + "Accept: */*\r\n"
                + "Connection: close\r\n\r\n";
        out.write(request.getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private List<Target> loadTargets(String source, Set<String> regionFilter, boolean filterByRegion) throws Exception {
        String text = source == null ? "" : source.trim();
        if (text.startsWith("http://") || text.startsWith("https://")) {
            HttpURLConnection conn = (HttpURLConnection) new URL(text).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            try (InputStream in = conn.getInputStream()) {
                text = new String(readAll(in), StandardCharsets.UTF_8);
            }
        }
        List<Target> targets = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            for (String token : line.split("[,;\\s]+")) {
                List<Target> parsed = parseTargets(token);
                for (Target t : parsed) {
                    if (filterByRegion && !regionFilter.isEmpty()) {
                        String code = normalizeRegion(t.region);
                        if (!regionFilter.contains(code)) continue;
                    }
                    String key = t.host + ":" + t.port + "#" + normalizeRegion(t.region);
                    if (seen.add(key)) targets.add(t);
                }
            }
        }
        return targets;
    }

    private List<Target> parseTargets(String token) {
        List<Target> out = new ArrayList<>();
        try {
            String value = token == null ? "" : token.trim();
            if (value.isEmpty()) return out;
            String region = "";
            int hash = value.indexOf('#');
            if (hash >= 0) {
                region = normalizeRegion(value.substring(hash + 1).trim());
                value = value.substring(0, hash).trim();
            }
            int port = 443;
            String host = value;
            if (value.startsWith("[") && value.contains("]")) {
                int end = value.indexOf(']');
                host = value.substring(1, end);
                if (value.length() > end + 2 && value.charAt(end + 1) == ':') {
                    port = Integer.parseInt(value.substring(end + 2));
                }
            } else if (value.indexOf(':') == value.lastIndexOf(':') && value.contains(":")) {
                int idx = value.lastIndexOf(':');
                host = value.substring(0, idx);
                port = Integer.parseInt(value.substring(idx + 1));
            }
            if (host.contains("/") && !host.contains(":")) {
                out.addAll(expandIpv4Cidr(host, port, region));
            } else if (!host.isEmpty()) {
                out.add(new Target(host, port, region));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private List<Target> expandIpv4Cidr(String cidr, int port, String region) {
        List<Target> out = new ArrayList<>();
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) return out;
            int prefix = Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > 32) return out;
            long base = ipv4ToLong(parts[0]);
            long mask = prefix == 0 ? 0 : (0xffffffffL << (32 - prefix)) & 0xffffffffL;
            long network = base & mask;
            long count = 1L << (32 - prefix);
            if (count > 65536) return out;
            long start = count > 2 ? network + 1 : network;
            long end = count > 2 ? network + count - 2 : network + count - 1;
            for (long ip = start; ip <= end; ip++) out.add(new Target(longToIpv4(ip), port, region));
        } catch (Exception ignored) {
        }
        return out;
    }

    private List<Target> evenSample(List<Target> input, int limit) {
        if (input.size() <= limit) return input;
        List<Target> out = new ArrayList<>();
        double step = input.size() / (double) limit;
        for (int i = 0; i < limit; i++) out.add(input.get((int) Math.floor(i * step)));
        return out;
    }

    private List<Result> filterByMaxTcp(List<Result> input, Config config) {
        if (config.maxTcpMs <= 0) return input;
        List<Result> out = new ArrayList<>();
        for (Result r : input) if (r.tcpMs <= config.maxTcpMs) out.add(r);
        return out;
    }

    private int compareResults(Result a, Result b) {
        int fast = Boolean.compare(b.fastOk, a.fastOk);
        if (fast != 0) return fast;
        int real = Boolean.compare(b.realOk, a.realOk);
        if (real != 0) return real;
        int ws = Boolean.compare(b.handshakeOk, a.handshakeOk);
        if (ws != 0) return ws;
        int realCount = Integer.compare(b.realSuccesses, a.realSuccesses);
        if (realCount != 0) return realCount;
        int wsCount = Integer.compare(b.wsSuccesses, a.wsSuccesses);
        if (wsCount != 0) return wsCount;
        int speed = Double.compare(b.speedMbps, a.speedMbps);
        if (speed != 0) return speed;
        int tcp = Double.compare(a.tcpMs, b.tcpMs);
        if (tcp != 0) return tcp;
        return Double.compare(a.tlsMs, b.tlsMs);
    }

    private String buildEdgetunnelBoundNodes(List<Result> results, Config config) throws Exception {
        if (config.uuid.isEmpty()) throw new IllegalArgumentException("请先填写 VLESS UUID");
        if (config.proxyip.isEmpty()) throw new IllegalArgumentException("请先填写要绑定的 ProxyIP");
        List<Result> selected = selectedTopResults(results);
        StringBuilder text = new StringBuilder();
        for (Result r : selected) text.append(formatEdgetunnelBoundNode(r, config)).append("\n");
        return text.toString().trim();
    }

    private List<Result> selectedTopResults(List<Result> results) {
        List<Result> selected = new ArrayList<>();
        for (Result r : results) if (r.fastOk) selected.add(r);
        if (selected.isEmpty()) for (Result r : results) if (r.realOk) selected.add(r);
        if (selected.isEmpty()) for (Result r : results) if (r.handshakeOk) selected.add(r);
        selected.sort(this::compareResults);
        if (selected.size() > 10) selected = new ArrayList<>(selected.subList(0, 10));
        return selected;
    }

    private String formatEdgetunnelBoundNode(Result r, Config config) throws Exception {
        String path = boundProxyPath(config.path, config.proxyip);
        String remark = regionName(r.region)
                + " " + String.format(Locale.US, "%.2fms", r.tcpMs)
                + " PX " + config.proxyip;
        String query = "path=" + urlPart(path)
                + "&security=tls"
                + "&encryption=none"
                + "&insecure=0"
                + "&host=" + urlPart(config.host)
                + "&fp=chrome"
                + "&type=ws"
                + "&allowInsecure=0"
                + "&sni=" + urlPart(config.host);
        return "vless://" + config.uuid + "@" + r.address() + "?" + query + "#" + urlPart(remark);
    }

    private String boundProxyPath(String base, String proxy) {
        String path = base == null || base.trim().isEmpty() ? "/" : base.trim();
        if (!path.startsWith("/")) path = "/" + path;
        String cleanProxy = proxy == null ? "" : proxy.trim();
        if (cleanProxy.isEmpty()) return path;
        int queryIndex = path.indexOf('?');
        String route = queryIndex >= 0 ? path.substring(0, queryIndex) : path;
        String query = queryIndex >= 0 ? path.substring(queryIndex + 1) : "";

        if (route.isEmpty() || route.equals("/")) {
            route = "/proxyip=" + cleanProxy;
        } else if (route.contains("proxyip=")) {
            route = route.replaceFirst("proxyip=[^/?&]+", Matcher.quoteReplacement("proxyip=" + cleanProxy));
        } else {
            route = route.replaceAll("/+$", "") + "/proxyip=" + cleanProxy;
        }

        if (query.isEmpty()) query = "ed=2560";
        else if (!query.matches("(^|&)ed=\\d+(&|$)")) query = query + "&ed=2560";
        return route + "?" + query;
    }

    private void saveTextspaceNote(String title, String content, Config config) throws Exception {
        JSONObject body = new JSONObject();
        body.put("title", title);
        body.put("content", content);

        String noteId = config.textspaceNoteId;
        if (noteId.isEmpty()) noteId = findNoteIdByTitle(title, config);

        JSONObject saved;
        if (!noteId.isEmpty()) {
            try {
                saved = new JSONObject(textspaceRequest("PUT", "/api/notes/" + urlPart(noteId), body.toString(), config));
            } catch (Exception putError) {
                String found = findNoteIdByTitle(title, config);
                if (!found.isEmpty() && !found.equals(noteId)) {
                    saved = new JSONObject(textspaceRequest("PUT", "/api/notes/" + urlPart(found), body.toString(), config));
                } else {
                    saved = new JSONObject(textspaceRequest("POST", "/api/notes", body.toString(), config));
                }
            }
        } else {
            saved = new JSONObject(textspaceRequest("POST", "/api/notes", body.toString(), config));
        }

        String savedId = saved.optString("id", "");
        if (!savedId.isEmpty()) {
            prefs.edit()
                    .putString("textspaceNoteId", savedId)
                    .putString("textspaceTitle", saved.optString("title", title))
                    .apply();
        }
    }

    private String findNoteIdByTitle(String title, Config config) {
        try {
            JSONArray notes;
            try {
                notes = new JSONArray(textspaceRequest("GET", "/api/notes?full=1", null, config));
            } catch (Exception fullError) {
                notes = new JSONArray(textspaceRequest("GET", "/api/notes", null, config));
            }
            for (int i = 0; i < notes.length(); i++) {
                JSONObject item = notes.getJSONObject(i);
                if (title.equals(item.optString("title", ""))) return item.optString("id", "");
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String textspaceRequest(String method, String path, String body, Config config) throws Exception {
        String base = config.textspaceBaseUrl;
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        URL url = new URL(base + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", "Bearer " + config.textspaceToken);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        if (body != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String text = stream == null ? "" : new String(readAll(stream), StandardCharsets.UTF_8);
        if (code < 200 || code >= 300) throw new IOException(readTextspaceError(text, code));
        return text;
    }

    private TextspaceSettings parseTextspaceSettings(boolean strict) {
        String raw = securePref("textspaceCombined", "").trim();
        String baseFallback = pref("textspaceUrl", "").trim();
        String fallbackToken = securePref("textspaceToken", "").trim();
        if (raw.isEmpty()) raw = baseFallback + (fallbackToken.isEmpty() ? "" : " " + fallbackToken);
        String base = "";
        String token = "";
        Matcher urlMatcher = Pattern.compile("https?://[^\\s|,，；;]+").matcher(raw);
        if (urlMatcher.find()) {
            base = urlMatcher.group();
            String before = raw.substring(0, urlMatcher.start());
            String after = raw.substring(urlMatcher.end());
            token = (before + " " + after).trim();
        } else {
            base = raw;
        }
        int tokenIndex = base.indexOf("token=");
        if (tokenIndex >= 0) {
            String queryToken = base.substring(tokenIndex + 6).split("[&#\\s]+", 2)[0].trim();
            if (!queryToken.isEmpty()) token = queryToken;
            base = base.replaceFirst("[?&]token=[^&#\\s]+", "");
        }
        base = base.replaceAll("[,|，；;]+$", "").trim();
        token = token.replaceFirst("^[,|，；;\\s/]+", "").trim();
        if (token.isEmpty() || token.matches("\\*+")) token = fallbackToken;
        if (strict) {
            if (base.isEmpty() || (!base.startsWith("http://") && !base.startsWith("https://"))) {
                throw new IllegalArgumentException("请填写 TextSpace 地址 / 秘钥");
            }
            if (token.isEmpty()) throw new IllegalArgumentException("请在 TextSpace 地址后填写管理员秘钥");
        }
        return new TextspaceSettings(base, token);
    }

    private Set<String> selectedRegions() {
        if (prefs.getBoolean("regionAllV2", false)) return Collections.emptySet();
        LinkedHashSet<String> regions = new LinkedHashSet<>();
        String saved = prefs.getString("regionSelectedV2", "HK,SG,JP,KR,US");
        for (String raw : saved.split(",")) {
            String code = cleanRegionCode(raw);
            if (!code.isEmpty()) regions.add(code);
        }
        return regions;
    }

    private Map<String, String> buildRegionNames() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("HK", "香港");
        map.put("SG", "新加坡");
        map.put("JP", "日本");
        map.put("KR", "韩国");
        map.put("US", "美国");
        map.put("TW", "台湾");
        map.put("DE", "德国");
        map.put("NL", "荷兰");
        map.put("GB", "英国");
        map.put("CA", "加拿大");
        map.put("AU", "澳大利亚");
        map.put("FR", "法国");
        map.put("RU", "俄罗斯");
        map.put("TH", "泰国");
        map.put("VN", "越南");
        map.put("MY", "马来西亚");
        map.put("PH", "菲律宾");
        map.put("ID", "印度尼西亚");
        String custom = prefs.getString("regionCustomItemsV2", "");
        for (String raw : custom.split("\\r?\\n")) {
            String[] parts = raw.split("\\|", 2);
            if (parts.length == 2) map.put(cleanRegionCode(parts[0]), parts[1].trim());
        }
        return map;
    }

    private String normalizeRegion(String region) {
        String raw = region == null ? "" : region.trim();
        String upper = raw.toUpperCase(Locale.ROOT);
        if (upper.startsWith("UK") || upper.contains("英国") || upper.contains("英國")) return "GB";
        if (upper.contains("美國")) return "US";
        if (upper.contains("韓國")) return "KR";
        if (upper.contains("台灣")) return "TW";
        for (Map.Entry<String, String> item : regionNames.entrySet()) {
            String name = item.getValue() == null ? "" : item.getValue();
            if (upper.startsWith(item.getKey()) || (!name.isEmpty() && upper.contains(name.toUpperCase(Locale.ROOT)))) {
                return item.getKey();
            }
        }
        return cleanRegionCode(upper);
    }

    private String regionName(String region) {
        String code = normalizeRegion(region);
        String name = regionNames.get(code);
        if (name != null && !name.isEmpty()) return name;
        return code.isEmpty() ? "未知" : code;
    }

    private void progress(String stage, int done, int total, int from, int to) {
        int safeDone = total <= 0 ? 0 : Math.min(Math.max(done, 0), total);
        int span = Math.max(1, to - from);
        int percent = total <= 0 ? from : from + (int) (safeDone * span / (double) total);
        notifyStatus(stage + " " + safeDone + "/" + total, percent, true);
    }

    private void notifyStatus(String text, int percent, boolean ongoing) {
        Notification notification = buildNotification(text, percent, ongoing);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, notification);
    }

    private Notification buildNotification(String text, int percent, boolean ongoing) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile_cf_boost)
                .setContentTitle("CF优选后台测速")
                .setContentText(text == null || text.isEmpty() ? "测速中" : text)
                .setContentIntent(pendingIntent)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .setShowWhen(false);
        if (ongoing) builder.setProgress(100, Math.max(0, Math.min(100, percent)), false);
        return builder.build();
    }

    private void ensureNotificationChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "CF优选测速",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("显示 CF优选 后台测速进度和保存状态");
        manager.createNotificationChannel(channel);
    }

    private void requestTileRefresh() {
        try {
            TileService.requestListeningState(
                    this,
                    new ComponentName(this, QuickRunTileService.class));
        } catch (Exception ignored) {
        }
    }

    private String pref(String key, String fallback) {
        return prefs.getString(key, fallback);
    }

    private String securePref(String key, String fallback) {
        String encrypted = prefs.getString(SECURE_PREFIX + key, "");
        if (encrypted != null && encrypted.startsWith(ENC_PREFIX)) {
            String decrypted = decryptString(encrypted);
            if (decrypted != null) return decrypted;
        }
        return prefs.getString(key, fallback);
    }

    private void putSecureString(SharedPreferences.Editor editor, String key, String value) {
        String plain = value == null ? "" : value;
        String encrypted = encryptString(plain);
        if (encrypted == null) {
            editor.remove(SECURE_PREFIX + key);
            editor.putString(key, plain);
            return;
        }
        editor.putString(SECURE_PREFIX + key, encrypted);
        editor.remove(key);
    }

    private String encryptString(String plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secureKey());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return ENC_PREFIX
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            return null;
        }
    }

    private String decryptString(String encryptedValue) {
        try {
            String payload = encryptedValue.substring(ENC_PREFIX.length());
            String[] parts = payload.split(":", 2);
            if (parts.length != 2) return null;
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secureKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private SecretKey secureKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);
        if (keyStore.containsAlias(SECURE_KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(SECURE_KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                SECURE_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }

    private String prefRealUrl() {
        String value = prefs.getString("realUrl", DEFAULT_REAL_URL);
        if (value == null || value.trim().isEmpty() || isBadRealUrl(value.trim())) return DEFAULT_REAL_URL;
        return value;
    }

    private boolean isBadRealUrl(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return OLD_REAL_URL.equals(lower) || BAD_REAL_URL.equals(lower);
    }

    private String firstToken(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return "";
        return trimmed.split("[,;\\s]+")[0].trim();
    }

    private double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value == null ? "" : value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String cleanRegionCode(String raw) {
        if (raw == null) return "";
        return raw.trim().replace("#", "").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private String pathWithProxy(String base, String proxy) throws Exception {
        String path = base == null || base.trim().isEmpty() ? "/" : base.trim();
        if (!path.startsWith("/")) path = "/" + path;
        if (proxy == null || proxy.trim().isEmpty()) return path;
        String encoded = URLEncoder.encode(proxy.trim(), "UTF-8");
        if (path.toLowerCase(Locale.ROOT).contains("proxyip=")) {
            return path.replaceFirst("(?i)proxyip=[^/?&#]+", "proxyip=" + Matcher.quoteReplacement(encoded));
        }
        String sep = path.contains("?") ? "&" : "?";
        return path + sep + "proxyip=" + encoded;
    }

    private String urlPart(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8").replace("+", "%20");
    }

    private String readTextspaceError(String text, int code) {
        try {
            JSONObject json = new JSONObject(text);
            String error = json.optString("error", "");
            if (!error.isEmpty()) return error;
        } catch (Exception ignored) {
        }
        return text == null || text.trim().isEmpty() ? "HTTP " + code : text;
    }

    private void writeWsFrame(OutputStream out, byte[] payload) throws IOException {
        out.write(0x82);
        int len = payload.length;
        if (len < 126) {
            out.write(0x80 | len);
        } else if (len < 65536) {
            out.write(0x80 | 126);
            out.write((len >> 8) & 0xff);
            out.write(len & 0xff);
        } else {
            out.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) out.write((len >> (8 * i)) & 0xff);
        }
        byte[] mask = randomBytes(4);
        out.write(mask);
        for (int i = 0; i < payload.length; i++) out.write(payload[i] ^ mask[i % 4]);
        out.flush();
    }

    private byte[] readWsFrame(InputStream in) throws IOException {
        int first = in.read();
        int second = in.read();
        if (first < 0 || second < 0) throw new IOException("ws closed");
        int opcode = first & 0x0f;
        if (opcode == 8) throw new IOException("ws close");
        int len = second & 0x7f;
        if (len == 126) len = (in.read() << 8) | in.read();
        else if (len == 127) {
            long longLen = 0;
            for (int i = 0; i < 8; i++) longLen = (longLen << 8) | in.read();
            if (longLen > Integer.MAX_VALUE) throw new IOException("frame too large");
            len = (int) longLen;
        }
        byte[] mask = null;
        if ((second & 0x80) != 0) mask = readExact(in, 4);
        byte[] payload = readExact(in, len);
        if (mask != null) {
            for (int i = 0; i < payload.length; i++) payload[i] = (byte) (payload[i] ^ mask[i % 4]);
        }
        return payload;
    }

    private byte[] stripVlessHeader(byte[] data) {
        if (data.length < 2) return new byte[0];
        int addon = data[1] & 0xff;
        int offset = 2 + addon;
        if (offset >= data.length) return new byte[0];
        byte[] out = new byte[data.length - offset];
        System.arraycopy(data, offset, out, 0, out.length);
        return out;
    }

    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) return out.size() == 0 ? null : out.toString("ISO-8859-1");
            if (b == '\n') break;
            if (b != '\r') out.write(b);
        }
        return out.toString("ISO-8859-1");
    }

    private int parseStatus(String line) {
        if (line == null) return 0;
        String[] parts = line.split("\\s+");
        if (parts.length >= 2) {
            try {
                return Integer.parseInt(parts[1]);
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private byte[] readExact(InputStream in, int len) throws IOException {
        byte[] data = new byte[len];
        int off = 0;
        while (off < len) {
            int n = in.read(data, off, len - off);
            if (n < 0) throw new IOException("unexpected eof");
            off += n;
        }
        return data;
    }

    private byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private void rememberBodySample(ByteArrayOutputStream sample, byte[] data, int offset, int len) {
        if (sample.size() >= BODY_SNIPPET_BYTES || data == null || len <= 0) return;
        int safeOffset = Math.max(0, offset);
        int safeLen = Math.min(len, data.length - safeOffset);
        if (safeLen <= 0) return;
        int copy = Math.min(safeLen, BODY_SNIPPET_BYTES - sample.size());
        sample.write(data, safeOffset, copy);
    }

    private String sanitizeSnippet(byte[] data) {
        if (data == null || data.length == 0) return "";
        String text = new String(data, StandardCharsets.UTF_8)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (!Character.isISOControl(ch)) out.append(ch);
        }
        return out.toString();
    }

    private byte[] randomBytes(int len) {
        byte[] b = new byte[len];
        random.nextBytes(b);
        return b;
    }

    private int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private long ipv4ToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) throw new IllegalArgumentException("bad ipv4");
        long value = 0;
        for (String part : parts) value = (value << 8) | (Integer.parseInt(part) & 0xff);
        return value & 0xffffffffL;
    }

    private String longToIpv4(long value) {
        return ((value >> 24) & 0xff) + "."
                + ((value >> 16) & 0xff) + "."
                + ((value >> 8) & 0xff) + "."
                + (value & 0xff);
    }

    private double elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000.0;
    }

    private String shortError(Exception e) {
        String name = e.getClass().getSimpleName();
        if (name.toLowerCase(Locale.ROOT).contains("timeout")) return "timeout";
        String message = e.getMessage();
        if (message != null && message.toLowerCase(Locale.ROOT).contains("timeout")) return "timeout";
        return name;
    }

    static class TextspaceSettings {
        final String baseUrl;
        final String token;

        TextspaceSettings(String baseUrl, String token) {
            this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
            this.token = token == null ? "" : token.trim();
        }
    }

    static class Config {
        String source;
        String proxySource;
        String host;
        String path;
        String uuid;
        String proxyip;
        String realUrl;
        String textspaceBaseUrl;
        String textspaceToken;
        String textspaceTitle;
        String textspaceNoteId;
        int timeoutMs;
        int concurrency;
        int candidates;
        int repeats;
        int downloadBytes;
        double minSpeedMbps;
        double maxTcpMs;
        boolean realCheck;
        Set<String> regions;
    }

    static class Target {
        final String host;
        final int port;
        final String region;

        Target(String host, int port, String region) {
            this.host = host;
            this.port = port;
            this.region = region == null ? "" : region;
        }

        String address() {
            return host.contains(":") ? "[" + host + "]:" + port : host + ":" + port;
        }
    }

    static class Result {
        final String host;
        final int port;
        final String region;
        boolean tcpOk;
        boolean handshakeOk;
        boolean realOk;
        boolean fastOk;
        int wsStatus;
        int httpStatus;
        int wsSuccesses;
        int realSuccesses;
        int bytesRead;
        double tcpMs;
        double tlsMs;
        double speedMbps;
        String error = "";

        Result(Target target) {
            host = target.host;
            port = target.port;
            region = target.region;
        }

        Result copy() {
            Result r = new Result(new Target(host, port, region));
            r.tcpOk = tcpOk;
            r.tcpMs = tcpMs;
            return r;
        }

        String address() {
            return host.contains(":") ? "[" + host + "]:" + port : host + ":" + port;
        }
    }

    static class ProxyResult {
        final String host;
        final int port;
        int successes;
        double bestMs;
        String error = "";

        ProxyResult(Target target) {
            host = target.host;
            port = target.port;
        }

        String address() {
            return host.contains(":") ? "[" + host + "]:" + port : host + ":" + port;
        }
    }

    static class RealSpeed {
        int status;
        int bytes;
        double mbps;
        String bodySnippet = "";

        boolean ok() {
            return status >= 200 && status < 300 && bytes >= MIN_REAL_BYTES;
        }
    }
}
