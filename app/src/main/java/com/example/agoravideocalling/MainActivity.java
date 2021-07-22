package com.example.agoravideocalling;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import io.agora.rtc.Constants;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcChannel;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.video.VideoCanvas;
import io.agora.rtc.video.VideoEncoderConfiguration;

public class MainActivity extends AppCompatActivity {

    private RtcEngine mRtcEngine;

    //Permissions
    private static final int PERMISSION_REQ_ID = 22;
    private static final String[] REQUESTED_PERMISSIONS = {Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA};

    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    //Handle SDK events
    private final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {
        @Override
        public void onUserJoined(final int uid, int elapsed) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //set first remote user to the main bg video
                    setupRemoteVideoStream(uid);
                }
            });
        }

        //Remote user has left channel
        @Override
        public void onUserOffline(int uid, int reason) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onRemoteUserLeft();
                }
            });
        }

        //Remote user has toggled their video
        @Override
        public void onRemoteVideoStateChanged(final int uid, final int state, int reason, int elapsed) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onRemoteUserVideoToggle(uid, state);
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (checkSelfPermission(REQUESTED_PERMISSIONS[0], PERMISSION_REQ_ID) &&
                checkSelfPermission(REQUESTED_PERMISSIONS[1],
                        PERMISSION_REQ_ID)) {
            initAgoraEngine();
        }
        //Set the audio button hidden
        findViewById(R.id.audio_button).setVisibility(View.GONE);
        //set the leave button hidden
        findViewById(R.id.leave_button).setVisibility(View.GONE);
        //set the video button hidden
        findViewById(R.id.video_button).setVisibility(View.GONE);
    }

    private void initAgoraEngine() {
        try {
            mRtcEngine = RtcEngine.create(getBaseContext(), getString(R.string.app_id), mRtcEventHandler);
        } catch (Exception e) {
            Log.e(LOG_TAG, Log.getStackTraceString(e));
            throw new RuntimeException("Need to check rtc sdk init fatal error\n" + Log.getStackTraceString(e));
        }
        setupSession();
    }

    private void setupSession() {
        mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION);
        mRtcEngine.enableAudio();
        mRtcEngine.setVideoEncoderConfiguration(new VideoEncoderConfiguration(VideoEncoderConfiguration.VD_640x480,
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_30,
                VideoEncoderConfiguration.STANDARD_BITRATE,
                VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT));
    }

    private void setupLocalVideoFeed() {
        //setting the frame for local user
        mRtcEngine.enableVideo();
        FrameLayout videoContainer = findViewById(R.id.floating_video);
        SurfaceView videoSurface = RtcEngine.CreateRendererView(getBaseContext());
        videoSurface.setZOrderMediaOverlay(true);
        videoContainer.addView(videoSurface);
        mRtcEngine.setupLocalVideo(new VideoCanvas(videoSurface, VideoCanvas.RENDER_MODE_FIT, 0));
    }

    private void setupRemoteVideoStream(int uid) {
        //setting ui for the remote user
        FrameLayout videoContainer = findViewById(R.id.bg_video);
        //ignoring the new call joins
        if (videoContainer.getChildCount() >= 1) {
            return;
        }

        SurfaceView videoSurface = RtcEngine.CreateRendererView(getBaseContext());
        videoContainer.addView(videoSurface);
        mRtcEngine.setupRemoteVideo(new VideoCanvas(videoSurface, VideoCanvas.RENDER_MODE_FIT, uid));
        mRtcEngine.setRemoteSubscribeFallbackOption(Constants.STREAM_FALLBACK_OPTION_AUDIO_ONLY);

    }

    public void onAudioMuteClicked(View view) {
        ImageView button = (ImageView) view;
        if (button.isSelected()) {
            button.setSelected(false);
            button.setImageResource(R.drawable.audio_toggle_btn);
        } else {
            button.setSelected(true);
            button.setImageResource(R.drawable.audio_toggle_active_btn);
        }
        mRtcEngine.muteLocalAudioStream(button.isSelected());
    }

    public void onVideoMuteClicked(View view) {
        ImageView button = (ImageView) view;
        if (button.isSelected()) {
            button.setSelected(false);
            button.setImageResource(R.drawable.video_toggle_btn);
        } else {
            button.setSelected(true);
            button.setImageResource(R.drawable.video_toggle_active_btn);
        }
        mRtcEngine.muteLocalVideoStream(button.isSelected());
        FrameLayout container = findViewById(R.id.floating_video);
        container.setVisibility(button.isSelected() ? View.GONE : View.VISIBLE);
        SurfaceView videoSurface = (SurfaceView) container.getChildAt(0);
        videoSurface.setZOrderMediaOverlay(!button.isSelected());
        videoSurface.setVisibility(button.isSelected() ? View.GONE : View.VISIBLE);
    }

    //Joining channel on clicking the join button
    public void onjoinChannelClicked(View view) {
        mRtcEngine.joinChannel(null, "test channel", "extra optional data", 0); //Agora assigns the uid if not specified
        setupLocalVideoFeed();
        //setting the join button hidden
        findViewById(R.id.join_button).setVisibility(View.GONE);
        //setting the audio button visible
        findViewById(R.id.audio_button).setVisibility(View.VISIBLE);
        //setting the leave button visible
        findViewById(R.id.leave_button).setVisibility(View.VISIBLE);
        //setting the video button visible
        findViewById(R.id.video_button).setVisibility(View.VISIBLE);
    }

    public void onLeaveChannelClicked(View view) {
        leaveChannel();
        removeVideo(R.id.floating_video);
        removeVideo(R.id.bg_video);
        //setting the join button visible
        findViewById(R.id.join_button).setVisibility(View.VISIBLE);
        //setting the audio button hidden
        findViewById(R.id.audio_button).setVisibility(View.GONE);
        //setting the leave button hidden
        findViewById(R.id.leave_button).setVisibility(View.GONE);
        //setting the video button hidden
        findViewById(R.id.video_button).setVisibility(View.GONE);
    }

    private void leaveChannel() {
        mRtcEngine.leaveChannel();
    }

    private void removeVideo(int containerID) {
        FrameLayout videoContainer = findViewById(containerID);
        videoContainer.removeAllViews();
    }

    private void onRemoteUserVideoToggle(int uid, int state) {
        FrameLayout videoContainer = findViewById(R.id.bg_video);
        SurfaceView videoSurface = (SurfaceView) videoContainer.getChildAt(0);
        videoSurface.setVisibility(state == 0 ? View.GONE : View.VISIBLE);

        //adding icon to let other user know the remote video has been disabled
        if (state == 0){
            ImageView noCamera = new ImageView(this);
            noCamera.setImageResource(R.drawable.video_disabled);
            videoContainer.addView(noCamera);
        } else {
            ImageView noCamera = (ImageView) videoContainer.getChildAt(1);
            if(noCamera != null) {
                videoContainer.removeView(noCamera);
            }
        }
    }
    private void onRemoteUserLeft() {
        removeVideo(R.id.bg_video);
    }

    public boolean checkSelfPermission(String permission, int requestCode) {
        Log.i(LOG_TAG, "checkSelfPermission " + permission + " " + requestCode);
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, requestCode);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        Log.i(LOG_TAG, "onRequestPermissionsResult " + grantResults[0] + " " + requestCode);

        switch (requestCode) {
            case PERMISSION_REQ_ID: {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(LOG_TAG, "Need Permissions " + Manifest.permission.RECORD_AUDIO + "/" + Manifest.permission.CAMERA);
                    break;
                }
                initAgoraEngine();
                break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        leaveChannel();
        RtcEngine.destroy();
        mRtcEngine = null;
    }

    public final void showLongToast(final String message) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }
}