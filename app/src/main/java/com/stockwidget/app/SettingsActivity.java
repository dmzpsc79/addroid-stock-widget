package com.stockwidget.app;

import android.app.Activity;
import android.content.ClipData;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {
    private static final int BG = Color.rgb(7, 12, 24);
    private static final int CARD = Color.rgb(17, 24, 39);
    private static final int CARD_SOFT = Color.rgb(24, 35, 54);
    private static final int BORDER = Color.rgb(51, 65, 85);
    private static final int TEXT = Color.rgb(248, 250, 252);
    private static final int MUTED = Color.rgb(148, 163, 184);
    private static final int ACCENT = Color.rgb(56, 189, 248);
    private static final int RED = Color.rgb(248, 113, 113);

    private LinearLayout searchResults;
    private LinearLayout stockList;
    private ScrollView settingsScroll;
    private EditText searchInput;
    private EditText codeInput;
    private EditText nameInput;
    private EditText refreshIntervalInput;
    private TextView refreshIntervalStatus;
    private final List<StockItem> stocks = new ArrayList<>();
    private int draggingIndex = -1;
    private int dragTargetIndex = -1;
    private boolean dragDropHandled = false;
    private View activeDragRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        stocks.clear();
        stocks.addAll(StockRepository.loadStocks(this));
        buildUi();
        renderStocks();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        settingsScroll = scroll;
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(12));
        root.setBackgroundColor(BG);
        scroll.addView(root);

        buildHeader(root);
        buildStockListSection(root);
        buildAddStockSection(root);
        buildRefreshIntervalSection(root);

        setContentView(scroll);
    }

    private void buildHeader(LinearLayout root) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(8));

        TextView back = text("←", 24, TEXT, true);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("뒤로가기");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(38), dp(38)));

        TextView title = text("설정", 22, TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        addBlock(root, header);
    }

    private void buildStockListSection(LinearLayout root) {
        root.addView(section("표시 종목"));
        TextView guide = helperText("≡를 길게 눌러 순서를 바꿉니다.");
        root.addView(guide);
        stockList = verticalBox();
        stockList.setOnDragListener(this::handleStockDrop);
        root.addView(stockList);
    }

    private void buildAddStockSection(LinearLayout root) {
        root.addView(section("종목 추가"));
        root.addView(subsection("종목 검색"));
        searchInput = input("종목명 또는 종목 코드 입력");

        Button searchButton = button("검색");
        searchButton.setOnClickListener(v -> searchStocks());
        addInputButtonBlock(root, searchInput, searchButton, dp(82));

        searchResults = verticalBox();
        root.addView(searchResults);

        root.addView(subsection("직접 추가"));
        codeInput = input("종목 코드 예: 005930");
        nameInput = input("종목명 예: 삼성전자");
        addBlock(root, codeInput);
        addBlock(root, nameInput);

        Button addButton = button("종목 추가");
        addButton.setOnClickListener(v -> addStock());
        addBlock(root, addButton);
    }

    private void buildRefreshIntervalSection(LinearLayout root) {
        root.addView(section("위젯 갱신 주기"));
        refreshIntervalStatus = text("", 13, MUTED, false);
        refreshIntervalStatus.setPadding(dp(12), dp(10), dp(12), dp(10));
        refreshIntervalStatus.setBackground(background(CARD, BORDER, 14));
        addBlock(root, refreshIntervalStatus);

        addQuickIntervalRow(root, new int[]{0, 1, 5}, new String[]{"안함", "1분", "5분"});
        addQuickIntervalRow(root, new int[]{10, 30, 60}, new String[]{"10분", "30분", "60분"});

        root.addView(subsection("직접 입력"));
        refreshIntervalInput = input("분 단위 입력 예: 5");
        refreshIntervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        int savedMinutes = StockRepository.loadRefreshIntervalMinutes(this);
        if (savedMinutes > 0) {
            refreshIntervalInput.setText(String.valueOf(savedMinutes));
        }

        Button saveRefreshButton = button("저장");
        saveRefreshButton.setOnClickListener(v -> saveRefreshInterval());
        addInputButtonBlock(root, refreshIntervalInput, saveRefreshButton, dp(82));
        updateRefreshIntervalStatus();
    }

    private void addQuickIntervalRow(LinearLayout root, int[] minutes, String[] labels) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < minutes.length; i++) {
            Button button = smallButton(labels[i], ACCENT);
            int value = minutes[i];
            button.setOnClickListener(v -> setRefreshInterval(value));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            params.setMargins(i == 0 ? 0 : dp(4), 0, i == minutes.length - 1 ? 0 : dp(4), 0);
            row.addView(button, params);
        }
        addBlock(root, row);
    }

    private void searchStocks() {
        String query = searchInput.getText().toString().trim();
        List<StockItem> results = StockCatalog.search(query, 10);
        renderSearchResults(results);
    }

    private void renderSearchResults(List<StockItem> results) {
        searchResults.removeAllViews();
        if (results.isEmpty()) {
            TextView empty = text("검색 결과가 없습니다.", 13, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(14), dp(12), dp(14));
            empty.setBackground(background(CARD, BORDER, 14));
            addBlock(searchResults, empty);
            return;
        }

        for (StockItem item : results) {
            LinearLayout row = rowBase();
            TextView name = text(item.name + "  " + item.code, 15, TEXT, true);
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            Button add = smallButton("추가", ACCENT);
            add.setOnClickListener(v -> addStockItem(item));
            row.addView(add);
            addBlock(searchResults, row);
        }
    }

    private void renderStocks() {
        stockList.removeAllViews();
        draggingIndex = -1;
        dragTargetIndex = -1;
        dragDropHandled = false;
        activeDragRow = null;
        for (int i = 0; i < stocks.size(); i++) {
            int index = i;
            StockItem item = stocks.get(i);
            LinearLayout row = rowBase();
            row.setTag(index);

            TextView dragHandle = text("≡", 24, MUTED, true);
            dragHandle.setGravity(Gravity.CENTER);
            dragHandle.setPadding(0, 0, dp(10), 0);
            dragHandle.setTag(index);
            dragHandle.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() != MotionEvent.ACTION_DOWN) {
                    return false;
                }
                return startStockDrag(view);
            });
            row.addView(dragHandle, new LinearLayout.LayoutParams(dp(34), LinearLayout.LayoutParams.MATCH_PARENT));

            TextView name = text(item.name + "  " + item.code, 15, TEXT, true);
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            Button remove = smallButton("삭제", RED);
            remove.setTag(index);
            remove.setOnClickListener(v -> {
                int removeIndex = (int) v.getTag();
                stocks.remove(removeIndex);
                saveStocks();
            });
            row.addView(remove);
            addBlock(stockList, row);
        }
    }

    private void addStock() {
        String code = codeInput.getText().toString().trim();
        String name = nameInput.getText().toString().trim();

        if (!code.matches("\\d{6}([.]KS|[.]KQ)?") && !code.matches("\\d{6}")) {
            Toast.makeText(this, "종목 코드는 6자리 숫자로 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (name.isEmpty()) {
            Toast.makeText(this, "종목명을 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        addStockItem(new StockItem(code, name));
        codeInput.setText("");
        nameInput.setText("");
    }

    private void addStockItem(StockItem item) {
        for (StockItem stock : stocks) {
            if (stock.code.equals(item.code)) {
                Toast.makeText(this, "이미 추가된 종목입니다.", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        stocks.add(item);
        saveStocks();
    }

    private boolean startStockDrag(View handle) {
        View rowView = (View) handle.getParent();
        Object tag = rowView.getTag();
        if (!(tag instanceof Integer)) {
            return false;
        }

        draggingIndex = (int) tag;
        dragTargetIndex = draggingIndex;
        dragDropHandled = false;
        activeDragRow = rowView;
        rowView.setAlpha(0.35f);
        rowView.setBackground(background(CARD_SOFT, ACCENT, 14));
        settingsScroll.requestDisallowInterceptTouchEvent(true);

        ClipData clipData = ClipData.newPlainText("stock-index", String.valueOf(draggingIndex));
        View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(rowView);
        boolean started;
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            started = rowView.startDragAndDrop(clipData, shadowBuilder, null, 0);
        } else {
            started = rowView.startDrag(clipData, shadowBuilder, null, 0);
        }
        if (!started) {
            resetActiveDragRow();
        }
        return started;
    }

    private boolean handleStockDrop(View view, android.view.DragEvent event) {
        switch (event.getAction()) {
            case android.view.DragEvent.ACTION_DRAG_LOCATION:
                dragTargetIndex = findDragTargetIndexInStockList(event.getY());
                return true;
            case android.view.DragEvent.ACTION_DROP:
                dragTargetIndex = findDragTargetIndexInStockList(event.getY());
                finishStockDrag(true);
                return true;
            case android.view.DragEvent.ACTION_DRAG_ENDED:
                if (!dragDropHandled) {
                    finishStockDrag(false);
                }
                return true;
            default:
                return true;
        }
    }

    private int findDragTargetIndexInStockList(float yInStockList) {
        int childCount = stockList.getChildCount();
        if (childCount == 0) {
            return -1;
        }

        int targetIndex = 0;
        for (int i = 0; i < childCount; i++) {
            if (i == draggingIndex) {
                continue;
            }
            View child = stockList.getChildAt(i);
            float centerY = child.getTop() + child.getHeight() / 2f;
            if (yInStockList > centerY) {
                targetIndex++;
            }
        }
        return Math.max(0, Math.min(targetIndex, childCount - 1));
    }

    private void reorderDraggedStock(int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex < 0 || fromIndex >= stocks.size() || toIndex >= stocks.size()) {
            return;
        }

        StockItem item = stocks.remove(fromIndex);
        stocks.add(toIndex, item);
    }

    private void finishStockDrag(boolean dropped) {
        settingsScroll.requestDisallowInterceptTouchEvent(false);
        int fromIndex = draggingIndex;
        int toIndex = dragTargetIndex;
        dragDropHandled = true;
        draggingIndex = -1;
        dragTargetIndex = -1;

        if (dropped && fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
            reorderDraggedStock(fromIndex, toIndex);
            saveStocks();
            return;
        }

        resetActiveDragRow();
    }

    private void resetActiveDragRow() {
        if (activeDragRow != null) {
            activeDragRow.setAlpha(1.0f);
            activeDragRow.setElevation(0);
            activeDragRow.setBackground(background(CARD, BORDER, 14));
        }
        activeDragRow = null;
        renderStocks();
    }

    private void updateRefreshIntervalStatus() {
        int minutes = StockRepository.loadRefreshIntervalMinutes(this);
        if (minutes <= 0) {
            refreshIntervalStatus.setText("현재 설정: 자동 갱신 안함");
            return;
        }
        refreshIntervalStatus.setText("현재 설정: " + minutes + "분마다 자동 갱신");
    }

    private void setRefreshInterval(int minutes) {
        if (minutes <= 0) {
            disableRefreshInterval();
            return;
        }
        StockRepository.saveRefreshIntervalMinutes(this, minutes);
        UpdateScheduler.applySettings(getApplicationContext());
        refreshIntervalInput.setText(String.valueOf(minutes));
        updateRefreshIntervalStatus();
        Toast.makeText(this, minutes + "분마다 자동 갱신합니다.", Toast.LENGTH_SHORT).show();
    }

    private void disableRefreshInterval() {
        StockRepository.saveRefreshIntervalMinutes(this, 0);
        UpdateScheduler.cancel(getApplicationContext());
        refreshIntervalInput.setText("");
        updateRefreshIntervalStatus();
        Toast.makeText(this, "자동 갱신을 사용하지 않습니다.", Toast.LENGTH_SHORT).show();
    }

    private void saveRefreshInterval() {
        String value = refreshIntervalInput.getText().toString().trim();
        if (value.isEmpty()) {
            disableRefreshInterval();
            return;
        }

        int minutes;
        try {
            minutes = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "갱신 주기는 숫자로 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (minutes <= 0) {
            disableRefreshInterval();
            return;
        }

        setRefreshInterval(minutes);
    }

    private void saveStocks() {
        StockRepository.saveStocks(this, stocks);
        renderStocks();
        new Thread(() -> WidgetUpdater.updateAll(getApplicationContext())).start();
    }

    private LinearLayout rowBase() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(background(CARD, BORDER, 14));
        return row;
    }

    private TextView section(String label) {
        TextView view = text(label, 16, TEXT, true);
        view.setPadding(0, dp(20), 0, dp(8));
        return view;
    }

    private TextView subsection(String label) {
        TextView view = text(label, 13, MUTED, true);
        view.setPadding(0, dp(10), 0, dp(4));
        return view;
    }

    private TextView helperText(String value) {
        TextView view = text(value, 12, MUTED, false);
        view.setPadding(0, 0, 0, dp(4));
        return view;
    }

    private LinearLayout verticalBox() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(background(CARD_SOFT, BORDER, 14));
        return input;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setMinHeight(dp(40));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(background(Color.rgb(37, 99, 235), Color.rgb(59, 130, 246), 14));
        return button;
    }

    private Button smallButton(String label, int color) {
        Button button = button(label);
        button.setTextSize(12);
        button.setMinHeight(dp(34));
        button.setMinWidth(dp(42));
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(background(Color.argb(36, Color.red(color), Color.green(color), Color.blue(color)), color, 999));
        button.setTextColor(color);
        return button;
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

    private LinearLayout card() {
        LinearLayout layout = verticalBox();
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        layout.setBackground(background(CARD, BORDER, 20));
        return layout;
    }

    private GradientDrawable background(int color, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void addInputButtonBlock(LinearLayout parent, View input, Button button, int buttonWidth) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        inputParams.setMargins(0, 0, dp(6), 0);
        row.addView(input, inputParams);

        row.addView(button, new LinearLayout.LayoutParams(buttonWidth, LinearLayout.LayoutParams.WRAP_CONTENT));
        addBlock(parent, row);
    }

    private void addBlock(LinearLayout parent, View child) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(6), 0, dp(6));
        parent.addView(child, params);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
