package com.aar.app.webrtcbarebone;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onWaitClick(View view) {
        Intent i = new Intent(this, CallActivity.class);
        i.putExtra(CallActivity.EXTRA_IS_MAKING_CALL, false);
        startActivity(i);
        finish();
    }

    public void onCallClick(View view) {
        EditText editText = findViewById(R.id.text_user_id);
        String roomName = editText.getText().toString();

        Intent i = new Intent(this, CallActivity.class);
        i.putExtra(CallActivity.EXTRA_ROOM_NAME, roomName);
        i.putExtra(CallActivity.EXTRA_IS_MAKING_CALL, true);
        startActivity(i);
        finish();
    }
}
