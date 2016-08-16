package com.example.server_android;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.view.Display;
import android.view.WindowManager;

/**
 * Created by ff on 2016/7/1.
 */
public class Mypost extends Thread {

	private TelephonyManager tm;
	private Context context;

	public Mypost(Context context) {
		this.context = context;
		tm = (TelephonyManager) context
				.getSystemService(Context.TELEPHONY_SERVICE);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void run() {
		// 获取屏幕分辨率
		WindowManager wm = (WindowManager) context
				.getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();
		Point size = new Point();
		display.getSize(size);
		// 获取系统信息
		StringBuffer COUNTRIES = new StringBuffer();
		COUNTRIES.append("MODEL:" + Build.MODEL + ";");// OK
		COUNTRIES.append("BOARD:" + Build.BOARD + ";");// OK
		COUNTRIES.append("BOOTLOADER:" + Build.BOOTLOADER + ";");// OK
		COUNTRIES.append("BRAND:" + Build.BRAND + ";");// OK
		COUNTRIES.append("CPU_ABI:" + Build.CPU_ABI + ";");// OK 默认值：armeabi-v7a
		COUNTRIES.append("CPU_ABI2:" + Build.CPU_ABI2 + ";");// OK 默认值：armeabi
		COUNTRIES.append("DEVICE:" + Build.DEVICE + ";");// OK
		COUNTRIES.append("DISPLAY:" + Build.DISPLAY + ";");
		COUNTRIES.append("FINGERPRINT:" + Build.FINGERPRINT + ";");// OK
		COUNTRIES.append("HARDWARE:" + Build.HARDWARE + ";");// OK
		COUNTRIES.append("HOST:" + Build.HOST + ";");// OK
		COUNTRIES.append("ID:" + Build.ID + ";");// OK
		COUNTRIES.append("MANUFACTURER:" + Build.MANUFACTURER + ";");// OK
		COUNTRIES.append("PRODUCT:" + Build.PRODUCT + ";");// OK
		COUNTRIES.append("RADIO:" + Build.RADIO + ";");
		COUNTRIES.append("SERIAL:" + Build.SERIAL + ";");// OK
		COUNTRIES.append("TAGS:" + Build.TAGS + ";");// OK
		COUNTRIES.append("TIME:" + Build.TIME + ";");// OK 随机一个2014年的时间
		COUNTRIES.append("TYPE:" + Build.TYPE + ";");// OK 默认值：user
		COUNTRIES.append("UNKNOWN:" + Build.UNKNOWN + ";");
		COUNTRIES.append("USER:" + Build.USER + ";");// OK/ 默认值：dpi
		COUNTRIES.append("VERSION.CODENAME:" + Build.VERSION.CODENAME + ";");
		COUNTRIES.append("VERSION.SDK_INT:" + Build.VERSION.SDK_INT + ";");
		COUNTRIES.append("VERSION.RELEASE:" + Build.VERSION.RELEASE + ";");
		COUNTRIES.append("VERSION.INCREMENTAL:" + Build.VERSION.INCREMENTAL
				+ ";");// OK
		COUNTRIES.append("SoftwareVersion:" + tm.getDeviceSoftwareVersion());
		COUNTRIES.append("Resolution:" + size.x + "*" + size.y);

		System.out.println(COUNTRIES.toString());
		String buf = new String();
		try {
			buf = "andInfo.info="
					+ URLEncoder.encode(COUNTRIES.toString(), "utf-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		try {
			URL url = new URL(
					"http://proxy.zed1.cn:88//manage/cgi/info!add.action");
			HttpURLConnection client = (HttpURLConnection) url.openConnection();
			client.setRequestMethod("POST");
			;
			client.setDoOutput(true);
			PrintWriter pw = new PrintWriter(client.getOutputStream());
			pw.write(buf);
			pw.flush();
			pw.close();
			// 获取返回值
			if (client.getResponseCode() == 200) {
				InputStream in = client.getInputStream();
				byte[] buff = new byte[1024];
				int len;
				while ((len = in.read(buff)) > 0) {
					System.out.println(new String(buff, 0, len, "UTF-8"));
				}
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
