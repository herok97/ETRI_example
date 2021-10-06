package etri.etriopenasr;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Base64;

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

public class CaptionCreator {
    private static final String MSG_KEY = "status";
    int startingIndex, endIndex;
    byte[] speechData;
    Handler handler;

    CaptionCreator(Handler handler_){
       this.handler  = handler_;
    }

    // record
    public void recordSpeech() throws RuntimeException {
        int lenSpeech = 0;
        boolean isRecording = true;
        speechData = new byte[16000 * 60 * 2];
        try {
            int bufferSize = AudioRecord.getMinBufferSize(
                    16000, // sampling frequency
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            AudioRecord audio = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    16000, // sampling frequency
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize);

            if (audio.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new RuntimeException("ERROR: Failed to initialize audio device. Allow app to access microphone");
            } else {
                short[] inBuffer = new short[bufferSize];
                audio.startRecording();
                int total = 0;
                int level = 0;
                int cnt = 0;
                startingIndex = 0;
                endIndex = 0;
                boolean voiceReconize = false;

                while (isRecording) {
                    int ret = audio.read(inBuffer, 0, bufferSize);

                    for (int i = 0; i < ret; i++) {
                        speechData[lenSpeech * 2] = (byte) (inBuffer[i] & 0x00FF);
                        speechData[lenSpeech * 2 + 1] = (byte) ((inBuffer[i] & 0xFF00) >> 8);
                        lenSpeech++;
                    }

                    total = 0;
                    level = 0;

                    for (int i = 0; i < ret; i++) {
                        total += Math.abs(inBuffer[i]);
                    }
                    level = total / ret;

                    // level 은 볼륨..
                    // level 값이 2000이 넘은 경우 목소리를 체크를 시작
                    // 2000이 넘는 상태에서 cnt 를 증가시켜 10회 이상 지속되면 목소리가 나는 것으로 간주함
                    // voiceReconize 가 활성화 되면 시작 포인트
                    if (!voiceReconize) {
                        if (level > 1000) {
                            if (cnt == 0)
                                startingIndex = lenSpeech;
                            cnt++;
                        }

                        if (cnt > 5) {
                            cnt = 0;
                            voiceReconize = true;
                            System.out.println("level 값이 처음으로 1000 값을 넘은시점으로부터 15 만큼 이전부터 플레이 시점 설정");
                            // level 값이 처음으로 1000 값을 넘은시점으로부터 15 만큼 이전부터 플레이 시점 설정
                            // 시작하는 목소리가 끊겨 들리지 않게 하기 위하여 -15
                            startingIndex -= 30;
                            if (startingIndex < 0)
                                startingIndex = 0;
                        }
                    }

                    if (voiceReconize) {
                        // 목소리가 끝나고 500이하로 떨어진 상태가 40이상 지속된 경우
                        // 더이상 말하지 않는것으로 간주.. 레벨 체킹 끝냄
                        if (level < 500) {
                            System.out.println("목소리가 끝나고 500이하로 떨어진 상태가 10이상 지속된 경우");
                            cnt++;
                        }
                        // 도중에 다시 소리가 커지는 경우 잠시 쉬었다가 계속 말하는 경우이므로 cnt 값은 0
                        if (level > 1000) {
                            System.out.println("도중에 다시 소리가 커지는 경우 잠시 쉬었다가 계속 말하는 경우이므로 cnt 값은 0");
                            cnt = 0;
                        }
                        // endIndex 를 저장하고 레벨체킹을 끝냄
                        if (cnt > 10) {
                            System.out.println("endIndex 를 저장하고 레벨체킹을 끝냄");
//                            System.out.println("startingIndex" + startingIndex);
                            endIndex = lenSpeech;

                            String response = sendAndGetData(makeRequest(Arrays.copyOfRange(speechData, 2 * startingIndex, 2 * endIndex + 1), endIndex-startingIndex));
                            SendMessage(response);

                            speechData = new byte[16000 * 60 * 2];
                            lenSpeech = 0;
                            endIndex = 0;
                            startingIndex = 0;
                            voiceReconize = false;
                            cnt = 0;
                        }
                    }
                }

                audio.stop();
                audio.release();
            }
        } catch (Throwable t) {
            throw new RuntimeException(t.toString());
        }

    }

    public void SendMessage(String str) {
        Message msg = handler.obtainMessage();
        Bundle bd = new Bundle();
        bd.putString(MSG_KEY, str);
        msg.setData(bd);
        handler.sendMessage(msg);
    }

    // send
    public Map<String, Object> makeRequest (byte[] speechData, int lenSpeech) {
        String accessKey = "154f164e-34ca-4b52-88b3-8108edd22849";
        String languageCode = "korean";
        String audioContents;


        Map<String, Object> request = new HashMap<>();
        Map<String, String> argument = new HashMap<>();
        audioContents = Base64.encodeToString(
                speechData, 0, lenSpeech*2, Base64.NO_WRAP);

        argument.put("language_code", languageCode);
        argument.put("audio", audioContents);

        request.put("access_key", accessKey);
        request.put("argument", argument);
        return request;
    }

    public String sendAndGetData (Map<String, Object> request) {
        URL url;
        Integer responseCode;
        String responBody;
        String openApiURL = "http://aiopen.etri.re.kr:8000/WiseASR/Recognition";
        Gson gson = new Gson();

        try {
            url = new URL(openApiURL);
            HttpURLConnection con = (HttpURLConnection)url.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);

            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.write(gson.toJson(request).getBytes("UTF-8"));
            wr.flush();
            wr.close();

            responseCode = con.getResponseCode();
            if ( responseCode == 200 ) {
                InputStream is = new BufferedInputStream(con.getInputStream());
                responBody = readStream(is);
                JSONObject json = new JSONObject(responBody);
                return json.getJSONObject("return_object").getString("recognized");
//                return responBody;
            }
            else
                return "ERROR: " + Integer.toString(responseCode);
        } catch (Throwable t) {
            return "ERROR: " + t.toString();
        }
    }

    public static String readStream(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(in),1000);
        for (String line = r.readLine(); line != null; line =r.readLine()){
            sb.append(line);
        }
        in.close();
        return sb.toString();
    }

}
