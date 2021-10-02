package etri.etriopenasr;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;

public class AutoVoiceReconizer {
	
	public static final int VOICE_READY = 1;
	public static final int VOICE_RECONIZING = 2;
	public static final int VOICE_RECONIZED = 3;
	public static final int VOICE_RECORDING_FINSHED = 4;
	public static final int VOICE_PLAYING = 5;
	
	RecordAudio recordTask;
	PlayAudio playTask;
	final int CUSTOM_FREQ_SOAP = 2;;

	File recordingFile;

	boolean isRecording = false;
	boolean isPlaying = false;

	int frequency = 11025;
	int outfrequency = frequency*CUSTOM_FREQ_SOAP;
	int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
	int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
	private int bufferReadResult;
	
	private Handler handler;
	
	
	LinkedList<short[]> recData = new LinkedList<short[]>();
	
	int level; // ��������
	private int startingIndex = -1; // ���� ���� �ε���
	private int endIndex = -1;
	private int cnt = 0;// ī����
	
	private boolean voiceReconize = false;



//	public AutoVoiceReconizer(Handler handler ){
//		this.handler = handler;
//		File path = new File(
//				Environment.getExternalStorageDirectory().getAbsolutePath()
//						+ "/sdcard/meditest/");
//		path.mkdirs();
//		try {
//			recordingFile = File.createTempFile("recording", ".pcm", path);
//		} catch (IOException e) {
//			throw new RuntimeException("Couldn't create file on SD card", e);
//		}
//	}
	
	public void startLevelCheck(){
		voiceReconize = false;
		cnt = 0;
		startingIndex = -1;
		endIndex = -1;
		recData.clear();
		recordTask = new RecordAudio();
		recordTask.execute();
		isRecording = true;
		
	}

	public void stopLevelCheck(){
		short[] buffer = null;

		isRecording = false;

		try {
			DataOutputStream dos = new DataOutputStream(
					new BufferedOutputStream(new FileOutputStream(
							recordingFile)));

			Log.i("test", "startingIndex = " + startingIndex + " endIndex = " + endIndex );
			for( int i = startingIndex ; i < endIndex ; i++ ){
				buffer = recData.get( i );
				for( int j = 0 ; j < bufferReadResult ; j++ ){
					dos.writeShort( buffer[ j ] );
				}
			}

			dos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}


		Message msg = handler.obtainMessage( VOICE_PLAYING );
		handler.sendMessage( msg );

		playTask = new PlayAudio();
		playTask.execute();

	}
	
	public void playVoice(){

	}

	private class PlayAudio extends AsyncTask<Void, Integer, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			isPlaying = true;

			int bufferSize = AudioTrack.getMinBufferSize((int)(outfrequency * 1.5),
					channelConfiguration, audioEncoding);
			short[] audiodata = new short[bufferSize / 4];

			/*
			int bufferSize = AudioTrack.getMinBufferSize((int)(outfrequency),
					channelConfiguration, audioEncoding);
			short[] audiodata = new short[bufferSize / 4];
			*/

			try {
				DataInputStream dis = new DataInputStream(
						new BufferedInputStream(new FileInputStream(
								recordingFile)));

				AudioTrack audioTrack = new AudioTrack(
						AudioManager.STREAM_MUSIC, (int) (outfrequency * 1.5),
						channelConfiguration, audioEncoding, bufferSize,
						AudioTrack.MODE_STREAM);
				///////////////////// �ణ ��Ҹ��� �����Ǿ� ����.. * 1.5 �� ���� ���� ��Ҹ��� ���� /////
				/*
				AudioTrack audioTrack = new AudioTrack(
						AudioManager.STREAM_MUSIC, (int) (outfrequency * 1.5),
						channelConfiguration, audioEncoding, bufferSize,
						AudioTrack.MODE_STREAM);
						*/

				audioTrack.play();

				while (isPlaying && dis.available() > 0) {
					int i = 0;
					while (dis.available() > 0 && i < audiodata.length) {
						audiodata[i] = dis.readShort();
						i++;
					}
					audioTrack.write(audiodata, 0, audiodata.length);
				}

				dis.close();

			} catch (Throwable t) {
				Log.e("AudioTrack", "Playback Failed");
			}

			Message msg = handler.obtainMessage( VOICE_READY );
			handler.sendMessage( msg );

			return null;
		}
	}

	private class RecordAudio extends AsyncTask<Void, Integer, Void> {
		@Override
		protected Void doInBackground(Void... params) {

			Message msg = null;
			try {
				
				msg = handler.obtainMessage( VOICE_RECONIZING );
				handler.sendMessage( msg );
				
				DataOutputStream dos = new DataOutputStream(
						new BufferedOutputStream(new FileOutputStream(
								recordingFile)));
				int bufferSize = AudioRecord.getMinBufferSize(outfrequency,
						channelConfiguration, audioEncoding);
				AudioRecord audioRecord = new AudioRecord(
						MediaRecorder.AudioSource.MIC, outfrequency,
						channelConfiguration, audioEncoding, bufferSize);
				short[] buffer = null;
				audioRecord.startRecording();
				int total = 0;
				buffer = new short[bufferSize];
				while (isRecording) {
					buffer = new short[bufferSize];
					bufferReadResult = audioRecord.read(buffer, 0,
							bufferSize);
					total = 0;
					for (int i = 0; i < bufferReadResult; i++) {
						total += Math.abs(buffer[i]);
					}
					recData.add( buffer );
					level = (int) ( total / bufferReadResult );
					
					// level �� ����..
					// level ���� 2000�� ���� ��� ��Ҹ��� üũ�� ����
					// 2000�� �Ѵ� ���¿��� cnt �� �������� 10ȸ �̻� ���ӵǸ� ��Ҹ��� ���� ������ ������
					// voiceReconize �� Ȱ��ȭ �Ǹ� ���� ����Ʈ
					if( voiceReconize == false ){
						if( level > 2000 ){
							if( cnt == 0 )
								startingIndex = recData.size();
							cnt++;						
						}
						
						if( cnt > 10 ){
							cnt = 0;
							voiceReconize = true;
							// level ���� ó������ 1000 ���� �����������κ��� 15 ��ŭ �������� �÷��� ���� ����
							// �����ϴ� ��Ҹ��� ���� �鸮�� �ʰ� �ϱ� ���Ͽ� -15
							startingIndex -= 15;
							if( startingIndex < 0 )
								startingIndex = 0;
							
							msg = handler.obtainMessage( VOICE_RECONIZED );
							handler.sendMessage( msg );
						}
					}
					
					if( voiceReconize == true ){
						// ��Ҹ��� ������ 500���Ϸ� ������ ���°� 40�̻� ���ӵ� ���
						// ���̻� ������ �ʴ°����� ����.. ���� üŷ ����
						if( level < 500 ){
							cnt++;
						}
						// ���߿� �ٽ� �Ҹ��� Ŀ���� ��� ��� �����ٰ� ��� ���ϴ� ����̹Ƿ� cnt ���� 0
						if( level > 2000 ){
							cnt = 0;
						}
						// endIndex �� �����ϰ� ����üŷ�� ����
						if( cnt > 20 ){
							endIndex = recData.size();
							isRecording = false;
							
							msg = handler.obtainMessage( VOICE_RECORDING_FINSHED );
							handler.sendMessage( msg );
						}
					}
				}
				audioRecord.stop();
				dos.close();
			} catch (Exception e) {
				Log.e("AudioRecord", "Recording Failed");
				Log.e("AudioRecord", e.toString() );
			}

			return null;
		}

		protected void onPostExecute(Void result) {
		}
	}

}
