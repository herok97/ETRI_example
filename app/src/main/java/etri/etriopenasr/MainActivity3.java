package etri.etriopenasr;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.widget.TextView;

import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class MainActivity3 extends AppCompatActivity {
    private static final String MSG_KEY = "status";
    TextView textView;
    int startingIndex, endIndex;
    byte[] speechData;

    private final Handler handler = new Handler() {
        @Override
        public synchronized void handleMessage(Message msg) {
            Bundle bd = msg.getData();
            String v = bd.getString(MSG_KEY);
            textView.setText(v);
            super.handleMessage(msg);
        }
    };

//    public void SendMessage(String str) {
//        Message msg = handler.obtainMessage();
//        Bundle bd = new Bundle();
//        bd.putString(MSG_KEY, str);
//        msg.setData(bd);
//        handler.sendMessage(msg);
//    }

    // oncreate
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = (TextView)findViewById(R.id.textResult);
        CaptionCreator captionCreator = new CaptionCreator(handler);

        try {
            new Thread(new Runnable() {
                public void run() {
                    captionCreator.recordSpeech();
                }
            }).start();
        } catch (Throwable t) {
            System.out.println("에러발생");
        }

    }

}
