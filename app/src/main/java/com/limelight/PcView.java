package com.limelight;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import com.limelight.binding.PlatformBinding;
import com.limelight.binding.crypto.AndroidCryptoProvider;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.PcGridAdapter;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.preferences.AddComputerManually;
import com.limelight.preferences.GlPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.preferences.StreamSettings;
import com.limelight.ui.AdapterFragment;
import com.limelight.ui.AdapterFragmentCallbacks;
import com.limelight.utils.Dialog;
import com.limelight.utils.HelpLauncher;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.UiHelper;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

import org.xmlpull.v1.XmlPullParserException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PcView extends Activity implements AdapterFragmentCallbacks {
    private RelativeLayout noPcFoundLayout;
    private PcGridAdapter pcGridAdapter;
    private ShortcutHelper shortcutHelper;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private boolean freezeUpdates, runningPolling, inForeground, completeOnCreateCalled;
    private PreferenceConfiguration prefs;
    private Map<String, PairingTask> activePairingTasks = new HashMap<>();
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder =
                    ((ComputerManagerService.ComputerManagerBinder)binder);

            // Wait in a separate thread to avoid stalling the UI
            new Thread() {
                @Override
                public void run() {
                    // Wait for the binder to be ready
                    localBinder.waitForReady();

                    // Now make the binder visible
                    managerBinder = localBinder;

                    // Start updates
                    startComputerUpdates();

                    // Force a keypair to be generated early to avoid discovery delays
                    new AndroidCryptoProvider(PcView.this).getClientCertificate();
                }
            }.start();
        }

        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LimeLog.info("PcView: onConfigurationChanged() called, completeOnCreateCalled: " + completeOnCreateCalled + ", pcGridAdapter: " + (pcGridAdapter != null));

        // Only reinitialize views if completeOnCreate() was called
        // before this callback. If it was not, completeOnCreate() will
        // handle initializing views with the config change accounted for.
        // This is not prone to races because both callbacks are invoked
        // in the main thread.
        if (completeOnCreateCalled) {
            // Reinitialize views just in case orientation changed
            initializeViews();
        }
    }

    private final static int PAIR_ID = 2;
    private final static int UNPAIR_ID = 3;
    private final static int WOL_ID = 4;
    private final static int DELETE_ID = 5;
    private final static int RESUME_ID = 6;
    private final static int QUIT_ID = 7;
    private final static int VIEW_DETAILS_ID = 8;
    private final static int FULL_APP_LIST_ID = 9;
    private final static int TEST_NETWORK_ID = 10;
    private final static int GAMESTREAM_EOL_ID = 11;
    private final static int DELETE_IP_ID = 12;

    private void initializeViews() {
        LimeLog.info("PcView: initializeViews() called, pcGridAdapter: " + (pcGridAdapter != null));
        
        setContentView(R.layout.activity_pc_view);

        UiHelper.notifyNewRootView(this);

        // Allow floating expanded PiP overlays while browsing PCs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setShouldDockBigOverlays(false);
        }

        // Set default preferences if we've never been run
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // Set the correct layout for the PC grid
        if (pcGridAdapter != null) {
            pcGridAdapter.updateLayoutWithPreferences(this, prefs);
        } else {
            LimeLog.severe("PcView: pcGridAdapter is null in initializeViews()!");
            // Try to reinitialize if null
            if (prefs != null) {
                pcGridAdapter = new PcGridAdapter(this, prefs);
                LimeLog.info("PcView: Reinitialized pcGridAdapter in initializeViews()");
            }
        }

        // Setup the list view
        ImageButton settingsButton = findViewById(R.id.settingsButton);
        ImageButton addComputerButton = findViewById(R.id.manuallyAddPc);
        ImageButton helpButton = findViewById(R.id.helpButton);

        settingsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(PcView.this, StreamSettings.class));
            }
        });
        addComputerButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(PcView.this, AddComputerManually.class);
                startActivity(i);
            }
        });
        helpButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                HelpLauncher.launchSetupGuide(PcView.this);
            }
        });

        // Amazon review didn't like the help button because the wiki was not entirely
        // navigable via the Fire TV remote (though the relevant parts were). Let's hide
        // it on Fire TV.
        if (getPackageManager().hasSystemFeature("amazon.hardware.fire_tv")) {
            helpButton.setVisibility(View.GONE);
        }

        getFragmentManager().beginTransaction()
            .replace(R.id.pcFragmentContainer, new AdapterFragment())
            .commitAllowingStateLoss();

        noPcFoundLayout = findViewById(R.id.no_pc_found_layout);
        if (pcGridAdapter != null && pcGridAdapter.getCount() == 0) {
            noPcFoundLayout.setVisibility(View.VISIBLE);
        }
        else {
            noPcFoundLayout.setVisibility(View.INVISIBLE);
        }
        if (pcGridAdapter != null) {
            pcGridAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LimeLog.info("PcView: onCreate() started");

        // Assume we're in the foreground when created to avoid a race
        // between binding to CMS and onResume()
        inForeground = true;

        // Create a GLSurfaceView to fetch GLRenderer unless we have
        // a cached result already.
        final GlPreferences glPrefs = GlPreferences.readPreferences(this);
        if (!glPrefs.savedFingerprint.equals(Build.FINGERPRINT) || glPrefs.glRenderer.isEmpty()) {
            LimeLog.info("PcView: Creating GLSurfaceView for renderer detection");
            GLSurfaceView surfaceView = new GLSurfaceView(this);
            surfaceView.setRenderer(new GLSurfaceView.Renderer() {
                @Override
                public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                    // Save the GLRenderer string so we don't need to do this next time
                    glPrefs.glRenderer = gl10.glGetString(GL10.GL_RENDERER);
                    glPrefs.savedFingerprint = Build.FINGERPRINT;
                    glPrefs.writePreferences();

                    LimeLog.info("PcView: Fetched GL Renderer: " + glPrefs.glRenderer);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            completeOnCreate();
                        }
                    });
                }

                @Override
                public void onSurfaceChanged(GL10 gl10, int i, int i1) {
                }

                @Override
                public void onDrawFrame(GL10 gl10) {
                }
            });
            setContentView(surfaceView);
        }
        else {
            LimeLog.info("PcView: Using cached GL Renderer: " + glPrefs.glRenderer);
            completeOnCreate();
        }
    }

    private void completeOnCreate() {
        completeOnCreateCalled = true;

        LimeLog.info("PcView: completeOnCreate() started");

        shortcutHelper = new ShortcutHelper(this);

        UiHelper.setLocale(this);

        // Bind to the computer manager service
        bindService(new Intent(PcView.this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);

        prefs = PreferenceConfiguration.readPreferences(this);
        pcGridAdapter = new PcGridAdapter(this, prefs);
        LimeLog.info("PcView: pcGridAdapter initialized: " + (pcGridAdapter != null));

        initializeViews();
    }

    private void startComputerUpdates() {
        // Only allow polling to start if we're bound to CMS, polling is not already running,
        // and our activity is in the foreground.
        if (managerBinder != null && !runningPolling && inForeground) {
            freezeUpdates = false;
            managerBinder.startPolling(new ComputerManagerListener() {
                @Override
                public void notifyComputerUpdated(final ComputerDetails details) {
                    if (!freezeUpdates) {
                        PcView.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateComputer(details);
                            }
                        });

                        // Add a launcher shortcut for this PC (off the main thread to prevent ANRs)
                        if (details.pairState == PairState.PAIRED) {
                            shortcutHelper.createAppViewShortcutForOnlineHost(details);
                        }
                    }
                }
            });
            runningPolling = true;
        }
    }

    private void stopComputerUpdates(boolean wait) {
        if (managerBinder != null) {
            if (!runningPolling) {
                return;
            }

            freezeUpdates = true;

            managerBinder.stopPolling();

            if (wait) {
                managerBinder.waitForPollingStopped();
            }

            runningPolling = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LimeLog.info("PcView: onDestroy() called");

        if (managerBinder != null) {
            unbindService(serviceConnection);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        LimeLog.info("PcView: onResume() called, pcGridAdapter: " + (pcGridAdapter != null) + ", completeOnCreateCalled: " + completeOnCreateCalled);

        // Display a decoder crash notification if we've returned after a crash
        UiHelper.showDecoderCrashDialog(this);

        // Reload preferences in case they have changed
        prefs = PreferenceConfiguration.readPreferences(this);
        
        // Ensure pcGridAdapter is initialized before calling methods on it
        // This prevents the NullPointerException that was occurring
        if (pcGridAdapter == null && prefs != null) {
            LimeLog.warning("PcView: pcGridAdapter was null in onResume(), initializing now");
            pcGridAdapter = new PcGridAdapter(this, prefs);
        }
        
        // Only update layout if adapter is properly initialized
        if (pcGridAdapter != null) {
            pcGridAdapter.updateLayoutWithPreferences(this, prefs);
        } else {
            LimeLog.severe("PcView: Unable to initialize pcGridAdapter in onResume()");
        }

        inForeground = true;
        startComputerUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LimeLog.info("PcView: onPause() called");

        inForeground = false;
        stopComputerUpdates(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        LimeLog.info("PcView: onStop() called");

        Dialog.closeDialogs();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        stopComputerUpdates(false);

        // Call superclass
        super.onCreateContextMenu(menu, v, menuInfo);
                
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(info.position);

        // Add a header with PC status details
        menu.clearHeader();
        String headerTitle = computer.details.name + " - ";
        switch (computer.details.state)
        {
            case ONLINE:
                headerTitle += getResources().getString(R.string.pcview_menu_header_online);
                break;
            case OFFLINE:
                menu.setHeaderIcon(R.drawable.ic_pc_offline);
                headerTitle += getResources().getString(R.string.pcview_menu_header_offline);
                break;
            case UNKNOWN:
                headerTitle += getResources().getString(R.string.pcview_menu_header_unknown);
                break;
        }

        menu.setHeaderTitle(headerTitle);

        // Inflate the context menu
        if (computer.details.state == ComputerDetails.State.OFFLINE ||
            computer.details.state == ComputerDetails.State.UNKNOWN) {
            menu.add(Menu.NONE, WOL_ID, 1, getResources().getString(R.string.pcview_menu_send_wol));
            menu.add(Menu.NONE, GAMESTREAM_EOL_ID, 2, getResources().getString(R.string.pcview_menu_eol));
        }
        else if (computer.details.pairState != PairState.PAIRED) {
            menu.add(Menu.NONE, PAIR_ID, 1, getResources().getString(R.string.pcview_menu_pair_pc));
            if (computer.details.nvidiaServer) {
                menu.add(Menu.NONE, GAMESTREAM_EOL_ID, 2, getResources().getString(R.string.pcview_menu_eol));
            }
        }
        else {
            if (computer.details.runningGameId != 0) {
                menu.add(Menu.NONE, RESUME_ID, 1, getResources().getString(R.string.applist_menu_resume));
                menu.add(Menu.NONE, QUIT_ID, 2, getResources().getString(R.string.applist_menu_quit));
            }

            if (computer.details.nvidiaServer) {
                menu.add(Menu.NONE, GAMESTREAM_EOL_ID, 3, getResources().getString(R.string.pcview_menu_eol));
            }

            menu.add(Menu.NONE, FULL_APP_LIST_ID, 4, getResources().getString(R.string.pcview_menu_app_list));
        }

        menu.add(Menu.NONE, TEST_NETWORK_ID, 5, getResources().getString(R.string.pcview_menu_test_network));
        if (computer.address != null && computer.details.manualAddresses.contains(computer.address)) {
            menu.add(Menu.NONE, DELETE_IP_ID, 6, R.string.pc_view_delete_ip);
        }
        menu.add(Menu.NONE, DELETE_ID, 7, getResources().getString(R.string.pcview_menu_delete_pc));
        menu.add(Menu.NONE, VIEW_DETAILS_ID, 8,  getResources().getString(R.string.pcview_menu_details));
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        // For some reason, this gets called again _after_ onPause() is called on this activity.
        // startComputerUpdates() manages this and won't actual start polling until the activity
        // returns to the foreground.
        startComputerUpdates();
    }

    private void doPair(final ComputerObject computer) {
        if (computer.details.state == ComputerDetails.State.OFFLINE || computer.address == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.pair_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        // 检查是否已经在配对中
        if (pcGridAdapter.isPairing(computer)) {
            return;
        }

        // 开始配对 - 在UI中显示配对状态
        final String pinStr = PairingManager.generatePinString();
        pcGridAdapter.startPairing(computer, pinStr);

        // 在后台线程中执行配对
        PairingTask pairingTask = new PairingTask(computer, pinStr);
        String taskKey = getComputerKey(computer);
        activePairingTasks.put(taskKey, pairingTask);
        pairingTask.execute();
    }

    private String getComputerKey(ComputerObject computer) {
        return computer.details.uuid + "_" + (computer.address != null ? computer.address.toString() : "");
    }

    // 取消配对
    public void cancelPairing(final ComputerObject computer) {
        if (pcGridAdapter.isPairing(computer)) {
            String taskKey = getComputerKey(computer);
            PairingTask pairingTask = activePairingTasks.get(taskKey);
            if (pairingTask != null) {
                pairingTask.cancelPairing();
                activePairingTasks.remove(taskKey);
            }
            
            pcGridAdapter.updatePairingStatus(computer, PcGridAdapter.PairingStatus.CANCELLED);
            // 延迟清除配对状态，让用户看到取消状态
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    pcGridAdapter.clearPairingStatus(computer);
                    // 重新开始轮询
                    startComputerUpdates();
                }
            }, 1500);
        }
    }

    // 配对任务类 - 在后台执行配对
    private class PairingTask extends android.os.AsyncTask<Void, Void, PairingResult> {
        private final ComputerObject computer;
        private final String pinStr;
        private volatile boolean cancelled = false;

        public PairingTask(ComputerObject computer, String pinStr) {
            this.computer = computer;
            this.pinStr = pinStr;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // 停止更新以专注于配对
            stopComputerUpdates(true);
        }

        @Override
        protected PairingResult doInBackground(Void... voids) {
            NvHTTP httpConn = null;
            try {
                httpConn = new NvHTTP(computer.address,
                        computer.details.httpsPort, managerBinder.getUniqueId(), computer.details.serverCert,
                        PlatformBinding.getCryptoProvider(PcView.this));
                
                if (httpConn.getPairState() == PairState.PAIRED) {
                    return new PairingResult(true, null, httpConn);
                }

                PairingManager pm = httpConn.getPairingManager();
                PairState pairState = pm.pair(httpConn.getServerInfo(true), pinStr);
                
                if (cancelled) {
                    return new PairingResult(false, getResources().getString(R.string.pairing_status_cancelled), httpConn);
                }

                if (pairState == PairState.PIN_WRONG) {
                    return new PairingResult(false, getResources().getString(R.string.pair_incorrect_pin), httpConn);
                }
                else if (pairState == PairState.FAILED) {
                    if (computer.details.runningGameId != 0) {
                        return new PairingResult(false, getResources().getString(R.string.pair_pc_ingame), httpConn);
                    }
                    else {
                        return new PairingResult(false, getResources().getString(R.string.pair_fail), httpConn);
                    }
                }
                else if (pairState == PairState.ALREADY_IN_PROGRESS) {
                    return new PairingResult(false, getResources().getString(R.string.pair_already_in_progress), httpConn);
                }
                else if (pairState == PairState.PAIRED) {
                    // 保存配对证书
                    managerBinder.getComputer(computer.details.uuid).serverCert = pm.getPairedCert();
                    // 强制刷新状态
                    managerBinder.invalidateStateForComputer(computer.details.uuid);
                    return new PairingResult(true, null, httpConn);
                }
                else {
                    return new PairingResult(false, getResources().getString(R.string.pair_fail), httpConn);
                }
            } catch (UnknownHostException e) {
                return new PairingResult(false, getResources().getString(R.string.error_unknown_host), httpConn);
            } catch (FileNotFoundException e) {
                return new PairingResult(false, getResources().getString(R.string.error_404), httpConn);
            } catch (XmlPullParserException | IOException e) {
                e.printStackTrace();
                return new PairingResult(false, e.getMessage(), httpConn);
            }
        }

        @Override
        protected void onPostExecute(PairingResult result) {
            super.onPostExecute(result);
            
            // 从活动任务中移除
            String taskKey = getComputerKey(computer);
            activePairingTasks.remove(taskKey);
            
            if (cancelled) {
                return;
            }

            if (result.success) {
                // 配对成功
                pcGridAdapter.updatePairingStatus(computer, PcGridAdapter.PairingStatus.SUCCESS);
                // 延迟打开应用列表，让用户看到成功状态
                new android.os.Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        pcGridAdapter.clearPairingStatus(computer);
                        doAppList(computer, true, false);
                    }
                }, 1000);
            } else {
                // 配对失败
                pcGridAdapter.updatePairingStatus(computer, PcGridAdapter.PairingStatus.FAILED);
                // 延迟清除状态并显示错误消息
                new android.os.Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        pcGridAdapter.clearPairingStatus(computer);
                        if (result.message != null) {
                            Toast.makeText(PcView.this, result.message, Toast.LENGTH_LONG).show();
                        }
                        // 重新开始轮询
                        startComputerUpdates();
                    }
                }, 1500);
            }
        }

        public void cancelPairing() {
            cancelled = true;
            cancel(true);
        }
    }

    // 配对结果类
    private static class PairingResult {
        boolean success;
        String message;
        NvHTTP httpConn;

        PairingResult(boolean success, String message, NvHTTP httpConn) {
            this.success = success;
            this.message = message;
            this.httpConn = httpConn;
        }
    }

    private void doWakeOnLan(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.ONLINE) {
            Toast.makeText(PcView.this, getResources().getString(R.string.wol_pc_online), Toast.LENGTH_SHORT).show();
            return;
        }

        if (computer.macAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.wol_no_mac), Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    WakeOnLanSender.sendWolPacket(computer);
                    message = getResources().getString(R.string.wol_waking_msg);
                } catch (IOException e) {
                    message = getResources().getString(R.string.wol_fail);
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void doUnpair(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(PcView.this, getResources().getString(R.string.unpairing), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                try {
                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                            PlatformBinding.getCryptoProvider(PcView.this));
                    if (httpConn.getPairState() == PairingManager.PairState.PAIRED) {
                        httpConn.unpair();
                        if (httpConn.getPairState() == PairingManager.PairState.NOT_PAIRED) {
                            message = getResources().getString(R.string.unpair_success);
                        }
                        else {
                            message = getResources().getString(R.string.unpair_fail);
                        }
                    }
                    else {
                        message = getResources().getString(R.string.unpair_error);
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    message = e.getMessage();
                    e.printStackTrace();
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void doAppList(ComputerObject computer, boolean newlyPaired, boolean showHiddenGames) {
        if (computer.details.state == ComputerDetails.State.OFFLINE) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Intent i = new Intent(this, AppView.class);
        i.putExtra(AppView.NAME_EXTRA, computer.details.name);
        i.putExtra(AppView.UUID_EXTRA, computer.details.uuid);
        i.putExtra("SELECTED_IP", computer.address.address);
        i.putExtra("SELECTED_PORT", computer.address.port);
        i.putExtra(AppView.NEW_PAIR_EXTRA, newlyPaired);
        i.putExtra(AppView.SHOW_HIDDEN_APPS_EXTRA, showHiddenGames);
        startActivity(i);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        final ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(info.position);
        switch (item.getItemId()) {
            case PAIR_ID:
                doPair(computer);
                return true;

            case UNPAIR_ID:
                doUnpair(computer.details);
                return true;

            case WOL_ID:
                doWakeOnLan(computer.details);
                return true;

            case DELETE_ID:
                if (ActivityManager.isUserAMonkey()) {
                    LimeLog.info("Ignoring delete PC request from monkey");
                    return true;
                }
                UiHelper.displayDeletePcConfirmationDialog(this, computer.details, new Runnable() {
                    @Override
                    public void run() {
                        if (managerBinder == null) {
                            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                            return;
                        }
                        removeComputer(computer.details);
                    }
                }, null);
                return true;

            case FULL_APP_LIST_ID:
                doAppList(computer, false, true);
                return true;

            case RESUME_ID:
                if (managerBinder == null) {
                    Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                    return true;
                }

                ServerHelper.doStart(this, new NvApp("app", computer.details.runningGameId, false), computer.details, managerBinder);
                return true;

            case QUIT_ID:
                if (managerBinder == null) {
                    Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                    return true;
                }

                // Display a confirmation dialog first
                UiHelper.displayQuitConfirmationDialog(this, new Runnable() {
                    @Override
                    public void run() {
                        ServerHelper.doQuit(PcView.this, computer.details,
                                new NvApp("app", 0, false), managerBinder, null);
                    }
                }, null);
                return true;

            case VIEW_DETAILS_ID:
                Dialog.displayDialog(PcView.this, getResources().getString(R.string.title_details), computer.details.toString(), false);
                return true;

            case TEST_NETWORK_ID:
                ServerHelper.doNetworkTest(PcView.this);
                return true;

            case GAMESTREAM_EOL_ID:
                HelpLauncher.launchGameStreamEolFaq(PcView.this);
                return true;

            case DELETE_IP_ID:
               doRemoveIp(computer);
               return true;

            default:
                return super.onContextItemSelected(item);
        }
    }

   private void doRemoveIp(final ComputerObject computer) {
       // Create a new set to avoid modifying the one we're iterating over
       java.util.HashSet<ComputerDetails.AddressTuple> newManualAddresses = new java.util.HashSet<>(computer.details.manualAddresses);
       newManualAddresses.remove(computer.address);
       computer.details.manualAddresses = newManualAddresses;

       // Save the updated computer details
       managerBinder.updateComputer(computer.details);

       // The UI will be refreshed by the callback
   }
    
    private void removeComputer(ComputerDetails details) {
        managerBinder.removeComputer(details);

        new DiskAssetLoader(this).deleteAssetsForComputer(details.uuid);

        // Delete hidden games preference value
        getSharedPreferences(AppView.HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .edit()
                .remove(details.uuid)
                .apply();

        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);

            if (details.equals(computer.details)) {
                // Disable or delete shortcuts referencing this PC
                shortcutHelper.disableComputerShortcut(details,
                        getResources().getString(R.string.scut_deleted_pc));

                pcGridAdapter.removeComputer(computer);
                pcGridAdapter.notifyDataSetChanged();

                if (pcGridAdapter.getCount() == 0) {
                    // Show the "Discovery in progress" view
                    noPcFoundLayout.setVisibility(View.VISIBLE);
                }

                break;
            }
        }
    }
    
    private void updateComputer(ComputerDetails details) {
        // First, remove all existing entries for this computer UUID
        for (int i = pcGridAdapter.getCount() - 1; i >= 0; i--) {
            ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);
            if (details.uuid.equals(computer.details.uuid)) {
                pcGridAdapter.removeComputer(computer);
            }
        }

        // Create a list of all available addresses
        java.util.ArrayList<ComputerDetails.AddressTuple> addresses = new java.util.ArrayList<>();
        if (details.localAddress != null) {
            addresses.add(details.localAddress);
        }
        if (details.remoteAddress != null) {
            addresses.add(details.remoteAddress);
        }
        if (details.ipv6Address != null) {
            addresses.add(details.ipv6Address);
        }
        // Handle migration of the old field
        if (details.manualAddress != null) {
            details.manualAddresses.add(details.manualAddress);
        }
        addresses.addAll(details.manualAddresses);

        // Use a HashSet to ensure we only add unique addresses
        java.util.HashSet<ComputerDetails.AddressTuple> uniqueAddresses = new java.util.HashSet<>(addresses);

        // Now add a new entry for each unique address
        for (ComputerDetails.AddressTuple addr : uniqueAddresses) {
            // Only add if we're showing offline PCs or this specific address is reachable
            if (prefs.showOfflinePcs || details.reachableAddresses.contains(addr)) {
                pcGridAdapter.addComputer(new ComputerObject(details, addr));
            }
        }

        if (pcGridAdapter.getCount() > 0) {
            // Hide the "Discovery in progress" view
            noPcFoundLayout.setVisibility(View.INVISIBLE);
        } else {
            noPcFoundLayout.setVisibility(View.VISIBLE);
        }

        // Notify the view that the data has changed
        pcGridAdapter.notifyDataSetChanged();
    }

    @Override
    public int getAdapterFragmentLayoutId() {
        return R.layout.pc_grid_view;
    }

    @Override
    public void receiveAbsListView(AbsListView listView) {
        listView.setAdapter(pcGridAdapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int pos,
                                    long id) {
                ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(pos);
                if (computer.details.state == ComputerDetails.State.UNKNOWN ||
                    computer.details.state == ComputerDetails.State.OFFLINE) {
                    // Open the context menu if a PC is offline or refreshing
                    openContextMenu(arg1);
                } else if (computer.details.pairState != PairState.PAIRED) {
                    // Pair an unpaired machine by default
                    doPair(computer);
                } else {
                    doAppList(computer, false, false);
                }
            }
        });
        UiHelper.applyStatusBarPadding(listView);
        registerForContextMenu(listView);
    }

    public static class ComputerObject {
        public ComputerDetails details;
        public ComputerDetails.AddressTuple address;

        public ComputerObject(ComputerDetails details, ComputerDetails.AddressTuple address) {
            if (details == null) {
                throw new IllegalArgumentException("details must not be null");
            }
            this.details = details;
            this.address = address;
        }

        @Override
        public String toString() {
            return details.name;
        }
    }
}
