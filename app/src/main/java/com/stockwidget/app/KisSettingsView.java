package com.stockwidget.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class KisSettingsView extends LinearLayout {
    private static final int CARD = Color.rgb(17, 24, 39);
    private static final int CARD_SOFT = Color.rgb(24, 35, 54);
    private static final int BORDER = Color.rgb(51, 65, 85);
    private static final int TEXT = Color.rgb(248, 250, 252);
    private static final int MUTED = Color.rgb(148, 163, 184);
    private static final int ACCENT = Color.rgb(56, 189, 248);
    private static final int RED = Color.rgb(248, 113, 113);

    private final Activity activity;
    private final EditText appKeyInput;
    private final EditText appSecretInput;
    private final CheckBox demoCheck;
    private final TextView status;
    private final Button testButton;

    public KisSettingsView(Activity activity) {
        super(activity);
        this.activity = activity;
        setOrientation(VERTICAL);

        addView(section("한국투자증권 API"));
        addView(helper("현재가 조회에는 App Key와 App Secret이 필요합니다."));

        KisCredentials saved = KisCredentialsStore.loadCredentials(activity);
        appKeyInput = input("App Key");
        appKeyInput.setText(saved.appKey);
        addBlock(appKeyInput);

        appSecretInput = input("App Secret");
        appSecretInput.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        appSecretInput.setText(saved.appSecret);
        addBlock(appSecretInput);

        demoCheck = new CheckBox(activity);
        demoCheck.setText("모의투자 서버 사용");
        demoCheck.setTextColor(TEXT);
        demoCheck.setTextSize(14);
        demoCheck.setChecked(saved.demo);
        addBlock(demoCheck);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(HORIZONTAL);
        Button saveButton = button("저장");
        saveButton.setOnClickListener(v -> saveCredentials(true));
        actions.addView(saveButton, weightedParams(1, 0));

        testButton = button("연결 테스트");
        testButton.setOnClickListener(v -> testConnection());
        actions.addView(testButton, weightedParams(1, dp(6)));
        addBlock(actions);

        status = text(
                saved.isComplete() ? "자격정보가 이 기기에 암호화되어 저장되어 있습니다." : "자격정보를 입력해 주세요.",
                12,
                saved.isComplete() ? MUTED : RED,
                false
        );
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        status.setBackground(background(CARD, BORDER, 8));
        addBlock(status);
    }

    private void testConnection() {
        if (!saveCredentials(false)) {
            return;
        }
        status.setText("한투 API 연결을 확인하고 있습니다.");
        status.setTextColor(ACCENT);
        testButton.setEnabled(false);

        new Thread(() -> {
            List<StockItem> stocks = StockRepository.loadStocks(activity);
            StockItem target = stocks.isEmpty()
                    ? new StockItem("005930", "삼성전자")
                    : stocks.get(0);
            StockQuote quote = KisFinanceClient.testConnection(activity, target);
            activity.runOnUiThread(() -> {
                testButton.setEnabled(true);
                if (quote.hasError()) {
                    status.setText("연결 실패: " + quote.error);
                    status.setTextColor(RED);
                    return;
                }
                status.setText(
                        "연결 성공: "
                                + quote.item.name
                                + " "
                                + StockQuoteFormatter.formatPrice(quote.price)
                                + "원"
                );
                status.setTextColor(ACCENT);
                new Thread(() -> WidgetUpdater.updateAll(activity.getApplicationContext())).start();
            });
        }).start();
    }

    private boolean saveCredentials(boolean showToast) {
        String appKey = appKeyInput.getText().toString().trim();
        String appSecret = appSecretInput.getText().toString().trim();
        if (appKey.isEmpty() || appSecret.isEmpty()) {
            status.setText("App Key와 App Secret을 모두 입력해 주세요.");
            status.setTextColor(RED);
            return false;
        }

        try {
            KisCredentialsStore.saveCredentials(
                    activity,
                    appKey,
                    appSecret,
                    demoCheck.isChecked()
            );
            status.setText("자격정보가 이 기기에 암호화되어 저장되었습니다.");
            status.setTextColor(MUTED);
            if (showToast) {
                Toast.makeText(activity, "한투 API 설정을 저장했습니다.", Toast.LENGTH_SHORT).show();
            }
            return true;
        } catch (Exception e) {
            status.setText("보안 저장소에 자격정보를 저장하지 못했습니다.");
            status.setTextColor(RED);
            return false;
        }
    }

    private EditText input(String hint) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(background(CARD_SOFT, BORDER, 8));
        return input;
    }

    private Button button(String label) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setMinHeight(dp(40));
        button.setBackground(background(Color.rgb(37, 99, 235), Color.rgb(59, 130, 246), 8));
        return button;
    }

    private TextView section(String label) {
        TextView view = text(label, 16, TEXT, true);
        view.setPadding(0, dp(20), 0, dp(8));
        return view;
    }

    private TextView helper(String value) {
        TextView view = text(value, 12, MUTED, false);
        view.setPadding(0, 0, 0, dp(4));
        return view;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.START);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private void addBlock(View child) {
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(6));
        addView(child, params);
    }

    private LayoutParams weightedParams(int weight, int leftMargin) {
        LayoutParams params = new LayoutParams(0, LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(leftMargin, 0, 0, 0);
        return params;
    }

    private GradientDrawable background(int color, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
