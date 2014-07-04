package com.zhongyan.demonfc;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	public static final String MIME_TEXT_PLAIN = "text/plain";

	private TextView nfcTv;
	private EditText editText;
	private Button writeBtn;

	private Activity context;
	private NfcAdapter mNfcAdapter;
	PendingIntent pendingIntent;
	IntentFilter writeTagFilters[];
	private Tag mytag;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		context = this;
		mNfcAdapter = NfcAdapter.getDefaultAdapter(context);
		pendingIntent = PendingIntent.getActivity(context, 0, new Intent(
				context, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
				0);
		IntentFilter tagDetected = new IntentFilter(
				NfcAdapter.ACTION_TAG_DISCOVERED);
		tagDetected.addCategory(Intent.CATEGORY_DEFAULT);
		writeTagFilters = new IntentFilter[] { tagDetected };
		nfcTv = (TextView) findViewById(R.id.nfc_info);
		editText = (EditText) findViewById(R.id.edit_message);
		writeBtn = (Button) findViewById(R.id.write_btn);
		writeBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				try {
					String writeInfo = editText.getText().toString();
					if(writeInfo != null){
						write(writeInfo);
					}else{
						showToast("请输入要写入的信息！");
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		
		if (mNfcAdapter == null) {
			showToast("该设备不支持NFC功能!");
			return;
		}
		if (!mNfcAdapter.isEnabled()) {
			showToast("NFC功能已禁用!");
			return ;
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		startNfc();
	}

	@Override
	protected void onPause() {
		super.onPause();
		stopNfc();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		handleIntent(intent);
	}
	
	
	public void handleIntent(Intent intent) {
		String action = intent.getAction();
		mytag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
			String type = intent.getType();
			if (MIME_TEXT_PLAIN.equals(type)) {
				new NdefReaderTask().execute(mytag);
			} else {
				Log.d("zj", "Wrong mime type: " + type);
			}
		} else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
			String[] techList = mytag.getTechList();
			String searchedTech = Ndef.class.getName();
			for (String tech : techList) {
				if (searchedTech.equals(tech)) {
					new NdefReaderTask().execute(mytag);
					break;
				}
			}
		} else if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
			showToast("声明成功！");
		}
	}
	
	private class NdefReaderTask extends AsyncTask<Tag, Void, String> {

		@Override
		protected String doInBackground(Tag... params) {
			Tag tag = params[0];

			Ndef ndef = Ndef.get(tag);
			if (ndef == null) {
				// NDEF is not supported by this Tag.
				return null;
			}
			NdefMessage ndefMessage = ndef.getCachedNdefMessage();
			NdefRecord[] records = ndefMessage.getRecords();
			for (NdefRecord ndefRecord : records) {
				if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN
						&& Arrays.equals(ndefRecord.getType(),
								NdefRecord.RTD_TEXT)) {
					try {
						return readText(ndefRecord);
					} catch (UnsupportedEncodingException e) {
						Log.e("zj", "Unsupported Encoding", e);
					}
				}
			}
			return null;
		}

		private String readText(NdefRecord record)
				throws UnsupportedEncodingException {
			byte[] payload = record.getPayload();
			String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8"
					: "UTF-16";
			int languageCodeLength = payload[0] & 0063;
			return new String(payload, languageCodeLength + 1, payload.length
					- languageCodeLength - 1, textEncoding);
		}

		@Override
		protected void onPostExecute(String result) {
			if (result != null) {
				nfcTv.setText("Read content: " + result);
			}
		}
	}

	public boolean writeNfcInfo(String info) {
		try {
			if (mytag == null) {
				showToast("未检测到标签,不能写入数据!");
			} else {
				write(info);
				Log.i("zj", "写入数据成功！");
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
			showToast("写入数据错误！");
		}
		return false;
	}

	private boolean write(String text) throws IOException, FormatException {
		try {
			NdefRecord[] records = { createRecord(text) };
			NdefMessage message = new NdefMessage(records);
			int size = message.toByteArray().length;
			// Get an instance of Ndef for the tag.
			Ndef ndef = Ndef.get(mytag);

			if (ndef != null) {
				// 允许对标签进行IO操作
				ndef.connect();
				if (!ndef.isWritable()) {
					Toast.makeText(context, "NFC Tag是只读的！", Toast.LENGTH_LONG)
							.show();
					return false;
				}
				if (ndef.getMaxSize() < size) {
					Toast.makeText(context, "NFC Tag的空间不足！", Toast.LENGTH_LONG)
							.show();
					return false;
				}
				// 向标签写入数据
				ndef.writeNdefMessage(message);
				Toast.makeText(context, "已成功写入数据！", Toast.LENGTH_LONG).show();
				return true;
			} else {
				// 获取可以格式化和向标签写入数据NdefFormatable对象
				NdefFormatable format = NdefFormatable.get(mytag);
				// 向非NDEF格式或未格式化的标签写入NDEF格式数据
				if (format != null) {
					try {
						// 允许对标签进行IO操作
						format.connect();
						format.format(message);
						Toast.makeText(context, "已成功写入数据！", Toast.LENGTH_LONG)
								.show();
						return true;
					} catch (Exception e) {
						Toast.makeText(context, "写入NDEF格式数据失败！",
								Toast.LENGTH_LONG).show();
						return false;
					}
				} else {
					Toast.makeText(context, "NFC标签不支持NDEF格式！",
							Toast.LENGTH_LONG).show();
					return false;
				}
			}
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
			Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
			return false;
		}
	}

	private NdefRecord createRecord(String text)
			throws UnsupportedEncodingException {
		String lang = "en";
		byte[] textBytes = text.getBytes();
		byte[] langBytes = lang.getBytes("US-ASCII");
		int langLength = langBytes.length;
		int textLength = textBytes.length;
		byte[] payload = new byte[1 + langLength + textLength];
		payload[0] = (byte) langLength;
		System.arraycopy(langBytes, 0, payload, 1, langLength);
		System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);

		NdefRecord recordNFC = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
				NdefRecord.RTD_TEXT, new byte[0], payload);
		return recordNFC;
	}
	
	private void showToast(String info) {
		Toast.makeText(context, info, Toast.LENGTH_LONG).show();
	}
	
	public void startNfc() {
		WriteModeOn();
		setupForegroundDispatch(context, mNfcAdapter);
	}

	public void stopNfc() {
		stopForegroundDispatch(context, mNfcAdapter);
		WriteModeOff();
	}

	public void WriteModeOn() {
		mNfcAdapter.enableForegroundDispatch(context, pendingIntent,
				writeTagFilters, null);
	}

	public void WriteModeOff() {
		mNfcAdapter.disableForegroundDispatch(context);
	}

	public static void setupForegroundDispatch(final Activity activity,
			NfcAdapter adapter) {
		final Intent intent = new Intent(activity.getApplicationContext(),
				activity.getClass());
		intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

		final PendingIntent pendingIntent = PendingIntent.getActivity(
				activity.getApplicationContext(), 0, intent, 0);

		IntentFilter[] filters = new IntentFilter[1];
		String[][] techList = new String[][] {};

		// Notice that this is the same filter as in our manifest.
		filters[0] = new IntentFilter();
		filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
		filters[0].addCategory(Intent.CATEGORY_DEFAULT);
		try {
			filters[0].addDataType(MIME_TEXT_PLAIN);
		} catch (MalformedMimeTypeException e) {
			throw new RuntimeException("Check your mime type.");
		}

		adapter.enableForegroundDispatch(activity, pendingIntent, filters,
				techList);
	}

	public static void stopForegroundDispatch(final Activity activity,
			NfcAdapter adapter) {
		adapter.disableForegroundDispatch(activity);
	}

}
