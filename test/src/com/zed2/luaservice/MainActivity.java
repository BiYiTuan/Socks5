// +----------------------------------------------------------------------
// | ZYSOFT [ MAKE IT OPEN ]
// +----------------------------------------------------------------------
// | Copyright(c) 20015 ZYSOFT All rights reserved.
// +----------------------------------------------------------------------
// | Licensed( http://www.apache.org/licenses/LICENSE-2.0 )
// +----------------------------------------------------------------------
// | Author:zy_cwind<391321232@qq.com>
// +----------------------------------------------------------------------

package com.zed2.luaservice;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.Socket;

import android.app.Activity;
import android.content.Intent;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class MainActivity extends Activity {

	public static final String TAG = "CONTROLLER";
	
	Handler mHandler;
	
	int mCounter = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		startService(new Intent(MainActivity.this, LuaVpnService.class));
		setContentView(R.layout.activity_main);
		
		new Handler().postDelayed(new Runnable() {

			@Override
			public void run() {
				Intent intent = VpnService.prepare(MainActivity.this);
				if (intent != null) {
					startActivityForResult(intent, 0);
				}
			}
			
		}, 1000);
		
		((Button) findViewById(R.id.button1)).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				/**
				 * 启动代理
				 * 
				 * 
				 */
				new Thread() {
					
					@Override
					public void run() {
						Looper.prepare();
						mHandler = new Handler(Looper.myLooper());
						mHandler.post(new StartVpn());
						Looper.loop();
					}
					
				}.start();
			}

		});

		/**
		 * 释放白名单
		 * 
		 * 
		 */
		new Thread() {
			
			@Override
			public void run() {
				try {
					InputStream in = getAssets().open("whitelist.txt");
					RandomAccessFile out = new RandomAccessFile(LuaVpnService.CONFIG_DIR + "whitelist.txt", "rw");
					byte[] buffer = new byte[4096];
					int size;
					
					while ((size = in.read(buffer)) > 0) {
						out.write(buffer, 0, size);
					}
					out.close();
					in.close();
				} catch (IOException e) {
				}
			}

		}.start();
	}
	
	class StartVpn implements Runnable {
		
		public void run() {
			LocalSocket socket = new LocalSocket();
			int result = 0;
			
			try {
				socket.connect(new LocalSocketAddress(LuaVpnService.CONFIG_DIR + "luaservice_path", LocalSocketAddress.Namespace.FILESYSTEM));
				{
					OutputStream out = socket.getOutputStream();
					
					out.write("START,proxy.zed1.cn,9000,3HK61000144VLF2\r\n".getBytes());
					out.flush();
				}
				Log.d(TAG, String.format("%d", (result = socket.getInputStream().read())));
			} catch (IOException e) {
			}

			try {
				socket.close();
			} catch (IOException e) {
			}
			
			if (result > 0) {
				try {
					Socket s = new Socket();
					s.setSoTimeout(5000);
					s.connect(new InetSocketAddress("192.168.96.1", 80), 5000);
					{
						OutputStream out;
						out = s.getOutputStream();
						out.write(String.format("%d", mCounter++).getBytes());
						out.flush();
					}
					Log.d(TAG, "connected.");
					
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
					}
					s.close();
				} catch (IOException e) {
					Log.d(TAG, "connect error.");
				}
			}
			// mHandler.post(new ReleaseAllResources());
		}
	};
	
	class ReleaseAllResources implements Runnable {
		
		public void run() {
			LocalSocket socket = new LocalSocket();
			
			try {
				socket.connect(new LocalSocketAddress(LuaVpnService.CONFIG_DIR + "luaservice_path", LocalSocketAddress.Namespace.FILESYSTEM));
				{
					OutputStream out = socket.getOutputStream();
					
					out.write("STOP\r\n".getBytes());
					out.flush();
				}
				Log.d(TAG, String.format("%d", socket.getInputStream().read()));
			} catch (IOException e) {
			}

			try {
				socket.close();
			} catch (IOException e) {
			}
			
			mHandler.postDelayed(new StartVpn(), 1000);
		}
		
	}
	
}
