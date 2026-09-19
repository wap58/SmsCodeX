package com.tianma.xsmscode.ui.forward;

import android.graphics.Color;

/**
 * 弹窗配色工具（2026-09-19）。
 *
 * <p>本项目 Cyanea 主题下通过主题属性取色会回退失败（SettingsFragment 2026-09-15 已实测），
 * 因此弹窗颜色一律从 Cyanea 配置显式取值。
 *
 * <p>明暗判断用**背景色亮度**而非 {@code Cyanea.isDark()}：
 * 用户可能选的是"浅色基底 + 深色背景"，此时 isDark() 仍为 false，
 * 但界面观感是深色的，按 isDark() 取色会给出白底。
 *
 * <p>弹窗底色对齐"隐私政策"弹窗（MaterialDialog）：它经
 * Theme.AppCompat.Dialog.Alert 取 android:colorBackgroundFloating，
 * 深色下为 Material 标准浮动色 #424242（比 Cyanea 的 #303030 更亮一档）。
 */
final class ChannelDialogTheme {

    /** 深色下的弹窗底色：Material 标准浮动背景色 */
    static final int DIALOG_BG_DARK = 0xFF424242;
    /** 浅色下的弹窗底色 */
    static final int DIALOG_BG_LIGHT = 0xFFFFFFFF;

    private ChannelDialogTheme() {
    }

    /** 当前界面是否为深色（按实际背景亮度判断） */
    static boolean isDark() {
        try {
            int bg = com.jaredrummler.cyanea.Cyanea.getInstance().getBackgroundColor();
            return luminance(bg) < 0.5f;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static int dialogBackground() {
        return isDark() ? DIALOG_BG_DARK : DIALOG_BG_LIGHT;
    }

    static int textColor() {
        return isDark() ? Color.WHITE : 0xFF212121;
    }

    static int hintColor() {
        return isDark() ? 0x66FFFFFF : 0x66000000;
    }

    static int dividerColor() {
        return isDark() ? 0x22FFFFFF : 0x22000000;
    }

    static int accentColor() {
        try {
            return com.jaredrummler.cyanea.Cyanea.getInstance().getAccent();
        } catch (Throwable ignored) {
            return isDark() ? Color.WHITE : 0xFF212121;
        }
    }

    /** 相对亮度（0=黑，1=白），用于判断底色明暗 */
    private static float luminance(int color) {
        return (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f;
    }
}
