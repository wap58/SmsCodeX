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
     * <p>2026-09-20：版本名现含构建号（如 {@code 3.0.5(60)}），
     * 直接 {@code Integer.parseInt} 会抛 NumberFormatException 导致崩溃。
     * 这里先剥离括号部分（构建号仅用于显示与 versionCode，不参与版本高低比较），
     * 再逐段解析；非数字段按 0 处理，避免脏数据导致崩溃。
     */
    private static int[] splitVersion(String versionName) {
        if (versionName == null) {
            return new int[0];
        }
        // 去掉 (60) 之类的构建号后缀
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
