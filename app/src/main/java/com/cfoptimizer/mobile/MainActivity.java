package com.cfoptimizer.mobile;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
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
import java.util.LinkedHashSet;
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

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String PREFS = "cf_optimizer_mobile";
    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int GREEN = Color.rgb(22, 163, 74);
    private static final int ORANGE = Color.rgb(234, 88, 12);
    private static final int BG = Color.rgb(246, 247, 251);
    private static final int CARD = Color.WHITE;
    private static final int TEXT = Color.rgb(17, 24, 39);
    private static final int MUTED = Color.rgb(100, 116, 139);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SecureRandom random = new SecureRandom();

    private SharedPreferences prefs;
    private EditText sourceEdit;
    private EditText proxySourceEdit;
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
    private EditText hashPrefixEdit;
    private EditText lineSuffixEdit;
    private EditText textspaceUrlEdit;
    private EditText textspaceTokenEdit;
    private EditText textspaceNoteIdEdit;
    private EditText textspaceTitleEdit;
    private EditText textspaceContentEdit;
    private CheckBox realCheck;
    private CheckBox allRegionCheck;
    private CheckBox hkCheck;
    private CheckBox sgCheck;
    private CheckBox jpCheck;
    private CheckBox krCheck;
    private CheckBox usCheck;
    private CheckBox twCheck;
    private Button startButton;
    private Button copyButton;
    private Button proxyButton;
    private Button copyProxyTopButton;
    private Button loadNoteButton;
    private Button fillResultButton;
    private Button saveNoteButton;
    private Button shareNoteButton;
    private ProgressBar progress;
    private TextView statusText;
    private TextView resultText;
    private TextView proxyResultText;
    private TextView logText;
    private LinearLayout proxyButtonList;

    private volatile boolean running = false;
    private volatile boolean textspaceRunning = false;
    private List<Result> lastResults = new ArrayList<>();
    private List<ProxyResult> lastProxyResults = new ArrayList<>();
    private NoteDetail currentNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setupWindow();
        buildUi();
    }

    private void setupWindow() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(18));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("CF 手机优选");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("按地区筛选 IP，验证 WS 握手和真实下载，也可以单独测试 ProxyIP。");
        sub.setTextSize(13);
        sub.setTextColor(MUTED);
        sub.setPadding(0, dp(3), 0, dp(10));
        root.addView(sub);

        LinearLayout sourceCard = card(root, "IP 数据源");
        sourceEdit = input(sourceCard, "网络地址或直接粘贴 IP 列表", pref("source", "https://zip.cm.edu.kg/all.txt"), 4, false);
        addRegionFilters(sourceCard);

        LinearLayout basicCard = card(root, "节点参数");
        hostEdit = input(basicCard, "SNI / Host 域名", pref("host", "xinnian.us.ci"), 1, false);
        pathEdit = input(basicCard, "WS 路径", pref("path", "/"), 1, false);
        uuidEdit = input(basicCard, "VLESS UUID（不填则只验证 WS 握手）", pref("uuid", ""), 1, false);
        proxyEdit = input(basicCard, "组合测试 ProxyIP（可留空，只支持一个）", pref("proxyip", ""), 1, false);
        realUrlEdit = input(basicCard, "真实下载 URL", pref("realUrl", "http://cachefly.cachefly.net/10mb.test"), 1, false);
        hashPrefixEdit = input(basicCard, "复制名称前缀（放在 # 后面，可留空）", pref("hashPrefix", ""), 1, false);
        lineSuffixEdit = input(basicCard, "复制行尾追加（放在每行最后，可留空）", pref("lineSuffix", ""), 1, false);

        realCheck = new CheckBox(this);
        realCheck.setText("开启真实下载测速（需要 UUID）");
        realCheck.setChecked(prefs.getBoolean("realCheck", true));
        basicCard.addView(realCheck);

        LinearLayout paramCard = card(root, "测速参数");
        LinearLayout row1 = row(paramCard);
        timeoutEdit = smallInput(row1, "超时秒", pref("timeout", "8"));
        concurrencyEdit = smallInput(row1, "并发", pref("concurrency", "32"));
        candidatesEdit = smallInput(row1, "候选数", pref("candidates", "100"));

        LinearLayout row2 = row(paramCard);
        repeatsEdit = smallInput(row2, "复测次数", pref("repeats", "2"));
        downloadMbEdit = smallInput(row2, "单 IP 下载 MiB", pref("downloadMb", "2"));
        minSpeedEdit = smallInput(row2, "最低 Mbps", pref("minSpeed", "80"));

        LinearLayout actionCard = card(root, "操作");
        LinearLayout buttons = row(actionCard);
        startButton = button("开始测速", BLUE);
        startButton.setOnClickListener(v -> startScan());
        buttons.addView(startButton, new LinearLayout.LayoutParams(0, dp(46), 1));

        copyButton = button("复制测速结果", GREEN);
        copyButton.setOnClickListener(v -> copyResults());
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        copyLp.leftMargin = dp(8);
        buttons.addView(copyButton, copyLp);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10));
        progressLp.topMargin = dp(10);
        actionCard.addView(progress, progressLp);

        statusText = new TextView(this);
        statusText.setText("就绪");
        statusText.setTextColor(MUTED);
        statusText.setPadding(0, dp(7), 0, 0);
        actionCard.addView(statusText);

        LinearLayout proxyCard = card(root, "ProxyIP 稳定性");
        proxySourceEdit = input(proxyCard, "ProxyIP 数据源（网络地址或直接粘贴列表）", pref("proxySource", ""), 3, false);
        LinearLayout proxyButtons = row(proxyCard);
        proxyButton = button("测试 ProxyIP", ORANGE);
        proxyButton.setOnClickListener(v -> startProxyTest());
        proxyButtons.addView(proxyButton, new LinearLayout.LayoutParams(0, dp(46), 1));

        copyProxyTopButton = button("复制前十", GREEN);
        copyProxyTopButton.setOnClickListener(v -> copyProxyTop());
        LinearLayout.LayoutParams proxyCopyLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        proxyCopyLp.leftMargin = dp(8);
        proxyButtons.addView(copyProxyTopButton, proxyCopyLp);

        proxyButtonList = new LinearLayout(this);
        proxyButtonList.setOrientation(LinearLayout.VERTICAL);
        proxyCard.addView(proxyButtonList);

        proxyResultText = monoText();
        proxyCard.addView(proxyResultText);

        LinearLayout resultCard = card(root, "测速结果");
        resultText = monoText();
        resultCard.addView(resultText);

        LinearLayout publishCard = card(root, "结果发布");
        textspaceUrlEdit = input(publishCard, "TextSpace Worker 地址", pref("textspaceUrl", ""), 1, false);
        textspaceTokenEdit = input(publishCard, "管理员密钥", pref("textspaceToken", ""), 1, false);
        textspaceTokenEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        textspaceNoteIdEdit = input(publishCard, "文本 ID（留空时读取列表第一条或新建）", pref("textspaceNoteId", ""), 1, false);
        textspaceTitleEdit = input(publishCard, "文本标题", pref("textspaceTitle", "CF 手机优选结果"), 1, false);
        textspaceContentEdit = input(publishCard, "文本内容（可编辑，保存后分享 URL 就会更新）", pref("textspaceContent", ""), 6, false);

        LinearLayout pubRow1 = row(publishCard);
        loadNoteButton = button("获取文本", BLUE);
        loadNoteButton.setOnClickListener(v -> loadTextspaceNote());
        pubRow1.addView(loadNoteButton, new LinearLayout.LayoutParams(0, dp(44), 1));

        fillResultButton = button("填入结果", ORANGE);
        fillResultButton.setOnClickListener(v -> fillTextspaceWithResults());
        LinearLayout.LayoutParams fillLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        fillLp.leftMargin = dp(8);
        pubRow1.addView(fillResultButton, fillLp);

        LinearLayout pubRow2 = row(publishCard);
        saveNoteButton = button("保存文本", GREEN);
        saveNoteButton.setOnClickListener(v -> saveTextspaceNote(false));
        pubRow2.addView(saveNoteButton, new LinearLayout.LayoutParams(0, dp(44), 1));

        shareNoteButton = button("保存并分享", BLUE);
        shareNoteButton.setOnClickListener(v -> saveTextspaceNote(true));
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        shareLp.leftMargin = dp(8);
        pubRow2.addView(shareNoteButton, shareLp);

        LinearLayout logCard = card(root, "运行日志");
        logText = monoText();
        logCard.addView(logText);

        setContentView(scroll);
    }

    private LinearLayout card(LinearLayout root, String titleText) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(8));
        bg.setStroke(1, Color.rgb(226, 232, 240));
        box.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = dp(10);
        root.addView(box, lp);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        title.setPadding(0, 0, 0, dp(8));
        box.addView(title);
        return box;
    }

    private EditText input(LinearLayout root, String label, String value, int minLines, boolean number) {
        TextView text = label(label);
        root.addView(text);
        EditText edit = new EditText(this);
        edit.setSingleLine(minLines <= 1);
        edit.setMinLines(minLines);
        edit.setText(value);
        edit.setTextSize(14);
        edit.setTextColor(TEXT);
        edit.setSelectAllOnFocus(false);
        edit.setPadding(dp(10), dp(6), dp(10), dp(6));
        edit.setBackground(fieldBg());
        if (number) edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        root.addView(edit, fieldLp());
        return edit;
    }

    private TextView label(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(13);
        text.setTextColor(MUTED);
        text.setPadding(0, dp(5), 0, 0);
        return text;
    }

    private LinearLayout.LayoutParams fieldLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(5);
        return lp;
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
        TextView text = label(label);
        box.addView(text);
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edit.setText(value);
        edit.setTextSize(14);
        edit.setTextColor(TEXT);
        edit.setGravity(Gravity.CENTER_VERTICAL);
        edit.setPadding(dp(8), 0, dp(8), 0);
        edit.setBackground(fieldBg());
        box.addView(edit, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.rightMargin = dp(6);
        row.addView(box, lp);
        return edit;
    }

    private Button button(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(8));
        b.setBackground(bg);
        return b;
    }

    private GradientDrawable fieldBg() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(248, 250, 252));
        bg.setCornerRadius(dp(7));
        bg.setStroke(1, Color.rgb(203, 213, 225));
        return bg;
    }

    private TextView monoText() {
        TextView view = new TextView(this);
        view.setTextIsSelectable(true);
        view.setTextSize(12);
        view.setTextColor(TEXT);
        view.setTypeface(Typeface.MONOSPACE);
        view.setPadding(0, dp(4), 0, 0);
        return view;
    }

    private void addRegionFilters(LinearLayout root) {
        TextView label = label("区域筛选（全不选时测试全部）");
        root.addView(label);

        LinearLayout rowA = row(root);
        allRegionCheck = regionCheck(rowA, "全部", prefs.getBoolean("regionAll", true));
        hkCheck = regionCheck(rowA, "香港", prefs.getBoolean("regionHK", true));
        sgCheck = regionCheck(rowA, "新加坡", prefs.getBoolean("regionSG", true));
        jpCheck = regionCheck(rowA, "日本", prefs.getBoolean("regionJP", true));

        LinearLayout rowB = row(root);
        krCheck = regionCheck(rowB, "韩国", prefs.getBoolean("regionKR", true));
        usCheck = regionCheck(rowB, "美国", prefs.getBoolean("regionUS", true));
        twCheck = regionCheck(rowB, "台湾", prefs.getBoolean("regionTW", true));

        allRegionCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) setRegionChecks(isChecked);
        });
        CompoundButton.OnCheckedChangeListener childListener = (buttonView, isChecked) -> {
            if (buttonView.isPressed() && !isChecked) allRegionCheck.setChecked(false);
        };
        hkCheck.setOnCheckedChangeListener(childListener);
        sgCheck.setOnCheckedChangeListener(childListener);
        jpCheck.setOnCheckedChangeListener(childListener);
        krCheck.setOnCheckedChangeListener(childListener);
        usCheck.setOnCheckedChangeListener(childListener);
        twCheck.setOnCheckedChangeListener(childListener);
    }

    private CheckBox regionCheck(LinearLayout row, String text, boolean checked) {
        CheckBox cb = new CheckBox(this);
        cb.setText(text);
        cb.setTextSize(13);
        cb.setChecked(checked);
        row.addView(cb, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return cb;
    }

    private void setRegionChecks(boolean checked) {
        hkCheck.setChecked(checked);
        sgCheck.setChecked(checked);
        jpCheck.setChecked(checked);
        krCheck.setChecked(checked);
        usCheck.setChecked(checked);
        twCheck.setChecked(checked);
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
                saveConfig();
                List<Target> targets = loadTargets(sourceEdit.getText().toString(), config.regions, true);
                log("解析目标 " + targets.size() + " 条");
                if (targets.isEmpty()) {
                    toast("没有解析到符合区域的 IP");
                    return;
                }

                List<Result> tcpOk = runTcpScan(targets, config);
                tcpOk.sort(Comparator.comparingDouble(r -> r.tcpMs));
                if (tcpOk.size() > config.candidates) {
                    tcpOk = new ArrayList<>(tcpOk.subList(0, config.candidates));
                }
                log("TCP 可连接 " + tcpOk.size() + " 条，进入 WS / 真实测速");

                List<Result> checked = runWsScan(tcpOk, config);
                checked.sort((a, b) -> {
                    int fast = Boolean.compare(b.fastOk, a.fastOk);
                    if (fast != 0) return fast;
                    int real = Boolean.compare(b.realOk, a.realOk);
                    if (real != 0) return real;
                    int ws = Boolean.compare(b.handshakeOk, a.handshakeOk);
                    if (ws != 0) return ws;
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

    private void startProxyTest() {
        if (running) return;
        running = true;
        proxyButton.setEnabled(false);
        proxyResultText.setText("");
        proxyButtonList.removeAllViews();
        lastProxyResults = new ArrayList<>();
        progress.setProgress(0);
        status("正在测试 ProxyIP");

        new Thread(() -> {
            try {
                Config config = readConfig();
                saveConfig();
                List<Target> proxies = loadTargets(proxySourceEdit.getText().toString(), config.regions, true);
                if (proxies.isEmpty()) {
                    toast("没有解析到 ProxyIP");
                    return;
                }
                if (proxies.size() > config.candidates) {
                    proxies = evenSample(proxies, config.candidates);
                }
                log("解析 ProxyIP " + proxies.size() + " 条");
                List<ProxyResult> results = runProxyTcpScan(proxies, config);
                results.sort((a, b) -> {
                    int ok = Integer.compare(b.successes, a.successes);
                    if (ok != 0) return ok;
                    return Double.compare(a.bestMs, b.bestMs);
                });
                lastProxyResults = results;
                ui.post(() -> showProxyResults(results, config));
            } catch (Exception e) {
                log("ProxyIP 错误: " + e.getMessage());
                toast("ProxyIP 测试失败: " + e.getMessage());
            } finally {
                running = false;
                ui.post(() -> proxyButton.setEnabled(true));
            }
        }).start();
    }

    private Config readConfig() {
        Config c = new Config();
        c.host = hostEdit.getText().toString().trim();
        c.path = pathEdit.getText().toString().trim();
        c.uuid = uuidEdit.getText().toString().trim();
        c.proxyip = firstToken(proxyEdit.getText().toString().trim());
        c.realUrl = realUrlEdit.getText().toString().trim();
        c.hashPrefix = hashPrefixEdit.getText().toString().trim();
        c.lineSuffix = lineSuffixEdit.getText().toString().trim();
        c.timeoutMs = Math.max(500, (int) (parseDouble(timeoutEdit, 8) * 1000));
        c.concurrency = clamp((int) parseDouble(concurrencyEdit, 32), 1, 256);
        c.candidates = clamp((int) parseDouble(candidatesEdit, 100), 1, 5000);
        c.repeats = clamp((int) parseDouble(repeatsEdit, 2), 1, 10);
        c.downloadBytes = Math.max(1, (int) (parseDouble(downloadMbEdit, 2) * 1024 * 1024));
        c.minSpeedMbps = Math.max(0, parseDouble(minSpeedEdit, 80));
        c.realCheck = realCheck.isChecked() && !c.uuid.isEmpty();
        c.regions = selectedRegions();
        if (c.host.isEmpty()) throw new IllegalArgumentException("请填写 SNI/Host 域名");
        if (realCheck.isChecked() && !c.uuid.isEmpty()) UUID.fromString(c.uuid);
        if (realCheck.isChecked() && c.uuid.isEmpty()) log("未填写 UUID，本次只验证 WS 握手，不做真实下载测速");
        return c;
    }

    private double parseDouble(EditText edit, double fallback) {
        try {
            return Double.parseDouble(edit.getText().toString().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String firstToken(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return "";
        return trimmed.split("[,;\\s]+")[0].trim();
    }

    private Set<String> selectedRegions() {
        if (allRegionCheck.isChecked()) return Collections.emptySet();
        Set<String> regions = new LinkedHashSet<>();
        if (hkCheck.isChecked()) regions.add("HK");
        if (sgCheck.isChecked()) regions.add("SG");
        if (jpCheck.isChecked()) regions.add("JP");
        if (krCheck.isChecked()) regions.add("KR");
        if (usCheck.isChecked()) regions.add("US");
        if (twCheck.isChecked()) regions.add("TW");
        return regions;
    }

    private void saveConfig() {
        prefs.edit()
                .putString("source", sourceEdit.getText().toString())
                .putString("proxySource", proxySourceEdit.getText().toString())
                .putString("host", hostEdit.getText().toString())
                .putString("path", pathEdit.getText().toString())
                .putString("uuid", uuidEdit.getText().toString())
                .putString("proxyip", proxyEdit.getText().toString())
                .putString("realUrl", realUrlEdit.getText().toString())
                .putString("hashPrefix", hashPrefixEdit.getText().toString())
                .putString("lineSuffix", lineSuffixEdit.getText().toString())
                .putString("textspaceUrl", textspaceUrlEdit.getText().toString())
                .putString("textspaceToken", textspaceTokenEdit.getText().toString())
                .putString("textspaceNoteId", textspaceNoteIdEdit.getText().toString())
                .putString("textspaceTitle", textspaceTitleEdit.getText().toString())
                .putString("textspaceContent", textspaceContentEdit.getText().toString())
                .putString("timeout", timeoutEdit.getText().toString())
                .putString("concurrency", concurrencyEdit.getText().toString())
                .putString("candidates", candidatesEdit.getText().toString())
                .putString("repeats", repeatsEdit.getText().toString())
                .putString("downloadMb", downloadMbEdit.getText().toString())
                .putString("minSpeed", minSpeedEdit.getText().toString())
                .putBoolean("realCheck", realCheck.isChecked())
                .putBoolean("regionAll", allRegionCheck.isChecked())
                .putBoolean("regionHK", hkCheck.isChecked())
                .putBoolean("regionSG", sgCheck.isChecked())
                .putBoolean("regionJP", jpCheck.isChecked())
                .putBoolean("regionKR", krCheck.isChecked())
                .putBoolean("regionUS", usCheck.isChecked())
                .putBoolean("regionTW", twCheck.isChecked())
                .apply();
    }

    private String pref(String key, String fallback) {
        return prefs.getString(key, fallback);
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
                if (n % 20 == 0 || n == targets.size()) progress("TCP 初筛", n, targets.size());
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
                progress("ProxyIP", n, proxies.size());
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
                for (int i = 0; i < config.repeats; i++) {
                    probeWs(r, config);
                }
                r.handshakeOk = r.wsSuccesses > 0;
                r.realOk = config.realCheck ? r.realSuccesses > 0 : r.handshakeOk;
                r.fastOk = r.realOk && (!config.realCheck || r.speedMbps >= config.minSpeedMbps);
                int n = done.incrementAndGet();
                progress("WS / 真实测速", n, candidates.size());
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
                    + "User-Agent: CFMobileOptimizer/1.1\r\n\r\n";
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
                        r.speedMbps = Math.max(r.speedMbps, speed.mbps);
                        r.error = "";
                    } else {
                        r.error = speed.status > 0 ? "真实测速 HTTP " + speed.status : "真实测速无数据";
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
                + "User-Agent: CFMobileOptimizer/1.1\r\n"
                + "Accept: */*\r\n"
                + "Connection: close\r\n\r\n";
        out.write(request.getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private List<Target> loadTargets(String source, Set<String> regionFilter, boolean filterByRegion) throws Exception {
        String text = source.trim();
        if (text.startsWith("http://") || text.startsWith("https://")) {
            HttpURLConnection conn = (HttpURLConnection) new URL(text).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "CFMobileOptimizer/1.1");
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

    private List<Target> evenSample(List<Target> input, int limit) {
        if (input.size() <= limit) return input;
        List<Target> out = new ArrayList<>();
        double step = input.size() / (double) limit;
        for (int i = 0; i < limit; i++) {
            out.add(input.get((int) Math.floor(i * step)));
        }
        return out;
    }

    private List<Target> parseTargets(String token) {
        List<Target> out = new ArrayList<>();
        try {
            String value = token.trim();
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
        int ws = 0;
        int real = 0;
        int fast = 0;
        for (Result r : results) {
            if (r.handshakeOk) ws++;
            if (r.realOk) real++;
            if (r.fastOk) fast++;
            out.append(r.address())
                    .append("#").append(regionName(r.region))
                    .append(" ")
                    .append(String.format(Locale.US, "%.2fms", r.tcpMs))
                    .append(" TLS ").append(String.format(Locale.US, "%.2fms", r.tlsMs))
                    .append(" ");
            if (config.realCheck) {
                out.append(String.format(Locale.US, "%.2fMbps", r.speedMbps))
                        .append(" 真实").append(r.realSuccesses).append("/").append(config.repeats);
            } else {
                out.append("未真实测速");
            }
            out.append(" WS").append(r.wsStatus)
                    .append(" 握手").append(r.wsSuccesses).append("/").append(config.repeats);
            if (!config.proxyip.isEmpty()) out.append(" ProxyIP=").append(config.proxyip);
            if (!r.error.isEmpty()) out.append(" 错误:").append(r.error);
            out.append("\n");
        }
        resultText.setText(out.toString());
        status("完成：WS成功 " + ws + " / 真实可用 " + real + " / 高速达标 " + fast + " / 总数 " + results.size());
        progress.setProgress(100);
    }

    private void showProxyResults(List<ProxyResult> results, Config config) {
        proxyButtonList.removeAllViews();
        StringBuilder out = new StringBuilder();
        int limit = Math.min(10, results.size());
        for (int i = 0; i < limit; i++) {
            ProxyResult r = results.get(i);
            String line = (i + 1) + ". " + r.address()
                    + " 成功" + r.successes + "/" + config.repeats
                    + " " + (r.bestMs > 0 ? String.format(Locale.US, "%.2fms", r.bestMs) : "不可连")
                    + (r.error.isEmpty() ? "" : " " + r.error);
            out.append(line).append("\n");

            Button copy = button("复制第" + (i + 1) + "名  " + r.address(), GREEN);
            final String value = r.address();
            copy.setOnClickListener(v -> copyText("proxyip", value, "已复制 " + value));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42));
            lp.topMargin = dp(6);
            proxyButtonList.addView(copy, lp);
        }
        proxyResultText.setText(out.toString());
        status("ProxyIP 完成：共 " + results.size() + " 条，显示前 " + limit);
        progress.setProgress(100);
    }

    private void copyResults() {
        String text = buildCopyResults();
        if (text.isEmpty()) {
            Toast.makeText(this, "没有可复制的成功结果", Toast.LENGTH_SHORT).show();
            return;
        }
        int count = text.split("\\r?\\n").length;
        copyText("cf-results", text, "已复制 " + count + " 条");
    }

    private String buildCopyResults() {
        List<Result> selected = new ArrayList<>();
        for (Result r : lastResults) if (r.fastOk) selected.add(r);
        if (selected.isEmpty()) {
            for (Result r : lastResults) if (r.realOk) selected.add(r);
        }
        if (selected.isEmpty()) {
            for (Result r : lastResults) if (r.handshakeOk) selected.add(r);
        }
        if (selected.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        Config config = readConfig();
        for (Result r : selected) {
            text.append(formatCopyLine(r, config)).append("\n");
        }
        return text.toString().trim();
    }

    private String formatCopyLine(Result r, Config config) {
        StringBuilder line = new StringBuilder();
        line.append(r.address()).append("#");
        if (!config.hashPrefix.isEmpty()) line.append(config.hashPrefix).append(" ");
        line.append(regionName(r.region))
                .append(" ")
                .append(String.format(Locale.US, "%.2fms", r.tcpMs));
        if (!config.lineSuffix.isEmpty()) line.append(" ").append(config.lineSuffix);
        return line.toString().trim();
    }

    private void copyProxyTop() {
        if (lastProxyResults.isEmpty()) {
            Toast.makeText(this, "没有 ProxyIP 排名可复制", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder text = new StringBuilder();
        int limit = Math.min(10, lastProxyResults.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) text.append(",");
            text.append(lastProxyResults.get(i).address());
        }
        copyText("proxyip-top10", text.toString(), "已复制前 " + limit + " 个 ProxyIP");
    }

    private void copyText(String label, String text, String toast) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
    }

    private void fillTextspaceWithResults() {
        String text = buildCopyResults();
        if (text.isEmpty()) {
            Toast.makeText(this, "没有可填入的测速结果", Toast.LENGTH_SHORT).show();
            return;
        }
        textspaceContentEdit.setText(text);
        saveConfig();
        Toast.makeText(this, "已填入测速结果", Toast.LENGTH_SHORT).show();
    }

    private void loadTextspaceNote() {
        if (textspaceRunning) return;
        textspaceRunning = true;
        setTextspaceButtons(false);
        status("正在获取 TextSpace 文本");

        new Thread(() -> {
            try {
                saveConfig();
                String noteId = textspaceNoteIdEdit.getText().toString().trim();
                NoteDetail note;
                if (!noteId.isEmpty()) {
                    note = parseNote(new JSONObject(textspaceRequest("GET", "/api/notes/" + urlPart(noteId), null)));
                } else {
                    JSONArray notes;
                    try {
                        notes = new JSONArray(textspaceRequest("GET", "/api/notes?full=1", null));
                    } catch (Exception fullError) {
                        notes = new JSONArray(textspaceRequest("GET", "/api/notes", null));
                    }
                    if (notes.length() == 0) {
                        toast("TextSpace 里还没有文本");
                        return;
                    }
                    String wantedTitle = textspaceTitleEdit.getText().toString().trim();
                    JSONObject picked = notes.getJSONObject(0);
                    if (!wantedTitle.isEmpty()) {
                        for (int i = 0; i < notes.length(); i++) {
                            JSONObject item = notes.getJSONObject(i);
                            if (wantedTitle.equals(item.optString("title"))) {
                                picked = item;
                                break;
                            }
                        }
                    }
                    if (!picked.has("content")) {
                        String pickedId = picked.optString("id", "");
                        if (pickedId.isEmpty()) throw new IOException("文本列表缺少 ID");
                        picked = new JSONObject(textspaceRequest("GET", "/api/notes/" + urlPart(pickedId), null));
                    }
                    note = parseNote(picked);
                }
                currentNote = note;
                ui.post(() -> {
                    textspaceNoteIdEdit.setText(note.id);
                    textspaceTitleEdit.setText(note.title);
                    textspaceContentEdit.setText(note.content);
                    status("已获取文本：" + note.title);
                });
            } catch (Exception e) {
                log("TextSpace 获取失败: " + e.getMessage());
                toast("获取失败: " + e.getMessage());
            } finally {
                textspaceRunning = false;
                ui.post(() -> setTextspaceButtons(true));
            }
        }).start();
    }

    private void saveTextspaceNote(boolean share) {
        if (textspaceRunning) return;
        textspaceRunning = true;
        setTextspaceButtons(false);
        status(share ? "正在保存并分享" : "正在保存文本");

        new Thread(() -> {
            try {
                saveConfig();
                String noteId = textspaceNoteIdEdit.getText().toString().trim();
                String title = textspaceTitleEdit.getText().toString().trim();
                String content = textspaceContentEdit.getText().toString();
                if (title.isEmpty()) title = "CF 手机优选结果";

                JSONObject body = new JSONObject();
                body.put("title", title);
                body.put("content", content);

                JSONObject savedJson;
                if (noteId.isEmpty()) {
                    savedJson = new JSONObject(textspaceRequest("POST", "/api/notes", body.toString()));
                } else {
                    savedJson = new JSONObject(textspaceRequest("PUT", "/api/notes/" + urlPart(noteId), body.toString()));
                }
                currentNote = parseNote(savedJson);

                String shareUrl = "";
                if (share) {
                    JSONObject sharedJson = new JSONObject(textspaceRequest("POST", "/api/notes/" + urlPart(currentNote.id) + "/share", "{}"));
                    currentNote = parseNote(sharedJson);
                    shareUrl = textspaceBaseUrl() + "/s/" + urlPart(currentNote.shareToken);
                }

                String finalTitle = title;
                String finalShareUrl = shareUrl;
                ui.post(() -> {
                    textspaceNoteIdEdit.setText(currentNote.id);
                    textspaceTitleEdit.setText(finalTitle);
                    if (!finalShareUrl.isEmpty()) {
                        copyText("textspace-share-url", finalShareUrl, "分享 URL 已复制");
                    }
                    status(share ? "已保存并复制分享 URL：" + finalShareUrl : "已保存文本：" + finalTitle);
                });
            } catch (Exception e) {
                log("TextSpace 保存失败: " + e.getMessage());
                toast("保存失败: " + e.getMessage());
            } finally {
                textspaceRunning = false;
                ui.post(() -> setTextspaceButtons(true));
            }
        }).start();
    }

    private void setTextspaceButtons(boolean enabled) {
        loadNoteButton.setEnabled(enabled);
        fillResultButton.setEnabled(enabled);
        saveNoteButton.setEnabled(enabled);
        shareNoteButton.setEnabled(enabled);
    }

    private String textspaceRequest(String method, String path, String body) throws Exception {
        URL url = new URL(textspaceBaseUrl() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", "Bearer " + textspaceToken());
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "CFMobileOptimizer/1.2");
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

    private String textspaceBaseUrl() {
        String value = textspaceUrlEdit.getText().toString().trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            throw new IllegalArgumentException("请填写正确的 Worker 地址");
        }
        return value;
    }

    private String textspaceToken() {
        String token = textspaceTokenEdit.getText().toString().trim();
        if (token.isEmpty()) throw new IllegalArgumentException("请填写管理员密钥");
        return token;
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

    private String urlPart(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8").replace("+", "%20");
    }

    private NoteDetail parseNote(JSONObject json) {
        NoteDetail note = new NoteDetail();
        note.id = json.optString("id", "");
        note.title = json.optString("title", "");
        note.content = json.optString("content", "");
        note.shareToken = json.optString("share_token", "");
        note.updatedAt = json.optLong("updated_at", 0);
        return note;
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
        String message = e.getMessage();
        if (message != null && message.toLowerCase(Locale.ROOT).contains("timeout")) return "timeout";
        return name;
    }

    private String normalizeRegion(String region) {
        String code = region == null ? "" : region.trim().toUpperCase(Locale.ROOT);
        if (code.startsWith("HK") || code.contains("香港")) return "HK";
        if (code.startsWith("SG") || code.contains("新加坡")) return "SG";
        if (code.startsWith("JP") || code.contains("日本")) return "JP";
        if (code.startsWith("KR") || code.contains("韩国") || code.contains("韓國")) return "KR";
        if (code.startsWith("US") || code.contains("美国") || code.contains("美國")) return "US";
        if (code.startsWith("TW") || code.contains("台湾") || code.contains("台灣")) return "TW";
        return code;
    }

    private String regionName(String region) {
        String code = normalizeRegion(region);
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
        String hashPrefix;
        String lineSuffix;
        int timeoutMs;
        int concurrency;
        int candidates;
        int repeats;
        int downloadBytes;
        double minSpeedMbps;
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

    static class NoteDetail {
        String id = "";
        String title = "";
        String content = "";
        String shareToken = "";
        long updatedAt;
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
