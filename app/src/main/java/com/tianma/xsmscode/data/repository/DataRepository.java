package com.tianma.xsmscode.data.repository;

import com.tianma.xsmscode.data.db.entity.ApkVersion;
import com.tianma.xsmscode.data.http.ApiConst;
import com.tianma.xsmscode.data.http.service.GithubService;
import com.tianma.xsmscode.data.http.service.ServiceGenerator;

import io.reactivex.Observable;

/**
 * 检测更新（2026-09-15 改版）：直接查询 GitHub Releases（wap58/SmsCodeX）。
 * tag_name 即版本号（允许 v 前缀，解析时剥离），release body 即更新说明。
 */
public class DataRepository {

    private DataRepository() {
    }

    public static Observable<ApkVersion> getLatestVersion() {
        GithubService githubService = ServiceGenerator.getInstance()
                .createService(ApiConst.GITHUB_BASE_URL, GithubService.class);
        return githubService.getLatestRelease(ApiConst.GITHUB_USERNAME, ApiConst.GITHUB_REPO_NAME)
                .map(githubRelease -> {
                    String tagName = githubRelease.getTagName();
                    if (tagName != null && (tagName.startsWith("v") || tagName.startsWith("V"))) {
                        tagName = tagName.substring(1);
                    }
                    String versionInfo = githubRelease.getBody() == null
                            ? "" : githubRelease.getBody().replaceAll("<br/>|<br>", "\n");
                    return new ApkVersion(tagName, versionInfo);
                });
    }

}
