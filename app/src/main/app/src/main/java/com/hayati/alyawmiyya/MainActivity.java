package com.hayati.alyawmiyya;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.view.Gravity;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView text = new TextView(this);
        text.setText("مرحباً بك في حياتي اليومية ❤️");
        text.setTextSize(24);
        text.setGravity(Gravity.CENTER);

        setContentView(text);
    }
}
