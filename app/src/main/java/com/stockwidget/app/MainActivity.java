package com.stockwidget.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(7, 12, 24);
    private static final int TEXT = Color.rgb(248, 250, 252);
    private static final int MUTED = Color.rgb(148, 163, 184);
    private static final int ACCENT = Color.rgb(56, 189, 248);
    private static final int RED = Color.rgb(248, 113, 113);
    private static final int BLUE = Color.rgb(96, 165, 250);
    private static final int LINE = Color.rgb(30, 41, 59);

    private LinearLayout quoteList;
    private TextView statusText;
    private final List<StockItem> stocks = new ArrayList<>();
    private volatile boolean isRefreshing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        stocks.clear();
        stocks.addAll(StockRepository.loadStocks(this));
        UpdateScheduler.applySettings(getApplicationContext());
        refreshQuotes();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.setBackgroundColor(BG);
        scroll.addView(root);

        LinearLayout header = verticalBox();
        header.setPadding(0, 0, 0, dp(6));
        LinearLayout headerTop = new LinearLayout(this);
        headerTop.setOrientation(LinearLayout.HORIZONTAL);
        headerTop.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleBox = verticalBox();
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.BOTTOM);
        TextView title = text("주식 위젯", 22, TEXT, true);
        titleRow.addView(title);
        String versionName = getVersionName();
        TextView version = text("  " + versionName, 11, MUTED, false);
        version.setPadding(0, 0, 0, dp(3));
        titleRow.addView(version);
        titleBox.addView(titleRow);

        statusText = text("대기 중", 12, MUTED, false);
        statusText.setPadding(0, dp(3), 0, 0);
        titleBox.addView(statusText);
        headerTop.addView(titleBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout iconBar = new LinearLayout(this);
        iconBar.setOrientation(LinearLayout.HORIZONTAL);
        iconBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView refreshIcon = iconButton("⟳", "주가 새로고침");
        refreshIcon.setOnClickListener(v -> refreshQuotes());
        iconBar.addView(refreshIcon);

        TextView settingsIcon = iconButton("⚙", "설정");
        settingsIcon.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        LinearLayout.LayoutParams settingsIconParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        settingsIconParams.setMargins(dp(6), 0, 0, 0);
        iconBar.addView(settingsIcon, settingsIconParams);

        headerTop.addView(iconBar);
        header.addView(headerTop);

        addBlock(root, header);

        quoteList = verticalBox();
        root.addView(quoteList);

        setContentView(scroll);
    }

    private void refreshQuotes() {
        if (isRefreshing) {
            return;
        }

        boolean marketOpen = MarketHours.isRegularMarketOpen();
        boolean afterClose = MarketHours.isAfterRegularMarketClose();
        if (!marketOpen && !afterClose) {
            List<StockQuote> cached = StockRepository.loadQuoteCache(this, stocks);
            if (!cached.isEmpty()) {
                renderQuotes(cached);
                statusText.setText(MarketHours.appStatusText());
                statusText.setTextColor(MUTED);
                new Thread(() -> WidgetUpdater.updateAll(this)).start();
                return;
            }
        }

        isRefreshing = true;
        String marketLabel = marketOpen ? "장중" : MarketHours.statusLabel();
        statusText.setText(marketLabel + " · 조회 중...");
        statusText.setTextColor(ACCENT);
        quoteList.removeAllViews();

        new Thread(() -> {
            try {
                List<StockQuote> quotes = NaverFinanceClient.fetchQuotes(new ArrayList<>(stocks));
                if (NaverFinanceClient.hasSuccessfulQuote(quotes)) {
                    StockRepository.saveQuoteCache(this, quotes);
                } else {
                    List<StockQuote> cached = StockRepository.loadQuoteCache(this, stocks);
                    if (!cached.isEmpty()) {
                        quotes = cached;
                    }
                }
                List<StockQuote> finalQuotes = quotes;
                runOnUiThread(() -> {
                    renderQuotes(finalQuotes);
                    String updated = new SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(new Date());
                    boolean hasDelayed = hasDelayedQuote(finalQuotes);
                    String suffix = marketOpen ? (hasDelayed ? "갱신 (Yahoo 15분 지연)" : "갱신") : "마감 정보";
                    statusText.setText(marketLabel + " · " + updated + " " + suffix);
                    statusText.setTextColor(MUTED);
                    isRefreshing = false;
                });
                WidgetUpdater.updateAll(this);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    renderCachedOrPausedRows();
                    statusText.setText("조회 실패 · 저장된 정보 표시");
                    statusText.setTextColor(MUTED);
                    isRefreshing = false;
                });
            }
        }).start();
    }

    private boolean hasDelayedQuote(List<StockQuote> quotes) {
        for (StockQuote q : quotes) {
            if (q.isDelayed()) return true;
        }
        return false;
    }

    private void renderCachedOrPausedRows() {
        List<StockQuote> cached = StockRepository.loadQuoteCache(this, stocks);
        if (!cached.isEmpty()) {
            renderQuotes(cached);
            return;
        }
        renderMarketPausedRows();
    }

    private void renderMarketPausedRows() {
        quoteList.removeAllViews();
        for (StockItem stock : stocks) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(4), dp(6), dp(4), dp(5));
            row.setBackgroundColor(BG);

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout left = verticalBox();
            TextView name = text(stock.name, 18, TEXT, false);
            name.setSingleLine(true);
            left.addView(name);

            View nameLine = new View(this);
            nameLine.setBackgroundColor(Color.rgb(75, 85, 99));
            LinearLayout.LayoutParams nameLineParams = new LinearLayout.LayoutParams(dp(82), dp(1));
            nameLineParams.setMargins(0, dp(2), 0, dp(2));
            left.addView(nameLine, nameLineParams);

            TextView code = text(stock.code, 12, MUTED, false);
            left.addView(code);
            top.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            LinearLayout right = verticalBox();
            right.setGravity(Gravity.END);
            TextView status = text(MarketHours.statusLabel(), 17, MUTED, true);
            status.setGravity(Gravity.END);
            right.addView(status);

            TextView detail = text(MarketHours.detailText(), 13, MUTED, false);
            detail.setGravity(Gravity.END);
            right.addView(detail);
            top.addView(right, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(top);

            View divider = new View(this);
            divider.setBackgroundColor(LINE);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(1)
            );
            dividerParams.setMargins(0, dp(6), 0, 0);
            row.addView(divider, dividerParams);
            addQuoteBlock(quoteList, row);
        }
    }

    private void renderQuotes(List<StockQuote> quotes) {
        quoteList.removeAllViews();
        for (StockQuote quote : quotes) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(4), dp(6), dp(4), dp(5));
            row.setBackgroundColor(BG);

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout left = verticalBox();
            TextView name = text(quote.item.name, 18, TEXT, false);
            name.setSingleLine(true);
            left.addView(name);

            View nameLine = new View(this);
            nameLine.setBackgroundColor(Color.rgb(75, 85, 99));
            LinearLayout.LayoutParams nameLineParams = new LinearLayout.LayoutParams(dp(82), dp(1));
            nameLineParams.setMargins(0, dp(2), 0, dp(2));
            left.addView(nameLine, nameLineParams);

            TextView code = text(quote.item.code, 12, MUTED, false);
            left.addView(code);
            top.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            if (quote.hasError()) {
                TextView error = text("조회 실패", 16, RED, true);
                error.setGravity(Gravity.END);
                top.addView(error, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                row.addView(top);
                row.addView(text(quote.error, 12, RED, false));
            } else {
                int color = quote.up ? RED : BLUE;
                LinearLayout right = verticalBox();
                right.setGravity(Gravity.END);

                TextView price = text(StockQuoteFormatter.formatPrice(quote.price), 20, color, false);
                price.setGravity(Gravity.END);
                right.addView(price);

                TextView change = text(changeMarker(quote) + " "
                        + StockQuoteFormatter.formatSignedChange(quote)
                        + "  "
                        + StockQuoteFormatter.formatSignedRate(quote), 16, color, false);
                change.setGravity(Gravity.END);
                right.addView(change);
                top.addView(right, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                row.addView(top);

                LinearLayout bottom = new LinearLayout(this);
                bottom.setOrientation(LinearLayout.HORIZONTAL);
                bottom.setGravity(Gravity.CENTER_VERTICAL);
                bottom.setPadding(0, dp(5), 0, 0);
                addInlineInfo(bottom, "고", StockQuoteFormatter.formatOptionalNumber(quote.high), RED);
                addInlineInfo(bottom, "저", StockQuoteFormatter.formatOptionalNumber(quote.low), BLUE);
                addInlineInfo(bottom, "거래량", StockQuoteFormatter.formatOptionalNumber(quote.volume), MUTED);
                row.addView(bottom);
            }

            View divider = new View(this);
            divider.setBackgroundColor(Color.rgb(30, 64, 175));
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(1)
            );
            dividerParams.setMargins(0, dp(6), 0, 0);
            row.addView(divider, dividerParams);
            addQuoteBlock(quoteList, row);
        }
    }

    private void addInlineInfo(LinearLayout parent, String label, String value, int valueColor) {
        TextView labelView = text(label + " ", 12, MUTED, false);
        parent.addView(labelView);

        TextView valueView = text(value + " ", 12, valueColor, false);
        parent.addView(valueView);
    }

    private String changeMarker(StockQuote quote) {
        return quote.up ? "▲" : "▼";
    }

    private TextView section(String label) {
        TextView view = text(label, 16, TEXT, true);
        view.setPadding(0, dp(20), 0, dp(8));
        return view;
    }

    private LinearLayout verticalBox() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private TextView iconButton(String value, String description) {
        TextView view = text(value, 18, Color.rgb(203, 213, 225), true);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(description);
        view.setBackground(background(BG, Color.rgb(30, 41, 59), 999));
        view.setMinWidth(dp(34));
        view.setMinHeight(dp(34));
        view.setPadding(0, 0, 0, dp(1));
        return view;
    }

    private GradientDrawable background(int color, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void addQuoteBlock(LinearLayout parent, View child) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(2));
        parent.addView(child, params);
    }

    private void addBlock(LinearLayout parent, View child) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(6), 0, dp(6));
        parent.addView(child, params);
    }

    private String getVersionName() {
        try {
            return "v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
