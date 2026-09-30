package com.limelight.preferences;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaCodecInfo;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.DisplayMetrics;
import android.util.Range;
import android.util.TypedValue;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import com.limelight.utils.OverlayAlertDialog;

import com.limelight.LimeLog;
import com.limelight.BuildConfig;
import com.limelight.PcView;
import com.limelight.R;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.utils.Dialog;
import com.limelight.utils.UiHelper;
import com.limelight.utils.UpdateChecker;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class StreamSettings extends Activity {
    /**
     * A page of the settings UI. The home page links to each section, and a section shows the
     * categories of preferences.xml whose keys are listed here.
     */
    private static final class Section {
        final String id;
        final int titleRes;
        final String[] categoryKeys;

        Section(String id, int titleRes, String... categoryKeys) {
            this.id = id;
            this.titleRes = titleRes;
            this.categoryKeys = categoryKeys;
        }
    }

    private static final Section[] SECTIONS = {
            new Section("video", R.string.settings_section_video,
                    "category_video_quality", "category_video_latency", "category_video_display"),
            new Section("audio", R.string.settings_section_audio,
                    "category_audio_settings"),
            new Section("host", R.string.settings_section_host,
                    "category_host_session", "category_host_display", "category_host_clipboard",
                    "category_host_reconnect"),
            new Section("gamepad", R.string.settings_section_gamepad,
                    "category_gamepad_settings", "category_gamepad_usb", "category_gamepad_mouse",
                    "category_gamepad_feedback"),
            new Section("touch", R.string.settings_section_touch,
                    "category_input_settings", "category_mouse_input", "category_game_menu_gesture"),
            new Section("onscreen", R.string.settings_section_onscreen,
                    "category_onscreen_controls", "category_onscreen_keyboard",
                    "category_onscreen_keyboard_profiles"),
            new Section("overlay", R.string.settings_section_overlay,
                    "category_perf_overlay", "category_debug"),
            new Section("interface", R.string.settings_section_interface,
                    "category_ui_settings"),
            new Section("maintenance", R.string.settings_section_maintenance,
                    "category_app_update", "category_backup_restore"),
    };

    private static final String STATE_CURRENT_SECTION = "currentSection";

    private PreferenceConfiguration previousPrefs;
    private int previousDisplayPixelCount;

    // The section shown on top of the home page, or null when the home page is visible
    private String currentSection;

    // OnBackInvokedCallback on Android 13+ while a section is open (typed as Object for older APIs)
    private Object sectionBackCallback;

    private TextView toolbarTitle;
    private TextView toolbarSubtitle;

    // HACK for Android 9
    static DisplayCutout displayCutoutP;

    private static Section findSection(String id) {
        for (Section section : SECTIONS) {
            if (section.id.equals(id)) {
                return section;
            }
        }
        return null;
    }

    void reloadSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode mode = getWindowManager().getDefaultDisplay().getMode();
            previousDisplayPixelCount = mode.getPhysicalWidth() * mode.getPhysicalHeight();
        }

        // Rebuild the whole stack (home + the open section) so display-dependent values are
        // recomputed without leaving a stale fragment behind the back stack.
        String section = currentSection;
        FragmentManager fragmentManager = getFragmentManager();
        try {
            fragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        } catch (IllegalStateException e) {
            // State already saved; the fragments will be recreated when we come back
            return;
        }

        fragmentManager.beginTransaction().replace(
                R.id.stream_settings, SettingsFragment.newInstance(null)
        ).commitAllowingStateLoss();

        if (section != null && findSection(section) != null) {
            openSection(section, false);
        }
        else {
            currentSection = null;
            updateToolbar();
            updateSectionBackCallback();
        }
    }

    void openSection(String sectionId) {
        openSection(sectionId, true);
    }

    private void openSection(String sectionId, boolean animate) {
        currentSection = sectionId;

        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        if (animate) {
            transaction.setCustomAnimations(
                    R.animator.settings_section_enter, R.animator.settings_section_exit,
                    R.animator.settings_section_pop_enter, R.animator.settings_section_pop_exit);
        }
        transaction.replace(R.id.stream_settings, SettingsFragment.newInstance(sectionId))
                .addToBackStack(sectionId)
                .commitAllowingStateLoss();

        updateToolbar();
        updateSectionBackCallback();
    }

    private void updateToolbar() {
        Section section = currentSection != null ? findSection(currentSection) : null;
        if (section != null) {
            toolbarTitle.setText(section.titleRes);
            toolbarSubtitle.setText(R.string.title_stream_settings);
        }
        else {
            toolbarTitle.setText(R.string.title_stream_settings);
            toolbarSubtitle.setText(R.string.subtitle_stream_settings);
        }
    }

    // With android:enableOnBackInvokedCallback="true", onBackPressed() is not called on Android 13+,
    // so a callback is needed to return from a section to the home page.
    private void updateSectionBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        boolean sectionOpen = currentSection != null;
        if (sectionOpen && sectionBackCallback == null) {
            OnBackInvokedCallback callback = () -> getFragmentManager().popBackStack();
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            sectionBackCallback = callback;
        }
        else if (!sectionOpen && sectionBackCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                    (OnBackInvokedCallback) sectionBackCallback);
            sectionBackCallback = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        previousPrefs = PreferenceConfiguration.readPreferences(this);

        UiHelper.setLocale(this);

        setContentView(R.layout.activity_stream_settings);

        toolbarTitle = findViewById(R.id.settingsTitle);
        toolbarSubtitle = findViewById(R.id.settingsSubtitle);

        if (savedInstanceState != null) {
            currentSection = savedInstanceState.getString(STATE_CURRENT_SECTION);
        }
        updateToolbar();

        findViewById(R.id.settingsBackButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });

        getFragmentManager().addOnBackStackChangedListener(() -> {
            FragmentManager fragmentManager = getFragmentManager();
            int count = fragmentManager.getBackStackEntryCount();
            currentSection = count > 0 ? fragmentManager.getBackStackEntryAt(count - 1).getName() : null;
            updateToolbar();
            updateSectionBackCallback();
        });

        UiHelper.notifyNewRootView(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_CURRENT_SECTION, currentSection);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // We have to use this hack on Android 9 because we don't have Display.getCutout()
        // which was added in Android 10.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            // Insets can be null when the activity is recreated on screen rotation
            // https://stackoverflow.com/questions/61241255/windowinsets-getdisplaycutout-is-null-everywhere-except-within-onattachedtowindo
            WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            if (insets != null) {
                displayCutoutP = insets.getDisplayCutout();
            }
        }

        reloadSettings();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode mode = getWindowManager().getDefaultDisplay().getMode();

            // If the display's physical pixel count has changed, we consider that it's a new display
            // and we should reload our settings (which include display-dependent values).
            //
            // NB: We aren't using displayId here because that stays the same (DEFAULT_DISPLAY) when
            // switching between screens on a foldable device.
            if (mode.getPhysicalWidth() * mode.getPhysicalHeight() != previousDisplayPixelCount) {
                reloadSettings();
            }
        }
    }

    @Override
    // NOTE: This will NOT be called on Android 13+ with android:enableOnBackInvokedCallback="true"
    // (except from the toolbar back button). Sections are closed by sectionBackCallback there.
    public void onBackPressed() {
        if (getFragmentManager().getBackStackEntryCount() > 0) {
            getFragmentManager().popBackStack();
            return;
        }

        finish();

        // Language changes are handled via configuration changes in Android 13+,
        // so manual activity relaunching is no longer required.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            PreferenceConfiguration newPrefs = PreferenceConfiguration.readPreferences(this);
            if (!newPrefs.language.equals(previousPrefs.language)) {
                // Restart the PC view to apply UI changes
                Intent intent = new Intent(this, PcView.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent, null);
            }
        }
    }

    public static class SettingsFragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        private static final String ARG_SECTION = "section";

        // Keep list content readable on tablets and landscape phones
        private static final int MAX_CONTENT_WIDTH_DP = 760;

        // null for the home page
        private String section;

        static SettingsFragment newInstance(String section) {
            SettingsFragment fragment = new SettingsFragment();
            Bundle args = new Bundle();
            args.putString(ARG_SECTION, section);
            fragment.setArguments(args);
            return fragment;
        }

        private int nativeResolutionStartIndex = Integer.MAX_VALUE;
        private boolean nativeFramerateShown = false;

        private void setValue(String preferenceKey, String value) {
            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            pref.setValue(value);
        }

        private void appendPreferenceEntry(ListPreference pref, String newEntryName, String newEntryValue) {
            CharSequence[] newEntries = Arrays.copyOf(pref.getEntries(), pref.getEntries().length + 1);
            CharSequence[] newValues = Arrays.copyOf(pref.getEntryValues(), pref.getEntryValues().length + 1);

            // Add the new option
            newEntries[newEntries.length - 1] = newEntryName;
            newValues[newValues.length - 1] = newEntryValue;

            pref.setEntries(newEntries);
            pref.setEntryValues(newValues);
        }

        private void addNativeResolutionEntry(int nativeWidth, int nativeHeight, boolean insetsRemoved, boolean portrait) {
            ListPreference pref = (ListPreference) findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING);

            String newName;

            if (insetsRemoved) {
                newName = getResources().getString(R.string.resolution_prefix_native_fullscreen);
            }
            else {
                newName = getResources().getString(R.string.resolution_prefix_native);
            }

            if (PreferenceConfiguration.isSquarishScreen(nativeWidth, nativeHeight)) {
                if (portrait) {
                    newName += " " + getResources().getString(R.string.resolution_prefix_native_portrait);
                }
                else {
                    newName += " " + getResources().getString(R.string.resolution_prefix_native_landscape);
                }
            }

            newName += " ("+nativeWidth+"x"+nativeHeight+")";

            String newValue = nativeWidth+"x"+nativeHeight;

            // Check if the native resolution is already present
            for (CharSequence value : pref.getEntryValues()) {
                if (newValue.equals(value.toString())) {
                    // It is present in the default list, so don't add it again
                    return;
                }
            }

            if (pref.getEntryValues().length < nativeResolutionStartIndex) {
                nativeResolutionStartIndex = pref.getEntryValues().length;
            }
            appendPreferenceEntry(pref, newName, newValue);
        }

        private void addNativeResolutionEntries(int nativeWidth, int nativeHeight, boolean insetsRemoved) {
            if (PreferenceConfiguration.isSquarishScreen(nativeWidth, nativeHeight)) {
                addNativeResolutionEntry(nativeHeight, nativeWidth, insetsRemoved, true);
            }
            addNativeResolutionEntry(nativeWidth, nativeHeight, insetsRemoved, false);
        }

        private void addNativeFrameRateEntry(float framerate) {
            int frameRateRounded = Math.round(framerate);
            if (frameRateRounded == 0) {
                return;
            }

            ListPreference pref = (ListPreference) findPreference(PreferenceConfiguration.FPS_PREF_STRING);
            String fpsValue = Integer.toString(frameRateRounded);
            String fpsName = getResources().getString(R.string.resolution_prefix_native) +
                    " (" + fpsValue + " " + getResources().getString(R.string.fps_suffix_fps) + ")";

            // Check if the native frame rate is already present
            for (CharSequence value : pref.getEntryValues()) {
                if (fpsValue.equals(value.toString())) {
                    // It is present in the default list, so don't add it again
                    nativeFramerateShown = false;
                    return;
                }
            }

            appendPreferenceEntry(pref, fpsName, fpsValue);
            nativeFramerateShown = true;
        }

        private void removeValue(String preferenceKey, String value, Runnable onMatched) {
            int matchingCount = 0;

            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            // Count the number of matching entries we'll be removing
            for (CharSequence seq : pref.getEntryValues()) {
                if (seq.toString().equalsIgnoreCase(value)) {
                    matchingCount++;
                }
            }

            // Create the new arrays
            CharSequence[] entries = new CharSequence[pref.getEntries().length-matchingCount];
            CharSequence[] entryValues = new CharSequence[pref.getEntryValues().length-matchingCount];
            int outIndex = 0;
            for (int i = 0; i < pref.getEntryValues().length; i++) {
                if (pref.getEntryValues()[i].toString().equalsIgnoreCase(value)) {
                    // Skip matching values
                    continue;
                }

                entries[outIndex] = pref.getEntries()[i];
                entryValues[outIndex] = pref.getEntryValues()[i];
                outIndex++;
            }

            if (pref.getValue().equalsIgnoreCase(value)) {
                onMatched.run();
            }

            // Update the preference with the new list
            pref.setEntries(entries);
            pref.setEntryValues(entryValues);
        }

        private void resetBitrateToDefault(SharedPreferences prefs, String res, String fps) {
            if (res == null) {
                res = prefs.getString(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.DEFAULT_RESOLUTION);
            }
            if (fps == null) {
                fps = prefs.getString(PreferenceConfiguration.FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS);
            }

            prefs.edit()
                    .putInt(PreferenceConfiguration.BITRATE_PREF_STRING,
                            PreferenceConfiguration.getDefaultBitrate(res, fps))
                    .apply();
        }

        // 2024-11-22 10:35:43 显示精简信息模板设置
        private void showSimplifyPerfOverlayPref(SharedPreferences prefs) {
            Context context = getActivity(); // 或者如果是 AndroidX：getContext()
            String template = prefs.getString(PreferenceConfiguration.EDITTEXT_SIMPLE_PERF_OVERLAY_PREF_STRING, context.getString(R.string.default_template_simple_perf_overlay));

            // 创建一个带滚动的对话框布局
            ScrollView scrollView = new ScrollView(context);
            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            scrollView.setLayoutParams(scrollParams);

            // 创建主布局
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            layout.setLayoutParams(layoutParams);

            // 添加说明文本
            TextView instructionText = new TextView(context);
            instructionText.setText(
                    String.format(
                        context.getString(R.string.instruction_text_simple_perf_overlay),
                        context.getString(R.string.default_template_simple_perf_overlay)
                    )
            );
            instructionText.setPadding(32, 16, 32, 16); // 增加左右内边距
            layout.addView(instructionText);

            // 创建并设置 EditText
            final EditText input = new EditText(context);
            input.setText(template);
            input.setHint("请输入文本");
            input.setPadding(32, input.getPaddingTop(), 32, input.getPaddingBottom()); // 增加左右内边距
//            input.setMinLines(3);
//            input.setMaxLines(5);
//            input.setGravity(Gravity.TOP);


            // 设置 EditText 的布局参数
//            LinearLayout.LayoutParams editTextParams = new LinearLayout.LayoutParams(
//                    LinearLayout.LayoutParams.WRAP_CONTENT,
//                    LinearLayout.LayoutParams.WRAP_CONTENT);
//            editTextParams.setMargins(0, 0, 0, 16); // 设置左右外边距
//            input.setLayoutParams(editTextParams);
            layout.addView(input);

            // 将主布局添加到滚动视图中
            scrollView.addView(layout);

            // 构建对话框
            OverlayAlertDialog.Builder builder = new OverlayAlertDialog.Builder(context);
            OverlayAlertDialog dialog = builder.setTitle("修改精简实时信息模板")
                    .setView(scrollView)
                    .setCancelable(true) // 允许通过返回键关闭
                    .setPositiveButton(context.getString(R.string.default_button), (dialogInterface, which) -> {
                        prefs.edit()
                                .putString(PreferenceConfiguration.EDITTEXT_SIMPLE_PERF_OVERLAY_PREF_STRING,
                                        context.getString(R.string.default_template_simple_perf_overlay))
                                .apply();
                    })
                    .setNeutralButton(context.getString(R.string.confirm_button), (dialogInterface, which) -> {
                        prefs.edit()
                                .putString(PreferenceConfiguration.EDITTEXT_SIMPLE_PERF_OVERLAY_PREF_STRING,
                                        input.getText().toString())
                                .apply();
                    })
                    .setNegativeButton(context.getString(R.string.cancel_button), (dialogInterface, which) -> {
                        dialogInterface.dismiss();
                    })
                    .setCanceledOnTouchOutside(true)
                    .create();

            // 设置对话框的最大高度（可选，防止对话框太长）
//            dialog.setOnShowListener(dialogInterface -> {
//                // 获取屏幕高度
//                WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
//                DisplayMetrics metrics = new DisplayMetrics();
//                windowManager.getDefaultDisplay().getMetrics(metrics);
//
//                // 设置对话框最大高度为屏幕高度的80%
//                int maxHeight = (int) (metrics.heightPixels * 0.8);
//
//                // 获取对话框窗口并设置最大高度
//                Window window = dialog.getWindow();
//                if (window != null) {
//                    window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
//                            Math.min(scrollView.getHeight(), maxHeight));
//                }
//            });

            // 显示对话框
            dialog.show();
        }

        private int dp(float value) {
            return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                    getResources().getDisplayMetrics()));
        }

        private void applyListPadding(ListView listView) {
            int screenWidthDp = getResources().getConfiguration().screenWidthDp;
            int sidePaddingDp = 4;
            if (screenWidthDp > MAX_CONTENT_WIDTH_DP + 2 * sidePaddingDp) {
                sidePaddingDp = (screenWidthDp - MAX_CONTENT_WIDTH_DP) / 2;
            }
            listView.setPaddingRelative(dp(sidePaddingDp), dp(4), dp(sidePaddingDp), dp(28));
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            view.setBackgroundColor(Color.TRANSPARENT);

            ListView listView = view.findViewById(android.R.id.list);
            if (listView != null) {
                listView.setBackgroundColor(Color.TRANSPARENT);
                listView.setCacheColorHint(Color.TRANSPARENT);
                listView.setDivider(new ColorDrawable(Color.TRANSPARENT));
                listView.setDividerHeight(dp(6));
                listView.setClipToPadding(false);
                listView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);

                // Rows draw their own rounded background and ripple, so the list selector only
                // shows a focus outline for D-pad/keyboard navigation.
                listView.setSelector(R.drawable.settings_list_selector);
                listView.setDrawSelectorOnTop(true);

                applyListPadding(listView);
            }

            return view;
        }

        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);

            // The activity handles rotation itself, so the view isn't recreated
            View view = getView();
            ListView listView = view != null ? view.findViewById(android.R.id.list) : null;
            if (listView != null) {
                applyListPadding(listView);
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

            if (section == null) {
                updateHomeSummaries();
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key == null) {
                return;
            }

            // Values like the bitrate can be rewritten from code (e.g. after changing resolution)
            Preference preference = findPreference(key);
            if (preference instanceof SeekBarPreference) {
                ((SeekBarPreference) preference).syncFromStorage();
            }
        }

        private void bindHomeNavigation() {
            for (Section navSection : SECTIONS) {
                Preference navPreference = findPreference("nav_" + navSection.id);
                if (navPreference == null) {
                    continue;
                }

                navPreference.setOnPreferenceClickListener(preference -> {
                    StreamSettings activity = (StreamSettings) getActivity();
                    if (activity != null) {
                        activity.openSection(navSection.id);
                    }
                    return true;
                });
            }
        }

        private void updateHomeSummaries() {
            Preference videoNav = findPreference("nav_video");
            if (videoNav == null) {
                return;
            }

            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
            String resolution = prefs.getString(PreferenceConfiguration.RESOLUTION_PREF_STRING,
                    PreferenceConfiguration.DEFAULT_RESOLUTION);
            String fps = prefs.getString(PreferenceConfiguration.FPS_PREF_STRING,
                    PreferenceConfiguration.DEFAULT_FPS);
            int bitrateKbps = prefs.getInt(PreferenceConfiguration.BITRATE_PREF_STRING,
                    PreferenceConfiguration.getDefaultBitrate(getActivity()));

            String bitrateMbps = bitrateKbps % 1000 == 0 ?
                    String.valueOf(bitrateKbps / 1000) :
                    String.format(Locale.getDefault(), "%.1f", bitrateKbps / 1000f);

            videoNav.setSummary(getString(R.string.settings_section_video_current,
                    resolution.replace('x', '×'), fps, bitrateMbps));
        }

        private void retainSectionCategories(Section visibleSection) {
            Set<String> visibleCategories = new HashSet<>(Arrays.asList(visibleSection.categoryKeys));
            PreferenceScreen screen = getPreferenceScreen();
            for (int i = screen.getPreferenceCount() - 1; i >= 0; i--) {
                Preference category = screen.getPreference(i);
                if (!visibleCategories.contains(category.getKey())) {
                    screen.removePreference(category);
                }
            }
        }

        private static PreferenceGroup findParent(PreferenceGroup group, Preference target) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference child = group.getPreference(i);
                if (child == target) {
                    return group;
                }
                if (child instanceof PreferenceGroup) {
                    PreferenceGroup parent = findParent((PreferenceGroup) child, target);
                    if (parent != null) {
                        return parent;
                    }
                }
            }
            return null;
        }

        // Removes a preference (or category) wherever it lives. Missing keys are ignored because
        // each section only contains part of preferences.xml.
        private void removePreferenceByKey(String key) {
            Preference preference = findPreference(key);
            if (preference == null) {
                return;
            }

            PreferenceGroup parent = findParent(getPreferenceScreen(), preference);
            if (parent != null) {
                parent.removePreference(preference);
            }
        }

        private void removeEmptyCategories() {
            PreferenceScreen screen = getPreferenceScreen();
            for (int i = screen.getPreferenceCount() - 1; i >= 0; i--) {
                Preference child = screen.getPreference(i);
                if (child instanceof PreferenceGroup && ((PreferenceGroup) child).getPreferenceCount() == 0) {
                    screen.removePreference(child);
                }
            }
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            section = getArguments() != null ? getArguments().getString(ARG_SECTION) : null;
            Section visibleSection = section != null ? findSection(section) : null;
            if (visibleSection == null) {
                section = null;
                addPreferencesFromResource(R.xml.settings_home);
                bindHomeNavigation();
                return;
            }

            addPreferencesFromResource(R.xml.preferences);
            retainSectionCategories(visibleSection);

            Preference updateNowPreference = findPreference("check_for_updates");
            if (updateNowPreference != null) {
                updateNowPreference.setSummary(getString(R.string.summary_check_for_updates,
                        BuildConfig.VERSION_NAME));
                updateNowPreference.setOnPreferenceClickListener(preference -> {
                    UpdateChecker.checkForUpdates(getActivity(), true);
                    return true;
                });
            }

            PackageManager packageManager = getActivity().getPackageManager();

            // hide on-screen controls category on non touch screen devices
            if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                removePreferenceByKey("category_onscreen_controls");
            }

            // Hide remote desktop mouse mode on pre-Oreo (which doesn't have pointer capture)
            // and NVIDIA SHIELD devices (which support raw mouse input in pointer capture mode)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    packageManager.hasSystemFeature("com.nvidia.feature.shield")) {
                removePreferenceByKey("checkbox_absolute_mouse_mode");
            }

            // Hide gamepad motion sensor option when running on OSes before Android 12.
            // Support for motion, LED, battery, and other extensions were introduced in S.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                removePreferenceByKey("checkbox_gamepad_motion_sensors");
            }

            // Hide gamepad motion sensor fallback option if the device has no gyro or accelerometer
            if (!packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER) &&
                    !packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)) {
                removePreferenceByKey("checkbox_gamepad_motion_fallback");
            }

            // Hide USB driver options on devices without USB host support
            if (!packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
                removePreferenceByKey("checkbox_usb_bind_all");
                removePreferenceByKey("checkbox_usb_driver");
            }

            // Remove PiP mode on devices pre-Oreo, where the feature is not available (some low RAM devices),
            // and on Fire OS where it violates the Amazon App Store guidelines for some reason.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    !packageManager.hasSystemFeature("android.software.picture_in_picture") ||
                    packageManager.hasSystemFeature("com.amazon.software.fireos")) {
                removePreferenceByKey("checkbox_enable_pip");
            }

            // Remove the vibration options if the device can't vibrate
            Vibrator vibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
            if (!vibrator.hasVibrator()) {
                removePreferenceByKey("checkbox_vibrate_fallback");
                removePreferenceByKey("seekbar_vibrate_fallback_strength");
                removePreferenceByKey("checkbox_vibrate_osc");
            }
            else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !vibrator.hasAmplitudeControl()) {
                // Remove the vibration strength selector of the device doesn't have amplitude control
                removePreferenceByKey("seekbar_vibrate_fallback_strength");
            }

            Display display = getActivity().getWindowManager().getDefaultDisplay();

            if (findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING) != null &&
                    findPreference(PreferenceConfiguration.FPS_PREF_STRING) != null) {
                configureResolutionAndFrameRate(display);
            }

            // Android L introduces the drop duplicate behavior of releaseOutputBuffer()
            // that the unlock FPS option relies on to not massively increase latency.
            Preference unlockFpsPref = findPreference(PreferenceConfiguration.UNLOCK_FPS_STRING);
            if (unlockFpsPref != null) {
                unlockFpsPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        // HACK: We need to let the preference change succeed before reinitializing to ensure
                        // it's reflected in the new layout.
                        final Handler h = new Handler();
                        h.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // Ensure the activity is still open when this timeout expires
                                StreamSettings settingsActivity = (StreamSettings) SettingsFragment.this.getActivity();
                                if (settingsActivity != null) {
                                    settingsActivity.reloadSettings();
                                }
                            }
                        }, 500);

                        // Allow the original preference change to take place
                        return true;
                    }
                });
            }

            if (findPreference("checkbox_enable_hdr") != null) {
                configureHdr(display);
            }

            Preference simplifyPerfOverlayPref = findPreference("edittext_simple_perf_overlay");
            if (simplifyPerfOverlayPref != null) {
                simplifyPerfOverlayPref.setOnPreferenceClickListener(preference -> {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
                    showSimplifyPerfOverlayPref(prefs); // 调用自定义逻辑
                    return true; // 返回 true 表示已处理点击事件，阻止系统行为
                });
            }

            removeEmptyCategories();
        }

        private void configureHdr(Display display) {
            // Remove HDR preference for devices below Nougat
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                LimeLog.info("Excluding HDR toggle based on OS");
                removePreferenceByKey("checkbox_enable_hdr");
                return;
            }

            Display.HdrCapabilities hdrCaps = display.getHdrCapabilities();

            // We must now ensure our display is compatible with HDR10
            boolean foundHdr10 = false;
            if (hdrCaps != null) {
                // getHdrCapabilities() returns null on Lenovo Lenovo Mirage Solo (vega), Android 8.0
                for (int hdrType : hdrCaps.getSupportedHdrTypes()) {
                    if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                        foundHdr10 = true;
                        break;
                    }
                }
            }

            if (!foundHdr10) {
                LimeLog.info("Excluding HDR toggle based on display capabilities");
                removePreferenceByKey("checkbox_enable_hdr");
            }
            else if (PreferenceConfiguration.isShieldAtvFirmwareWithBrokenHdr()) {
                LimeLog.info("Disabling HDR toggle on old broken SHIELD TV firmware");
                CheckBoxPreference hdrPref = (CheckBoxPreference) findPreference("checkbox_enable_hdr");
                hdrPref.setEnabled(false);
                hdrPref.setChecked(false);
                hdrPref.setSummary("Update the firmware on your NVIDIA SHIELD Android TV to enable HDR");
            }
        }

        private void configureResolutionAndFrameRate(Display display) {
            float maxSupportedFps = display.getRefreshRate();

            // Hide non-supported resolution/FPS combinations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int maxSupportedResW = 0;

                // Add a native resolution with any insets included for users that don't want content
                // behind the notch of their display
                boolean hasInsets = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    DisplayCutout cutout;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Use the much nicer Display.getCutout() API on Android 10+
                        cutout = display.getCutout();
                    }
                    else {
                        // Android 9 only
                        cutout = displayCutoutP;
                    }

                    if (cutout != null) {
                        int widthInsets = cutout.getSafeInsetLeft() + cutout.getSafeInsetRight();
                        int heightInsets = cutout.getSafeInsetBottom() + cutout.getSafeInsetTop();

                        if (widthInsets != 0 || heightInsets != 0) {
                            DisplayMetrics metrics = new DisplayMetrics();
                            display.getRealMetrics(metrics);

                            int width = Math.max(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets);
                            int height = Math.min(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets);

                            addNativeResolutionEntries(width, height, false);
                            hasInsets = true;
                        }
                    }
                }

                // Always allow resolutions that are smaller or equal to the active
                // display resolution because decoders can report total non-sense to us.
                // For example, a p201 device reports:
                // AVC Decoder: OMX.amlogic.avc.decoder.awesome
                // HEVC Decoder: OMX.amlogic.hevc.decoder.awesome
                // AVC supported width range: 64 - 384
                // HEVC supported width range: 64 - 544
                for (Display.Mode candidate : display.getSupportedModes()) {
                    // Some devices report their dimensions in the portrait orientation
                    // where height > width. Normalize these to the conventional width > height
                    // arrangement before we process them.

                    int width = Math.max(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());
                    int height = Math.min(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());

                    // Some TVs report strange values here, so let's avoid native resolutions on a TV
                    // unless they report greater than 4K resolutions.
                    if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                            (width > 3840 || height > 2160)) {
                        addNativeResolutionEntries(width, height, hasInsets);
                    }

                    if ((width >= 3840 || height >= 2160) && maxSupportedResW < 3840) {
                        maxSupportedResW = 3840;
                    }
                    else if ((width >= 2560 || height >= 1440) && maxSupportedResW < 2560) {
                        maxSupportedResW = 2560;
                    }
                    else if ((width >= 1920 || height >= 1080) && maxSupportedResW < 1920) {
                        maxSupportedResW = 1920;
                    }

                    if (candidate.getRefreshRate() > maxSupportedFps) {
                        maxSupportedFps = candidate.getRefreshRate();
                    }
                }

                // This must be called to do runtime initialization before calling functions that evaluate
                // decoder lists.
                MediaCodecHelper.initialize(getContext(), GlPreferences.readPreferences(getContext()).glRenderer);

                MediaCodecInfo avcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/avc", -1);
                MediaCodecInfo hevcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/hevc", -1);

                if (avcDecoder != null) {
                    Range<Integer> avcWidthRange = avcDecoder.getCapabilitiesForType("video/avc").getVideoCapabilities().getSupportedWidths();

                    LimeLog.info("AVC supported width range: "+avcWidthRange.getLower()+" - "+avcWidthRange.getUpper());

                    // If 720p is not reported as supported, ignore all results from this API
                    if (avcWidthRange.contains(1280)) {
                        if (avcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                            maxSupportedResW = 3840;
                        }
                        else if (avcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                            maxSupportedResW = 1920;
                        }
                        else if (maxSupportedResW < 1280) {
                            maxSupportedResW = 1280;
                        }
                    }
                }

                if (hevcDecoder != null) {
                    Range<Integer> hevcWidthRange = hevcDecoder.getCapabilitiesForType("video/hevc").getVideoCapabilities().getSupportedWidths();

                    LimeLog.info("HEVC supported width range: "+hevcWidthRange.getLower()+" - "+hevcWidthRange.getUpper());

                    // If 720p is not reported as supported, ignore all results from this API
                    if (hevcWidthRange.contains(1280)) {
                        if (hevcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                            maxSupportedResW = 3840;
                        }
                        else if (hevcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                            maxSupportedResW = 1920;
                        }
                        else if (maxSupportedResW < 1280) {
                            maxSupportedResW = 1280;
                        }
                    }
                }

                LimeLog.info("Maximum resolution slot: "+maxSupportedResW);

                if (maxSupportedResW != 0) {
                    if (maxSupportedResW < 3840) {
                        // 4K is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_4K, new Runnable() {
                            @Override
                            public void run() {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                                setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1440P);
                                resetBitrateToDefault(prefs, null, null);
                            }
                        });
                    }
                    if (maxSupportedResW < 2560) {
                        // 1440p is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1440P, new Runnable() {
                            @Override
                            public void run() {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                                setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1080P);
                                resetBitrateToDefault(prefs, null, null);
                            }
                        });
                    }
                    if (maxSupportedResW < 1920) {
                        // 1080p is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1080P, new Runnable() {
                            @Override
                            public void run() {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                                setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_720P);
                                resetBitrateToDefault(prefs, null, null);
                            }
                        });
                    }
                    // Never remove 720p
                }
            }
            else {
                // We can get the true metrics via the getRealMetrics() function (unlike the lies
                // that getWidth() and getHeight() tell to us).
                DisplayMetrics metrics = new DisplayMetrics();
                display.getRealMetrics(metrics);
                int width = Math.max(metrics.widthPixels, metrics.heightPixels);
                int height = Math.min(metrics.widthPixels, metrics.heightPixels);
                addNativeResolutionEntries(width, height, false);
            }

            if (!PreferenceConfiguration.readPreferences(this.getActivity()).unlockFps) {
                // We give some extra room in case the FPS is rounded down
                if (maxSupportedFps < 118) {
                    removeValue(PreferenceConfiguration.FPS_PREF_STRING, "120", new Runnable() {
                        @Override
                        public void run() {
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                            setValue(PreferenceConfiguration.FPS_PREF_STRING, "90");
                            resetBitrateToDefault(prefs, null, null);
                        }
                    });
                }
                if (maxSupportedFps < 88) {
                    // 1080p is unsupported
                    removeValue(PreferenceConfiguration.FPS_PREF_STRING, "90", new Runnable() {
                        @Override
                        public void run() {
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                            setValue(PreferenceConfiguration.FPS_PREF_STRING, "60");
                            resetBitrateToDefault(prefs, null, null);
                        }
                    });
                }
                // Never remove 30 FPS or 60 FPS
            }
            addNativeFrameRateEntry(maxSupportedFps);

            // Add a listener to the FPS and resolution preference
            // so the bitrate can be auto-adjusted
            findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                    String valueStr = (String) newValue;

                    // Detect if this value is the native resolution option
                    CharSequence[] values = ((ListPreference)preference).getEntryValues();
                    boolean isNativeRes = true;
                    for (int i = 0; i < values.length; i++) {
                        // Look for a match prior to the start of the native resolution entries
                        if (valueStr.equals(values[i].toString()) && i < nativeResolutionStartIndex) {
                            isNativeRes = false;
                            break;
                        }
                    }

                    // If this is native resolution, show the warning dialog
                    if (isNativeRes) {
                        Dialog.displayDialog(getActivity(),
                                getResources().getString(R.string.title_native_res_dialog),
                                getResources().getString(R.string.text_native_res_dialog),
                                false);
                    }

                    // Write the new bitrate value
                    resetBitrateToDefault(prefs, valueStr, null);

                    // Allow the original preference change to take place
                    return true;
                }
            });
            findPreference(PreferenceConfiguration.FPS_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                    String valueStr = (String) newValue;

                    // If this is native frame rate, show the warning dialog
                    CharSequence[] values = ((ListPreference)preference).getEntryValues();
                    if (nativeFramerateShown && values[values.length - 1].toString().equals(newValue.toString())) {
                        Dialog.displayDialog(getActivity(),
                                getResources().getString(R.string.title_native_fps_dialog),
                                getResources().getString(R.string.text_native_res_dialog),
                                false);
                    }

                    // Write the new bitrate value
                    resetBitrateToDefault(prefs, null, valueStr);

                    // Allow the original preference change to take place
                    return true;
                }
            });
        }
    }
}
