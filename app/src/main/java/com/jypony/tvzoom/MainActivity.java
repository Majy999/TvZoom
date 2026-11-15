package com.jypony.tvzoom;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int[] PRESETS = {50, 57, 60, 70, 80, 90, 95, 100};
    private static final int BG = Color.rgb(16, 17, 20);
    private static final int PANEL = Color.rgb(28, 30, 36);
    private static final int POSITION_PANEL = Color.rgb(22, 24, 29);
    private static final int TEXT = Color.rgb(238, 241, 247);
    private static final int MUTED = Color.rgb(151, 160, 176);
    private static final int ACCENT = Color.rgb(77, 163, 255);

    private TextView percentView;
    private TextView statusView;
    private SeekBar seekBar;
    private LinearLayout presetRow;
    private int[] presetIds;
    private TextView positionView;
    private Button applyButton;
    private Button resetButton;
    private Button moveUpButton;
    private Button moveDownButton;
    private Button moveLeftButton;
    private Button moveRightButton;
    private Button centerButton;
    private Switch autoRestoreSwitch;
    private Switch adbKeepAliveSwitch;
    private boolean busy;
    private int selectedPercent;
    private int selectedOffsetX;
    private int selectedOffsetY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SettingsStore.migrateFromCredentialStorage(this);
        selectedPercent = SettingsStore.getPercent(this);
        selectedOffsetX = SettingsStore.getOffsetX(this);
        selectedOffsetY = SettingsStore.getOffsetY(this);
        setContentView(buildUi());
        updateSelection();
        focusSelectedPreset();
        showStatus(selectedPercent >= 100
                ? "当前设置：全屏"
                : "当前设置：" + selectedPercent + "% · " + formatOffset());
    }

    @Override
    protected void onResume() {
        super.onResume();
        BootReceiver.scheduleBoot(this, 1000L);
        BootReceiver.scheduleWatchdog(this);
        BootReceiver.scheduleAdbKeeper(this);
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setFocusable(false);
        scroll.setFocusableInTouchMode(false);
        scroll.setSmoothScrollingEnabled(false);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setFocusable(false);
        root.setFocusableInTouchMode(false);
        root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        int pad = dp(48);
        root.setPadding(pad, dp(36), pad, dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("电视画面缩放");
        title.setTextColor(TEXT);
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("整幅画面等比缩小，默认居中；可用方向按钮移动位置，不会裁剪内容。");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(10), 0, dp(24));
        root.addView(subtitle, matchWrap());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackgroundColor(PANEL);
        card.setPadding(dp(36), dp(26), dp(36), dp(30));
        root.addView(card, matchWrap());

        percentView = new TextView(this);
        percentView.setTextColor(TEXT);
        percentView.setTextSize(58);
        percentView.setTypeface(Typeface.DEFAULT_BOLD);
        percentView.setGravity(Gravity.CENTER);
        card.addView(percentView, matchWrap());

        seekBar = new SeekBar(this);
        seekBar.setId(View.generateViewId());
        seekBar.setMax(50);
        seekBar.setProgress(0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    selectedPercent = 50 + progress;
                    updateSelection();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                dp(620), ViewGroup.LayoutParams.WRAP_CONTENT);
        seekParams.topMargin = dp(10);
        card.addView(seekBar, seekParams);

        TextView hint = new TextView(this);
        hint.setText("可调范围 50% – 100%");
        hint.setTextColor(MUTED);
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(4), 0, dp(22));
        card.addView(hint, matchWrap());

        presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetRow.setGravity(Gravity.CENTER);
        presetIds = new int[PRESETS.length];
        for (int i = 0; i < PRESETS.length; i++) {
            int preset = PRESETS[i];
            Button button = new Button(this);
            button.setId(View.generateViewId());
            presetIds[i] = button.getId();
            button.setText(preset == 100 ? "全屏" : preset + "%");
            button.setTextSize(15);
            button.setAllCaps(false);
            button.setBackground(presetBackground());
            button.setOnClickListener(v -> {
                selectedPercent = preset;
                updateSelection();
                applySelected();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, dp(52), 1f);
            lp.setMargins(dp(4), 0, dp(4), 0);
            presetRow.addView(button, lp);
        }
        for (int i = 0; i < presetRow.getChildCount(); i++) {
            View button = presetRow.getChildAt(i);
            button.setNextFocusUpId(seekBar.getId());
            button.setNextFocusLeftId(presetIds[Math.max(0, i - 1)]);
            button.setNextFocusRightId(
                    presetIds[Math.min(PRESETS.length - 1, i + 1)]);
        }
        card.addView(presetRow, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, dp(26), 0, 0);

        applyButton = new Button(this);
        applyButton.setId(View.generateViewId());
        applyButton.setText("应用缩放");
        applyButton.setTextSize(18);
        applyButton.setAllCaps(false);
        applyButton.setOnClickListener(v -> applySelected());
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(
                dp(250), dp(62));
        applyLp.setMargins(dp(6), 0, dp(10), 0);
        actions.addView(applyButton, applyLp);

        resetButton = new Button(this);
        resetButton.setId(View.generateViewId());
        resetButton.setText("恢复全屏");
        resetButton.setTextSize(18);
        resetButton.setAllCaps(false);
        resetButton.setOnClickListener(v -> {
            selectedPercent = 100;
            selectedOffsetX = 0;
            selectedOffsetY = 0;
            updateSelection();
            applySelected();
        });
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                dp(250), dp(62));
        resetLp.setMargins(dp(10), 0, dp(6), 0);
        actions.addView(resetButton, resetLp);

        card.addView(actions, matchWrap());

        LinearLayout positionCard = new LinearLayout(this);
        positionCard.setOrientation(LinearLayout.VERTICAL);
        positionCard.setGravity(Gravity.CENTER);
        positionCard.setBackgroundColor(POSITION_PANEL);
        positionCard.setPadding(dp(36), dp(20), dp(36), dp(22));
        LinearLayout.LayoutParams positionCardParams = matchWrap();
        positionCardParams.topMargin = dp(22);
        card.addView(positionCard, positionCardParams);

        TextView positionTitle = new TextView(this);
        positionTitle.setText("画面位置");
        positionTitle.setTextColor(TEXT);
        positionTitle.setTextSize(18);
        positionTitle.setTypeface(Typeface.DEFAULT_BOLD);
        positionTitle.setGravity(Gravity.CENTER);
        positionCard.addView(positionTitle, matchWrap());

        positionView = new TextView(this);
        positionView.setTextColor(MUTED);
        positionView.setTextSize(14);
        positionView.setGravity(Gravity.CENTER);
        positionView.setPadding(0, dp(4), 0, dp(12));
        positionCard.addView(positionView, matchWrap());

        moveUpButton = createPositionButton("↑ 上移");
        moveDownButton = createPositionButton("↓ 下移");
        moveLeftButton = createPositionButton("← 左移");
        moveRightButton = createPositionButton("→ 右移");
        centerButton = createPositionButton("居中");

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER);
        addPositionSpacer(topRow);
        topRow.addView(moveUpButton, positionButtonParams());
        addPositionSpacer(topRow);
        positionCard.addView(topRow, matchWrap());

        LinearLayout middleRow = new LinearLayout(this);
        middleRow.setOrientation(LinearLayout.HORIZONTAL);
        middleRow.setGravity(Gravity.CENTER);
        middleRow.setPadding(0, dp(8), 0, dp(8));
        middleRow.addView(moveLeftButton, positionButtonParams());
        middleRow.addView(centerButton, positionButtonParams());
        middleRow.addView(moveRightButton, positionButtonParams());
        positionCard.addView(middleRow, matchWrap());

        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER);
        addPositionSpacer(bottomRow);
        bottomRow.addView(moveDownButton, positionButtonParams());
        addPositionSpacer(bottomRow);
        positionCard.addView(bottomRow, matchWrap());

        final int moveStep = dp(24);
        moveUpButton.setOnClickListener(v -> applyMove(0, -moveStep));
        moveDownButton.setOnClickListener(v -> applyMove(0, moveStep));
        moveLeftButton.setOnClickListener(v -> applyMove(-moveStep, 0));
        moveRightButton.setOnClickListener(v -> applyMove(moveStep, 0));
        centerButton.setOnClickListener(v -> centerSelected());

        moveUpButton.setNextFocusLeftId(moveUpButton.getId());
        moveUpButton.setNextFocusRightId(moveUpButton.getId());
        moveUpButton.setNextFocusDownId(centerButton.getId());
        moveLeftButton.setNextFocusUpId(moveUpButton.getId());
        moveLeftButton.setNextFocusDownId(moveDownButton.getId());
        moveLeftButton.setNextFocusLeftId(moveLeftButton.getId());
        moveLeftButton.setNextFocusRightId(centerButton.getId());
        centerButton.setNextFocusUpId(moveUpButton.getId());
        centerButton.setNextFocusDownId(moveDownButton.getId());
        centerButton.setNextFocusLeftId(moveLeftButton.getId());
        centerButton.setNextFocusRightId(moveRightButton.getId());
        moveRightButton.setNextFocusUpId(moveUpButton.getId());
        moveRightButton.setNextFocusDownId(moveDownButton.getId());
        moveRightButton.setNextFocusLeftId(centerButton.getId());
        moveRightButton.setNextFocusRightId(moveRightButton.getId());
        moveDownButton.setNextFocusUpId(centerButton.getId());
        moveDownButton.setNextFocusLeftId(moveDownButton.getId());
        moveDownButton.setNextFocusRightId(moveDownButton.getId());

        for (int i = 0; i < presetRow.getChildCount(); i++) {
            presetRow.getChildAt(i).setNextFocusDownId(applyButton.getId());
        }
        applyButton.setNextFocusLeftId(applyButton.getId());
        applyButton.setNextFocusRightId(resetButton.getId());
        resetButton.setNextFocusLeftId(applyButton.getId());
        resetButton.setNextFocusRightId(resetButton.getId());

        autoRestoreSwitch = new Switch(this);
        autoRestoreSwitch.setId(View.generateViewId());
        autoRestoreSwitch.setText("开机自动恢复上次缩放");
        autoRestoreSwitch.setTextColor(TEXT);
        autoRestoreSwitch.setTextSize(16);
        autoRestoreSwitch.setChecked(SettingsStore.isAutoRestore(this));
        autoRestoreSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            SettingsStore.setAutoRestore(this, checked);
            if (checked) {
                BootReceiver.scheduleBoot(this, 1000L);
                BootReceiver.scheduleWatchdog(this);
            } else {
                BootReceiver.cancelScaleJobs(this);
            }
        });
        LinearLayout.LayoutParams switchParams = matchWrap();
        switchParams.topMargin = dp(22);
        switchParams.leftMargin = dp(8);
        card.addView(autoRestoreSwitch, switchParams);

        adbKeepAliveSwitch = new Switch(this);
        adbKeepAliveSwitch.setId(View.generateViewId());
        adbKeepAliveSwitch.setText("保持 ADB 调试开启");
        adbKeepAliveSwitch.setTextColor(TEXT);
        adbKeepAliveSwitch.setTextSize(16);
        adbKeepAliveSwitch.setChecked(SettingsStore.isAdbKeepAlive(this));
        adbKeepAliveSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            SettingsStore.setAdbKeepAlive(this, checked);
            if (checked) {
                BootReceiver.scheduleAdbKeeper(this);
                showStatus(AdbKeeper.ensureEnabled(this).message);
            } else {
                BootReceiver.cancelAdbKeeper(this);
            }
        });
        LinearLayout.LayoutParams adbSwitchParams = matchWrap();
        adbSwitchParams.topMargin = dp(10);
        adbSwitchParams.leftMargin = dp(8);
        card.addView(adbKeepAliveSwitch, adbSwitchParams);

        applyButton.setNextFocusDownId(moveUpButton.getId());
        resetButton.setNextFocusDownId(moveUpButton.getId());
        moveDownButton.setNextFocusDownId(autoRestoreSwitch.getId());
        autoRestoreSwitch.setNextFocusDownId(adbKeepAliveSwitch.getId());
        adbKeepAliveSwitch.setNextFocusUpId(autoRestoreSwitch.getId());
        moveUpButton.setNextFocusUpId(applyButton.getId());
        autoRestoreSwitch.setNextFocusUpId(moveDownButton.getId());

        statusView = new TextView(this);
        statusView.setTextColor(MUTED);
        statusView.setTextSize(15);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, dp(24), 0, 0);
        root.addView(statusView, matchWrap());

        TextView footer = new TextView(this);
        footer.setText("开启下方开关后，开机将重放上次比例并恢复 ADB。首次使用若弹出“允许 USB 调试”，请选择始终允许。");
        footer.setTextColor(MUTED);
        footer.setTextSize(13);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(18), 0, 0);
        root.addView(footer, matchWrap());
        return scroll;
    }

    private void updateSelection() {
        int percent = Math.max(50, Math.min(100, selectedPercent));
        selectedPercent = percent;
        percentView.setText(percent + "%");
        seekBar.setProgress(percent - 50);
        int selectedIndex = nearestPresetIndex(percent);
        for (int i = 0; i < presetRow.getChildCount(); i++) {
            Button button = (Button) presetRow.getChildAt(i);
            boolean active = PRESETS[i] == percent;
            boolean fullScreen = PRESETS[i] == 100 && percent >= 98;
            button.setTextColor(active || fullScreen ? Color.WHITE : TEXT);
            button.setSelected(active || fullScreen);
        }
        if (selectedIndex >= 0) {
            seekBar.setNextFocusDownId(presetIds[selectedIndex]);
            applyButton.setNextFocusUpId(presetIds[selectedIndex]);
            resetButton.setNextFocusUpId(presetIds[selectedIndex]);
        }
        updatePositionUi();
    }

    private void focusSelectedPreset() {
        final int index = nearestPresetIndex(selectedPercent);
        if (index < 0 || presetRow == null) {
            return;
        }
        presetRow.post(new Runnable() {
            @Override
            public void run() {
                presetRow.getChildAt(index).requestFocus();
            }
        });
    }

    private int nearestPresetIndex(int percent) {
        int best = -1;
        int distance = Integer.MAX_VALUE;
        for (int i = 0; i < PRESETS.length; i++) {
            int candidate = Math.abs(PRESETS[i] - percent);
            if (candidate < distance) {
                distance = candidate;
                best = i;
            }
        }
        return best;
    }

    private StateListDrawable presetBackground() {
        int radius = dp(6);
        StateListDrawable states = new StateListDrawable();

        GradientDrawable focused = new GradientDrawable();
        focused.setColor(Color.rgb(42, 102, 176));
        focused.setCornerRadius(radius);
        focused.setStroke(dp(3), Color.WHITE);
        states.addState(new int[] { android.R.attr.state_focused }, focused);

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(Color.rgb(60, 130, 210));
        pressed.setCornerRadius(radius);
        states.addState(new int[] { android.R.attr.state_pressed }, pressed);

        GradientDrawable selected = new GradientDrawable();
        selected.setColor(ACCENT);
        selected.setCornerRadius(radius);
        states.addState(new int[] { android.R.attr.state_selected }, selected);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.rgb(47, 51, 60));
        normal.setCornerRadius(radius);
        states.addState(new int[] {}, normal);
        return states;
    }

    private Button createPositionButton(String text) {
        Button button = new Button(this);
        button.setId(View.generateViewId());
        button.setText(text);
        button.setTextSize(17);
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setBackground(presetBackground());
        return button;
    }

    private void addPositionSpacer(LinearLayout row) {
        View spacer = new View(this);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
    }

    private LinearLayout.LayoutParams positionButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(54), 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
    }

    private void updatePositionUi() {
        if (positionView == null) {
            return;
        }
        if (selectedPercent >= 100) {
            positionView.setText("全屏时没有偏移空间");
        } else {
            positionView.setText(formatOffset());
        }
    }

    private String formatOffset() {
        return "X " + signed(selectedOffsetX) + " px · Y "
                + signed(selectedOffsetY) + " px";
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private void centerSelected() {
        if (busy) {
            return;
        }
        if (selectedPercent >= 100) {
            showStatus("全屏时画面已经居中");
            return;
        }
        applyPosition(0, 0, "正在居中 …", "已居中");
    }

    private void applySelected() {
        applySelected(selectedPercent, selectedOffsetX, selectedOffsetY, true);
    }

    private void applyMove(int dx, int dy) {
        if (busy) {
            return;
        }
        if (selectedPercent >= 100) {
            showStatus("全屏时没有可移动空间，请先选择较小比例");
            return;
        }
        applyPosition(
                selectedOffsetX + dx,
                selectedOffsetY + dy,
                "正在移动画面 …",
                "已移动");
    }

    private void applyPosition(final int offsetX, final int offsetY,
                               final String pendingMessage,
                               final String successMessage) {
        final int percent = selectedPercent;
        final View focused = getCurrentFocus();
        setBusy(true);
        showStatus(pendingMessage);
        new Thread(() -> {
            ZoomController.Result result = ZoomController.apply(
                    this, percent, offsetX, offsetY);
            runOnUiThread(() -> {
                setBusy(false);
                if (result.success) {
                    selectedOffsetX = result.offsetX;
                    selectedOffsetY = result.offsetY;
                    SettingsStore.setOffsets(this, selectedOffsetX, selectedOffsetY);
                    updatePositionUi();
                    showStatus(successMessage + " · " + formatOffset());
                } else {
                    showStatus("调整失败：" + result.message);
                }
                if (focused != null && focused.isShown() && focused.isEnabled()) {
                    focused.requestFocus();
                }
            });
        }, "tvzoom-position").start();
    }

    private void applySelected(final int percent, final int offsetX,
                               final int offsetY, final boolean focusPreset) {
        if (busy) {
            return;
        }
        setBusy(true);
        showStatus("正在应用 " + percent + "% …");
        new Thread(() -> {
            ZoomController.Result result = ZoomController.apply(
                    this, percent, offsetX, offsetY);
            runOnUiThread(() -> {
                setBusy(false);
                if (result.success) {
                    selectedPercent = percent;
                    selectedOffsetX = percent >= 100 ? 0 : result.offsetX;
                    selectedOffsetY = percent >= 100 ? 0 : result.offsetY;
                    SettingsStore.setPercent(this, percent);
                    if (percent >= 100) {
                        SettingsStore.clearOffsets(this);
                    } else {
                        SettingsStore.setOffsets(this, selectedOffsetX, selectedOffsetY);
                    }
                    SettingsStore.resetRetry(this);
                    if (SettingsStore.isAutoRestore(this)) {
                        BootReceiver.scheduleWatchdog(this);
                    }
                    updateSelection();
                    if (percent >= 100) {
                        showStatus("已恢复全屏");
                    } else if (SettingsStore.isAutoRestore(this)) {
                        showStatus("已应用 " + percent + "% · "
                                + formatOffset() + "，重启后会自动恢复");
                    } else {
                        showStatus("已应用 " + percent + "% · "
                                + formatOffset() + "，开机自动恢复已关闭");
                    }
                } else {
                    showStatus("应用失败：" + result.message);
                }
                if (focusPreset) {
                    focusSelectedPreset();
                }
            });
        }, "tvzoom-apply-now").start();
    }

    private void setBusy(boolean value) {
        busy = value;
        applyButton.setEnabled(!value);
        resetButton.setEnabled(!value);
        seekBar.setEnabled(!value);
        moveUpButton.setEnabled(!value);
        moveDownButton.setEnabled(!value);
        moveLeftButton.setEnabled(!value);
        moveRightButton.setEnabled(!value);
        centerButton.setEnabled(!value);
        for (int i = 0; i < presetRow.getChildCount(); i++) {
            presetRow.getChildAt(i).setEnabled(!value);
        }
    }

    private void showStatus(String text) {
        statusView.setText(text);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
