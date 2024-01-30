package io.opentakserver.opentakicu;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.pedro.common.ConnectChecker;
import com.pedro.encoder.input.video.CameraHelper;
import com.pedro.encoder.input.video.CameraOpenException;

import androidx.preference.PreferenceManager;
import io.opentakserver.opentakicu.utils.PathUtils;

import com.pedro.library.rtmp.RtmpCamera2;
import com.pedro.library.rtsp.RtspCamera2;
import com.pedro.library.srt.SrtCamera2;
import com.pedro.library.view.OpenGlView;
import com.pedro.rtsp.rtsp.Protocol;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
        implements Button.OnClickListener, ConnectChecker, SurfaceHolder.Callback,
        View.OnTouchListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private final String LOGTAG = "MainActivity";
    private final ArrayList<String> PERMISSIONS = new ArrayList<>();
    SharedPreferences pref;

    private RtspCamera2 rtspCamera2;
    private RtmpCamera2 rtmpCamera2;
    private SrtCamera2 srtCamera2;
    private OpenGlView openGlView;
    private FloatingActionButton bStartStop;
    private FloatingActionButton flashlight;
    private String currentDateAndTime = "";
    private File folder;
    //options menu
    private TextView tvBitrate;
    private FloatingActionButton settingsButton;

    private String protocol;
    private String address;
    private int port;
    private String path;
    private String username;
    private String password;
    private int samplerate;
    private boolean stereo;
    private boolean echo_cancel;
    private boolean noise_reduction;
    private int fps;
    private Size resolution;
    private boolean record;
    private boolean stream;
    private boolean enable_audio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);
        pref = PreferenceManager.getDefaultSharedPreferences(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        folder = PathUtils.getRecordPath();

        permissions();
        pref.registerOnSharedPreferenceChangeListener(this);

        openGlView = findViewById(R.id.openGlView);
        openGlView.getHolder().addCallback(this);
        openGlView.setOnTouchListener(this);

        settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(this);

        if (!hasPermissions(this, PERMISSIONS)) {
            Intent intent = new Intent(MainActivity.this, OnBoardingActivity.class);
            startActivity(intent);
            finish();
        }

        rtspCamera2 = new RtspCamera2(openGlView, this);
        rtmpCamera2 = new RtmpCamera2(openGlView, this);
        srtCamera2 = new SrtCamera2(openGlView, this);

        getSettings();

        tvBitrate = findViewById(R.id.tv_bitrate);
        bStartStop = findViewById(R.id.b_start_stop);
        bStartStop.setOnClickListener(this);
        FloatingActionButton switchCamera = findViewById(R.id.switch_camera);
        switchCamera.setOnClickListener(this);

        flashlight = findViewById(R.id.flashlight);
        flashlight.setOnClickListener(this);

        prepareEncoders();
    }

    private void lockScreenOrientation() {
        int orientation;
        switch (CameraHelper.getCameraOrientation(this)) {
            case 90:
                orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                break;
            case 180:
                orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                break;
            case 270:
                orientation =ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                break;
            default:
                orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        }
        this.setRequestedOrientation(orientation);
    }

    private void unlockScreenOrientation() {
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    private void permissions() {
        PERMISSIONS.add(Manifest.permission.RECORD_AUDIO);
        PERMISSIONS.add(Manifest.permission.CAMERA);
        PERMISSIONS.add(Manifest.permission.ACCESS_FINE_LOCATION);
        PERMISSIONS.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PERMISSIONS.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PERMISSIONS.add(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private boolean hasPermissions(Context context, ArrayList<String> permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        return false;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.b_start_stop) {
            if (!rtspCamera2.isStreaming() && !rtspCamera2.isRecording()) {
                bStartStop.setImageResource(R.drawable.stop);

                if (pref.getBoolean("tcp", true)) {
                    rtspCamera2.getStreamClient().setProtocol(Protocol.TCP);
                } else {
                    rtspCamera2.getStreamClient().setProtocol(Protocol.UDP);
                }

                if (rtspCamera2.isRecording() || prepareEncoders()) {

                    if (!username.isEmpty() && !password.isEmpty()) {
                        rtspCamera2.getStreamClient().setAuthorization(username, password);
                    }

                    String url = protocol + "://" + address + ":" + port + "/" + path;
                    Log.d(LOGTAG, url);

                    if (!rtspCamera2.isAutoFocusEnabled())
                        rtspCamera2.enableAutoFocus();

                    if (stream) {
                        rtspCamera2.startStream(url);
                        Log.d(LOGTAG, "Started stream to " + url);
                    }

                    lockScreenOrientation();
                    startRecording();
                } else {
                    //If you see this all time when you start stream,
                    //it is because your encoder device doesn't support the configuration
                    //in video encoder maybe color format.
                    //If you have more encoder go to VideoEncoder or AudioEncoder class,
                    //change encoder and try
                    Toast.makeText(this, "Error preparing stream, This device cant do it",
                            Toast.LENGTH_SHORT).show();
                    bStartStop.setImageResource(R.drawable.ic_record);
                }
            } else {
                bStartStop.setImageResource(R.drawable.ic_record);
                if (rtspCamera2.isStreaming())
                    rtspCamera2.stopStream();
                stopRecording();
                unlockScreenOrientation();
            }
        } else if (id == R.id.switch_camera) {
            try {
                rtspCamera2.switchCamera();
            } catch (CameraOpenException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            //options menu
        } else if (id == R.id.settingsButton) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        } else if (id == R.id.flashlight) {
            if (rtspCamera2.isLanternEnabled())
                rtspCamera2.disableLantern();
            else {
                try {
                    rtspCamera2.enableLantern();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void startRecording() {
        if (record) {
            try {
                if (!folder.exists()) {
                    folder.mkdir();
                }
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
                currentDateAndTime = sdf.format(new Date());
                if (!rtspCamera2.isStreaming()) {
                    if (prepareEncoders()) {
                        rtspCamera2.startRecord(
                                folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
                        lockScreenOrientation();
                        Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Error preparing stream, This device cant do it",
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    rtspCamera2.startRecord(
                            folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
                    lockScreenOrientation();
                    Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                rtspCamera2.stopRecord();
                unlockScreenOrientation();
                PathUtils.updateGallery(this, folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void stopRecording() {
        if (rtspCamera2.isRecording()) {
            rtspCamera2.stopRecord();
            unlockScreenOrientation();
            PathUtils.updateGallery(this, folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
            Toast.makeText(this,
                    "file " + currentDateAndTime + ".mp4 saved in " + folder.getAbsolutePath(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private boolean prepareEncoders() {
        Log.d(LOGTAG, "prepareEncoders");
        int width = resolution.getWidth();
        int height = resolution.getHeight();
        int fps = Integer.parseInt(pref.getString("fps", "30"));
        int bitrate = Integer.parseInt(pref.getString("bitrate", "1000"));
        int audio_bitrate = Integer.parseInt(pref.getString("audioBitrate", "128"));
        int samplerate = Integer.parseInt(pref.getString("samplerate", "44100"));
        boolean stereo = pref.getBoolean("stereo", true);
        boolean echo_cancel = pref.getBoolean("echo_cancel", true);
        boolean noise_reduction = pref.getBoolean("noise_reduction", true);

        Log.d(LOGTAG, "Setting bitrate to " + bitrate);
        Log.d(LOGTAG, "Setting res to " + width + " x " + height);

        boolean prepareVideo = rtspCamera2.prepareVideo(width, height, fps,
                bitrate * 1024,
                CameraHelper.getCameraOrientation(this));


        boolean prepareAudio = rtspCamera2.prepareAudio(
                    audio_bitrate * 1024,
                    samplerate,
                    stereo,
                    echo_cancel,
                    noise_reduction);

        if (!enable_audio) {
            Log.d(LOGTAG, "disabling audio");
            rtspCamera2.disableAudio();
        } else {
            rtspCamera2.enableAudio();
            Log.d(LOGTAG, "enabling audio");
        }

        Log.d(LOGTAG, "PrepareVideo: " + prepareVideo + " Audio " + prepareAudio);
        return prepareVideo && prepareAudio;
    }

    @Override
    public void onConnectionStarted(@NotNull String rtspUrl) {
    }

    @Override
    public void onConnectionSuccess() {
        Toast.makeText(MainActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionFailed(@NonNull final String reason) {
        Toast.makeText(MainActivity.this, "Connection failed. " + reason, Toast.LENGTH_SHORT)
                .show();
        rtspCamera2.stopStream();
        bStartStop.setImageResource(R.drawable.ic_record);
    }

    @Override
    public void onNewBitrate(final long bitrate) {
        tvBitrate.setText(bitrate + " bps");
    }

    @Override
    public void onDisconnect() {
        bStartStop.setImageResource(R.drawable.ic_record);
        Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAuthError() {
        bStartStop.setImageResource(R.drawable.ic_record);
        rtspCamera2.stopStream();
        Toast.makeText(MainActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAuthSuccess() {
        Toast.makeText(MainActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        int action = motionEvent.getAction();
        if (motionEvent.getPointerCount() > 1) {
            if (action == MotionEvent.ACTION_MOVE) {
                rtspCamera2.setZoom(motionEvent);
            }
        } else if (action == MotionEvent.ACTION_DOWN) {
            rtspCamera2.tapToFocus(motionEvent);
        }
        return true;
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {

    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int format, int width, int height) {
        Log.d(LOGTAG, "Setting preview to " + width + " x " + height + " " + format);
        rtspCamera2.startPreview();
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
        if (rtspCamera2.isRecording()) {
            rtspCamera2.stopRecord();
            PathUtils.updateGallery(this, folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
            Toast.makeText(this,
                    "file " + currentDateAndTime + ".mp4 saved in " + folder.getAbsolutePath(),
                    Toast.LENGTH_SHORT).show();
            currentDateAndTime = "";
        }
        if (rtspCamera2.isStreaming()) {
            rtspCamera2.stopStream();
            bStartStop.setImageResource(R.drawable.ic_record);
        }
        unlockScreenOrientation();
        rtspCamera2.stopPreview();
    }

    private void getSettings() {
        stream = pref.getBoolean("stream_video", true);
        enable_audio = pref.getBoolean("enable_audio", true);
        protocol = pref.getString("protocol", "rtsp");
        address = pref.getString("address", "192.168.1.10");
        port = Integer.parseInt(pref.getString("port", "8554"));
        path = pref.getString("path", "stream");
        username = pref.getString("username", "administrator");
        password = pref.getString("password", "password");
        samplerate = Integer.parseInt(pref.getString("samplerate", "44100"));
        stereo = pref.getBoolean("stereo", true);
        echo_cancel = pref.getBoolean("echo_cancel", true);
        noise_reduction = pref.getBoolean("noise_reduction", true);
        fps = Integer.parseInt(pref.getString("fps", "30"));
        record = pref.getBoolean("record", false);
        getResolution();
        prepareEncoders();
    }

    private void getResolution() {
        ArrayList<Size> resolutions = new ArrayList<>();
        List<Size> frontResolutions = rtspCamera2.getResolutionsFront();

        // Only get resolutions supported by both cameras
        for (Size res : rtspCamera2.getResolutionsBack()) {
            if (frontResolutions.contains(res)) {
                resolutions.add(res);
            }
        }

        resolution = resolutions.get(Integer.parseInt(pref.getString("resolution", "0")));
        Log.d(LOGTAG, "getResultion " + resolution.getWidth() + " x " + resolution.getHeight());
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String s) {
        Log.d(LOGTAG, "onSharedPreferenceChange " + s);
        getSettings();
    }
}