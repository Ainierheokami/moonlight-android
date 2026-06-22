package com.limelight;

import java.io.FileNotFoundException;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.util.Log;
import com.limelight.binding.PlatformBinding;
import com.limelight.binding.crypto.AndroidCryptoProvider;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.PcGridAdapter;
import com.limelight.grid.PcRecyclerAdapter;
import com.limelight.grid.assets.ComputerScreenshotCache;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.heokami.StreamEnhanceMenu;
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
import android.app.AlertDialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.MotionEvent;

import org.xmlpull.v1.XmlPullParserException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PcView extends Activity implements AdapterFragmentCallbacks {
    private View noPcFoundLayout;
    private View pcRefreshOverlay;
    private PcGridAdapter pcGridAdapter;
    private PcRecyclerAdapter pcRecyclerAdapter;
    private ShortcutHelper shortcutHelper;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private boolean freezeUpdates, runningPolling, inForeground, completeOnCreateCalled;
    private boolean debugSampleComputersAdded;
    private PreferenceConfiguration prefs;
    private Map<String, PairingTask> activePairingTasks = new HashMap<>();
    private Set<String> activeServicePairingTasks = new HashSet<>();
    private static final String PAIRING_TAG = "Pairing";
    private PairingService pairingService;
    private boolean pairingServiceBound = false;
    private final ServiceConnection pairingServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PairingService.PairingBinder binder = (PairingService.PairingBinder) service;
            pairingService = binder.getService();
            pairingServiceBound = true;
            Log.i(PAIRING_TAG, "配对服务已连接");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            pairingService = null;
            pairingServiceBound = false;
            Log.i(PAIRING_TAG, "配对服务已断开");
        }
    };
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
            runningPolling = false;
        }
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i("PcView", "onConfigurationChanged() called, completeOnCreateCalled: " + completeOnCreateCalled + ", pcGridAdapter: " + (pcGridAdapter != null));

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

    public final static int PAIR_ID = 2;
    public final static int UNPAIR_ID = 3;
    public final static int WOL_ID = 4;
    public final static int DELETE_ID = 5;
    public final static int RESUME_ID = 6;
    public final static int QUIT_ID = 7;
    public final static int VIEW_DETAILS_ID = 8;
    public final static int FULL_APP_LIST_ID = 9;
    public final static int TEST_NETWORK_ID = 10;
    public final static int GAMESTREAM_EOL_ID = 11;
    public final static int DELETE_IP_ID = 12;

    private void initializeViews() {
        Log.i("PcView", "initializeViews() called, pcGridAdapter: " + (pcGridAdapter != null));
        
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
            Log.e("PcView", "pcGridAdapter is null in initializeViews()!");
            // Try to reinitialize if null
            if (prefs != null) {
                pcGridAdapter = new PcGridAdapter(this, prefs);
                Log.i("PcView", "Reinitialized pcGridAdapter in initializeViews()");
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

        ImageButton stopRefreshButton = findViewById(R.id.stopRefreshButton);
        ImageButton refreshButton = findViewById(R.id.refreshButton);

        stopRefreshButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // 停止刷新
                stopComputerUpdates(true);
                Toast.makeText(PcView.this, R.string.scut_stop_refresh, Toast.LENGTH_SHORT).show();
            }
        });

        refreshButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                //通过停止并重新开始来触发手动刷新
                stopComputerUpdates(true);
                startComputerUpdates();
                Toast.makeText(PcView.this, R.string.scut_refresh_list, Toast.LENGTH_SHORT).show();
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
        pcRefreshOverlay = findViewById(R.id.pcRefreshOverlay);
        if (pcGridAdapter != null && pcGridAdapter.getCount() == 0) {
            noPcFoundLayout.setVisibility(View.VISIBLE);
        }
        else {
            noPcFoundLayout.setVisibility(View.INVISIBLE);
        }
        updateRefreshOverlay(false);
        if (pcGridAdapter != null) {
            pcGridAdapter.notifyDataSetChanged();
        }

        addDebugSampleComputersIfNeeded();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("PcView", "onCreate() started");

        // Assume we're in the foreground when created to avoid a race
        // between binding to CMS and onResume()
        inForeground = true;

        // Create a GLSurfaceView to fetch GLRenderer unless we have
        // a cached result already.
        final GlPreferences glPrefs = GlPreferences.readPreferences(this);
        if (!glPrefs.savedFingerprint.equals(Build.FINGERPRINT) || glPrefs.glRenderer.isEmpty()) {
            Log.i("PcView", "Creating GLSurfaceView for renderer detection");
            GLSurfaceView surfaceView = new GLSurfaceView(this);
            surfaceView.setRenderer(new GLSurfaceView.Renderer() {
                @Override
                public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                    // Save the GLRenderer string so we don't need to do this next time
                    glPrefs.glRenderer = gl10.glGetString(GL10.GL_RENDERER);
                    glPrefs.savedFingerprint = Build.FINGERPRINT;
                    glPrefs.writePreferences();

                    Log.i("PcView", "Fetched GL Renderer: " + glPrefs.glRenderer);

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
            Log.i("PcView", "Using cached GL Renderer: " + glPrefs.glRenderer);
            completeOnCreate();
        }
    }

    private void completeOnCreate() {
        completeOnCreateCalled = true;

        Log.i("PcView", "completeOnCreate() started");

        shortcutHelper = new ShortcutHelper(this);

        UiHelper.setLocale(this);

        // Bind to the computer manager service
        bindService(new Intent(PcView.this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);
        
        // Bind to the pairing service
        bindService(new Intent(PcView.this, PairingService.class), pairingServiceConnection,
                Service.BIND_AUTO_CREATE);

        prefs = PreferenceConfiguration.readPreferences(this);
        pcGridAdapter = new PcGridAdapter(this, prefs);
        pcRecyclerAdapter = new PcRecyclerAdapter(this);
        Log.i("PcView", "pcGridAdapter initialized: " + (pcGridAdapter != null));

        initializeViews();
    }

    private void startComputerUpdates() {
        // Only allow polling to start if we're bound to CMS, polling is not already running,
        // and our activity is in the foreground.
        if (managerBinder != null && !runningPolling && inForeground) {
            freezeUpdates = false;
            updateRefreshOverlay(true);
            
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (pcGridAdapter != null) {
                        pcGridAdapter.setPolling(true);
                    }
                    if (pcRecyclerAdapter != null) {
                        pcRecyclerAdapter.setPolling(true);
                    }
                }
            });

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
                updateRefreshOverlay(false);
                return;
            }

            freezeUpdates = true;
            
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (pcGridAdapter != null) {
                        pcGridAdapter.setPolling(false);
                    }
                    if (pcRecyclerAdapter != null) {
                        pcRecyclerAdapter.setPolling(false);
                    }
                }
            });

            managerBinder.stopPolling();

            if (wait) {
                managerBinder.waitForPollingStopped();
            }

            runningPolling = false;
            updateRefreshOverlay(false);
        }
    }

    private void updateRefreshOverlay(final boolean refreshing) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (pcRefreshOverlay != null) {
                    pcRefreshOverlay.setVisibility(refreshing ? View.VISIBLE : View.GONE);
                }
                if (noPcFoundLayout != null && (pcGridAdapter == null || pcGridAdapter.getCount() == 0)) {
                    noPcFoundLayout.setVisibility(refreshing ? View.GONE : View.VISIBLE);
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("PcView", "onDestroy() called");

        if (managerBinder != null) {
            unbindService(serviceConnection);
        }
        
        if (pairingServiceBound) {
            unbindService(pairingServiceConnection);
            pairingServiceBound = false;
        }
        
        // 取消所有正在进行的配对任务
        for (PairingTask task : activePairingTasks.values()) {
            task.cancelPairing();
        }
        activePairingTasks.clear();
        activeServicePairingTasks.clear();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.i("PcView", "onResume() called, pcGridAdapter: " + (pcGridAdapter != null) + ", completeOnCreateCalled: " + completeOnCreateCalled);

        // Display a decoder crash notification if we've returned after a crash
        UiHelper.showDecoderCrashDialog(this);

        // Reload preferences in case they have changed
        prefs = PreferenceConfiguration.readPreferences(this);
        
        // Ensure pcGridAdapter is initialized before calling methods on it
        // This prevents the NullPointerException that was occurring
        if (pcGridAdapter == null && prefs != null) {
            Log.w("PcView", "pcGridAdapter was null in onResume(), initializing now");
            pcGridAdapter = new PcGridAdapter(this, prefs);
        }

        if (pcRecyclerAdapter == null) {
            Log.w("PcView", "pcRecyclerAdapter was null in onResume(), initializing now");
            pcRecyclerAdapter = new PcRecyclerAdapter(this);
        }

        
        // Only update layout if adapter is properly initialized
        if (pcGridAdapter != null) {
            pcGridAdapter.updateLayoutWithPreferences(this, prefs);
            pcGridAdapter.notifyDataSetChanged();
        } else {
            Log.e("PcView", "Unable to initialize pcGridAdapter in onResume()");
        }
        if (pcRecyclerAdapter != null) {
            pcRecyclerAdapter.notifyDataSetChanged();
        }

        inForeground = true;
        startComputerUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i("PcView", "onPause() called");

        inForeground = false;
        stopComputerUpdates(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i("PcView", "onStop() called");

        Dialog.closeDialogs();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        Log.d("PcView", "onCreateContextMenu called, view: " + v.getClass().getSimpleName() +
              ", view id: " + v.getId() + ", menuInfo: " + menuInfo);
        stopComputerUpdates(false);

        // Call superclass
        super.onCreateContextMenu(menu, v, menuInfo);
        
        ComputerObject computer = null;
        
        // 处理GridView的上下文菜单
        if (menuInfo instanceof AdapterContextMenuInfo) {
            AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
            computer = (ComputerObject) pcGridAdapter.getItem(info.position);
            Log.d("PcView", "GridView上下文菜单，位置: " + info.position);
        }
        // 处理RecyclerView的上下文菜单
        else {
            // 对于RecyclerView，我们需要从view的tag中获取位置信息
            Object tag = v.getTag();
            Log.d("PcView", "RecyclerView上下文菜单，view类型: " + v.getClass().getSimpleName() +
                  ", tag: " + tag + ", adapter: " + (pcRecyclerAdapter != null));
            
            if (tag instanceof Integer) {
                int position = (Integer) tag;
                if (pcRecyclerAdapter != null && position >= 0 && position < pcRecyclerAdapter.getItemCount()) {
                    computer = pcRecyclerAdapter.getItem(position);
                    Log.d("PcView", "获取到计算机对象: " + (computer != null ? computer.details.name : "null"));
                } else {
                    Log.w("PcView", "无效的位置或适配器为空: position=" + position + ", adapter=" + pcRecyclerAdapter);
                }
            } else {
                Log.w("PcView", "无法获取位置信息，tag类型: " + (tag != null ? tag.getClass().getSimpleName() : "null") +
                      ", view id: " + v.getId());
            }
        }
        
        if (computer == null) {
            Log.w("PcView", "无法获取计算机对象，跳过菜单创建");
            return;
        }

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
        if (computer.address != null) {
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

    public void doPair(final ComputerObject computer) {
        Log.i(PAIRING_TAG, "开始配对: " + computer.details.name + " (" + computer.address + ")");
        
        if (computer.details.state == ComputerDetails.State.OFFLINE || computer.address == null) {
            Log.w(PAIRING_TAG, "配对失败: PC离线或地址为空");
            Toast.makeText(PcView.this, getResources().getString(R.string.pair_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Log.e(PAIRING_TAG, "配对失败: ComputerManager服务未运行");
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        // 检查是否已经在配对中
        if (pcGridAdapter.isPairing(computer)) {
            Log.i(PAIRING_TAG, "配对已在进行中，跳过重复配对");
            return;
        }

        // 开始配对 - 在UI中显示配对状态
        final String pinStr = PairingManager.generatePinString();
        Log.i(PAIRING_TAG, "生成PIN码: " + pinStr);
        pcGridAdapter.startPairing(computer, pinStr);
        if (pcRecyclerAdapter != null) {
            pcRecyclerAdapter.startPairing(computer, pinStr);
        }

        // Keep discovery polling from replacing this row while the stateful pairing
        // handshake is in progress.
        stopComputerUpdates(true);

        // 使用后台服务执行配对，确保在应用切换到后台时也能继续
        if (pairingServiceBound && pairingService != null) {
            String taskKey = getComputerKey(computer);
            activeServicePairingTasks.add(taskKey);
            Log.i(PAIRING_TAG, "启动后台配对服务: " + taskKey);
            
            // 创建配对回调
            final ComputerObject finalComputer = computer;
            PairingService.PairingCallback callback = new PairingService.PairingCallback() {
                @Override
                public void onPairingSuccess(ComputerDetails computerDetails) {
                    Log.i(PAIRING_TAG, "后台配对成功: " + computerDetails.name);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String taskKey = getComputerKey(finalComputer);
                            activeServicePairingTasks.remove(taskKey);

                            computerDetails.pairState = PairState.PAIRED;
                            finalComputer.details.update(computerDetails);
                            managerBinder.updateComputer(computerDetails);
                            managerBinder.invalidateStateForComputer(computerDetails.uuid);

                            pcGridAdapter.updatePairingStatus(finalComputer, PcGridAdapter.PairingStatus.SUCCESS);
                            if (pcRecyclerAdapter != null) {
                                pcRecyclerAdapter.updatePairingStatus(finalComputer, PcRecyclerAdapter.PairingStatus.SUCCESS);
                            }
                            // 延迟打开应用列表，让用户看到成功状态
                            new android.os.Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    pcGridAdapter.clearPairingStatus(finalComputer);
                                    if (pcRecyclerAdapter != null) {
                                        pcRecyclerAdapter.clearPairingStatus(finalComputer);
                                    }
                                    doAppList(finalComputer, true, false);
                                }
                            }, 1000);
                        }
                    });
                }

                @Override
                public void onPairingFailed(ComputerDetails computerDetails, String errorMessage) {
                    Log.e(PAIRING_TAG, "后台配对失败: " + computerDetails.name + " - " + errorMessage);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String taskKey = getComputerKey(finalComputer);
                            activeServicePairingTasks.remove(taskKey);

                            pcGridAdapter.updatePairingStatus(finalComputer, PcGridAdapter.PairingStatus.FAILED);
                            if (pcRecyclerAdapter != null) {
                                pcRecyclerAdapter.updatePairingStatus(finalComputer, PcRecyclerAdapter.PairingStatus.FAILED);
                            }
                            // 延迟清除状态并显示错误消息
                            new android.os.Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    pcGridAdapter.clearPairingStatus(finalComputer);
                                    if (pcRecyclerAdapter != null) {
                                        pcRecyclerAdapter.clearPairingStatus(finalComputer);
                                    }
                                    Toast.makeText(PcView.this, errorMessage, Toast.LENGTH_LONG).show();
                                    // 重新开始轮询
                                    startComputerUpdates();
                                }
                            }, 1500);
                        }
                    });
                }

                @Override
                public void onPairingCancelled(ComputerDetails computerDetails) {
                    Log.i(PAIRING_TAG, "后台配对取消: " + computerDetails.name);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String taskKey = getComputerKey(finalComputer);
                            activeServicePairingTasks.remove(taskKey);

                            pcGridAdapter.updatePairingStatus(finalComputer, PcGridAdapter.PairingStatus.CANCELLED);
                            if (pcRecyclerAdapter != null) {
                                pcRecyclerAdapter.updatePairingStatus(finalComputer, PcRecyclerAdapter.PairingStatus.CANCELLED);
                            }
                            // 延迟清除配对状态，让用户看到取消状态
                            new android.os.Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    Log.i(PAIRING_TAG, "清除配对状态: " + finalComputer.details.name);
                                    pcGridAdapter.clearPairingStatus(finalComputer);
                                    // 重新开始轮询
                                    startComputerUpdates();
                                }
                            }, 1500);
                        }
                    });
                }
            };
            
            // 启动后台配对服务
            pairingService.startPairing(computer.details, computer.address.address, computer.address.port, pinStr, callback);
        } else {
            Log.w(PAIRING_TAG, "配对服务未绑定，使用原来的AsyncTask");
            // 如果服务未绑定，回退到原来的AsyncTask实现
            PairingTask pairingTask = new PairingTask(computer, pinStr);
            String taskKey = getComputerKey(computer);
            activePairingTasks.put(taskKey, pairingTask);
            Log.i(PAIRING_TAG, "启动配对任务: " + taskKey);
            pairingTask.execute();
        }
    }

    private String getComputerKey(ComputerObject computer) {
        return computer.details.uuid + "_" + (computer.address != null ? computer.address.toString() : "");
    }

    // 取消配对
    public void cancelPairing(final ComputerObject computer) {
        Log.i(PAIRING_TAG, "用户取消配对: " + computer.details.name);
        
        if (pcGridAdapter.isPairing(computer)) {
            String taskKey = getComputerKey(computer);
            PairingTask pairingTask = activePairingTasks.get(taskKey);
            if (pairingTask != null) {
                Log.i(PAIRING_TAG, "取消配对任务: " + taskKey);
                pairingTask.cancelPairing();
                activePairingTasks.remove(taskKey);
            } else if (activeServicePairingTasks.remove(taskKey) && pairingServiceBound && pairingService != null) {
                Log.i(PAIRING_TAG, "取消后台配对服务任务: " + taskKey);
                pairingService.cancelPairing();
            } else {
                Log.w(PAIRING_TAG, "未找到对应的配对任务: " + taskKey);
            }
            
            pcGridAdapter.updatePairingStatus(computer, PcGridAdapter.PairingStatus.CANCELLED);
            if (pcRecyclerAdapter != null) {
                pcRecyclerAdapter.updatePairingStatus(computer, PcRecyclerAdapter.PairingStatus.CANCELLED);
            }
            // 延迟清除配对状态，让用户看到取消状态
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.i(PAIRING_TAG, "清除配对状态: " + computer.details.name);
                    pcGridAdapter.clearPairingStatus(computer);
                    if (pcRecyclerAdapter != null) {
                        pcRecyclerAdapter.clearPairingStatus(computer);
                    }
                    // 重新开始轮询
                    startComputerUpdates();
                }
            }, 1500);
        } else {
            Log.w(PAIRING_TAG, "取消配对失败: 该PC不在配对状态");
        }
    }

    // 配对任务类 - 在后台执行配对
    private class PairingTask extends android.os.AsyncTask<Void, Void, PairingResult> {
        private final ComputerObject computer;
        private final String pinStr;
        private volatile boolean cancelled = false;
        private static final int MAX_RETRY_COUNT = 2;
        private static final int PAIRING_TIMEOUT_MS = 30000; // 30秒超时
        private long backgroundStartTime = 0;
        private boolean wasInBackground = false;

        public PairingTask(ComputerObject computer, String pinStr) {
            this.computer = computer;
            this.pinStr = pinStr;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Log.i(PAIRING_TAG, "配对任务开始: " + computer.details.name + " PIN: " + pinStr);
            // 停止更新以专注于配对
            stopComputerUpdates(true);
        }
        
        @Override
        protected void onProgressUpdate(Void... values) {
            // 检查应用是否在后台
            if (!inForeground) {
                if (!wasInBackground) {
                    wasInBackground = true;
                    backgroundStartTime = System.currentTimeMillis();
                    Log.w(PAIRING_TAG, "应用切换到后台，配对可能被中断");
                } else {
                    long backgroundTime = System.currentTimeMillis() - backgroundStartTime;
                    if (backgroundTime > 10000) { // 在后台超过10秒
                        Log.w(PAIRING_TAG, "应用在后台时间过长(" + backgroundTime + "ms)，建议用户返回应用");
                    }
                }
            } else {
                if (wasInBackground) {
                    wasInBackground = false;
                    Log.i(PAIRING_TAG, "应用回到前台，继续配对");
                }
            }
        }

        @Override
        protected PairingResult doInBackground(Void... voids) {
            NvHTTP httpConn = null;
            int retryCount = 0;
            
            while (retryCount <= MAX_RETRY_COUNT && !cancelled) {
                try {
                    Log.i(PAIRING_TAG, "第 " + (retryCount + 1) + " 次配对尝试");
                    
                    // 定期检查应用状态
                    publishProgress();
                    httpConn = new NvHTTP(computer.address,
                            computer.details.httpsPort, managerBinder.getUniqueId(), computer.details.serverCert,
                            PlatformBinding.getCryptoProvider(PcView.this));
                    
                    // 检查是否已经配对
                    if (httpConn.getPairState() == PairState.PAIRED) {
                        Log.i(PAIRING_TAG, "PC已经配对，跳过配对过程");
                        return new PairingResult(true, null, httpConn);
                    }

                    // 设置配对超时
                    long startTime = System.currentTimeMillis();
                    PairingManager pm = httpConn.getPairingManager();
                    PairState pairState = null;
                    
                    Log.i(PAIRING_TAG, "开始执行配对过程");
                    // 在超时时间内执行配对
                    while (System.currentTimeMillis() - startTime < PAIRING_TIMEOUT_MS && !cancelled) {
                        try {
                            pairState = pm.pair(httpConn.getServerInfo(true), pinStr);
                            Log.i(PAIRING_TAG, "配对API调用完成，状态: " + pairState);
                            break; // 配对完成，退出循环
                        } catch (IOException e) {
                            // 网络错误，可能是临时问题，等待后重试
                            Log.w(PAIRING_TAG, "配对网络错误: " + e.getMessage());
                            
                            // 检查是否因为应用在后台导致的错误
                            if (!inForeground && e.getMessage() != null &&
                                (e.getMessage().contains("abort") || e.getMessage().contains("end of stream"))) {
                                Log.w(PAIRING_TAG, "检测到后台中断错误，建议用户保持应用在前台");
                            }
                            
                            if (System.currentTimeMillis() - startTime < PAIRING_TIMEOUT_MS - 5000) {
                                // 还有时间，等待1秒后重试
                                Log.i(PAIRING_TAG, "网络错误重试，等待1秒");
                                Thread.sleep(1000);
                                continue;
                            } else {
                                throw e; // 超时了，抛出异常
                            }
                        }
                    }
                    
                    if (cancelled) {
                        Log.i(PAIRING_TAG, "配对任务被取消");
                        return new PairingResult(false, getResources().getString(R.string.pairing_status_cancelled), httpConn);
                    }

                    // 检查超时
                    if (System.currentTimeMillis() - startTime >= PAIRING_TIMEOUT_MS) {
                        Log.e(PAIRING_TAG, "配对超时，耗时: " + (System.currentTimeMillis() - startTime) + "ms");
                        return new PairingResult(false, getResources().getString(R.string.pairing_timeout), httpConn);
                    }

                    if (pairState == PairState.PIN_WRONG) {
                        Log.e(PAIRING_TAG, "PIN码错误");
                        return new PairingResult(false, getResources().getString(R.string.pair_incorrect_pin), httpConn);
                    }
                    else if (pairState == PairState.FAILED) {
                        if (computer.details.runningGameId != 0) {
                            Log.e(PAIRING_TAG, "配对失败: PC正在游戏中");
                            return new PairingResult(false, getResources().getString(R.string.pair_pc_ingame), httpConn);
                        }
                        else {
                            // 如果是第一次失败，重试
                            if (retryCount < MAX_RETRY_COUNT) {
                                retryCount++;
                                Log.i(PAIRING_TAG, "配对失败，第 " + retryCount + " 次重试");
                                Thread.sleep(2000); // 等待2秒后重试
                                continue;
                            } else {
                                Log.e(PAIRING_TAG, "配对失败，已达到最大重试次数");
                                return new PairingResult(false, getResources().getString(R.string.pair_fail), httpConn);
                            }
                        }
                    }
                    else if (pairState == PairState.ALREADY_IN_PROGRESS) {
                        Log.e(PAIRING_TAG, "配对失败: 已有其他设备正在配对");
                        return new PairingResult(false, getResources().getString(R.string.pair_already_in_progress), httpConn);
                    }
                    else if (pairState == PairState.PAIRED) {
                        Log.i(PAIRING_TAG, "配对成功!");
                        // 保存配对证书
                        managerBinder.getComputer(computer.details.uuid).serverCert = pm.getPairedCert();
                        // 强制刷新状态
                        managerBinder.invalidateStateForComputer(computer.details.uuid);
                        return new PairingResult(true, null, httpConn);
                    }
                    else {
                        Log.e(PAIRING_TAG, "配对失败: 未知状态 " + pairState);
                        return new PairingResult(false, getResources().getString(R.string.pair_fail), httpConn);
                    }
                } catch (UnknownHostException e) {
                    return new PairingResult(false, getResources().getString(R.string.error_unknown_host), httpConn);
                } catch (FileNotFoundException e) {
                    return new PairingResult(false, getResources().getString(R.string.error_404), httpConn);
                } catch (XmlPullParserException e) {
                    return new PairingResult(false, getResources().getString(R.string.pairing_server_error), httpConn);
                } catch (IOException e) {
                    // 网络错误，重试
                    if (retryCount < MAX_RETRY_COUNT) {
                        retryCount++;
                        Log.i(PAIRING_TAG, "网络错误，第 " + retryCount + " 次重试: " + e.getMessage());
                        try {
                            Thread.sleep(2000); // 等待2秒后重试
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return new PairingResult(false, getResources().getString(R.string.pairing_interrupted), httpConn);
                        }
                        continue;
                    } else {
                        return new PairingResult(false, getResources().getString(R.string.pairing_network_error) + ": " + e.getMessage(), httpConn);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new PairingResult(false, getResources().getString(R.string.pairing_interrupted), httpConn);
                } catch (Exception e) {
                    // 其他未知异常
                    e.printStackTrace();
                    return new PairingResult(false, getResources().getString(R.string.pairing_unknown_error) + ": " + e.getMessage(), httpConn);
                }
            }
            
            return new PairingResult(false, getResources().getString(R.string.pairing_max_retries), httpConn);
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
                if (pcRecyclerAdapter != null) {
                    pcRecyclerAdapter.updatePairingStatus(computer, PcRecyclerAdapter.PairingStatus.SUCCESS);
                }
                // 延迟打开应用列表，让用户看到成功状态
                new android.os.Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        pcGridAdapter.clearPairingStatus(computer);
                        if (pcRecyclerAdapter != null) {
                            pcRecyclerAdapter.clearPairingStatus(computer);
                        }
                        if (pcRecyclerAdapter != null) {
                            pcRecyclerAdapter.clearPairingStatus(computer);
                        }
                        doAppList(computer, true, false);
                    }
                }, 1000);
            } else {
                // 配对失败
                pcGridAdapter.updatePairingStatus(computer, PcGridAdapter.PairingStatus.FAILED);
                if (pcRecyclerAdapter != null) {
                    pcRecyclerAdapter.updatePairingStatus(computer, PcRecyclerAdapter.PairingStatus.FAILED);
                }
                // 延迟清除状态并显示错误消息
                new android.os.Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        pcGridAdapter.clearPairingStatus(computer);
                        String errorMessage = result.message;
                        
                        // 如果应用在后台时间过长，给出更明确的提示
                        if (wasInBackground && (System.currentTimeMillis() - backgroundStartTime) > 5000) {
                            errorMessage = "配对失败：应用在后台时间过长，请保持Moonlight在前台完成配对";
                            Log.w(PAIRING_TAG, "配对失败原因：应用在后台时间过长");
                        }
                        
                        if (errorMessage != null) {
                            Toast.makeText(PcView.this, errorMessage, Toast.LENGTH_LONG).show();
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

    public void doAppList(ComputerObject computer, boolean newlyPaired, boolean showHiddenGames) {
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
                confirmRemoveComputer(computer);
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
       if (managerBinder == null) {
           Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
           return;
       }
       if (computer == null || computer.address == null) {
           return;
       }
       if (!removeSelectedAddress(computer.details, computer.address)) {
           return;
       }

       // Save the updated computer details
       managerBinder.updateComputer(computer.details);
       Toast.makeText(PcView.this, getResources().getString(R.string.delete_ip_success), Toast.LENGTH_SHORT).show();

       // The UI will be refreshed by the callback
   }

   private boolean removeSelectedAddress(ComputerDetails details, ComputerDetails.AddressTuple address) {
       boolean removed = false;

       // Create a new set to avoid modifying the one we're iterating over.
       HashSet<ComputerDetails.AddressTuple> newManualAddresses = new HashSet<>(details.manualAddresses);
       removed |= newManualAddresses.remove(address);
       details.manualAddresses = newManualAddresses;

       if (address.equals(details.localAddress)) {
           details.localAddress = null;
           removed = true;
       }
       if (address.equals(details.remoteAddress)) {
           details.remoteAddress = null;
           removed = true;
       }
       if (address.equals(details.ipv6Address)) {
           details.ipv6Address = null;
           removed = true;
       }
       if (address.equals(details.activeAddress)) {
           details.activeAddress = null;
       }

       details.reachableAddresses.remove(address);
       return removed;
   }
   
   // 处理自定义上下文菜单项点击
    public boolean onContextItemSelected(android.view.MenuItem item, ComputerObject computer) {
       Log.d("PcView", "自定义菜单项点击: " + item.getItemId() + ", 计算机: " + computer.details.name);
       
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
               confirmRemoveComputer(computer);
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
               return false;
       }
   }

    public void showComputerActions(final ComputerObject computer) {
        if (computer == null || computer.details == null || isFinishing()) {
            return;
        }

        final AlertDialog actionDialog = new AlertDialog.Builder(this).create();
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_pc_actions, null);

        TextView title = content.findViewById(R.id.pcActionTitle);
        TextView subtitle = content.findViewById(R.id.pcActionSubtitle);
        LinearLayout actionList = content.findViewById(R.id.pcActionList);

        title.setText(computer.details.name);
        String addressText = computer.address != null ? computer.address.address : getString(R.string.pcview_menu_header_unknown);
        subtitle.setText(getString(R.string.pc_actions_address, addressText));

        if (computer.details.state == ComputerDetails.State.OFFLINE ||
                computer.details.state == ComputerDetails.State.UNKNOWN) {
            addComputerAction(actionList, actionDialog, R.string.pcview_menu_send_wol, false, new Runnable() {
                @Override
                public void run() {
                    doWakeOnLan(computer.details);
                }
            });
            addComputerAction(actionList, actionDialog, R.string.pcview_menu_eol, false, new Runnable() {
                @Override
                public void run() {
                    HelpLauncher.launchGameStreamEolFaq(PcView.this);
                }
            });
        }
        else if (computer.details.pairState != PairState.PAIRED) {
            addComputerAction(actionList, actionDialog, R.string.pcview_menu_pair_pc, false, new Runnable() {
                @Override
                public void run() {
                    doPair(computer);
                }
            });
            if (computer.details.nvidiaServer) {
                addComputerAction(actionList, actionDialog, R.string.pcview_menu_eol, false, new Runnable() {
                    @Override
                    public void run() {
                        HelpLauncher.launchGameStreamEolFaq(PcView.this);
                    }
                });
            }
        }
        else {
            if (computer.details.runningGameId != 0) {
                addComputerAction(actionList, actionDialog, R.string.applist_menu_resume, false, new Runnable() {
                    @Override
                    public void run() {
                        if (managerBinder == null) {
                            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                            return;
                        }
                        ServerHelper.doStart(PcView.this, new NvApp("app", computer.details.runningGameId, false), computer.details, managerBinder);
                    }
                });
                addComputerAction(actionList, actionDialog, R.string.applist_menu_quit, false, new Runnable() {
                    @Override
                    public void run() {
                        if (managerBinder == null) {
                            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                            return;
                        }
                        UiHelper.displayQuitConfirmationDialog(PcView.this, new Runnable() {
                            @Override
                            public void run() {
                                ServerHelper.doQuit(PcView.this, computer.details,
                                        new NvApp("app", 0, false), managerBinder, null);
                            }
                        }, null);
                    }
                });
            }

            if (computer.details.nvidiaServer) {
                addComputerAction(actionList, actionDialog, R.string.pcview_menu_eol, false, new Runnable() {
                    @Override
                    public void run() {
                        HelpLauncher.launchGameStreamEolFaq(PcView.this);
                    }
                });
            }

            addComputerAction(actionList, actionDialog, R.string.pcview_menu_app_list, false, new Runnable() {
                @Override
                public void run() {
                    doAppList(computer, false, true);
                }
            });
        }

        addComputerAction(actionList, actionDialog, R.string.pcview_menu_test_network, false, new Runnable() {
            @Override
            public void run() {
                ServerHelper.doNetworkTest(PcView.this);
            }
        });

        addComputerAction(actionList, actionDialog, R.string.pcview_menu_stream_enhance, false, new Runnable() {
            @Override
            public void run() {
                if (managerBinder == null) {
                    Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                    return;
                }
                StreamEnhanceMenu.show(PcView.this, computer.details, managerBinder.getUniqueId());
            }
        });

        if (BuildConfig.DEBUG) {
            addComputerAction(actionList, actionDialog, R.string.debug_write_test_cover, false, new Runnable() {
                @Override
                public void run() {
                    writeDebugTestCover(computer);
                }
            });
        }

        if (computer.address != null) {
            addComputerAction(actionList, actionDialog, R.string.pc_view_delete_ip, true, new Runnable() {
                @Override
                public void run() {
                    confirmRemoveIp(computer);
                }
            });
        }

        addComputerAction(actionList, actionDialog, R.string.pcview_menu_delete_pc, true, new Runnable() {
            @Override
            public void run() {
                confirmRemoveComputer(computer);
            }
        });

        addComputerAction(actionList, actionDialog, R.string.pcview_menu_details, false, new Runnable() {
            @Override
            public void run() {
                Dialog.displayDialog(PcView.this, getResources().getString(R.string.title_details), computer.details.toString(), false);
            }
        });

        actionDialog.setView(content);
        actionDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                startComputerUpdates();
            }
        });
        stopComputerUpdates(false);
        actionDialog.show();

        Window window = actionDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void addComputerAction(LinearLayout actionList, final AlertDialog dialog, int labelResId,
                                   boolean dangerous, final Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(labelResId);
        button.setTextColor(dangerous ? Color.rgb(255, 172, 186) : Color.rgb(220, 230, 241));
        button.setTextSize(14);
        button.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setBackgroundResource(dangerous ? R.drawable.pc_action_danger_button_background : R.drawable.pc_action_button_background);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setStateListAnimator(null);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, 0, 0, dp(8));
        actionList.addView(button, params);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                if (action != null) {
                    action.run();
                }
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void writeDebugTestCover(ComputerObject computer) {
        if (computer == null || computer.details == null || computer.details.uuid == null) {
            return;
        }

        ComputerScreenshotCache screenshotCache = new ComputerScreenshotCache(this);
        screenshotCache.saveBitmap(computer.details.uuid,
                createDebugCoverBitmap("#132235", "#11B8A7", "TEST " + System.currentTimeMillis() % 100000));
        File coverFile = screenshotCache.getFile(computer.details.uuid);
        long coverSize = coverFile != null && coverFile.exists() ? coverFile.length() : 0;

        if (pcGridAdapter != null) {
            pcGridAdapter.notifyDataSetChanged();
        }
        if (pcRecyclerAdapter != null) {
            pcRecyclerAdapter.notifyDataSetChanged();
        }

        Toast.makeText(this, getString(R.string.debug_test_cover_written, coverSize), Toast.LENGTH_SHORT).show();
    }

    private void confirmRemoveComputer(final ComputerObject computer) {
        if (ActivityManager.isUserAMonkey()) {
            LimeLog.info("Ignoring delete PC request from monkey");
            return;
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
    }

    private void confirmRemoveIp(final ComputerObject computer) {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_modern_message, null);
        ((TextView) content.findViewById(R.id.dialogTitleText)).setText(computer.details.name);
        ((TextView) content.findViewById(R.id.dialogMessageText)).setText(getString(R.string.delete_ip_msg));

        Button helpButton = content.findViewById(R.id.dialogHelpButton);
        helpButton.setText(R.string.no);
        Button okButton = content.findViewById(R.id.dialogOkButton);
        okButton.setText(R.string.yes);

        helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                doRemoveIp(computer);
            }
        });

        dialog.setView(content);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
    
    private void removeComputer(ComputerDetails details) {
        managerBinder.removeComputer(details);

        new DiskAssetLoader(this).deleteAssetsForComputer(details.uuid);
        new ComputerScreenshotCache(this).delete(details.uuid);

        // Delete hidden games preference value
        getSharedPreferences(AppView.HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .edit()
                .remove(details.uuid)
                .apply();

        shortcutHelper.disableComputerShortcut(details,
                getResources().getString(R.string.scut_deleted_pc));

        for (int i = pcGridAdapter.getCount() - 1; i >= 0; i--) {
            ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);

            if (details.uuid.equals(computer.details.uuid)) {
                pcGridAdapter.removeComputer(computer);
            }
        }

        pcGridAdapter.notifyDataSetChanged();
        if (pcRecyclerAdapter != null) {
            List<ComputerObject> currentList = new ArrayList<>();
            for (int i = 0; i < pcGridAdapter.getCount(); i++) {
                currentList.add((ComputerObject) pcGridAdapter.getItem(i));
            }
            pcRecyclerAdapter.setComputers(currentList);
        }

        if (pcGridAdapter.getCount() == 0) {
            // Show the "Discovery in progress" view
            noPcFoundLayout.setVisibility(View.VISIBLE);
            if (pcRefreshOverlay != null && pcRefreshOverlay.getVisibility() == View.VISIBLE) {
                noPcFoundLayout.setVisibility(View.GONE);
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
        List<ComputerObject> computerObjects = new ArrayList<>();
        for (ComputerDetails.AddressTuple addr : uniqueAddresses) {
            // Only add if we're showing offline PCs or this specific address is reachable
            if (prefs.showOfflinePcs || details.reachableAddresses.contains(addr)) {
                ComputerObject computer = new ComputerObject(details, addr);
                pcGridAdapter.addComputer(computer);
                computerObjects.add(computer);
            }
        }

        // 更新RecyclerView适配器
        if (pcRecyclerAdapter != null) {
            List<ComputerObject> currentList = new ArrayList<>();
            for (int i = 0; i < pcGridAdapter.getCount(); i++) {
                currentList.add((ComputerObject) pcGridAdapter.getItem(i));
            }
            pcRecyclerAdapter.setComputers(currentList);
        }

        if (pcGridAdapter.getCount() > 0) {
            // Hide the "Discovery in progress" view
            noPcFoundLayout.setVisibility(View.INVISIBLE);
        } else {
            if (pcRefreshOverlay == null || pcRefreshOverlay.getVisibility() != View.VISIBLE) {
                noPcFoundLayout.setVisibility(View.VISIBLE);
            } else {
                noPcFoundLayout.setVisibility(View.GONE);
            }
        }

        // Notify the view that the data has changed
        pcGridAdapter.notifyDataSetChanged();
    }

    private void addDebugSampleComputersIfNeeded() {
        if (!BuildConfig.DEBUG || debugSampleComputersAdded || pcGridAdapter == null || pcRecyclerAdapter == null) {
            return;
        }

        debugSampleComputersAdded = true;
        ComputerScreenshotCache screenshotCache = new ComputerScreenshotCache(this);

        saveDebugCoverIfMissing(screenshotCache, "demo-online", "#213547", "#38BDF8", "AURORA");
        updateComputer(createDebugComputer(
                "demo-online",
                "Aurora Studio",
                "192.168.31.28",
                ComputerDetails.State.ONLINE,
                PairState.PAIRED,
                0,
                true));

        saveDebugCoverIfMissing(screenshotCache, "demo-running", "#1B2430", "#34D399", "SESSION");
        updateComputer(createDebugComputer(
                "demo-running",
                "Neon Rig",
                "10.0.0.42",
                ComputerDetails.State.ONLINE,
                PairState.PAIRED,
                1,
                true));

        saveDebugCoverIfMissing(screenshotCache, "demo-unpaired", "#2A2336", "#FBBF24", "PAIR");
        updateComputer(createDebugComputer(
                "demo-unpaired",
                "Pairing Lab",
                "172.16.2.15",
                ComputerDetails.State.ONLINE,
                PairState.NOT_PAIRED,
                0,
                true));

        saveDebugCoverIfMissing(screenshotCache, "demo-offline", "#242933", "#8A97A5", "OFFLINE");
        updateComputer(createDebugComputer(
                "demo-offline",
                "Archive PC",
                "192.168.31.77",
                ComputerDetails.State.OFFLINE,
                PairState.PAIRED,
                0,
                false));
    }

    private void saveDebugCoverIfMissing(ComputerScreenshotCache screenshotCache, String uuid,
                                         String startColor, String endColor, String label) {
        File coverFile = screenshotCache.getFile(uuid);
        if (coverFile == null || coverFile.exists()) {
            return;
        }

        screenshotCache.saveBitmap(uuid, createDebugCoverBitmap(startColor, endColor, label));
    }

    private ComputerDetails createDebugComputer(String uuid, String name, String address,
                                                ComputerDetails.State state, PairState pairState,
                                                int runningGameId, boolean reachable) {
        ComputerDetails details = new ComputerDetails();
        details.uuid = uuid;
        details.name = name;
        details.localAddress = new ComputerDetails.AddressTuple(address, NvHTTP.DEFAULT_HTTP_PORT);
        details.activeAddress = details.localAddress;
        details.httpsPort = 0;
        details.state = state;
        details.pairState = pairState;
        details.runningGameId = runningGameId;
        details.nvidiaServer = false;
        if (reachable) {
            details.reachableAddresses.add(details.localAddress);
        }
        return details;
    }

    private Bitmap createDebugCoverBitmap(String backgroundColor, String accentColor, String label) {
        int width = 960;
        int height = 540;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        canvas.drawColor(Color.parseColor(backgroundColor));

        paint.setColor(Color.parseColor(accentColor));
        paint.setAlpha(210);
        canvas.drawRoundRect(new RectF(80, 72, 880, 468), 38, 38, paint);

        paint.setColor(Color.WHITE);
        paint.setAlpha(42);
        canvas.drawCircle(780, 130, 210, paint);
        canvas.drawCircle(130, 430, 170, paint);

        paint.setAlpha(255);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(64);
        paint.setFakeBoldText(true);
        canvas.drawText(label, width / 2f, height / 2f + 22, paint);

        return bitmap;
    }

    @Override
    public int getAdapterFragmentLayoutId() {
        return R.layout.pc_grid_view;
    }

    @Override
    public void receiveAbsListView(AbsListView listView) {
        // 使用原有的GridView实现
        listView.setAdapter(pcGridAdapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int pos,
                                    long id) {
                ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(pos);
                if (computer.details.state == ComputerDetails.State.OFFLINE ||
                    (computer.details.state == ComputerDetails.State.UNKNOWN && computer.address == null)) {
                    // Open the context menu if a PC is offline or refreshing (and we have no address to try)
                    openContextMenu(arg1);
                } else if (computer.details.pairState != PairState.PAIRED) {
                    // Pair an unpaired machine by default
                    doPair(computer);
                } else {
                    doAppList(computer, false, false);
                }
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showComputerActions((ComputerObject) pcGridAdapter.getItem(position));
                return true;
            }
        });
        UiHelper.applyStatusBarPadding(listView);
        registerForContextMenu(listView);
    }
    
    private int calculateSpanCount() {
        // 根据屏幕宽度计算列数，每列160dp + 边距
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        float screenWidthDp = displayMetrics.widthPixels / displayMetrics.density;
        // 考虑边距后计算列数
        return Math.max(2, (int) (screenWidthDp / 170));
    }
    
    @Override
    public void receiveRecyclerView(RecyclerView recyclerView) {
        GridLayoutManager layoutManager = new GridLayoutManager(this, calculateSpanCount());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(pcRecyclerAdapter);
        
        // 添加项目装饰器来提供间距
        recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view,
                                     @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);
                int spanCount = layoutManager.getSpanCount();
                
                // 水平间距
                outRect.left = 10;
                outRect.right = 10;
                
                // 垂直间距
                outRect.top = 10;
                outRect.bottom = 10;
            }
        });
        
        UiHelper.applyStatusBarPadding(recyclerView);
        // 为RecyclerView注册上下文菜单
        registerForContextMenu(recyclerView);
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
