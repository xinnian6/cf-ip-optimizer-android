package com.cfoptimizer.mobile;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SecureRandom random = new SecureRandom();

    private EditText sourceEdit;
    private EditText hostEdit;
    private EditText pathEdit;
    private EditText uuidEdit;
    private EditText proxyEdit;
    private EditText timeoutEdit;
    private EditText concurrencyEdit;
    private EditText candidatesEdit;
    private EditText repeatsEdit;
    private EditText downloadMbEdit;
    private EditText minSpeedEdit;
    private EditText realUrlEdit;
    private CheckBox realCheck;
    private Button startButton;
    private Button copyButton;
    private ProgressBar progress;
    private TextView statusText;
    private TextView resultText;
    private TextView logText;

    private volatile boolean running = false;
    private List<Result> lastResults = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(16));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("CF 手机优选测速");
        title.setTextSize(22);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        sourceEdit = input(root, "IP 数据源 URL 或直接粘贴 IP 列表", "https://zip.cm.edu.kg/all.txt", false);
        hostEdit = input(root, "SNI/Host 域名", "xinnian.us.ci", false);
        pathEdit = input(root, "WS 路径", "/", false);
        uuidEdit = input(root, "VLESS UUID", "", false);
        proxyEdit = input(root, "组合测试 ProxyIP（只填一个，可留空）", "", false);
        realUrlEdit = input(root, "真实测速 URL", "http://cachefly.cachefly.net/10mb.test", false);

        LinearLayout row1 = row(root);
        timeoutEdit = smallInput(row1, "超时秒", "8");
        concurrencyEdit = smallInput(row1, "并发", "32");
        candidatesEdit = smallInput(row1, "候选", "100");

        LinearLayout row2 = row(root);
        repeatsEdit = smallInput(row2, "复测", "2");
        downloadMbEdit = smallInput(row2, "下载MiB", "2");
        minSpeedEdit = smallInput(row2, "最低Mbps", "80");

        realCheck = new CheckBox(this);
        realCheck.setText("真实节点测速：VLESS 下载验证");
        realCheck.setChecked(true);
        root.addView(realCheck);

        LinearLayout buttons = row(root);
        startButton = new Button(this);
        startButton.setText("开始测速");
        startButton.setOnClickListener(v -> startScan());
        buttons.addView(startButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        copyButton = new Button(this);
        copyButton.setText("复制结果");
        copyButton.setOnClickListener(v -> copyResults());
        buttons.addView(copyButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        root.addView(progress);

        statusText = new TextView(this);
        statusText.setText("就绪");
        statusText.setPadding(0, dp(6), 0, dp(6));
        root.addView(statusText);

        TextView resultTitle = new TextView(this);
        resultTitle.setText("测速结果");
        resultTitle.setTextSize(18);
        root.addView(resultTitle);

        resultText = new TextView(this);
        resultText.setTextIsSelectable(true);
        resultText.setTextSize(13);
        root.addView(resultText);

        TextView logTitle = new TextView(this);
        logTitle.setText("运行日志");
        logTitle.setTextSize(18);
        logTitle.setPadding(0, dp(10), 0, 0);
        root.addView(logTitle);

        logText = new TextView(this);
        logText.setTextIsSelectable(true);
        logText.setTextSize(12);
        root.addView(logText);

        setContentView(scroll);
    }

    private EditText input(LinearLayout root, String label, String value, boolean number) {
        TextView text = new TextView(this);
        text.setText(label);
        root.addView(text);
        EditText edit = new EditText(this);
        edit.setSingleLine(false);
        edit.setMinLines(label.contains("数据源") ? 2 : 1);
        edit.setText(value);
        if (number) edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        root.addView(edit);
        return edit;
    }

    private LinearLayout row(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row);
        return row;
    }

    private EditText smallInput(LinearLayout row, String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView text = new TextView(this);
        text.setText(label);
        box.addView(text);
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edit.setText(value);
        box.addView(edit);
        row.addView(box, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return edit;
    }

    private void startScan() {
        if (running) return;
        running = true;
        startButton.setEnabled(false);
        resultText.setText("");
        logText.setText("");
        lastResults = new ArrayList<>();
        progress.setProgress(0);
        status("正在准备");

        new Thread(() -> {
            try {
                Config config = readConfig();
                List<Target> targets = loadTargets(sourceEdit.getText().toString());
                log("解析目标 " + targets.size() + " 条");
                if (targets.isEmpty()) {
                    toast("没有解析到 IP");
                    return;
                }

                List<Result> tcpOk = runTcpScan(targets, config);
                tcpOk.sort(Comparator.comparingDouble(r -> r.tcpMs));
                if (tcpOk.size() > config.candidates) {
                    tcpOk = new ArrayList<>(tcpOk.subList(0, config.candidates));
                }
                log("TCP 初筛可用 " + tcpOk.size() + " 条，进入深度测速");

                List<Result> checked = runWsScan(tcpOk, config);
                checked.sort((a, b) -> {
                    int ok = Boolean.compare(b.ok, a.ok);
                    if (ok != 0) return ok;
                    int speed = Double.compare(b.speedMbps, a.speedMbps);
                    if (speed != 0) return speed;
                    return Double.compare(a.tcpMs, b.tcpMs);
                });
                lastResults = checked;
                ui.post(() -> showResults(checked, config));
            } catch (Exception e) {
                log("错误: " + e.getMessage());
                toast("测速失败: " + e.getMessage());
            } finally {
                running = false;
                ui.post(() -> startButton.setEnabled(true));
            }
        }).start();
    }

    private Config readConfig() {
        Config c = new Config();
        c.host = hostEdit.getText().toString().trim();
        c.path = pathEdit.getText().toString().trim();
        c.uuid = uuidEdit.getText().toString().trim();
        c.proxyip = proxyEdit.getText().toString().trim().split("\\s+")[0];
        c.realUrl = realUrlEdit.getText().toString().trim();
        c.timeoutMs = Math.max(1000, (int) (Double.parseDouble(timeoutEdit.getText().toString()) * 1000));
        c.concurrency = Math.max(1, Integer.parseInt(concurrencyEdit.getText().toString()));
        c.candidates = Math.max(1, Integer.parseInt(candidatesEdit.getText().toString()));
        c.repeats = Math.max(1, Integer.parseInt(repeatsEdit.getText().toString()));
        c.downloadBytes = Math.max(1, (int) (Double.parseDouble(downloadMbEdit.getText().toString()) * 1024 * 1024));
        c.minSpeedMbps = Math.max(0, Double.parseDouble(minSpeedEdit.getText().toString()));
        c.realCheck = realCheck.isChecked() && !c.uuid.isEmpty();
        if (c.host.isEmpty()) throw new IllegalArgumentException("请填写 SNI/Host 域名");
        if (c.realCheck) UUID.fromString(c.uuid);
        return c;
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
                if (n % 20 == 0 || n == targets.size()) progress("tcp", n, targets.size());
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

    private List<Result> runWsScan(List<Result> candidates, Config config) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(config.concurrency);
        List<Future<Result>> futures = new ArrayList<>();
        AtomicInteger done = new AtomicInteger();
        for (Result candidate : candidates) {
            futures.add(pool.submit(() -> {
                Result r = candidate.copy();
                for (int i = 0; i < config.repeats; i++) {
                    probeWs(r, config);
                }
                r.successRate = r.successes / (double) config.repeats;
                r.ok = r.successes == config.repeats && (!config.realCheck || r.speedMbps >= config.minSpeedMbps);
                int n = done.incrementAndGet();
                progress("ws", n, candidates.size());
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
                    + "User-Agent: CFMobileOptimizer/1.0\r\n\r\n";
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
                r.wsOk = true;
                if (config.realCheck) {
                    RealSpeed speed = vlessHttpSpeed(in, out, config);
                    r.httpStatus = speed.status;
                    r.bytesRead += speed.bytes;
                    if (speed.ok()) {
                        r.successes++;
                        r.speedMbps = r.speedMbps == 0 ? speed.mbps : Math.min(r.speedMbps, speed.mbps);
                        r.error = "";
                    } else {
                        r.error = speed.status > 0 ? "HTTP " + speed.status : "真实测速无响应";
                    }
                } else {
                    r.successes++;
                    r.error = "";
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
                continue;
            }
            response.write(frame);
            byte[] data = response.toByteArray();
            int headerEnd = indexOf(data, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            if (headerEnd < 0) continue;
            String head = new String(data, 0, headerEnd, StandardCharsets.ISO_8859_1);
            String first = head.split("\\r?\\n", 2)[0];
            status = parseStatus(first);
            body += data.length - headerEnd - 4;
            headersDone = true;
        }
        double seconds = Math.max(elapsedMs(start) / 1000.0, 0.001);
        RealSpeed speed = new RealSpeed();
        speed.status = status;
        speed.bytes = body;
        speed.mbps = body * 8.0 / seconds / 1_000_000.0;
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
                + "User-Agent: CFMobileOptimizer/1.0\r\n"
                + "Accept: */*\r\n"
                + "Connection: close\r\n\r\n";
        out.write(request.getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private List<Target> loadTargets(String source) throws Exception {
        String text = source.trim();
        if (text.startsWith("http://") || text.startsWith("https://")) {
            HttpURLConnection conn = (HttpURLConnection) new URL(text).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", "CFMobileOptimizer/1.0");
            try (InputStream in = conn.getInputStream()) {
                text = new String(readAll(in), StandardCharsets.UTF_8);
            }
        }
        List<Target> targets = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            for (String token : line.split("[,;\\s]+")) {
                List<Target> parsed = parseTargets(token);
                for (Target t : parsed) {
                    String key = t.host + ":" + t.port;
                    if (seen.add(key)) targets.add(t);
                }
            }
        }
        return targets;
    }

    private List<Target> parseTargets(String token) {
        List<Target> out = new ArrayList<>();
        try {
            String value = token.trim();
            if (value.isEmpty()) return out;
            String region = "";
            int hash = value.indexOf('#');
            if (hash >= 0) {
                region = value.substring(hash + 1).trim().toUpperCase(Locale.ROOT);
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
            } else {
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
            for (long ip = start; ip <= end; ip++) {
                out.add(new Target(longToIpv4(ip), port, region));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private long ipv4ToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) throw new IllegalArgumentException("bad ipv4");
        long value = 0;
        for (String part : parts) {
            value = (value << 8) | (Integer.parseInt(part) & 0xff);
        }
        return value & 0xffffffffL;
    }

    private String longToIpv4(long value) {
        return ((value >> 24) & 0xff) + "."
                + ((value >> 16) & 0xff) + "."
                + ((value >> 8) & 0xff) + "."
                + (value & 0xff);
    }

    private void showResults(List<Result> results, Config config) {
        StringBuilder out = new StringBuilder();
        int best = 0;
        for (Result r : results) {
            if (r.ok) best++;
            out.append(r.address())
                    .append("#").append(regionName(r.region))
                    .append(" ")
                    .append(String.format(Locale.US, "%.2fms", r.tcpMs))
                    .append(" ")
                    .append(String.format(Locale.US, "%.2fMbps", r.speedMbps))
                    .append(" WS").append(r.wsStatus)
                    .append(" 成功").append(r.successes).append("次/共").append(config.repeats).append("次")
                    .append(r.proxyipText(config))
                    .append(r.error.isEmpty() ? "" : " 错误:" + r.error)
                    .append("\n");
        }
        resultText.setText(out.toString());
        status("完成：真实可用 " + best + " / " + results.size());
        progress.setProgress(100);
    }

    private void copyResults() {
        List<Result> ok = new ArrayList<>();
        for (Result r : lastResults) {
            if (r.ok) ok.add(r);
        }
        if (ok.isEmpty()) {
            Toast.makeText(this, "没有可复制的成功结果", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder text = new StringBuilder();
        for (Result r : ok) {
            text.append(r.address())
                    .append("#").append(regionName(r.region))
                    .append(" ")
                    .append(String.format(Locale.US, "%.2fms", r.tcpMs))
                    .append("\n");
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("cf-results", text.toString().trim()));
        Toast.makeText(this, "已复制 " + ok.size() + " 条", Toast.LENGTH_SHORT).show();
    }

    private String pathWithProxy(String base, String proxy) throws Exception {
        String path = base == null || base.trim().isEmpty() ? "/" : base.trim();
        if (!path.startsWith("/")) path = "/" + path;
        if (proxy == null || proxy.trim().isEmpty()) return path;
        String sep = path.contains("?") ? "&" : "?";
        return path + sep + "proxyip=" + URLEncoder.encode(proxy.trim(), "UTF-8");
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

    private double elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000.0;
    }

    private void progress(String stage, int done, int total) {
        int percent = total <= 0 ? 0 : (int) (done * 100.0 / total);
        ui.post(() -> {
            progress.setProgress(percent);
            statusText.setText(stage + " " + done + "/" + total);
        });
    }

    private void status(String text) {
        ui.post(() -> statusText.setText(text));
    }

    private void log(String text) {
        ui.post(() -> logText.append(text + "\n"));
    }

    private void toast(String text) {
        ui.post(() -> Toast.makeText(this, text, Toast.LENGTH_SHORT).show());
    }

    private String shortError(Exception e) {
        String name = e.getClass().getSimpleName();
        if (name.toLowerCase(Locale.ROOT).contains("timeout")) return "timeout";
        return name;
    }

    private String regionName(String region) {
        String code = region == null ? "" : region.toUpperCase(Locale.ROOT);
        switch (code) {
            case "HK": return "香港";
            case "JP": return "日本";
            case "KR": return "韩国";
            case "SG": return "新加坡";
            case "TW": return "台湾";
            case "US": return "美国";
            default: return code.isEmpty() ? "未知" : code;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    static class Config {
        String host;
        String path;
        String uuid;
        String proxyip;
        String realUrl;
        int timeoutMs;
        int concurrency;
        int candidates;
        int repeats;
        int downloadBytes;
        double minSpeedMbps;
        boolean realCheck;
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
    }

    static class Result {
        final String host;
        final int port;
        final String region;
        boolean tcpOk;
        boolean wsOk;
        boolean ok;
        int wsStatus;
        int httpStatus;
        int successes;
        int bytesRead;
        double tcpMs;
        double tlsMs;
        double speedMbps;
        double successRate;
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

        String proxyipText(Config config) {
            return config.proxyip == null || config.proxyip.isEmpty() ? "" : " proxyip=" + config.proxyip;
        }
    }

    static class RealSpeed {
        int status;
        int bytes;
        double mbps;

        boolean ok() {
            return status >= 200 && status < 400 && bytes > 0;
        }
    }
}
