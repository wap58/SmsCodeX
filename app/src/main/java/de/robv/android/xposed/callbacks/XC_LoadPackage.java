package de.robv.android.xposed.callbacks;

public class XC_LoadPackage {

    public XC_LoadPackage() {
    }

    public static class LoadPackageParam {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
        public boolean isFirstApplication;

        public LoadPackageParam() {
        }

        public LoadPackageParam(LoadPackageParam lpp) {
            this.packageName = lpp.packageName;
            this.processName = lpp.processName;
            this.classLoader = lpp.classLoader;
            this.isFirstApplication = lpp.isFirstApplication;
        }
    }
}
