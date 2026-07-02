package com.cfoptimizer.mobile;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
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
    private static final int MIN_REAL_BYTES = 64 * 1024;
    private static final String DEFAULT_REAL_URL = "http://cachefly.cachefly.net/100mb.test";
    private static final String OLD_REAL_URL = "http://cachefly.cachefly.net/10mb.test";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SecureRandom random = new SecureRandom();
    private final AtomicInteger runSeq = new AtomicInteger();
    private volatile int activeRunId = 0;

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
    private EditText maxTcpMsEdit;
    private EditText repeatsEdit;
    private EditText downloadMbEdit;
    private EditText minSpeedEdit;
    private EditText realUrlEdit;
    private EditText hashPrefixEdit;
    private EditText lineSuffixEdit;
    private EditText textspaceUrlEdit;
    private EditText textspaceTokenEdit;
    private EditText textspaceTitleEdit;
    private EditText textspaceContentEdit;
    private Spinner textspaceNoteSpinner;
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
    private Button refreshNotesButton;
    private Button saveResultButton;
    private Button saveNoteButton;
    private Button shareNoteButton;
    private Button deleteNoteButton;
    private ProgressBar progress;
    private TextView statusText;
    private TextView resultText;
    private TextView proxyResultText;
    private TextView logText;
    private LinearLayout proxyButtonList;

    private volatile boolean running = false;
    private volatile boolean textspaceRunning = false;
    private boolean loadingTextspaceSpinner = false;
    private List<Result> lastResults = new ArrayList<>();
    private List<ProxyResult> lastProxyResults = new ArrayList<>();
    private List<NoteSummary> textspaceNotes = new ArrayList<>();
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
        title.setText("CF 手机优选 v1.4");
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
        sourceEdit = input(sourceCard, "网络地址或直接粘贴 IP 列表", pref("source", "https://zip.cm.edu.kg/all.txt"), 1, false);
        addRegionFilters(sourceCard);

        LinearLayout basicCard = card(root, "节点参数");
        hostEdit = input(basicCard, "SNI / Host 域名", pref("host", "xinnian.us.ci"), 1, false);
        pathEdit = input(basicCard, "WS 路径", pref("path", "/"), 1, false);
        uuidEdit = input(basicCard, "VLESS UUID（不填则只验证 WS 握手）", pref("uuid", ""), 1, false);
        proxyEdit = input(basicCard, "组合测试 ProxyIP（可留空，只支持一个）", pref("proxyip", ""), 1, false);
        realUrlEdit = input(basicCard, "真实下载 URL", prefRealUrl(), 1, false);
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
        maxTcpMsEdit = smallInput(row2, "最大 TCP ms", pref("maxTcpMs", "120"));
        repeatsEdit = smallInput(row2, "复测次数", pref("repeats", "2"));
        downloadMbEdit = smallInput(row2, "单 IP 下载 MiB", pref("downloadMb", "2"));

        LinearLayout row3 = row(paramCard);
        minSpeedEdit = smallInput(row3, "最低 Mbps", pref("minSpeed", "80"));

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
        proxySourceEdit = input(proxyCard, "ProxyIP 数据源（网络地址或直接粘贴列表）", pref("proxySource", ""), 1, false);
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
        View.OnFocusChangeListener autoRefreshTextspace = (view, hasFocus) -> {
            if (!hasFocus
                    && !textspaceUrlEdit.getText().toString().trim().isEmpty()
                    && !textspaceTokenEdit.getText().toString().trim().isEmpty()) {
                loadTextspaceNotes(false);
            }
        };
        textspaceUrlEdit.setOnFocusChangeListener(autoRefreshTextspace);
        textspaceTokenEdit.setOnFocusChangeListener(autoRefreshTextspace);

        TextView noteLabel = label("文本列表（选择后自动读取）");
        publishCard.addView(noteLabel);
        LinearLayout noteRow = row(publishCard);
        textspaceNoteSpinner = new Spinner(this);
        textspaceNoteSpinner.setBackground(fieldBg());
        noteRow.addView(textspaceNoteSpinner, new LinearLayout.LayoutParams(0, dp(44), 1));
        refreshNotesButton = button("刷新", BLUE);
        refreshNotesButton.setOnClickListener(v -> loadTextspaceNotes(true));
        LinearLayout.LayoutParams refreshLp = new LinearLayout.LayoutParams(dp(82), dp(44));
        refreshLp.leftMargin = dp(8);
        noteRow.addView(refreshNotesButton, refreshLp);
        textspaceNoteSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (loadingTextspaceSpinner || position < 0 || position >= textspaceNotes.size()) return;
                NoteSummary note = textspaceNotes.get(position);
                if (currentNote != null && note.id.equals(currentNote.id)) return;
                loadTextspaceNoteById(note.id);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        textspaceTitleEdit = input(publishCard, "文本标题", pref("textspaceTitle", "CF 手机优选结果"), 1, false);
        textspaceContentEdit = input(publishCard, "文本内容（可编辑，保存后分享 URL 就会更新）", pref("textspaceContent", ""), 6, false);

        LinearLayout pubRow1 = row(publishCard);
        saveNoteButton = button("保存文本", GREEN);
        saveNoteButton.setOnClickListener(v -> saveTextspaceNote(false));
        pubRow1.addView(saveNoteButton, new LinearLayout.LayoutParams(0, dp(44), 1));

        shareNoteButton = button("保存并分享", BLUE);
        shareNoteButton.setOnClickListener(v -> saveTextspaceNote(true));
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        shareLp.leftMargin = dp(8);
        pubRow1.addView(shareNoteButton, shareLp);

        LinearLayout pubRow2 = row(publishCard);
        saveResultButton = button("保存测速结果", ORANGE);
        saveResultButton.setOnClickListener(v -> saveScanResultsToTextspace());
        pubRow2.addView(saveResultButton, new LinearLayout.LayoutParams(0, dp(44), 1));

        deleteNoteButton = button("删除文本", Color.rgb(220, 38, 38));
        deleteNoteButton.setOnClickListener(v -> confirmDeleteTextspaceNote());
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        deleteLp.leftMargin = dp(8);
        pubRow2.addView(deleteNoteButton, deleteLp);

        LinearLayout logCard = card(root, "运行日志");
        logText = monoText();
        logCard.addView(logText);

        setContentView(scroll);
        ui.postDelayed(() -> {
            if (!textspaceUrlEdit.getText().toString().trim().isEmpty()
                    && !textspaceTokenEdit.getText().toString().trim().isEmpty()) {
                loadTextspaceNotes(false);
            } else {
                updateTextspaceSpinner();
            }
        }, 300);
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = dp(6);
        root.addView(row, lp);
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
        int runId = beginProgressRun();

        new Thread(() -> {
            try {
                Config config = readConfig();
                config.runId = runId;
                saveConfig();
                log("配置：真实测速=" + (config.realCheck ? "开启" : "关闭")
                        + " UUID=" + (config.uuid.isEmpty() ? "空" : "已填写")
                        + " Host=" + config.host
                        + " WS路径=" + config.path
                        + " 下载URL=" + config.realUrl
                        + (config.proxyip.isEmpty() ? "" : " ProxyIP=" + config.proxyip));
                List<Target> targets = loadTargets(sourceEdit.getText().toString(), config.regions, true);
                log("解析目标 " + targets.size() + " 条");
                if (targets.isEmpty()) {
                    toast("没有解析到符合区域的 IP");
                    return;
                }

                List<Result> tcpOk = runTcpScan(targets, config);
                tcpOk.sort(Comparator.comparingDouble(r -> r.tcpMs));
                int beforeDelayFilter = tcpOk.size();
                tcpOk = filterByMaxTcp(tcpOk, config);
                if (config.maxTcpMs > 0 && beforeDelayFilter != tcpOk.size()) {
                    log("最大 TCP 延迟过滤：保留 " + tcpOk.size() + " / " + beforeDelayFilter + " 条");
                }
                if (tcpOk.size() > config.candidates) {
                    tcpOk = new ArrayList<>(tcpOk.subList(0, config.candidates));
                }
                log("TCP 可连接 " + tcpOk.size() + " 条，进入 WS / 真实测速");

                List<Result> checked = runWsScan(tcpOk, config);
                checked.sort(this::compareResults);
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
        int runId = beginProgressRun();

        new Thread(() -> {
            try {
                Config config = readConfig();
                config.runId = runId;
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
        c.maxTcpMs = Math.max(0, parseDouble(maxTcpMsEdit, 120));
        c.repeats = clamp((int) parseDouble(repeatsEdit, 2), 1, 10);
        c.downloadBytes = Math.max(1, (int) (parseDouble(downloadMbEdit, 2) * 1024 * 1024));
        c.minSpeedMbps = Math.max(0, parseDouble(minSpeedEdit, 80));
        c.realCheck = realCheck.isChecked() && !c.uuid.isEmpty();
        c.regions = selectedRegions();
        if (c.host.isEmpty()) throw new IllegalArgumentException("请填写 SNI/Host 域名");
        if (realCheck.isChecked() && !c.uuid.isEmpty()) UUID.fromString(c.uuid);
        if (c.realCheck && !c.realUrl.toLowerCase(Locale.ROOT).startsWith("http://")) {
            throw new IllegalArgumentException("真实下载 URL 目前请使用 http:// 大文件地址");
        }
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
                .putString("textspaceTitle", textspaceTitleEdit.getText().toString())
                .putString("textspaceContent", textspaceContentEdit.getText().toString())
                .putString("timeout", timeoutEdit.getText().toString())
                .putString("concurrency", concurrencyEdit.getText().toString())
                .putString("candidates", candidatesEdit.getText().toString())
                .putString("maxTcpMs", maxTcpMsEdit.getText().toString())
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

    private String prefRealUrl() {
        String value = prefs.getString("realUrl", DEFAULT_REAL_URL);
        if (value == null || value.trim().isEmpty() || OLD_REAL_URL.equals(value.trim())) {
            return DEFAULT_REAL_URL;
        }
        return value;
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
                if (n % 20 == 0 || n == targets.size()) progress(config.runId, "TCP 初筛", n, targets.size());
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

    private List<Result> filterByMaxTcp(List<Result> input, Config config) {
        if (config.maxTcpMs <= 0) return input;
        List<Result> out = new ArrayList<>();
        for (Result r : input) {
            if (r.tcpMs <= config.maxTcpMs) out.add(r);
        }
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
                progress(config.runId, "ProxyIP", n, proxies.size());
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
                r.handshakeOk = r.wsSuccesses == config.repeats;
                r.realOk = config.realCheck ? r.realSuccesses == config.repeats : r.handshakeOk;
                r.fastOk = r.realOk && (!config.realCheck || r.speedMbps >= config.minSpeedMbps);
                int n = done.incrementAndGet();
                progress(config.runId, "WS / 真实测速", n, candidates.size());
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
                    + "User-Agent: CFMobileOptimizer/1.4\r\n\r\n";
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
                        r.error = speed.status > 0
                                ? "真实测速 HTTP " + speed.status + " bytes " + speed.bytes
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
                + "User-Agent: CFMobileOptimizer/1.4\r\n"
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
            conn.setRequestProperty("User-Agent", "CFMobileOptimizer/1.4");
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
                        .append(" 真实").append(r.realSuccesses).append("/").append(config.repeats)
                        .append(" HTTP").append(r.httpStatus)
                        .append(" bytes").append(r.bytesRead);
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
        selected.sort(this::compareResults);
        if (selected.size() > 10) {
            selected = new ArrayList<>(selected.subList(0, 10));
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

    private void saveScanResultsToTextspace() {
        String text = buildCopyResults();
        if (text.isEmpty()) {
            Toast.makeText(this, "没有可保存的测速结果", Toast.LENGTH_SHORT).show();
            return;
        }
        textspaceContentEdit.setText(text);
        saveTextspaceNote(false);
    }

    private void loadTextspaceNotes(boolean showToast) {
        if (textspaceRunning) return;
        textspaceRunning = true;
        setTextspaceButtons(false);
        status("正在获取 TextSpace 文本列表");
        String preferredId = currentNote == null ? "" : currentNote.id;
        String preferredTitle = textspaceTitleEdit.getText().toString().trim();

        new Thread(() -> {
            try {
                saveConfig();
                JSONArray notes;
                try {
                    notes = new JSONArray(textspaceRequest("GET", "/api/notes?full=1", null));
                } catch (Exception fullError) {
                    notes = new JSONArray(textspaceRequest("GET", "/api/notes", null));
                }

                List<NoteSummary> loaded = new ArrayList<>();
                List<NoteDetail> detailCache = new ArrayList<>();
                for (int i = 0; i < notes.length(); i++) {
                    JSONObject item = notes.getJSONObject(i);
                    loaded.add(parseNoteSummary(item));
                    if (item.has("content")) detailCache.add(parseNote(item));
                }

                NoteDetail selected = findDetail(detailCache, preferredId);
                NoteSummary selectedSummary = null;
                for (NoteSummary item : loaded) {
                    if (!preferredId.isEmpty() && preferredId.equals(item.id)) {
                        selectedSummary = item;
                        break;
                    }
                    if (selectedSummary == null && !preferredTitle.isEmpty() && preferredTitle.equals(item.title)) {
                        selectedSummary = item;
                    }
                }
                if (selectedSummary == null && !loaded.isEmpty()) selectedSummary = loaded.get(0);
                if (selected == null && selectedSummary != null) selected = findDetail(detailCache, selectedSummary.id);
                if (selected == null && selectedSummary != null && !selectedSummary.id.isEmpty()) {
                    selected = parseNote(new JSONObject(textspaceRequest("GET", "/api/notes/" + urlPart(selectedSummary.id), null)));
                }
                NoteDetail selectedNote = selected;

                ui.post(() -> {
                    textspaceNotes = loaded;
                    updateTextspaceSpinner();
                    if (loaded.isEmpty()) {
                        currentNote = null;
                        status("TextSpace 里还没有文本，保存时会新建");
                        if (showToast) Toast.makeText(this, "还没有文本，保存时会新建", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (selectedNote != null) applyNote(selectedNote);
                    status("已获取文本列表：" + loaded.size() + " 条");
                });
            } catch (Exception e) {
                log("TextSpace 列表获取失败: " + e.getMessage());
                if (showToast) toast("获取失败: " + e.getMessage());
            } finally {
                textspaceRunning = false;
                ui.post(() -> setTextspaceButtons(true));
            }
        }).start();
    }

    private void loadTextspaceNoteById(String noteId) {
        if (noteId == null || noteId.trim().isEmpty() || textspaceRunning) return;
        textspaceRunning = true;
        setTextspaceButtons(false);
        status("正在读取文本");

        new Thread(() -> {
            try {
                saveConfig();
                NoteDetail note = parseNote(new JSONObject(textspaceRequest("GET", "/api/notes/" + urlPart(noteId), null)));
                ui.post(() -> applyNote(note));
            } catch (Exception e) {
                log("TextSpace 读取失败: " + e.getMessage());
                toast("读取失败: " + e.getMessage());
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
                String noteId = currentNote == null ? "" : currentNote.id;
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
                    textspaceTitleEdit.setText(finalTitle);
                    upsertNoteSummary(currentNote);
                    updateTextspaceSpinner();
                    selectNoteInSpinner(currentNote.id);
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

    private void confirmDeleteTextspaceNote() {
        if (currentNote == null || currentNote.id.isEmpty()) {
            Toast.makeText(this, "当前没有选中文本", Toast.LENGTH_SHORT).show();
            return;
        }
        String title = currentNote.title == null || currentNote.title.isEmpty() ? "当前文本" : currentNote.title;
        new AlertDialog.Builder(this)
                .setTitle("删除文本")
                .setMessage("确定删除「" + title + "」吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteTextspaceNote())
                .show();
    }

    private void deleteTextspaceNote() {
        if (currentNote == null || currentNote.id.isEmpty() || textspaceRunning) return;
        textspaceRunning = true;
        setTextspaceButtons(false);
        String deleteId = currentNote.id;
        status("正在删除文本");

        new Thread(() -> {
            try {
                textspaceRequest("DELETE", "/api/notes/" + urlPart(deleteId), null);
                ui.post(() -> {
                    removeNoteSummary(deleteId);
                    currentNote = null;
                    textspaceTitleEdit.setText("");
                    textspaceContentEdit.setText("");
                    updateTextspaceSpinner();
                    status("已删除文本");
                    Toast.makeText(this, "已删除文本", Toast.LENGTH_SHORT).show();
                    if (!textspaceNotes.isEmpty()) {
                        String nextId = textspaceNotes.get(0).id;
                        ui.postDelayed(() -> loadTextspaceNoteById(nextId), 250);
                    }
                });
            } catch (Exception e) {
                log("TextSpace 删除失败: " + e.getMessage());
                toast("删除失败: " + e.getMessage());
            } finally {
                textspaceRunning = false;
                ui.post(() -> setTextspaceButtons(true));
            }
        }).start();
    }

    private void setTextspaceButtons(boolean enabled) {
        refreshNotesButton.setEnabled(enabled);
        saveResultButton.setEnabled(enabled);
        saveNoteButton.setEnabled(enabled);
        shareNoteButton.setEnabled(enabled);
        deleteNoteButton.setEnabled(enabled);
    }

    private void updateTextspaceSpinner() {
        loadingTextspaceSpinner = true;
        List<String> titles = new ArrayList<>();
        if (textspaceNotes.isEmpty()) {
            titles.add("暂无文本，保存时新建");
        } else {
            for (NoteSummary note : textspaceNotes) {
                String title = note.title == null || note.title.trim().isEmpty() ? "未命名文本" : note.title.trim();
                titles.add(title);
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, titles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        textspaceNoteSpinner.setAdapter(adapter);
        if (currentNote != null) selectNoteInSpinner(currentNote.id);
        loadingTextspaceSpinner = false;
    }

    private void selectNoteInSpinner(String noteId) {
        if (noteId == null || noteId.isEmpty()) return;
        for (int i = 0; i < textspaceNotes.size(); i++) {
            if (noteId.equals(textspaceNotes.get(i).id)) {
                textspaceNoteSpinner.setSelection(i, false);
                return;
            }
        }
    }

    private void applyNote(NoteDetail note) {
        currentNote = note;
        textspaceTitleEdit.setText(note.title);
        textspaceContentEdit.setText(note.content);
        upsertNoteSummary(note);
        updateTextspaceSpinner();
        selectNoteInSpinner(note.id);
        saveConfig();
        status("已读取文本：" + note.title);
    }

    private NoteDetail findDetail(List<NoteDetail> details, String id) {
        if (details.isEmpty()) return null;
        if (id != null && !id.isEmpty()) {
            for (NoteDetail note : details) {
                if (id.equals(note.id)) return note;
            }
        }
        return null;
    }

    private void upsertNoteSummary(NoteDetail detail) {
        if (detail == null || detail.id == null || detail.id.isEmpty()) return;
        NoteSummary summary = NoteSummary.fromDetail(detail);
        for (int i = 0; i < textspaceNotes.size(); i++) {
            if (detail.id.equals(textspaceNotes.get(i).id)) {
                textspaceNotes.set(i, summary);
                return;
            }
        }
        textspaceNotes.add(0, summary);
    }

    private void removeNoteSummary(String id) {
        List<NoteSummary> next = new ArrayList<>();
        for (NoteSummary note : textspaceNotes) {
            if (!id.equals(note.id)) next.add(note);
        }
        textspaceNotes = next;
    }

    private String textspaceRequest(String method, String path, String body) throws Exception {
        URL url = new URL(textspaceBaseUrl() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", "Bearer " + textspaceToken());
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "CFMobileOptimizer/1.4");
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

    private NoteSummary parseNoteSummary(JSONObject json) {
        NoteSummary note = new NoteSummary();
        note.id = json.optString("id", "");
        note.title = json.optString("title", "");
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

    private int beginProgressRun() {
        int id = runSeq.incrementAndGet();
        activeRunId = id;
        return id;
    }

    private void progress(int runId, String stage, int done, int total) {
        if (runId != activeRunId) return;
        int safeDone = total <= 0 ? 0 : Math.min(Math.max(done, 0), total);
        int percent = total <= 0 ? 0 : (int) (safeDone * 100.0 / total);
        ui.post(() -> {
            if (runId != activeRunId) return;
            progress.setProgress(percent);
            statusText.setText(stage + " " + safeDone + "/" + total);
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
        double maxTcpMs;
        boolean realCheck;
        Set<String> regions;
        int runId;
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

    static class NoteSummary {
        String id = "";
        String title = "";
        String shareToken = "";
        long updatedAt;

        static NoteSummary fromDetail(NoteDetail detail) {
            NoteSummary summary = new NoteSummary();
            summary.id = detail.id;
            summary.title = detail.title;
            summary.shareToken = detail.shareToken;
            summary.updatedAt = detail.updatedAt;
            return summary;
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
            return status >= 200 && status < 300 && bytes >= MIN_REAL_BYTES;
        }
    }
}
