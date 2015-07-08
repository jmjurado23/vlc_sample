package com.example.jjurado.vlc_app;

import java.lang.ref.WeakReference;



import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.Toast;

import org.videolan.libvlc.EventHandler;
import org.videolan.libvlc.IVideoPlayer;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaList;

public class VideoActivity extends Activity implements SurfaceHolder.Callback,
        IVideoPlayer, View.OnClickListener {
    public final static String TAG = "LibVLCAndroidSample/VideoActivity";

    public final static String LOCATION = "com.example.jjurado.vlc_app.VideoActivity.location";

    private String mFilePath;

    // display surface
    private SurfaceView mSurface;
    private SurfaceHolder holder;
    private Button play_button;
    private Button pause_button;

    // media player
    private LibVLC libvlc;
    private int mVideoWidth;
    private int mVideoHeight;
    private final static int VideoSizeChanged = -1;

    /*************
     * Activity
     *************/

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sample);

        // Receive path to play from intent
        Intent intent = getIntent();
        mFilePath = intent.getExtras().getString(LOCATION);

        Log.d(TAG, "Playing back " + mFilePath);

        play_button = (Button) findViewById(R.id.button_play);
        pause_button = (Button) findViewById(R.id.button_pause);
        play_button.setOnClickListener(this);
        pause_button.setOnClickListener(this);
        mSurface = (SurfaceView) findViewById(R.id.surface);
        holder = mSurface.getHolder();
        holder.addCallback(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setSize(mVideoWidth, mVideoHeight);
    }

    @Override
    protected void onResume() {
        super.onResume();
        createPlayer(mFilePath);
    }

    @Override
    protected void onPause() {
        super.onPause();
        releasePlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }

    /*************
     * Surface
     *************/

    public void surfaceCreated(SurfaceHolder holder) {
    }

    public void surfaceChanged(SurfaceHolder surfaceholder, int format,
            int width, int height) {
        if (libvlc != null)
            libvlc.attachSurface(holder.getSurface(), this);
    }

    public void surfaceDestroyed(SurfaceHolder surfaceholder) {
    }

    private void setSize(int width, int height) {
        mVideoWidth = width;
        mVideoHeight = height;
        if (mVideoWidth * mVideoHeight <= 1)
            return;

        // get screen size
        int w = getWindow().getDecorView().getWidth();
        int h = getWindow().getDecorView().getHeight();

        // getWindow().getDecorView() doesn't always take orientation into
        // account, we have to correct the values
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        if (w > h && isPortrait || w < h && !isPortrait) {
            int i = w;
            w = h;
            h = i;
        }

        float videoAR = (float) mVideoWidth / (float) mVideoHeight;
        float screenAR = (float) w / (float) h;

        if (screenAR < videoAR)
            h = (int) (w / videoAR);
        else
            w = (int) (h * videoAR);

        // force surface buffer size
        holder.setFixedSize(mVideoWidth, mVideoHeight);

        // set display size
        LayoutParams lp = mSurface.getLayoutParams();
        lp.width = w;
        lp.height = h;
        mSurface.setLayoutParams(lp);
        mSurface.invalidate();
    }

    @Override
    public void setSurfaceSize(int width, int height, int visible_width,
            int visible_height, int sar_num, int sar_den) {
        Message msg = Message.obtain(mHandler, VideoSizeChanged, width, height);
        msg.sendToTarget();
    }

    /*************
     * Player
     *************/

    private void createPlayer(String media) {
        releasePlayer();
        try {
            if (media.length() > 0) {
                Toast toast = Toast.makeText(this, media, Toast.LENGTH_LONG);
                toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0,
                        0);
                toast.show();
            }

            // Create a new media player
            libvlc = LibVLC.getInstance();
            libvlc.setHardwareAcceleration(LibVLC.HW_ACCELERATION_DISABLED);
            libvlc.setSubtitlesEncoding("");
            libvlc.setAout(LibVLC.AOUT_OPENSLES);
            libvlc.setTimeStretching(true);
            libvlc.setChroma("RV32");
            libvlc.setVerboseMode(true);
            LibVLC.restart(this);
            EventHandler.getInstance().addHandler(mHandler);
            holder.setFormat(PixelFormat.RGBX_8888);
            holder.setKeepScreenOn(true);
            MediaList list = libvlc.getMediaList();
            list.clear();
            list.add(new Media(libvlc, LibVLC.PathToURI(media)), false);
            libvlc.playIndex(0);
        } catch (Exception e) {
            Toast.makeText(this, "Error creating player!", Toast.LENGTH_LONG).show();
        }
    }

    private void releasePlayer() {
        if (libvlc == null)
            return;
        EventHandler.getInstance().removeHandler(mHandler);
        libvlc.stop();
        libvlc.detachSurface();
        holder = null;
        libvlc.closeAout();
        libvlc.destroy();
        libvlc = null;

        mVideoWidth = 0;
        mVideoHeight = 0;
    }

    /*************
     * Events
     *************/

    private Handler mHandler = new MyHandler(this);

    @Override
    public void onClick(View view) {
        if(view.equals(play_button)) {
            libvlc.play();
        } else if(view.equals(pause_button)){
            libvlc.pause();
        }

    }


    private static class MyHandler extends Handler {
        private WeakReference<VideoActivity> mOwner;

        public MyHandler(VideoActivity owner) {
            mOwner = new WeakReference<VideoActivity>(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            VideoActivity player = mOwner.get();

            // SamplePlayer events
            if (msg.what == VideoSizeChanged) {
                player.setSize(msg.arg1, msg.arg2);
                return;
            }

            // Libvlc events
            Bundle b = msg.getData();
            switch (b.getInt("event")) {
            case EventHandler.MediaPlayerEndReached:
                player.releasePlayer();
                break;
            case EventHandler.MediaPlayerPlaying:
            case EventHandler.MediaPlayerPaused:
            case EventHandler.MediaPlayerStopped:
            default:
                break;
            }
        }
    }
}
