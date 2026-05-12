package com.limelight.grid.assets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.SurfaceView;

import com.limelight.LimeLog;
import com.limelight.utils.CacheHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ComputerScreenshotCache {
    private static final String CACHE_DIR = "computer-screenshots";
    private static final int TARGET_WIDTH = 960;
    private static final int TARGET_HEIGHT = 540;
    private static final int JPEG_QUALITY = 82;

    private final Context context;
    private final File cacheDir;

    public ComputerScreenshotCache(Context context) {
        this.context = context.getApplicationContext();
        this.cacheDir = context.getCacheDir();
    }

    public File getFile(String computerUuid) {
        if (computerUuid == null || computerUuid.isEmpty()) {
            return null;
        }
        return CacheHelper.openPath(false, cacheDir, CACHE_DIR, computerUuid + ".jpg");
    }

    public Bitmap load(String computerUuid) {
        File file = getFile(computerUuid);
        if (file == null || !file.exists() || file.length() == 0) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    public void delete(String computerUuid) {
        File file = getFile(computerUuid);
        if (file != null && file.exists() && !file.delete()) {
            LimeLog.warning("Unable to delete computer screenshot cache: " + file);
        }
    }

    public void saveBitmap(String computerUuid, Bitmap bitmap) {
        if (computerUuid == null || computerUuid.isEmpty() || bitmap == null) {
            return;
        }
        saveScaledBitmap(computerUuid, bitmap);
    }

    public void captureFromSurface(final String computerUuid, final SurfaceView surfaceView) {
        if (computerUuid == null || computerUuid.isEmpty() || surfaceView == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        if (surfaceView.getWidth() <= 0 || surfaceView.getHeight() <= 0 ||
                surfaceView.getHolder() == null || surfaceView.getHolder().getSurface() == null ||
                !surfaceView.getHolder().getSurface().isValid()) {
            return;
        }

        final Bitmap captureBitmap = Bitmap.createBitmap(surfaceView.getWidth(), surfaceView.getHeight(), Bitmap.Config.ARGB_8888);
        PixelCopy.request(surfaceView, captureBitmap, new PixelCopy.OnPixelCopyFinishedListener() {
            @Override
            public void onPixelCopyFinished(int copyResult) {
                if (copyResult != PixelCopy.SUCCESS) {
                    captureBitmap.recycle();
                    return;
                }

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        saveScaledBitmap(computerUuid, captureBitmap);
                        captureBitmap.recycle();
                    }
                }, "ComputerScreenshotWriter").start();
            }
        }, new Handler(Looper.getMainLooper()));
    }

    private void saveScaledBitmap(String computerUuid, Bitmap bitmap) {
        File file = getFile(computerUuid);
        if (file == null) {
            return;
        }

        Bitmap scaledBitmap = bitmap;
        if (bitmap.getWidth() > TARGET_WIDTH || bitmap.getHeight() > TARGET_HEIGHT) {
            float scale = Math.min((float) TARGET_WIDTH / bitmap.getWidth(), (float) TARGET_HEIGHT / bitmap.getHeight());
            int width = Math.max(1, Math.round(bitmap.getWidth() * scale));
            int height = Math.max(1, Math.round(bitmap.getHeight() * scale));
            scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
        }

        try (FileOutputStream out = new FileOutputStream(file)) {
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        } catch (IOException e) {
            LimeLog.warning("Unable to save computer screenshot cache: " + e.getMessage());
        } finally {
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle();
            }
        }
    }
}
