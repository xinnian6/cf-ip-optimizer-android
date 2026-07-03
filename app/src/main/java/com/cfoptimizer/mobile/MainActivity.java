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
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
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
import java.net.URLDecoder;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final int BODY_SNIPPET_BYTES = 96;
    private static final String DEFAULT_REAL_URL = "http://speedtest.tele2.net/10MB.zip";
    private static final String OLD_REAL_URL = "http://cachefly.cachefly.net/10mb.test";
    private static final String BAD_REAL_URL = "http://cachefly.cachefly.net/100mb.test";

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
    private EditText textspaceUrlEdit;
    private EditText textspaceTokenEdit;
    private EditText textspaceTitleEdit;
    private EditText textspaceContentEdit;
    private Spinner textspaceNoteSpinner;
    private CheckBox allRegionCheck;
    private Button startButton;
    private Button shareNoteButton;
    private Button manageNodesButton;
    private ProgressBar progress;
    private TextView statusText;
    private TextView resultText;
    private TextView logText;
    private TextView regionSummaryText;
    private Button regionCustomButton;

    private volatile boolean running = false;
    private volatile boolean textspaceRunning = false;
    private boolean loadingTextspaceSpinner = false;
    private List<Result> lastResults = new ArrayList<>();
    private List<ProxyResult> lastProxyResults = new ArrayList<>();
    private List<NoteSummary> textspaceNotes = new ArrayList<>();
    private final List<RegionItem> regionItems = new ArrayList<>();
    private final Set<String> selectedRegionCodes = new LinkedHashSet<>();
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
        title.setText("CF 手机优选 v1.22");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("按地区筛选 IP，验证 WS 握手和真实下载，也可以单独测速 ProxyIP。");
        sub.setTextSize(13);
        sub.setTextColor(MUTED);
        sub.setPadding(0, dp(3), 0, dp(10));
        root.addView(sub);

        LinearLayout sourceCard = card(root, "IP 数据源");
        sourceEdit = input(sourceCard, "网络地址或直接粘贴 IP 列表", pref("source", "https://zip.cm.edu.kg/all.txt"), 1, false);
        proxySourceEdit = input(sourceCard, "ProxyIP 数据源（网络地址或直接粘贴列表）", pref("proxySource", ""), 1, false);
        textspaceUrlEdit = input(sourceCard, "TextSpace Worker 地址", pref("textspaceUrl", ""), 1, false);
        textspaceTokenEdit = input(sourceCard, "管理员密钥", pref("textspaceToken", ""), 1, false);
        textspaceTokenEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        textspaceTitleEdit = input(sourceCard, "文本标题", pref("textspaceTitle", "CF 手机优选结果"), 1, false);
        View.OnFocusChangeListener autoRefreshTextspace = (view, hasFocus) -> {
            if (!hasFocus
                    && !textspaceUrlEdit.getText().toString().trim().isEmpty()
                    && !textspaceTokenEdit.getText().toString().trim().isEmpty()) {
                loadTextspaceNotes(false);
            }
        };
        textspaceUrlEdit.setOnFocusChangeListener(autoRefreshTextspace);
        textspaceTokenEdit.setOnFocusChangeListener(autoRefreshTextspace);
        addRegionFilters(sourceCard);

        LinearLayout basicCard = card(root, "节点参数");
        hostEdit = input(basicCard, "SNI / Host 域名", pref("host", "xinnian.us.ci"), 1, false);
        pathEdit = input(basicCard, "WS 路径", pref("path", "/"), 1, false);
        uuidEdit = input(basicCard, "VLESS UUID（不填则只验证 WS 握手）", pref("uuid", ""), 1, false);
        proxyEdit = input(basicCard, "ProxyIP（可留空，只支持一个）", pref("proxyip", ""), 1, false);
        realUrlEdit = input(basicCard, "真实下载 URL", prefRealUrl(), 1, false);

        LinearLayout paramCard = card(root, "测速参数");
        LinearLayout row1 = row(paramCard);
        timeoutEdit = smallInput(row1, "超时秒", pref("timeout", "8"));
        concurrencyEdit = smallInput(row1, "并发", pref("concurrency", "32"));
        candidatesEdit = smallInput(row1, "候选数", pref("candidates", "100"));

        LinearLayout row2 = row(paramCard);
        maxTcpMsEdit = smallInput(row2, "最大 TCP ms", pref("maxTcpMs", "250"));
        repeatsEdit = smallInput(row2, "复测次数", pref("repeats", "2"));
        downloadMbEdit = smallInput(row2, "单 IP 下载 MiB", pref("downloadMb", "2"));

        LinearLayout row3 = row(paramCard);
        minSpeedEdit = smallInput(row3, "最低 Mbps", pref("minSpeed", "80"));

        LinearLayout actionCard = card(root, "操作");
        TextView noteLabel = label("保存目标（选择后自动读取）");
        actionCard.addView(noteLabel);
        LinearLayout noteRow = row(actionCard);
        textspaceNoteSpinner = new Spinner(this);
        textspaceNoteSpinner.setBackground(fieldBg());
        noteRow.addView(textspaceNoteSpinner, new LinearLayout.LayoutParams(0, dp(44), 1));
        shareNoteButton = button("分享", GREEN);
        shareNoteButton.setOnClickListener(v -> saveTextspaceNote(true));
        LinearLayout.LayoutParams shareTopLp = new LinearLayout.LayoutParams(dp(66), dp(44));
        shareTopLp.leftMargin = dp(8);
        noteRow.addView(shareNoteButton, shareTopLp);
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

        LinearLayout buttons = row(actionCard);
        startButton = button("开始测速", BLUE);
        startButton.setOnClickListener(v -> startScan());
        buttons.addView(startButton, new LinearLayout.LayoutParams(0, dp(44), 1));

        manageNodesButton = button("节点管理", GREEN);
        manageNodesButton.setOnClickListener(v -> showNodeManager());
        LinearLayout.LayoutParams manageLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        manageLp.leftMargin = dp(8);
        buttons.addView(manageNodesButton, manageLp);

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

        LinearLayout resultCard = card(root, "测速结果");
        resultText = monoText();
        resultCard.addView(resultText);

        textspaceContentEdit = bareInput(actionCard, pref("textspaceContent", ""), 8, false);
        textspaceContentEdit.setVisibility(View.GONE);

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

    private EditText bareInput(LinearLayout root, String value, int minLines, boolean number) {
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
        initRegionState();

        TextView label = label("区域筛选");
        root.addView(label);

        LinearLayout regionBox = new LinearLayout(this);
        regionBox.setOrientation(LinearLayout.VERTICAL);
        regionBox.setPadding(dp(10), dp(8), dp(10), dp(10));
        regionBox.setBackground(fieldBg());
        root.addView(regionBox, fieldLp());

        regionSummaryText = new TextView(this);
        regionSummaryText.setTextSize(13);
        regionSummaryText.setTextColor(TEXT);
        regionSummaryText.setPadding(0, 0, 0, dp(6));
        regionBox.addView(regionSummaryText);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        regionBox.addView(controls);

        allRegionCheck = new CheckBox(this);
        allRegionCheck.setText("全部区域");
        allRegionCheck.setTextSize(13);
        allRegionCheck.setChecked(prefs.getBoolean("regionAllV2", false));
        allRegionCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateRegionSummary();
            saveRegionPrefs();
        });
        controls.addView(allRegionCheck, new LinearLayout.LayoutParams(0, dp(42), 1));

        regionCustomButton = button("自定义区域", BLUE);
        regionCustomButton.setOnClickListener(v -> showRegionDialog());
        LinearLayout.LayoutParams customLp = new LinearLayout.LayoutParams(dp(122), dp(42));
        customLp.leftMargin = dp(8);
        controls.addView(regionCustomButton, customLp);

        updateRegionSummary();
    }

    private void initRegionState() {
        if (!regionItems.isEmpty()) return;
        addRegionItem(regionItems, "HK", "香港");
        addRegionItem(regionItems, "SG", "新加坡");
        addRegionItem(regionItems, "JP", "日本");
        addRegionItem(regionItems, "KR", "韩国");
        addRegionItem(regionItems, "US", "美国");
        addRegionItem(regionItems, "TW", "台湾");
        addRegionItem(regionItems, "DE", "德国");
        addRegionItem(regionItems, "NL", "荷兰");
        addRegionItem(regionItems, "GB", "英国");
        addRegionItem(regionItems, "CA", "加拿大");
        addRegionItem(regionItems, "AU", "澳大利亚");
        addRegionItem(regionItems, "FR", "法国");
        addRegionItem(regionItems, "RU", "俄罗斯");
        addRegionItem(regionItems, "TH", "泰国");
        addRegionItem(regionItems, "VN", "越南");
        addRegionItem(regionItems, "MY", "马来西亚");
        addRegionItem(regionItems, "PH", "菲律宾");
        addRegionItem(regionItems, "ID", "印度尼西亚");
        loadCustomRegions(regionItems, prefs.getString("regionCustomItemsV2", ""));

        selectedRegionCodes.clear();
        String saved = prefs.getString("regionSelectedV2", "HK,SG,JP,KR,US");
        for (String raw : saved.split(",")) {
            String code = cleanRegionCode(raw);
            if (!code.isEmpty()) selectedRegionCodes.add(code);
        }
    }

    private void showRegionDialog() {
        initRegionState();
        List<RegionItem> draftItems = new ArrayList<>();
        for (RegionItem item : regionItems) draftItems.add(new RegionItem(item.code, item.name, item.custom));
        Set<String> draftSelected = new LinkedHashSet<>(selectedRegionCodes);

        LinearLayout dialogRoot = new LinearLayout(this);
        dialogRoot.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(14);
        dialogRoot.setPadding(pad, dp(8), pad, 0);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("搜索国家/地区，例如 香港、HK、日本、JP");
        search.setTextSize(14);
        search.setPadding(dp(10), 0, dp(10), 0);
        search.setBackground(fieldBg());
        dialogRoot.addView(search, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView listScroll = new ScrollView(this);
        listScroll.addView(list);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(300));
        listLp.topMargin = dp(8);
        dialogRoot.addView(listScroll, listLp);

        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams addRowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        addRowLp.topMargin = dp(8);
        dialogRoot.addView(addRow, addRowLp);

        EditText customInput = new EditText(this);
        customInput.setSingleLine(true);
        customInput.setHint("添加：德国#DE 或 DE 德国");
        customInput.setTextSize(14);
        customInput.setPadding(dp(10), 0, dp(10), 0);
        customInput.setBackground(fieldBg());
        addRow.addView(customInput, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button addButton = button("添加", GREEN);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(dp(78), dp(44));
        addLp.leftMargin = dp(8);
        addRow.addView(addButton, addLp);

        Runnable render = () -> renderRegionDialogList(list, draftItems, draftSelected, search.getText().toString());
        render.run();

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                render.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        addButton.setOnClickListener(v -> {
            RegionItem item = parseRegionInput(customInput.getText().toString());
            if (item == null) {
                Toast.makeText(this, "请输入 国家#代码，例如 香港#HK", Toast.LENGTH_SHORT).show();
                return;
            }
            addRegionItem(draftItems, item.code, item.name, true);
            draftSelected.add(item.code);
            customInput.setText("");
            render.run();
        });

        new AlertDialog.Builder(this)
                .setTitle("自定义区域")
                .setView(dialogRoot)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    regionItems.clear();
                    regionItems.addAll(draftItems);
                    selectedRegionCodes.clear();
                    selectedRegionCodes.addAll(draftSelected);
                    if (allRegionCheck != null) allRegionCheck.setChecked(false);
                    saveRegionPrefs();
                    updateRegionSummary();
                })
                .show();
    }

    private void renderRegionDialogList(LinearLayout list, List<RegionItem> items, Set<String> selected, String query) {
        list.removeAllViews();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (RegionItem item : items) {
            String text = item.name + " #" + item.code;
            if (!q.isEmpty()
                    && !item.code.toLowerCase(Locale.ROOT).contains(q)
                    && !item.name.toLowerCase(Locale.ROOT).contains(q)
                    && !text.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            CheckBox cb = new CheckBox(this);
            cb.setText(text);
            cb.setTextSize(14);
            cb.setChecked(selected.contains(item.code));
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) selected.add(item.code);
                else selected.remove(item.code);
            });
            list.addView(cb, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
            shown++;
        }
        if (shown == 0) {
            TextView empty = label("没有匹配项，可以在下面添加。");
            list.addView(empty);
        }
    }

    private void updateRegionSummary() {
        if (regionSummaryText == null) return;
        if (allRegionCheck != null && allRegionCheck.isChecked()) {
            regionSummaryText.setText("当前：全部区域（不按 #HK / #JP 过滤）");
            return;
        }
        if (selectedRegionCodes.isEmpty()) {
            regionSummaryText.setText("当前：未勾选，将测试全部区域");
            return;
        }
        List<String> names = new ArrayList<>();
        for (String code : selectedRegionCodes) names.add(regionName(code) + " #" + code);
        regionSummaryText.setText("当前：" + join(names, "、"));
    }

    private void saveRegionPrefs() {
        prefs.edit()
                .putBoolean("regionAllV2", allRegionCheck != null && allRegionCheck.isChecked())
                .putString("regionSelectedV2", join(new ArrayList<>(selectedRegionCodes), ","))
                .putString("regionCustomItemsV2", customRegionPrefsValue())
                .apply();
    }

    private String customRegionPrefsValue() {
        StringBuilder out = new StringBuilder();
        for (RegionItem item : regionItems) {
            if (!item.custom) continue;
            out.append(item.code).append("|").append(item.name.replace("|", " ")).append("\n");
        }
        return out.toString().trim();
    }

    private void loadCustomRegions(List<RegionItem> target, String value) {
        if (value == null || value.trim().isEmpty()) return;
        for (String raw : value.split("\\r?\\n")) {
            String[] parts = raw.split("\\|", 2);
            if (parts.length == 2) addRegionItem(target, parts[0], parts[1], true);
        }
    }

    private void addRegionItem(List<RegionItem> target, String code, String name) {
        addRegionItem(target, code, name, false);
    }

    private void addRegionItem(List<RegionItem> target, String code, String name, boolean custom) {
        String cleanCode = cleanRegionCode(code);
        String cleanName = name == null ? "" : name.trim();
        if (cleanCode.isEmpty()) return;
        if (cleanName.isEmpty()) cleanName = cleanCode;
        for (int i = 0; i < target.size(); i++) {
            RegionItem item = target.get(i);
            if (cleanCode.equals(item.code)) {
                target.set(i, new RegionItem(cleanCode, cleanName, item.custom && custom));
                return;
            }
        }
        target.add(new RegionItem(cleanCode, cleanName, custom));
    }

    private RegionItem parseRegionInput(String value) {
        if (value == null) return null;
        String text = value.trim();
        if (text.isEmpty()) return null;
        String name = "";
        String code = "";
        int hash = text.lastIndexOf('#');
        if (hash >= 0) {
            name = text.substring(0, hash).trim();
            code = text.substring(hash + 1).trim();
        } else {
            String[] parts = text.split("[,;\\s]+", 2);
            if (parts.length == 2) {
                if (parts[0].matches("(?i)[A-Z]{2,4}")) {
                    code = parts[0];
                    name = parts[1];
                } else {
                    name = parts[0];
                    code = parts[1];
                }
            } else if (parts[0].matches("(?i)[A-Z]{2,4}")) {
                code = parts[0];
                name = parts[0].toUpperCase(Locale.ROOT);
            }
        }
        code = cleanRegionCode(code);
        if (code.isEmpty()) return null;
        if (name.isEmpty()) name = code;
        return new RegionItem(code, name, true);
    }

    private String cleanRegionCode(String raw) {
        if (raw == null) return "";
        return raw.trim().replace("#", "").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private String join(List<String> values, String separator) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isEmpty()) continue;
            if (out.length() > 0) out.append(separator);
            out.append(value);
        }
        return out.toString();
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
                selectBestProxyIfAvailable(config);
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
                ui.post(() -> {
                    showResults(checked, config);
                    promptSaveBoundNodes();
                });
            } catch (Exception e) {
                log("错误: " + e.getMessage());
                toast("测速失败: " + e.getMessage());
            } finally {
                running = false;
                ui.post(() -> startButton.setEnabled(true));
            }
        }).start();
    }

    private void selectBestProxyIfAvailable(Config config) throws Exception {
        String source = proxySourceEdit.getText().toString().trim();
        if (source.isEmpty()) {
            lastProxyResults = new ArrayList<>();
            return;
        }
        status("正在测速 ProxyIP");
        List<Target> proxies = loadTargets(source, config.regions, true);
        if (proxies.isEmpty()) {
            log("ProxyIP 数据源没有解析到符合区域的地址，继续使用当前 ProxyIP");
            lastProxyResults = new ArrayList<>();
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

        ProxyResult best = firstUsableProxy(results);
        if (best == null) {
            log("ProxyIP 没有可连接结果，继续使用当前 ProxyIP");
            return;
        }
        String bestAddress = best.address();
        config.proxyip = bestAddress;
        prefs.edit().putString("proxyip", bestAddress).apply();
        ui.post(() -> proxyEdit.setText(bestAddress));
        log("已自动选用 ProxyIP 第一名：" + bestAddress
                + " 成功" + best.successes + "/" + config.repeats
                + " " + String.format(Locale.US, "%.2fms", best.bestMs));
    }

    private ProxyResult firstUsableProxy(List<ProxyResult> results) {
        for (ProxyResult result : results) {
            if (result.successes > 0) return result;
        }
        return null;
    }

    private Config readConfig() {
        Config c = new Config();
        c.host = hostEdit.getText().toString().trim();
        c.path = pathEdit.getText().toString().trim();
        c.uuid = uuidEdit.getText().toString().trim();
        c.proxyip = firstToken(proxyEdit.getText().toString().trim());
        c.realUrl = realUrlEdit.getText().toString().trim();
        c.timeoutMs = Math.max(500, (int) (parseDouble(timeoutEdit, 8) * 1000));
        c.concurrency = clamp((int) parseDouble(concurrencyEdit, 32), 1, 256);
        c.candidates = clamp((int) parseDouble(candidatesEdit, 100), 1, 5000);
        c.maxTcpMs = Math.max(0, parseDouble(maxTcpMsEdit, 120));
        c.repeats = clamp((int) parseDouble(repeatsEdit, 2), 1, 10);
        c.downloadBytes = Math.max(1, (int) (parseDouble(downloadMbEdit, 2) * 1024 * 1024));
        c.minSpeedMbps = Math.max(0, parseDouble(minSpeedEdit, 80));
        c.realCheck = !c.uuid.isEmpty();
        c.regions = selectedRegions();
        if (c.host.isEmpty()) throw new IllegalArgumentException("请填写 SNI/Host 域名");
        if (!c.uuid.isEmpty()) UUID.fromString(c.uuid);
        if (c.realCheck && !c.realUrl.toLowerCase(Locale.ROOT).startsWith("http://")) {
            throw new IllegalArgumentException("真实下载 URL 目前请使用 http:// 大文件地址");
        }
        if (c.uuid.isEmpty()) log("未填写 UUID，本次只验证 WS 握手，不做真实下载测速");
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
        initRegionState();
        if (allRegionCheck != null && allRegionCheck.isChecked()) return Collections.emptySet();
        return new LinkedHashSet<>(selectedRegionCodes);
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
                .putBoolean("regionAllV2", allRegionCheck != null && allRegionCheck.isChecked())
                .putString("regionSelectedV2", join(new ArrayList<>(selectedRegionCodes), ","))
                .putString("regionCustomItemsV2", customRegionPrefsValue())
                .apply();
    }

    private String pref(String key, String fallback) {
        return prefs.getString(key, fallback);
    }

    private String prefRealUrl() {
        String value = prefs.getString("realUrl", DEFAULT_REAL_URL);
        if (value == null || value.trim().isEmpty() || isBadRealUrl(value.trim())) {
            return DEFAULT_REAL_URL;
        }
        return value;
    }

    private boolean isBadRealUrl(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return OLD_REAL_URL.equals(lower) || BAD_REAL_URL.equals(lower);
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
                r.handshakeOk = r.wsSuccesses >= 1;
                r.realOk = config.realCheck ? r.realSuccesses >= 1 : r.handshakeOk;
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
                    + "User-Agent: CFMobileOptimizer/1.22\r\n\r\n";
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
                + "User-Agent: CFMobileOptimizer/1.22\r\n"
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
            conn.setRequestProperty("User-Agent", "CFMobileOptimizer/1.22");
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
        appendProxyResults(out, config);
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
        StringBuilder out = new StringBuilder();
        appendProxyResults(out, config);
        resultText.setText(out.toString());
        int limit = Math.min(10, results.size());
        status("ProxyIP 完成：共 " + results.size() + " 条，显示前 " + limit);
        progress.setProgress(100);
    }

    private void appendProxyResults(StringBuilder out, Config config) {
        if (lastProxyResults.isEmpty()) return;
        out.append("ProxyIP 排名\n");
        int limit = Math.min(10, lastProxyResults.size());
        for (int i = 0; i < limit; i++) {
            ProxyResult r = lastProxyResults.get(i);
            out.append(i + 1).append(". ").append(r.address())
                    .append(" 成功").append(r.successes).append("/").append(config.repeats)
                    .append(" ")
                    .append(r.bestMs > 0 ? String.format(Locale.US, "%.2fms", r.bestMs) : "不可连");
            if (!r.error.isEmpty()) out.append(" ").append(r.error);
            out.append("\n");
        }
        out.append("\n");
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
        List<Result> selected = selectedTopResults();
        if (selected.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (Result r : selected) {
            text.append(formatCopyLine(r)).append("\n");
        }
        return text.toString().trim();
    }

    private List<Result> selectedTopResults() {
        List<Result> selected = new ArrayList<>();
        for (Result r : lastResults) if (r.fastOk) selected.add(r);
        if (selected.isEmpty()) {
            for (Result r : lastResults) if (r.realOk) selected.add(r);
        }
        if (selected.isEmpty()) {
            for (Result r : lastResults) if (r.handshakeOk) selected.add(r);
        }
        if (selected.isEmpty()) {
            return selected;
        }
        selected.sort(this::compareResults);
        if (selected.size() > 10) {
            selected = new ArrayList<>(selected.subList(0, 10));
        }
        return selected;
    }

    private String formatCopyLine(Result r) {
        StringBuilder line = new StringBuilder();
        line.append(r.address()).append("#");
        line.append(regionName(r.region))
                .append(" ")
                .append(String.format(Locale.US, "%.2fms", r.tcpMs));
        return line.toString().trim();
    }

    private void copyBoundNodes() {
        try {
            String text = buildEdgetunnelBoundNodes();
            if (text.isEmpty()) {
                Toast.makeText(this, "没有可复制的绑定节点", Toast.LENGTH_SHORT).show();
                return;
            }
            int count = text.split("\\r?\\n").length;
            copyText("edgetunnel-bound-nodes", text, "已复制 " + count + " 个绑定节点");
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String buildEdgetunnelBoundNodes() throws Exception {
        Config config = readConfig();
        if (config.uuid.isEmpty()) throw new IllegalArgumentException("请先填写 VLESS UUID");
        if (config.proxyip.isEmpty()) throw new IllegalArgumentException("请先填写要绑定的 ProxyIP");
        List<Result> selected = selectedTopResults();
        if (selected.isEmpty()) return "";

        StringBuilder text = new StringBuilder();
        for (Result r : selected) {
            text.append(formatEdgetunnelBoundNode(r, config)).append("\n");
        }
        return text.toString().trim();
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

    private void copyText(String label, String text, String toast) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
    }

    private void promptSaveBoundNodes() {
        String title = textspaceTitleEdit.getText().toString().trim();
        if (title.isEmpty() && currentNote != null) title = currentNote.title;
        if (title == null || title.trim().isEmpty()) title = "CF 手机优选结果";
        String message = "当前文件：" + title + "\n\n保存本次生成的绑定节点？";
        new AlertDialog.Builder(this)
                .setTitle("保存绑定节点")
                .setMessage(message)
                .setNegativeButton("覆盖保存", (dialog, which) -> saveBoundNodesToTextspace(false))
                .setPositiveButton("追加保存", (dialog, which) -> saveBoundNodesToTextspace(true))
                .setNeutralButton("取消", null)
                .show();
    }

    private void saveBoundNodesToTextspace(boolean append) {
        try {
            String text = buildEdgetunnelBoundNodes();
            if (text.isEmpty()) {
                Toast.makeText(this, "没有可保存的绑定节点", Toast.LENGTH_SHORT).show();
                return;
            }
            if (textspaceTitleEdit.getText().toString().trim().isEmpty()) {
                textspaceTitleEdit.setText("Edgetunnel 绑定节点");
            }
            if (append) {
                String existing = normalizeEditableSubscription(textspaceContentEdit.getText().toString());
                textspaceContentEdit.setText(mergeLines(existing, text));
            } else {
                textspaceContentEdit.setText(text);
            }
            saveTextspaceNote(false);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showNodeManager() {
        String content = normalizeEditableSubscription(textspaceContentEdit.getText().toString());
        textspaceContentEdit.setText(content);
        List<NodeLine> nodes = parseNodeLines(content);
        if (nodes.isEmpty()) {
            Toast.makeText(this, "当前文本里没有解析到节点", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<Integer> selectedLineIndexes = new LinkedHashSet<>();
        List<Integer> visibleLineIndexes = new ArrayList<>();

        LinearLayout dialogRoot = new LinearLayout(this);
        dialogRoot.setOrientation(LinearLayout.VERTICAL);
        dialogRoot.setPadding(dp(14), dp(8), dp(14), 0);

        TextView tip = label("搜索 IP / 端口 / 地区 / ProxyIP，勾选坏节点后删除。删除只会先更新编辑框，保存后才同步到 TextSpace。");
        dialogRoot.addView(tip);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("例如 219.76.13.167 或 香港");
        search.setTextSize(14);
        search.setPadding(dp(10), 0, dp(10), 0);
        search.setBackground(fieldBg());
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        searchLp.topMargin = dp(8);
        dialogRoot.addView(search, searchLp);

        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams quickLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        quickLp.topMargin = dp(8);
        dialogRoot.addView(quickRow, quickLp);

        Button selectVisibleButton = button("全选当前", BLUE);
        quickRow.addView(selectVisibleButton, new LinearLayout.LayoutParams(0, dp(40), 1));

        Button clearVisibleButton = button("取消当前", ORANGE);
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(0, dp(40), 1);
        clearLp.leftMargin = dp(8);
        quickRow.addView(clearVisibleButton, clearLp);

        TextView countText = label("");
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        countLp.topMargin = dp(4);
        dialogRoot.addView(countText, countLp);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView listScroll = new ScrollView(this);
        listScroll.addView(list);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(360));
        listLp.topMargin = dp(8);
        dialogRoot.addView(listScroll, listLp);

        Runnable render = () -> renderNodeManagerList(list, countText, nodes, selectedLineIndexes, visibleLineIndexes, search.getText().toString());
        render.run();

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                render.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        selectVisibleButton.setOnClickListener(v -> {
            selectedLineIndexes.addAll(visibleLineIndexes);
            render.run();
        });

        clearVisibleButton.setOnClickListener(v -> {
            for (Integer index : visibleLineIndexes) selectedLineIndexes.remove(index);
            render.run();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("节点管理")
                .setView(dialogRoot)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除勾选", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (selectedLineIndexes.isEmpty()) {
                Toast.makeText(this, "还没有勾选要删除的节点", Toast.LENGTH_SHORT).show();
                return;
            }
            String next = removeNodeLines(content, selectedLineIndexes);
            textspaceContentEdit.setText(next);
            Toast.makeText(this, "已删除 " + selectedLineIndexes.size() + " 个节点，保存后生效", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void renderNodeManagerList(LinearLayout list, TextView countText, List<NodeLine> nodes,
                                       Set<Integer> selectedLineIndexes, List<Integer> visibleLineIndexes, String query) {
        list.removeAllViews();
        visibleLineIndexes.clear();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (NodeLine node : nodes) {
            if (!q.isEmpty() && !node.searchText.contains(q)) continue;
            visibleLineIndexes.add(node.lineIndex);

            CheckBox cb = new CheckBox(this);
            cb.setText(node.display);
            cb.setTextSize(13);
            cb.setTextColor(TEXT);
            cb.setChecked(selectedLineIndexes.contains(node.lineIndex));
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) selectedLineIndexes.add(node.lineIndex);
                else selectedLineIndexes.remove(node.lineIndex);
                countText.setText("显示 " + visibleLineIndexes.size() + " / 总 " + nodes.size() + "，已勾选 " + selectedLineIndexes.size());
            });
            list.addView(cb, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            shown++;
        }
        if (shown == 0) {
            TextView empty = label("没有匹配节点。");
            list.addView(empty);
        }
        countText.setText("显示 " + shown + " / 总 " + nodes.size() + "，已勾选 " + selectedLineIndexes.size());
    }

    private List<NodeLine> parseNodeLines(String content) {
        List<NodeLine> nodes = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) return nodes;
        String[] lines = content.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            NodeLine node = parseNodeLine(i, line);
            if (node != null) nodes.add(node);
        }
        return nodes;
    }

    private NodeLine parseNodeLine(int lineIndex, String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.startsWith("vless://") || lower.startsWith("trojan://") || lower.startsWith("ss://") || lower.startsWith("vmess://")) {
            return parseUrlNodeLine(lineIndex, line);
        }
        String address = firstAddress(line);
        if (address.isEmpty()) return null;
        String remark = "";
        int hash = line.indexOf('#');
        if (hash >= 0 && hash + 1 < line.length()) remark = line.substring(hash + 1).trim();
        String region = regionName(remark);
        String display = address + "  " + (remark.isEmpty() ? region : remark);
        return new NodeLine(lineIndex, line, address, region, "", remark, display);
    }

    private NodeLine parseUrlNodeLine(int lineIndex, String line) {
        String address = "";
        String remark = "";
        String proxy = "";

        int scheme = line.indexOf("://");
        if (scheme >= 0) {
            String rest = line.substring(scheme + 3);
            int end = firstIndexOf(rest, '?', '#');
            String authority = end >= 0 ? rest.substring(0, end) : rest;
            int at = authority.lastIndexOf('@');
            address = at >= 0 ? authority.substring(at + 1) : authority;
        }

        int hash = line.indexOf('#');
        if (hash >= 0 && hash + 1 < line.length()) remark = urlDecodeSafe(line.substring(hash + 1));

        String decodedLine = urlDecodeSafe(line);
        int proxyIndex = decodedLine.toLowerCase(Locale.ROOT).indexOf("proxyip=");
        if (proxyIndex >= 0) {
            int start = proxyIndex + "proxyip=".length();
            int end = decodedLine.length();
            for (char stop : new char[]{'&', '?', '#', '/', ' '}) {
                int p = decodedLine.indexOf(stop, start);
                if (p >= 0 && p < end) end = p;
            }
            proxy = decodedLine.substring(start, end).trim();
        }

        if (address.isEmpty()) address = firstAddress(decodedLine);
        if (address.isEmpty()) return null;

        String region = regionName(remark);
        String latency = firstMatch(remark, "\\d+(?:\\.\\d+)?ms");
        StringBuilder display = new StringBuilder();
        display.append(address);
        if (!region.equals("未知")) display.append("  ").append(region);
        if (!latency.isEmpty()) display.append("  ").append(latency);
        if (!proxy.isEmpty()) display.append("  PX ").append(proxy);
        if (region.equals("未知") && latency.isEmpty() && proxy.isEmpty() && !remark.isEmpty()) {
            display.append("  ").append(remark);
        }
        return new NodeLine(lineIndex, line, address, region, proxy, remark, display.toString());
    }

    private String removeNodeLines(String content, Set<Integer> selectedLineIndexes) {
        String[] lines = content == null ? new String[0] : content.split("\\r?\\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (selectedLineIndexes.contains(i)) continue;
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            out.append(line).append("\n");
        }
        return out.toString().trim();
    }

    private String firstAddress(String text) {
        Matcher matcher = Pattern.compile("(\\[[0-9A-Fa-f:.]+\\]:\\d{2,5}|(?:\\d{1,3}\\.){3}\\d{1,3}:\\d{2,5}|[A-Za-z0-9.-]+:\\d{2,5})").matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String firstMatch(String text, String regex) {
        if (text == null) return "";
        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
        return matcher.find() ? matcher.group() : "";
    }

    private int firstIndexOf(String text, char a, char b) {
        int ia = text.indexOf(a);
        int ib = text.indexOf(b);
        if (ia < 0) return ib;
        if (ib < 0) return ia;
        return Math.min(ia, ib);
    }

    private String urlDecodeSafe(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }

    private String normalizeEditableSubscription(String value) {
        String text = value == null ? "" : value.trim();
        String decoded = decodeBase64Subscription(text);
        if (decoded != null && !decoded.trim().isEmpty()) {
            return mergeLines("", decoded.trim());
        }
        if (looksLikeEditableSubscription(text)) {
            return mergeLines("", text);
        }
        return text;
    }

    private String decodeBase64Subscription(String value) {
        if (value == null) return null;
        String text = value.trim();
        if (text.isEmpty()) return null;
        if (looksLikeEditableSubscription(text)) return null;
        String compact = text.replaceAll("\\s+", "");
        if (compact.length() < 12 || !compact.matches("[A-Za-z0-9+/=_-]+")) return null;

        List<String> attempts = new ArrayList<>();
        attempts.add(compact);
        attempts.add(padBase64(compact));
        attempts.add(compact.replace('-', '+').replace('_', '/'));
        attempts.add(padBase64(compact.replace('-', '+').replace('_', '/')));

        for (String candidate : attempts) {
            String decoded = tryDecodeBase64(candidate, false);
            if (decoded != null) return decoded;
            decoded = tryDecodeBase64(candidate, true);
            if (decoded != null) return decoded;
        }
        return null;
    }

    private String tryDecodeBase64(String value, boolean urlSafe) {
        try {
            byte[] bytes = urlSafe ? Base64.getUrlDecoder().decode(value) : Base64.getDecoder().decode(value);
            String decoded = new String(bytes, StandardCharsets.UTF_8).trim();
            if (looksLikeEditableSubscription(decoded)) return decoded;
        } catch (Exception ignored) {
        }
        return null;
    }

    private String padBase64(String value) {
        int mod = value.length() % 4;
        if (mod == 0) return value;
        StringBuilder out = new StringBuilder(value);
        for (int i = mod; i < 4; i++) out.append("=");
        return out.toString();
    }

    private boolean looksLikeEditableSubscription(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("vless://") || lower.contains("trojan://") || lower.contains("ss://") || lower.contains("vmess://")) {
            return true;
        }
        return text.matches("(?s).*\\b(?:\\d{1,3}\\.){3}\\d{1,3}:\\d{2,5}.*");
    }

    private String mergeLines(String existing, String added) {
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        for (String block : new String[]{existing, added}) {
            if (block == null) continue;
            for (String raw : block.split("\\r?\\n")) {
                String line = raw.trim();
                if (!line.isEmpty()) lines.add(line);
            }
        }
        StringBuilder out = new StringBuilder();
        for (String line : lines) out.append(line).append("\n");
        return out.toString().trim();
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
        status(share ? "正在保存并复制分享 URL" : "正在同步 TextSpace");

        new Thread(() -> {
            try {
                saveConfig();
                String noteId = currentNote == null ? "" : currentNote.id;
                String title = textspaceTitleEdit.getText().toString().trim();
                String content = normalizeEditableSubscription(textspaceContentEdit.getText().toString());
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
                    if (currentNote.shareToken == null || currentNote.shareToken.trim().isEmpty()) {
                        JSONObject sharedJson = new JSONObject(textspaceRequest("POST", "/api/notes/" + urlPart(currentNote.id) + "/share", "{}"));
                        currentNote = parseNote(sharedJson);
                    }
                    shareUrl = textspaceBaseUrl() + "/sub/" + urlPart(currentNote.shareToken);
                }

                String finalTitle = title;
                String finalContent = content;
                String finalShareUrl = shareUrl;
                ui.post(() -> {
                    textspaceTitleEdit.setText(finalTitle);
                    textspaceContentEdit.setText(finalContent);
                    upsertNoteSummary(currentNote);
                    updateTextspaceSpinner();
                    selectNoteInSpinner(currentNote.id);
                    if (!finalShareUrl.isEmpty()) {
                        copyText("textspace-share-url", finalShareUrl, "分享 URL 已复制");
                    }
                    status(share ? "已复制分享 URL：" + finalShareUrl : "已同步 TextSpace：" + finalTitle);
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
        shareNoteButton.setEnabled(enabled);
        manageNodesButton.setEnabled(enabled);
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
        textspaceContentEdit.setText(normalizeEditableSubscription(note.content));
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

    private String textspaceRequest(String method, String path, String body) throws Exception {
        URL url = new URL(textspaceBaseUrl() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", "Bearer " + textspaceToken());
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "CFMobileOptimizer/1.22");
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
        initRegionState();
        String raw = region == null ? "" : region.trim();
        String upper = raw.toUpperCase(Locale.ROOT);
        if (upper.startsWith("UK") || upper.contains("英国") || upper.contains("英國")) return "GB";
        if (upper.contains("美國")) return "US";
        if (upper.contains("韓國")) return "KR";
        if (upper.contains("台灣")) return "TW";
        for (RegionItem item : regionItems) {
            if (upper.startsWith(item.code) || (!item.name.isEmpty() && upper.contains(item.name.toUpperCase(Locale.ROOT)))) {
                return item.code;
            }
        }
        return cleanRegionCode(upper);
    }

    private String regionName(String region) {
        String code = normalizeRegion(region);
        for (RegionItem item : regionItems) {
            if (code.equals(item.code)) return item.name;
        }
        return code.isEmpty() ? "未知" : code;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    static class RegionItem {
        final String code;
        final String name;
        final boolean custom;

        RegionItem(String code, String name, boolean custom) {
            this.code = code == null ? "" : code;
            this.name = name == null ? "" : name;
            this.custom = custom;
        }
    }

    static class NodeLine {
        final int lineIndex;
        final String rawLine;
        final String address;
        final String region;
        final String proxy;
        final String remark;
        final String display;
        final String searchText;

        NodeLine(int lineIndex, String rawLine, String address, String region, String proxy, String remark, String display) {
            this.lineIndex = lineIndex;
            this.rawLine = rawLine == null ? "" : rawLine;
            this.address = address == null ? "" : address;
            this.region = region == null ? "" : region;
            this.proxy = proxy == null ? "" : proxy;
            this.remark = remark == null ? "" : remark;
            this.display = display == null ? this.address : display;
            this.searchText = (this.rawLine + " " + this.address + " " + this.region + " " + this.proxy + " " + this.remark + " " + this.display)
                    .toLowerCase(Locale.ROOT);
        }
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
        String bodySnippet = "";

        boolean ok() {
            return status >= 200 && status < 300 && bytes >= MIN_REAL_BYTES;
        }
    }
}
