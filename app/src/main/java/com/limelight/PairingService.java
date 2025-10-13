package com.limelight;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.xmlpull.v1.XmlPullParserException;

public class PairingService extends Service {
    private static final String TAG = "PairingService";
    private static final String CHANNEL_ID = "pairing_channel";
    private static final int NOTIFICATION_ID = 1;
    
    private final IBinder binder = new PairingBinder();
    private ExecutorService executorService;
    private PairingTask currentTask;
    
    public class PairingBinder extends Binder {
        public PairingService getService() {
            return PairingService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "PairingService created");
        executorService = Executors.newSingleThreadExecutor();
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "PairingService started");
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "PairingService destroyed");
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "配对服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("用于在后台执行配对过程");
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        
        builder.setContentTitle("Moonlight")
               .setContentText("正在配对中...")
               .setSmallIcon(R.drawable.ic_computer)
               .setPriority(Notification.PRIORITY_LOW)
               .setOngoing(true);
        
        return builder.build();
    }
    
    public void startPairing(ComputerDetails computer, String address, int port, String pin, PairingCallback callback) {
        Log.i(TAG, "开始后台配对: " + computer.name + " (" + address + ":" + port + ") PIN: " + pin);
        
        // 启动前台服务以确保在后台继续运行
        // 对于Android 14+，必须指定有效的前台服务类型
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // 对于配对服务，使用数据同步类型是最合适的
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, createNotification());
        }
        
        if (currentTask != null) {
            Log.w(TAG, "已有配对任务在进行中，取消之前的任务");
            currentTask.cancel();
        }
        
        currentTask = new PairingTask(computer, address, port, pin, callback);
        executorService.execute(currentTask);
    }
    
    public void cancelPairing() {
        Log.i(TAG, "取消后台配对");
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
        stopForeground(true);
        stopSelf();
    }
    
    private class PairingTask implements Runnable {
        private final ComputerDetails computer;
        private final String address;
        private final int port;
        private final String pin;
        private final PairingCallback callback;
        private volatile boolean cancelled = false;
        private static final int MAX_RETRY_COUNT = 2;
        private static final int PAIRING_TIMEOUT_MS = 30000;
        
        public PairingTask(ComputerDetails computer, String address, int port, String pin, PairingCallback callback) {
            this.computer = computer;
            this.address = address;
            this.port = port;
            this.pin = pin;
            this.callback = callback;
        }
        
        public void cancel() {
            cancelled = true;
        }
        
        @Override
        public void run() {
            Log.i(TAG, "后台配对任务开始: " + computer.name);
            NvHTTP httpConn = null;
            int retryCount = 0;
            
            while (retryCount <= MAX_RETRY_COUNT && !cancelled) {
                try {
                    Log.i(TAG, "第 " + (retryCount + 1) + " 次配对尝试");
                    // 创建AddressTuple对象
                    ComputerDetails.AddressTuple addressTuple = new ComputerDetails.AddressTuple(address, port);
                    httpConn = new NvHTTP(addressTuple, computer.httpsPort,
                                         computer.uuid, computer.serverCert,
                                         new com.limelight.binding.crypto.AndroidCryptoProvider(PairingService.this));
                    
                    // 检查是否已经配对
                    if (httpConn.getPairState() == PairState.PAIRED) {
                        Log.i(TAG, "PC已经配对，跳过配对过程");
                        if (callback != null) {
                            callback.onPairingSuccess(computer);
                        }
                        break;
                    }
                    
                    // 设置配对超时
                    long startTime = System.currentTimeMillis();
                    PairingManager pm = httpConn.getPairingManager();
                    PairState pairState = null;
                    
                    Log.i(TAG, "开始执行配对过程");
                    // 在超时时间内执行配对
                    while (System.currentTimeMillis() - startTime < PAIRING_TIMEOUT_MS && !cancelled) {
                        try {
                            pairState = pm.pair(httpConn.getServerInfo(true), pin);
                            Log.i(TAG, "配对API调用完成，状态: " + pairState);
                            break; // 配对完成，退出循环
                        } catch (IOException e) {
                            // 网络错误，可能是临时问题，等待后重试
                            Log.w(TAG, "配对网络错误: " + e.getMessage());
                            if (System.currentTimeMillis() - startTime < PAIRING_TIMEOUT_MS - 5000) {
                                // 还有时间，等待1秒后重试
                                Log.i(TAG, "网络错误重试，等待1秒");
                                Thread.sleep(1000);
                                continue;
                            } else {
                                throw e; // 超时了，抛出异常
                            }
                        }
                    }
                    
                    if (cancelled) {
                        Log.i(TAG, "配对任务被取消");
                        if (callback != null) {
                            callback.onPairingCancelled(computer);
                        }
                        break;
                    }
                    
                    // 检查超时
                    if (System.currentTimeMillis() - startTime >= PAIRING_TIMEOUT_MS) {
                        Log.e(TAG, "配对超时，耗时: " + (System.currentTimeMillis() - startTime) + "ms");
                        if (callback != null) {
                            callback.onPairingFailed(computer, "配对超时");
                        }
                        break;
                    }
                    
                    if (pairState == PairState.PIN_WRONG) {
                        Log.e(TAG, "PIN码错误");
                        if (callback != null) {
                            callback.onPairingFailed(computer, "PIN码错误");
                        }
                        break;
                    }
                    else if (pairState == PairState.FAILED) {
                        if (computer.runningGameId != 0) {
                            Log.e(TAG, "配对失败: PC正在游戏中");
                            if (callback != null) {
                                callback.onPairingFailed(computer, "PC正在游戏中");
                            }
                            break;
                        }
                        else {
                            // 如果是第一次失败，重试
                            if (retryCount < MAX_RETRY_COUNT) {
                                retryCount++;
                                Log.i(TAG, "配对失败，第 " + retryCount + " 次重试");
                                Thread.sleep(2000); // 等待2秒后重试
                                continue;
                            } else {
                                Log.e(TAG, "配对失败，已达到最大重试次数");
                                if (callback != null) {
                                    callback.onPairingFailed(computer, "配对失败");
                                }
                                break;
                            }
                        }
                    }
                    else if (pairState == PairState.ALREADY_IN_PROGRESS) {
                        Log.e(TAG, "配对失败: 已有其他设备正在配对");
                        if (callback != null) {
                            callback.onPairingFailed(computer, "已有其他设备正在配对");
                        }
                        break;
                    }
                    else if (pairState == PairState.PAIRED) {
                        Log.i(TAG, "配对成功!");
                        // 保存配对证书到数据库
                        try {
                            com.limelight.computers.ComputerDatabaseManager dbManager =
                                new com.limelight.computers.ComputerDatabaseManager(PairingService.this);
                            // 更新计算机的配对证书
                            computer.serverCert = pm.getPairedCert();
                            dbManager.updateComputer(computer);
                            Log.i(TAG, "配对证书已保存到数据库");
                        } catch (Exception e) {
                            Log.e(TAG, "保存配对证书失败: " + e.getMessage());
                        }
                        if (callback != null) {
                            callback.onPairingSuccess(computer);
                        }
                        break;
                    }
                    else {
                        Log.e(TAG, "配对失败: 未知状态 " + pairState);
                        if (callback != null) {
                            callback.onPairingFailed(computer, "配对失败");
                        }
                        break;
                    }
                } catch (UnknownHostException e) {
                    if (callback != null) {
                        callback.onPairingFailed(computer, "未知主机");
                    }
                    break;
                } catch (FileNotFoundException e) {
                    if (callback != null) {
                        callback.onPairingFailed(computer, "服务器未找到");
                    }
                    break;
                } catch (XmlPullParserException e) {
                    if (callback != null) {
                        callback.onPairingFailed(computer, "服务器响应错误");
                    }
                    break;
                } catch (IOException e) {
                    // 网络错误，重试
                    if (retryCount < MAX_RETRY_COUNT) {
                        retryCount++;
                        Log.i(TAG, "网络错误，第 " + retryCount + " 次重试: " + e.getMessage());
                        try {
                            Thread.sleep(2000); // 等待2秒后重试
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            if (callback != null) {
                                callback.onPairingFailed(computer, "配对被中断");
                            }
                            break;
                        }
                        continue;
                    } else {
                        if (callback != null) {
                            callback.onPairingFailed(computer, "网络错误: " + e.getMessage());
                        }
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (callback != null) {
                        callback.onPairingFailed(computer, "配对被中断");
                    }
                    break;
                } catch (Exception e) {
                    // 其他未知异常
                    Log.e(TAG, "配对过程中发生未知异常: " + e.getMessage());
                    e.printStackTrace();
                    if (callback != null) {
                        callback.onPairingFailed(computer, "未知错误: " + e.getMessage());
                    }
                    break;
                }
            }
            
            // 清理工作
            currentTask = null;
            stopForeground(true);
            stopSelf();
            Log.i(TAG, "后台配对任务结束");
        }
    }
    
    public interface PairingCallback {
        void onPairingSuccess(ComputerDetails computer);
        void onPairingFailed(ComputerDetails computer, String errorMessage);
        void onPairingCancelled(ComputerDetails computer);
    }
}