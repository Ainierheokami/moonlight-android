package com.limelight.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.limelight.BuildConfig;
import com.limelight.LimeLog;
import com.limelight.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class UpdateChecker {
    public static final String AUTO_CHECK_PREF = "checkbox_auto_update_check";

    private static final String LAST_CHECK_PREF = "update_last_successful_check_ms";
    private static final String RELEASE_API_URL =
            "https://api.github.com/repos/Ainierheokami/moonlight-android/releases/tags/latest";
    private static final long AUTO_CHECK_INTERVAL_MS = TimeUnit.DAYS.toMillis(1);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");
    private static final Pattern APK_NAME_PATTERN = Pattern.compile(
            "^Moonlight-(.+)-(debug|release)\\.apk$", Pattern.CASE_INSENSITIVE);
    private static final AtomicBoolean CHECK_IN_PROGRESS = new AtomicBoolean(false);
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private UpdateChecker() {
    }

    public static void checkForUpdates(Activity activity, boolean userInitiated) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        if (!userInitiated) {
            if (!prefs.getBoolean(AUTO_CHECK_PREF, true)) {
                return;
            }

            long lastCheck = prefs.getLong(LAST_CHECK_PREF, 0);
            if (System.currentTimeMillis() - lastCheck < AUTO_CHECK_INTERVAL_MS) {
                return;
            }
        }

        if (!CHECK_IN_PROGRESS.compareAndSet(false, true)) {
            if (userInitiated) {
                Toast.makeText(activity, R.string.update_check_in_progress, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (userInitiated) {
            Toast.makeText(activity, R.string.update_check_in_progress, Toast.LENGTH_SHORT).show();
        }

        Request request = new Request.Builder()
                .url(RELEASE_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Moonlight-Android-Update-Checker")
                .build();

        HTTP_CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                LimeLog.warning("Update check failed: " + e.getMessage());
                finishWithError(activity, userInitiated);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response closeableResponse = response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        LimeLog.warning("Update check returned HTTP " + response.code());
                        finishWithError(activity, userInitiated);
                        return;
                    }

                    ReleaseInfo parsedRelease = parseRelease(response.body().string(), BuildConfig.DEBUG);
                    final ReleaseInfo release;
                    if (BuildConfig.ROOT_BUILD) {
                        // The repository's release workflow currently publishes NonRoot APKs only.
                        release = new ReleaseInfo(parsedRelease.versionName, parsedRelease.releasePageUrl,
                                null, null);
                    }
                    else {
                        release = parsedRelease;
                    }
                    prefs.edit().putLong(LAST_CHECK_PREF, System.currentTimeMillis()).apply();
                    CHECK_IN_PROGRESS.set(false);
                    activity.runOnUiThread(() -> {
                        if (!isActivityUsable(activity)) {
                            return;
                        }

                        if (isNewerVersion(BuildConfig.VERSION_NAME, release.versionName)) {
                            showUpdateDialog(activity, release);
                        }
                        else if (userInitiated) {
                            new AlertDialog.Builder(activity)
                                    .setTitle(R.string.update_no_update_title)
                                    .setMessage(activity.getString(R.string.update_no_update_message,
                                            BuildConfig.VERSION_NAME))
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                        }
                    });
                }
                catch (IOException | JSONException | IllegalArgumentException e) {
                    LimeLog.warning("Unable to parse update response: " + e.getMessage());
                    finishWithError(activity, userInitiated);
                }
            }
        });
    }

    private static void finishWithError(Activity activity, boolean userInitiated) {
        CHECK_IN_PROGRESS.set(false);
        if (!userInitiated) {
            return;
        }

        activity.runOnUiThread(() -> {
            if (isActivityUsable(activity)) {
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.update_check_failed_title)
                        .setMessage(R.string.update_check_failed_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });
    }

    private static void showUpdateDialog(Activity activity, ReleaseInfo release) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(activity.getString(R.string.update_available_message,
                        BuildConfig.VERSION_NAME, release.versionName))
                .setNegativeButton(R.string.update_later, null)
                .setNeutralButton(R.string.update_view_release,
                        (dialog, which) -> openUrl(activity, release.releasePageUrl));

        if (release.apkDownloadUrl != null) {
            builder.setPositiveButton(R.string.update_download,
                    (dialog, which) -> downloadApk(activity, release));
        }

        builder.show();
    }

    private static void downloadApk(Activity activity, ReleaseInfo release) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(release.apkDownloadUrl))
                    .setTitle(activity.getString(R.string.update_download_title, release.versionName))
                    .setDescription(activity.getString(R.string.update_download_description))
                    .setMimeType("application/vnd.android.package-archive")
                    .setAllowedOverMetered(true)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS,
                            System.currentTimeMillis() + "-" + release.apkFileName);
            DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) {
                throw new IllegalStateException("DownloadManager unavailable");
            }
            manager.enqueue(request);
            Toast.makeText(activity, R.string.update_download_started, Toast.LENGTH_LONG).show();
        }
        catch (RuntimeException e) {
            LimeLog.warning("Unable to enqueue update download: " + e.getMessage());
            openUrl(activity, release.apkDownloadUrl);
        }
    }

    private static void openUrl(Activity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        }
        catch (RuntimeException e) {
            Toast.makeText(activity, R.string.update_open_link_failed, Toast.LENGTH_LONG).show();
        }
    }

    private static boolean isActivityUsable(Activity activity) {
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    static ReleaseInfo parseRelease(String json, boolean debugBuild) throws JSONException {
        JSONObject root = new JSONObject(json);
        String releasePageUrl = requireHttpsUrl(root.getString("html_url"));
        JSONArray assets = root.getJSONArray("assets");
        JSONObject selectedAsset = selectApkAsset(assets, debugBuild);
        if (selectedAsset == null) {
            throw new JSONException("Release has no compatible APK asset");
        }

        String fileName = selectedAsset.getString("name");
        String downloadUrl = requireHttpsUrl(selectedAsset.getString("browser_download_url"));
        String versionName = extractVersionFromAssetName(fileName);
        return new ReleaseInfo(versionName, releasePageUrl, downloadUrl, fileName);
    }

    private static JSONObject selectApkAsset(JSONArray assets, boolean debugBuild) throws JSONException {
        String preferredSuffix = debugBuild ? "-debug.apk" : "-release.apk";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String name = asset.optString("name").toLowerCase(Locale.ROOT);
            if (name.endsWith(preferredSuffix)) {
                return asset;
            }
        }
        return null;
    }

    static String extractVersionFromAssetName(String fileName) {
        Matcher matcher = APK_NAME_PATTERN.matcher(fileName);
        if (!matcher.matches() || matcher.group(1).isEmpty()) {
            throw new IllegalArgumentException("Unexpected APK file name: " + fileName);
        }
        return matcher.group(1);
    }

    static boolean isNewerVersion(String currentVersion, String candidateVersion) {
        List<Long> currentParts = numericParts(currentVersion);
        List<Long> candidateParts = numericParts(candidateVersion);
        int count = Math.max(currentParts.size(), candidateParts.size());
        for (int i = 0; i < count; i++) {
            long current = i < currentParts.size() ? currentParts.get(i) : 0;
            long candidate = i < candidateParts.size() ? candidateParts.get(i) : 0;
            if (candidate != current) {
                return candidate > current;
            }
        }
        return false;
    }

    private static List<Long> numericParts(String version) {
        List<Long> parts = new ArrayList<>();
        Matcher matcher = NUMBER_PATTERN.matcher(version);
        while (matcher.find()) {
            try {
                parts.add(Long.parseLong(matcher.group()));
            }
            catch (NumberFormatException e) {
                parts.add(Long.MAX_VALUE);
            }
        }
        return parts;
    }

    private static String requireHttpsUrl(String url) {
        final URI uri;
        try {
            uri = new URI(url);
        }
        catch (URISyntaxException e) {
            throw new IllegalArgumentException("Malformed update URL", e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
                !("github.com".equalsIgnoreCase(uri.getHost()) ||
                        "api.github.com".equalsIgnoreCase(uri.getHost()))) {
            throw new IllegalArgumentException("Unexpected update URL");
        }
        return url;
    }

    static final class ReleaseInfo {
        final String versionName;
        final String releasePageUrl;
        final String apkDownloadUrl;
        final String apkFileName;

        ReleaseInfo(String versionName, String releasePageUrl, String apkDownloadUrl,
                    String apkFileName) {
            this.versionName = versionName;
            this.releasePageUrl = releasePageUrl;
            this.apkDownloadUrl = apkDownloadUrl;
            this.apkFileName = apkFileName;
        }
    }
}
