package com.example.mariostudio.lyricviewdemo.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.example.mariostudio.lyricviewdemo.Constant;
import com.example.mariostudio.lyricviewdemo.LyricView;
import com.example.mariostudio.lyricviewdemo.R;
import com.lidroid.xutils.HttpUtils;
import com.lidroid.xutils.exception.HttpException;
import com.lidroid.xutils.http.ResponseInfo;
import com.lidroid.xutils.http.callback.RequestCallBack;
import com.nineoldandroids.view.ViewHelper;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, SeekBar.OnSeekBarChangeListener, LyricView.OnPlayerClickListener {

    private LyricView lyricView;
    private MediaPlayer mediaPlayer;

    private View statueBar;
    private SeekBar display_seek;
    private TextView display_total;
    private TextView display_title;
    private TextView display_position;

    private ImageView btnPre, btnPlay, btnNext;

    private String song_urls[] = null;
    private String song_names[] = null;
    private String song_lyrics[] = null;

    private int position = 0;
    private State currentState = State.STATE_STOP;

    private final int MSG_REFRESH = 0x167;

    private long animatorDuration = 120;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setTranslucentStatus();
        }

        initAllViews();
        initAllDatum();
    }

    @TargetApi(19)
    private void setTranslucentStatus() {
        Window window = getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        final int status = WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
        params.flags |= status;
        window.setAttributes(params);
    }

    public int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    private void initAllViews() {
        statueBar = findViewById(R.id.statue_bar);
        statueBar.getLayoutParams().height = getStatusBarHeight();
        lyricView = (LyricView) findViewById(R.id.lyric_view);
        lyricView.setOnPlayerClickListener(this);
        display_title = (TextView) findViewById(R.id.title_view);
        display_position = (TextView) findViewById(android.R.id.text1);
        display_total = (TextView) findViewById(android.R.id.text2);
        display_seek = (SeekBar) findViewById(android.R.id.progress);
        display_seek.setOnSeekBarChangeListener(this);
        btnNext = (ImageView) findViewById(android.R.id.button3);
        btnPlay = (ImageView) findViewById(android.R.id.button2);
        btnPre = (ImageView) findViewById(android.R.id.button1);
        btnPlay.setOnClickListener(this);
        btnNext.setOnClickListener(this);
        btnPre.setOnClickListener(this);
    }

    private void initAllDatum() {
        song_lyrics = getResources().getStringArray(R.array.song_lyrics);
        song_names = getResources().getStringArray(R.array.song_names);
        song_urls = getResources().getStringArray(R.array.song_urls);

        mediaPlayerSetup();  // 准备
    }

    /**
     * 准备
     * */
    private void mediaPlayerSetup() {
        try {
            mediaPlayer = new MediaPlayer();
            setCurrentState(State.STATE_SETUP);
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.setDataSource(song_urls[position]);
            mediaPlayer.prepareAsync();

            display_title.setText(song_names[position]);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止
     * */
    private boolean stop() {
        if(null != mediaPlayer && currentState != State.STATE_STOP) {
            handler.removeMessages(MSG_REFRESH);
            lyricView.reset("加载中..");
            setCurrentState(State.STATE_STOP);
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            return true;
        } else {
            return false;
        }
    }

    /**
     * 暂停
     * */
    private void pause() {
        if(mediaPlayer != null && currentState == State.STATE_PLAYING) {
            setCurrentState(State.STATE_PAUSE);
            mediaPlayer.pause();
            handler.removeMessages(MSG_REFRESH);
        }
    }

    /**
     * 开始
     * */
    private void start() {
        if(mediaPlayer != null && (currentState == State.STATE_PAUSE || currentState == State.STATE_PREPARE)) {
            setCurrentState(State.STATE_PLAYING);
            mediaPlayer.start();
            handler.sendEmptyMessage(MSG_REFRESH);
        }
    }

    /**
     * 上一首
     * */
    private void previous() {
        if(stop()) {
            position --;
            if(position < 0) {
                position = Math.min(Math.min(song_names.length, song_lyrics.length), song_urls.length) - 1;
            }
            mediaPlayerSetup();
        }
    }

    /**
     * 上一首
     * */
    private void next() {
        if(stop()) {
            position ++;
            if(position >= Math.min(Math.min(song_names.length, song_lyrics.length), song_urls.length)) {
                position = 0;
            }
            mediaPlayerSetup();
        }
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        setCurrentState(State.STATE_PREPARE);
        DecimalFormat format = new DecimalFormat("00");
        display_seek.setMax(mediaPlayer.getDuration());
        display_total.setText(format.format(mediaPlayer.getDuration() / 1000 / 60) + ":" + format.format(mediaPlayer.getDuration() / 1000 % 60));
        File file = new File(Constant.lyricPath + song_names[position] + ".lrc");
        if(file.exists()) {
            lyricView.setLyricFile(file, "GBK");
        } else {
            downloadLyric(song_lyrics[position], file);
        }
        start();
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mediaPlayer, int percent) {

    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        next();
    }

    /**
     * 设置当前播放状态
     * */
    private void setCurrentState(State state) {
        if(state == this.currentState) {
            return;
        }
        this.currentState = state;
        switch (state) {
            case STATE_PAUSE:
                btnPlay.setImageResource(R.mipmap.m_icon_player_play_normal);
                break;
            case STATE_PLAYING:
                btnPlay.setImageResource(R.mipmap.m_icon_player_pause_normal);
                break;
        }
    }

    Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_REFRESH:
                    if(mediaPlayer != null) {
                        if(!display_seek.isPressed()) {
                            lyricView.setCurrentTimeMillis(mediaPlayer.getCurrentPosition());
                            DecimalFormat format = new DecimalFormat("00");
                            display_seek.setProgress(mediaPlayer.getCurrentPosition());
                            display_position.setText(format.format(mediaPlayer.getCurrentPosition() / 1000 / 60) + ":" + format.format(mediaPlayer.getCurrentPosition() / 1000 % 60));
                        }
                    }
                    handler.sendEmptyMessageDelayed(MSG_REFRESH, 120);
                    break;
            }
        }
    };

    @Override
    public void onPlayerClicked(long progress, String content) {
        Log.e(getClass().getName(), "long: " + progress);
        Log.e(getClass().getName(), "integer: " + (int) progress);
        mediaPlayer.seekTo((int) progress);
        if(currentState == State.STATE_PAUSE) {
            start();
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if(fromUser) {
            DecimalFormat format = new DecimalFormat("00");
            display_position.setText(format.format(progress / 1000 / 60) + ":" + format.format(progress / 1000 % 60));
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        handler.removeMessages(MSG_REFRESH);
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        mediaPlayer.seekTo(seekBar.getProgress());
        handler.sendEmptyMessageDelayed(MSG_REFRESH, 120);
    }

    private void downloadLyric(String url, File file) {
        HttpUtils httpUtils = new HttpUtils();
        httpUtils.download(url, file.getAbsolutePath(), true, true, new RequestCallBack<File>() {

            @Override
            public void onSuccess(ResponseInfo<File> responseInfo) {
                lyricView.setLyricFile(responseInfo.result, "GBK");
            }

            @Override
            public void onFailure(HttpException e, String s) {

            }
        });
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case android.R.id.button1:
                previous();
                break;
            case android.R.id.button2:
                if(currentState == State.STATE_PAUSE) {
                    start();
                    break;
                }
                if(currentState == State.STATE_PLAYING) {
                    pause();
                    break;
                }
                break;
            case android.R.id.button3:
                next();
                break;
            default:
                break;
        }
        pressAnimator(view).start();
    }

    public ValueAnimator pressAnimator(final View view) {
        ValueAnimator animator = ValueAnimator.ofFloat(view.getScaleX(), view.getScaleX() * 0.7f);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                ViewHelper.setScaleX(view, (Float) animation.getAnimatedValue());
                ViewHelper.setScaleY(view, (Float) animation.getAnimatedValue());
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                upAnimator(view).start();
            }
        });
        animator.setDuration(animatorDuration);
        return animator;
    }

    public ValueAnimator upAnimator(final View view) {
        ValueAnimator animator = ValueAnimator.ofFloat(view.getScaleX(), view.getScaleX() * 10 / 7.00f);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                ViewHelper.setScaleX(view, (Float) animation.getAnimatedValue());
                ViewHelper.setScaleY(view, (Float) animation.getAnimatedValue());
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
            }
        });
        animator.setDuration(animatorDuration);
        return animator;
    }

    private enum State {
        STATE_STOP,STATE_SETUP,STATE_PREPARE,STATE_PLAYING,STATE_PAUSE;
    }
}
