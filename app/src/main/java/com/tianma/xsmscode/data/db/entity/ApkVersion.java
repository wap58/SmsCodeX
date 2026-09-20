package com.tianma.xsmscode.data.db.entity;

/**
 * Apk version info
 */
public class ApkVersion implements Comparable<ApkVersion> {

    private final String mVersionName;
    private final String mVersionInfo;

    public ApkVersion(String versionName, String versionInfo) {
        mVersionName = versionName;
        mVersionInfo = versionInfo;
    }

    public String getVersionInfo() {
        return mVersionInfo;
    }

    public String getVersionName() {
        return mVersionName;
    }

    @Override
    public String toString() {
        return "ApkVersion{" +
                "mVersionName='" + mVersionName + '\'' +
                ", mVersionInfo='" + mVersionInfo + '\'' +
                '}';
    }

    @Override
    public int compareTo(ApkVersion that) {
        if (that == null) {
            return 1;
        }

        int[] thisParts = splitVersion(this.getVersionName());
        int[] thatParts = splitVersion(that.getVersionName());

        int maxLength = Math.max(thisParts.length, thatParts.length);
        for (int i = 0; i < maxLength; i++) {
            int thisPart = i < thisParts.length ? thisParts[i] : 0;
            int thatPart = i < thatParts.length ? thatParts[i] : 0;
            if (thisPart < thatPart) {
                return -1;
            } else if (thisPart > thatPart) {
                return 1;
            }
        }
        return 0;
    }

    /**
     * 解析版本号为数字段数组。
     *
     * <p>2026-09-20：曾出现 versionName 含构建号（如 {@code 3.0.5(68)}）的情况，
     * 此时直接 {@code Integer.parseInt} 会抛 NumberFormatException 导致
     * 检查更新时崩溃。现版本名已改回纯语义版本，但保留括号剥离作为防御：
     * 兼容历史版本与外部来源（如 GitHub Release 标题）可能带括号的情形。
     * 非数字段按 0 处理，避免脏数据导致崩溃。
     */
    private static int[] splitVersion(String versionName) {
        if (versionName == null) {
            return new int[0];
        }
        // 去掉 (68) 之类的构建号后缀
        String core = versionName.replaceAll("\\(.*?\\)", "").trim();
        String[] parts = core.split("\\.");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                nums[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                nums[i] = 0;
            }
        }
        return nums;
    }
}
