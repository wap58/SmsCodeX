package com.tianma.xsmscode.common.utils;

import android.graphics.Color;

import com.jaredrummler.cyanea.Cyanea;

/**
 * 主题取色工具（2026-10-17 新增）。
 *
 * <p>本项目通过主题属性（{@code ?attr/colorPrimary} 等）取色在 Cyanea 下会回退失败
 * （2026-09-15 实测，见 ChannelDialogTheme 注释），因此统一从 Cyanea 配置显式取值。
 *
 * <p>深色判定用<b>背景色亮度</b>而非 {@code Cyanea.isDark()}：用户可能选
 * "浅色基底 + 深色背景"，此时 isDark() 为 false 但观感是深色。
 */
public final class ThemeColors {

    private ThemeColors() {
    }

    /** 强调色（跟随用户所选配色） */
    public static int accent() {
        try {
            int c = Cyanea.getInstance().getAccent();
            if (c != 0) {
                return c;
            }
        } catch (Throwable ignored) {
        }
        return 0xFF2F9D56;
    }

    /** 当前界面是否为深色（按实际背景亮度） */
    public static boolean isDark() {
        try {
            return luminance(Cyanea.getInstance().getBackgroundColor()) < 0.5f;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 把颜色按 alpha 比例叠加透明度（0..1） */
    public static int withAlpha(int color, float alpha) {
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static float luminance(int color) {
        return (0.299f * Color.red(color) + 0.587f * Color.green(color)
                + 0.114f * Color.blue(color)) / 255f;
    }
}
