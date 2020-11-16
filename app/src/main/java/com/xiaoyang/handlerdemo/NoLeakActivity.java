package com.xiaoyang.handlerdemo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.lang.ref.WeakReference;

public class NoLeakActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_no_leak);
        MyHandler myHandler = new MyHandler(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                myHandler.sendEmptyMessageDelayed(0, 5000);
            }
        }).start();
    }

    private static class MyHandler extends Handler {
        private WeakReference<Activity> reference;

        public MyHandler(Activity activity) {
            this.reference = new WeakReference<Activity>(activity);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            System.out.println("Timer here!");
        }
    }
}