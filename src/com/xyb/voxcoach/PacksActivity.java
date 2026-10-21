package com.xyb.voxcoach;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Content library: subscribe to a catalog (index.json), install / update packs one by one or all at once. */
public class PacksActivity extends Activity {

    private final Handler h = new Handler();
    private EditText url;
    private TextView status;
    private LinearLayout catalogBox, installedBox;
    private JSONArray catalog;
    private String catalogUrl;
    private boolean busy;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout body = UI.screen(this, "内容库", null);

        LinearLayout src = UI.card(this);
        src.addView(UI.text(this, "📦 场景包订阅", 16, UI.TEXT, true));
        src.addView(UI.text(this, "内容库是一个目录文件（index.json），里面列出各个场景包。仓库里的包有更新时，这里点「检查更新」就能一键升级；"
                + "也可以打开每天自动更新。学习记录不会因为更新而丢失。", 12, UI.SUB, false), UI.matchWrap(this, 0, 4, 0, 8));
        url = UI.input(this, PackSync.JSDELIVR);
        url.setSingleLine(true);
        url.setTextSize(13);
        url.setText(Store.packUrl(this));
        src.addView(url);
        LinearLayout mirrors = UI.hbox(this);
        TextView m1 = UI.chip(this, "国内镜像 jsDelivr", false);
        m1.setTextSize(12);
        m1.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { url.setText(PackSync.JSDELIVR); check(); } });
        TextView m2 = UI.chip(this, "GitHub 原始（需代理）", false);
        m2.setTextSize(12);
        m2.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { url.setText(PackSync.GITHUB_RAW); check(); } });
        mirrors.addView(m1, UI.lpw(this, 1, 3));
        mirrors.addView(m2, UI.lpw(this, 1, 3));
        src.addView(mirrors, UI.matchWrap(this, -3, 6, -3, 4));
        LinearLayout btns = UI.hbox(this);
        TextView chk = UI.button(this, "🔄 检查更新", true);
        chk.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { check(); } });
        TextView imp = UI.button(this, "📂 导入文件", false);
        imp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, 41);
                } catch (Exception e) { UI.toast(PacksActivity.this, "无法打开文件选择器"); }
            }
        });
        btns.addView(chk, UI.lpw(this, 1, 3));
        btns.addView(imp, UI.lpw(this, 1, 3));
        src.addView(btns, UI.matchWrap(this, -3, 0, -3, 0));
        final TextView auto = UI.chip(this, autoLabel(), Store.autoUpdatePacks(this));
        auto.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Store.setAutoUpdatePacks(PacksActivity.this, !Store.autoUpdatePacks(PacksActivity.this));
                auto.setText(autoLabel());
                UI.setChip(PacksActivity.this, auto, Store.autoUpdatePacks(PacksActivity.this));
            }
        });
        src.addView(auto, UI.matchWrap(this, 0, 8, 0, 0));
        status = UI.text(this, "", 13, UI.SUB, false);
        src.addView(status, UI.matchWrap(this, 0, 8, 0, 0));
        body.addView(src, UI.matchWrap(this, 0, 4, 0, 12));

        catalogBox = UI.card(this);
        catalogBox.setVisibility(View.GONE);
        body.addView(catalogBox, UI.matchWrap(this, 0, 0, 0, 12));

        installedBox = UI.card(this);
        body.addView(installedBox, UI.matchWrap(this, 0, 0, 0, 12));
        renderInstalled();
        check();
    }

    private String autoLabel() {
        return Store.autoUpdatePacks(this) ? "✓ 每天打开 App 时自动更新已安装的包" : "自动更新已关闭（点击开启）";
    }

    // ------------------------------------------------------------------ catalog

    private void check() {
        if (busy) return;
        final String u = url.getText().toString().trim();
        if (!u.startsWith("http")) { status.setText("请填写 http(s) 开头的地址"); return; }
        Store.setPackUrl(this, u);
        busy = true;
        status.setTextColor(UI.SUB);
        status.setText("正在读取内容库…");
        new Thread(new Runnable() {
            public void run() {
                String err = null, json = null;
                try { json = PackSync.fetch(u); } catch (Exception e) { err = e.getMessage(); }
                final String fe = err, fj = json;
                h.post(new Runnable() {
                    public void run() {
                        busy = false;
                        if (isFinishing() || isDestroyed()) return;
                        if (fe != null) {
                            status.setTextColor(UI.RED);
                            status.setText("❌ 读取失败：" + fe + "\n国内网络建议用「国内镜像 jsDelivr」；刚推送到 GitHub 的新内容，镜像可能要几小时后才更新。");
                            return;
                        }
                        JSONArray entries = PackSync.catalogEntries(fj);
                        if (entries == null) { installSingle(fj); return; }
                        catalog = entries;
                        catalogUrl = u;
                        renderCatalog();
                    }
                });
            }
        }).start();
    }

    private void installSingle(String json) {
        try {
            String msg = Store.installPacks(this, json);
            status.setTextColor(UI.GREEN);
            status.setText("✅ " + msg);
            renderInstalled();
        } catch (Exception e) {
            status.setTextColor(UI.RED);
            status.setText("❌ 这不是有效的内容包或目录：" + e.getMessage());
        }
    }

    private void renderCatalog() {
        catalogBox.removeAllViews();
        catalogBox.setVisibility(View.VISIBLE);
        final List<JSONObject> pending = new ArrayList<JSONObject>();
        for (int i = 0; i < catalog.length(); i++) {
            JSONObject e = catalog.optJSONObject(i);
            if (e != null && Store.installedVersion(this, e.optString("id")) < e.optInt("version", 1)) pending.add(e);
        }
        LinearLayout head = UI.hbox(this);
        head.addView(UI.text(this, "可用内容包 · " + catalog.length(), 16, UI.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (!pending.isEmpty()) {
            TextView all = UI.button(this, "全部安装/更新 (" + pending.size() + ")", true);
            all.setTextSize(13);
            all.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { install(pending); } });
            head.addView(all);
        }
        catalogBox.addView(head);
        status.setTextColor(pending.isEmpty() ? UI.GREEN : UI.SUB);
        status.setText(pending.isEmpty() ? "✅ 已安装的内容包都是最新的" : "有 " + pending.size() + " 个内容包可以安装或更新");

        String curLang = Store.lang(this);
        for (int pass = 0; pass < 2; pass++) { // current language first
            for (int i = 0; i < catalog.length(); i++) {
                final JSONObject e = catalog.optJSONObject(i);
                if (e == null) continue;
                boolean mine = curLang.equals(e.optString("lang"));
                if ((pass == 0) != mine) continue;
                catalogBox.addView(entryRow(e), UI.matchWrap(this, 0, 8, 0, 0));
            }
        }
    }

    private View entryRow(final JSONObject e) {
        LinearLayout r = UI.hbox(this);
        r.setBackground(UI.round(this, UI.BG, 12));
        int p = UI.dp(this, 12);
        r.setPadding(p, p, p, p);
        LinearLayout t = UI.vbox(this);
        String flag = "ja".equals(e.optString("lang")) ? "🇯🇵 " : "en".equals(e.optString("lang")) ? "🇺🇸 " : "";
        t.addView(UI.text(this, flag + e.optString("name", e.optString("id")) + "  v" + e.optInt("version", 1), 15, UI.TEXT, true));
        if (e.optString("desc").length() > 0) t.addView(UI.text(this, e.optString("desc"), 12, UI.SUB, false));
        JSONObject cnt = e.optJSONObject("counts");
        if (cnt != null) t.addView(UI.text(this, "场景 " + cnt.optInt("scenarios") + " · 句子 " + cnt.optInt("sentences")
                + (cnt.optInt("pairs") > 0 ? " · 对立词 " + cnt.optInt("pairs") : ""), 12, UI.HINT, false));
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        int have = Store.installedVersion(this, e.optString("id"));
        int ver = e.optInt("version", 1);
        String label = have < 0 ? "安装" : have < ver ? "更新" : "已最新";
        TextView act = UI.button(this, label, have < ver);
        act.setTextSize(13);
        act.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                List<JSONObject> one = new ArrayList<JSONObject>();
                one.add(e);
                install(one);
            }
        });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.setMargins(UI.dp(this, 8), 0, 0, 0);
        r.addView(act, ap);
        return r;
    }

    private void install(final List<JSONObject> entries) {
        if (busy || catalogUrl == null) return;
        busy = true;
        status.setTextColor(UI.SUB);
        status.setText("正在下载 " + entries.size() + " 个内容包…");
        new Thread(new Runnable() {
            public void run() {
                final StringBuilder sb = new StringBuilder();
                int ok = 0;
                for (JSONObject e : entries) {
                    try {
                        sb.append("✅ ").append(PackSync.installEntry(PacksActivity.this, catalogUrl, e)).append("\n");
                        ok++;
                    } catch (Exception ex) {
                        sb.append("❌ ").append(e.optString("name", e.optString("id"))).append("：").append(ex.getMessage()).append("\n");
                    }
                }
                final int fok = ok;
                h.post(new Runnable() {
                    public void run() {
                        busy = false;
                        if (isFinishing() || isDestroyed()) return;
                        renderCatalog();
                        renderInstalled();
                        status.setTextColor(fok == entries.size() ? UI.GREEN : UI.RED);
                        status.setText(sb.toString().trim() + (fok > 0 ? "\n新场景在「AI 情景对话」里，新句子会出现在「发音跟读」和「听写」中。" : ""));
                    }
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------ installed

    private void renderInstalled() {
        installedBox.removeAllViews();
        List<JSONObject> packs = Store.packs(this);
        installedBox.addView(UI.text(this, "已安装 · " + packs.size(), 16, UI.TEXT, true));
        if (packs.isEmpty()) {
            installedBox.addView(UI.text(this, "还没有安装内容包", 12, UI.HINT, false), UI.matchWrap(this, 0, 6, 0, 0));
            return;
        }
        for (final JSONObject p : packs) {
            LinearLayout r = UI.hbox(this);
            int pd = UI.dp(this, 10);
            r.setPadding(0, pd, 0, pd);
            int ns = p.optJSONArray("sentences") == null ? 0 : p.optJSONArray("sentences").length();
            int nc = p.optJSONArray("scenarios") == null ? 0 : p.optJSONArray("scenarios").length();
            int np = p.optJSONArray("pairs") == null ? 0 : p.optJSONArray("pairs").length();
            r.addView(UI.text(this, p.optString("name", p.optString("id")) + "  v" + p.optInt("version", 1)
                            + "\n场景 " + nc + " · 句子 " + ns + (np > 0 ? " · 对立词 " + np : ""), 13, UI.TEXT, false),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView del = UI.text(this, "删除", 13, UI.RED, true);
            del.setPadding(pd, pd, 0, pd);
            del.setGravity(Gravity.CENTER);
            del.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    new AlertDialog.Builder(PacksActivity.this).setMessage("删除内容包「" + p.optString("name") + "」？（已有的练习得分不会删除）")
                            .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface d, int w) {
                                    Store.removePack(PacksActivity.this, p.optString("id"));
                                    renderInstalled();
                                    if (catalog != null) renderCatalog();
                                }
                            }).setNegativeButton("取消", null).show();
                }
            });
            r.addView(del);
            installedBox.addView(r);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 41 || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            String json = CloudAudio.readString(getContentResolver().openInputStream(data.getData()));
            if (PackSync.catalogEntries(json) != null) {
                status.setTextColor(UI.RED);
                status.setText("这是目录文件（index.json），请导入具体的内容包文件，或用上面的链接订阅");
                return;
            }
            installSingle(json);
        } catch (Exception e) {
            status.setTextColor(UI.RED);
            status.setText("❌ 导入失败：" + e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        String u = url.getText().toString().trim();
        if (u.startsWith("http")) Store.setPackUrl(this, u);
    }
}
