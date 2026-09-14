package de.robv.android.xposed;

import android.content.SharedPreferences;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;

/**
 * 主路径：读取模块应用 shared_prefs XML 文件（原版机制，app 设置即时生效）。
 * 兜底：libxposed 远程偏好。
 */
public class XSharedPreferences {

    private final String mPrefFileName;
    private File mFile;
    private Map<String, String> mValues = Collections.emptyMap();
    private SharedPreferences mRemotePrefs = null;
    private boolean mUseRemote = false;
    private long mLastModified = -1;

    public XSharedPreferences(String packageName) {
        this(packageName, packageName + "_preferences");
    }

    public XSharedPreferences(String packageName, String prefFileName) {
        mPrefFileName = prefFileName;
        mFile = new File("/data/data/" + packageName + "/shared_prefs/" + prefFileName + ".xml");
        reload();
    }

    public void makeWorldReadable() {
    }

    public void reload() {
        try {
            if (mFile != null && mFile.canRead()) {
                long lastModified = mFile.lastModified();
                if (lastModified != mLastModified || mValues.isEmpty()) {
                    mValues = parseXml(mFile);
                    mLastModified = lastModified;
                    mUseRemote = false;
                }
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (!mUseRemote) {
                XposedInterface xi = XposedBridge.getXposedInterface();
                if (xi != null) {
                    mRemotePrefs = xi.getRemotePreferences(mPrefFileName);
                    mUseRemote = true;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public boolean getFile() {
        return mFile != null && mFile.canRead();
    }

    public boolean getBoolean(String key, boolean defValue) {
        if (mUseRemote && mRemotePrefs != null) return mRemotePrefs.getBoolean(key, defValue);
        String v = mValues.get(key);
        return v != null ? Boolean.parseBoolean(v) : defValue;
    }

    public String getString(String key, String defValue) {
        if (mUseRemote && mRemotePrefs != null) return mRemotePrefs.getString(key, defValue);
        String v = mValues.get(key);
        return v != null ? v : defValue;
    }

    public int getInt(String key, int defValue) {
        if (mUseRemote && mRemotePrefs != null) return mRemotePrefs.getInt(key, defValue);
        String v = mValues.get(key);
        try {
            return v != null ? Integer.parseInt(v) : defValue;
        } catch (NumberFormatException e) {
            return defValue;
        }
    }

    public long getLong(String key, long defValue) {
        if (mUseRemote && mRemotePrefs != null) return mRemotePrefs.getLong(key, defValue);
        String v = mValues.get(key);
        try {
            return v != null ? Long.parseLong(v) : defValue;
        } catch (NumberFormatException e) {
            return defValue;
        }
    }

    public float getFloat(String key, float defValue) {
        if (mUseRemote && mRemotePrefs != null) return mRemotePrefs.getFloat(key, defValue);
        String v = mValues.get(key);
        try {
            return v != null ? Float.parseFloat(v) : defValue;
        } catch (NumberFormatException e) {
            return defValue;
        }
    }

    public Set<String> getStringSet(String key, Set<String> defValues) {
        if (mUseRemote && mRemotePrefs != null) return mRemotePrefs.getStringSet(key, defValues);
        return defValues;
    }

    public boolean contains(String key) {
        if (mUseRemote && mRemotePrefs != null) return mRemotePrefs.contains(key);
        return mValues.containsKey(key);
    }

    public Map<String, ?> getAll() {
        if (mUseRemote && mRemotePrefs != null) return mRemotePrefs.getAll();
        return mValues;
    }

    private static Map<String, String> parseXml(File file) throws Exception {
        Map<String, String> out = new HashMap<>();
        FileInputStream fis = new FileInputStream(file);
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new InputStreamReader(fis, StandardCharsets.UTF_8));
            int event = parser.getEventType();
            String name = null;
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    name = parser.getName();
                    String key = parser.getAttributeValue(null, "name");
                    if (key != null && "string".equals(name)) {
                        // string 类型的值在文本节点而非 value 属性（旧实现漏掉，
                        // 导致延时/关键词等字符串设置在 hook 进程永远读成默认值）
                        out.put(key, parser.nextText());
                        event = parser.getEventType();
                        continue;
                    }
                    String value = parser.getAttributeValue(null, "value");
                    if (key != null && value != null
                            && ("boolean".equals(name) || "int".equals(name)
                            || "long".equals(name) || "float".equals(name))) {
                        out.put(key, value);
                    }
                }
                event = parser.next();
            }
        } finally {
            try {
                fis.close();
            } catch (Throwable ignored) {
            }
        }
        return out;
    }
}
