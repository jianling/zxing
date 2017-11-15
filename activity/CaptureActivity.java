package com.zxing.activity;

import android.content.Intent;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.view.*;
import android.view.SurfaceHolder.Callback;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;
import com.baidu.TitleActivity;
import com.baidu.bce.R;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.zxing.camera.CameraManager;
import com.zxing.decoding.CaptureActivityHandler;
import com.zxing.decoding.InactivityTimer;
import com.zxing.view.ViewfinderView;

import java.io.IOException;
import java.util.Vector;

/**
 * Initial the camera
 *
 * @author Ryan.Tang
 */
public class CaptureActivity extends TitleActivity implements Callback {

    public static final String BUNDLE_SCAN_RESULT_TEXT = "result_text";

    private FrameLayout rootView;

    private CaptureActivityHandler handler;
    private ViewfinderView viewfinderView;
    private boolean hasSurface;
    private Vector<BarcodeFormat> decodeFormats;
    private String characterSet;
    private InactivityTimer inactivityTimer;
    private MediaPlayer mediaPlayer;
    private boolean playBeep;
    private static final float BEEP_VOLUME = 0.10f;
    private boolean vibrate;
    private boolean flashLightOn = false;
    private boolean viewsAdded = false;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        rootView = (FrameLayout) getLayoutInflater().inflate(R.layout.layout_sapi_capture, null);
        setContentView(rootView);

        setupViews();

        CameraManager.init(getApplication());
        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
        hasSurface = false;
        inactivityTimer = new InactivityTimer(this);
    }

    @Override
    protected void setupViews() {
        super.setupViews();

        mRightBtn.setText(R.string.sapi_capture_turn_on_flash_light);
        if (isFlashLightSupported()) {
            setBtnVisibility(View.VISIBLE, View.VISIBLE);
        } else {
            setBtnVisibility(View.VISIBLE, View.INVISIBLE);
        }
        setTitleText(R.string.sapi_capture_title_text);
    }

    @Override
    protected void onLeftBtnClick() {
        super.onLeftBtnClick();

        finish();
    }

    @Override
    protected void onRightBtnClick() {
        super.onRightBtnClick();

        flashLightOn = !flashLightOn;
        if (flashLightOn) {
            mRightBtn.setText(R.string.sapi_capture_turn_off_flash_light);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    CameraManager.get().turnOnFlashLight();
                }
            }).start();
        } else {
            mRightBtn.setText(R.string.sapi_capture_turn_on_flash_light);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    CameraManager.get().turnOffFlashLight();
                }
            }).start();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onResume() {
        super.onResume();
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            initCamera(surfaceHolder);
        } else {
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
        decodeFormats = null;
        characterSet = null;

        playBeep = true;
        AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            playBeep = false;
        }
        initBeepSound();
        vibrate = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        CameraManager.get().closeDriver();
        flashLightOn = false;
        mRightBtn.setText(R.string.sapi_capture_turn_on_flash_light);
    }

    @Override
    public void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    /**
     * Handler scan result
     */
    public void handleDecode(Result result, @SuppressWarnings("unused") Bitmap barcode) {
        inactivityTimer.onActivity();
        playBeepSoundAndVibrate();
        String resultString = result.getText();

        if (resultString.equals("")) {
            Toast.makeText(CaptureActivity.this, "Scan failed!", Toast.LENGTH_SHORT).show();
        } else {
            Intent resultIntent = new Intent();
            Bundle bundle = new Bundle();
            bundle.putString(BUNDLE_SCAN_RESULT_TEXT, resultString);
            resultIntent.putExtras(bundle);
            this.setResult(RESULT_OK, resultIntent);
        }
        CaptureActivity.this.finish();
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        try {
            CameraManager.get().openDriver(surfaceHolder);
        } catch (IOException ioe) {
            return;
        } catch (RuntimeException e) {
            return;
        }
        if (handler == null) {
            handler = new CaptureActivityHandler(this, decodeFormats,
                    characterSet);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }
        if (!viewsAdded) {
            ImageView scanLine = new ImageView(this);
            scanLine.setImageResource(R.drawable.sapi_qrcode_scan_line);
            Rect maskRect = CameraManager.get().getFramingRect();
            if (maskRect != null) {
                FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(maskRect.width(), ViewGroup.LayoutParams.WRAP_CONTENT);
                lineParams.leftMargin = maskRect.left;
                lineParams.topMargin = maskRect.top + 20;
                lineParams.gravity = Gravity.HORIZONTAL_GRAVITY_MASK;
                scanLine.setLayoutParams(lineParams);
                rootView.addView(scanLine);

                TranslateAnimation animation = new TranslateAnimation(TranslateAnimation.ABSOLUTE, 0, TranslateAnimation.ABSOLUTE, 0, TranslateAnimation.ABSOLUTE, 0, TranslateAnimation.ABSOLUTE, maskRect.height() - scanLine.getHeight() - 70);
                animation.setDuration(3000);
                animation.setRepeatCount(Animation.INFINITE);
                animation.setInterpolator(new LinearInterpolator());
                scanLine.startAnimation(animation);

                View scanTip = findViewById(R.id.qrcode_scan_tip_text);
                RelativeLayout.LayoutParams tipParams = (RelativeLayout.LayoutParams) scanTip.getLayoutParams();
                tipParams.topMargin = maskRect.bottom + getResources().getInteger(R.integer.sapi_capture_mask_tip_margin);
                tipParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
                scanTip.setLayoutParams(tipParams);
            }
            viewsAdded = true;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;

    }

    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    public Handler getHandler() {
        return handler;
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();

    }

    private void initBeepSound() {
        if (playBeep && mediaPlayer == null) {
            // The volume on STREAM_SYSTEM is not adjustable, and users found it
            // too loud,
            // so we now play on the music stream.
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setOnCompletionListener(beepListener);

            AssetFileDescriptor file = getResources().openRawResourceFd(
                    R.raw.beep);
            try {
                mediaPlayer.setDataSource(file.getFileDescriptor(),
                        file.getStartOffset(), file.getLength());
                file.close();
                mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
                mediaPlayer.prepare();
            } catch (IOException e) {
                mediaPlayer = null;
            }
        }
    }

    private static final long VIBRATE_DURATION = 200L;

    private void playBeepSoundAndVibrate() {
        if (playBeep && mediaPlayer != null) {
            mediaPlayer.start();
        }
        if (vibrate) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(VIBRATE_DURATION);
        }
    }

    /**
     * When the beep has finished playing, rewind to queue up another one.
     */
    private final OnCompletionListener beepListener = new OnCompletionListener() {
        public void onCompletion(MediaPlayer mediaPlayer) {
            mediaPlayer.seekTo(0);
        }
    };

    private boolean isFlashLightSupported() {

        PackageManager packageManager = getPackageManager();
        FeatureInfo[] features = packageManager.getSystemAvailableFeatures();
        if (features != null) {
            for (FeatureInfo feature : features) {
                if (PackageManager.FEATURE_CAMERA_FLASH.equals(feature.name)) {
                    return true;
                }
            }
        }
        return false;
    }
}