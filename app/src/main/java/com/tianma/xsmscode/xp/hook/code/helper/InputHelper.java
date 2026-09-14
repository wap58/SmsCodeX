package com.tianma.xsmscode.xp.hook.code.helper;

import android.annotation.SuppressLint;
import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import com.tianma.xsmscode.common.utils.XLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Helper for InputMethod Input Characters.<br/>
 * Refer: com.android.commands.input.Input
 *
 * 2026-09-12 重写：旧实现依赖 de.robv XposedHelpers.callMethod(obj, name, Class[], Object[])，
 * 而本项目自带 shim 缺少该重载，调用退化为把 {Class[], Object[]} 当普通实参模糊匹配，
 * injectInputEvent 永远解析不到 → 自动输入无声失败（原版模块由 LSPosed 提供的正版
 * de.robv 实现接住同名调用，故老共存版正常）。
 *
 * 新实现：纯 JDK 反射，不依赖任何 Xposed API，A14~A17 行为一致：
 *  - 主通道 InputManager.getInstance().injectInputEvent(InputEvent,int)（签名自 2013 年未变）
 *  - 兜底通道 InputManagerGlobal（A12+；防 A17 移除 InputManager 旧入口）
 *  - 按"方法名+参数个数+可赋值性"匹配（KeyEvent 是 InputEvent 子类，精确匹配会漏）
 *  - 检查 boolean 返回值：false=事件被系统丢弃，显式抛异常交给上层降级，不再无声吞掉
 */
public class InputHelper {

    /** InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH 自 A4.3 起恒为 2 */
    private static final int MODE_WAIT_FOR_FINISH_FALLBACK = 2;

    private InputHelper() {
    }

    /**
     * refer: com.android.commands.input.Input#sendText()
     *
     * @throws Throwable 无 INJECT_EVENTS 权限、方法解析失败或事件被系统丢弃时抛出
     */
    public static void sendText(String text) throws Throwable {
        StringBuilder sb = new StringBuilder(text);
        boolean escapeFlag = false;
        for (int i = 0; i < sb.length(); i++) {
            if (escapeFlag) {
                escapeFlag = false;
                if (sb.charAt(i) == 's') {
                    sb.setCharAt(i, ' ');
                    sb.deleteCharAt(--i);
                }
            }
            if (sb.charAt(i) == '%') {
                escapeFlag = true;
            }
        }

        char[] chars = sb.toString().toCharArray();
        KeyCharacterMap kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        KeyEvent[] events = kcm.getEvents(chars);
        if (events == null || events.length == 0) {
            throw new IllegalStateException("KeyCharacterMap produced no events for: " + text);
        }
        for (KeyEvent keyEvent : events) {
            if (keyEvent.getSource() != InputDevice.SOURCE_KEYBOARD) {
                keyEvent.setSource(InputDevice.SOURCE_KEYBOARD);
            }
            injectKeyEvent(keyEvent);
        }
    }

    public static void sendKeyEvent(int inputSource, int keyCode, boolean longpress) throws Throwable {
        long now = SystemClock.uptimeMillis();
        injectKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, inputSource));
        if (longpress) {
            injectKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 1, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_LONG_PRESS, inputSource));
        }
        injectKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, inputSource));
    }

    /**
     * Ctrl+V 粘贴——第二层降级：剪贴板此时已持有验证码（CopyToClipboardAction 先于本动作执行）。
     * 仍走按键注入（需 INJECT_EVENTS），但粘贴语义对部分输入框兼容性更好。
     */
    public static void sendPasteViaCtrlV() throws Throwable {
        long now = SystemClock.uptimeMillis();
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        injectKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_V, 0, meta,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD));
        injectKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_V, 0, meta,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD));
    }

    /**
     * Root fallback: run "input text" as root (no INJECT_EVENTS needed).
     *
     * @return true on success
     */
    public static boolean sendTextViaRoot(String text) {
        try {
            if (text == null || !text.matches("[0-9a-zA-Z]+")) {
                return false; // 只对常规验证码启用 root 路径，避免命令注入
            }
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "input text " + text});
            int exit = p.waitFor();
            if (exit != 0) {
                byte[] err = new byte[512];
                int n = p.getErrorStream().read(err);
                XLog.d("sendTextViaRoot: su exit=%d err=%s", exit,
                        n > 0 ? new String(err, 0, n).trim() : "<empty>");
            }
            try {
                p.getErrorStream().close();
                p.getInputStream().close();
                p.getOutputStream().close();
            } catch (Throwable ignored) {
            }
            return exit == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 注入核心：双通道 + 返回值检查
    // ------------------------------------------------------------------

    @SuppressLint("PrivateApi")
    private static void injectKeyEvent(KeyEvent keyEvent) throws Throwable {
        Throwable lastError;
        // 通道1：InputManager（A4.3~A16 稳定存在；A12+ 内部委托 InputManagerGlobal）
        try {
            Method getInstance = InputManager.class.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            Object im = getInstance.invoke(null);
            if (im == null) {
                throw new IllegalStateException("InputManager.getInstance() returned null");
            }
            Method inject = findInjectMethod(im.getClass());
            checkResult(inject.invoke(im, keyEvent, getInjectMode()), keyEvent);
            return;
        } catch (Throwable t) {
            lastError = t;
        }
        // 通道2：InputManagerGlobal 直连（A12+；防未来版本移除 InputManager 旧入口）
        try {
            Class<?> imgClazz = Class.forName("android.hardware.input.InputManagerGlobal");
            Method getInstance = imgClazz.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            Object img = getInstance.invoke(null);
            if (img == null) {
                throw new IllegalStateException("InputManagerGlobal.getInstance() returned null");
            }
            Method inject = findInjectMethod(img.getClass());
            checkResult(inject.invoke(img, keyEvent, getInjectMode()), keyEvent);
        } catch (Throwable t) {
            t.addSuppressed(lastError);
            throw t;
        }
    }

    /** 精确匹配优先，失败则按 名称+参数个数+可赋值性 匹配（KeyEvent→InputEvent 子类场景）。 */
    private static Method findInjectMethod(Class<?> clazz) throws NoSuchMethodException {
        try {
            Method m = clazz.getMethod("injectInputEvent", InputEvent.class, int.class);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException ignored) {
        }
        for (Method m : clazz.getMethods()) {
            if (!"injectInputEvent".equals(m.getName())) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 2 && p[0].isAssignableFrom(KeyEvent.class)
                    && (p[1] == int.class || p[1] == Integer.class)) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new NoSuchMethodException("injectInputEvent(InputEvent,int) not found on " + clazz.getName());
    }

    private static int getInjectMode() {
        for (String className : new String[]{
                "android.hardware.input.InputManager",
                "android.hardware.input.InputManagerGlobal"}) {
            try {
                Class<?> c = Class.forName(className);
                Field f = c.getDeclaredField("INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH");
                f.setAccessible(true);
                return f.getInt(null);
            } catch (Throwable ignored) {
            }
        }
        return MODE_WAIT_FOR_FINISH_FALLBACK;
    }

    private static void checkResult(Object result, KeyEvent keyEvent) {
        if (result instanceof Boolean && !((Boolean) result)) {
            throw new IllegalStateException("injectInputEvent returned false (dropped, keyCode="
                    + keyEvent.getKeyCode() + ")");
        }
    }
}
