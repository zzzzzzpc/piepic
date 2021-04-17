package com.google.mediapipe.apps.mpportraitsegmentation;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }

    // 人像分割按钮
    public void onClickPortraitSeg(View v) {
        Intent intent = new Intent(this, PortraitActivity.class);
        startActivity(intent);
        this.finish();
    }

    // 发色分割按钮
    public void onClickHairSeg(View v) {
        Intent intent = new Intent(this, HairActivity.class);
        startActivity(intent);
        this.finish();
    }
}