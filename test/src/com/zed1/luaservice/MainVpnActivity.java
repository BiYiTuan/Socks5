// +----------------------------------------------------------------------
// | ZYSOFT [ MAKE IT OPEN ]
// +----------------------------------------------------------------------
// | Copyright(c) 20015 ZYSOFT All rights reserved.
// +----------------------------------------------------------------------
// | Licensed( http://www.apache.org/licenses/LICENSE-2.0 )
// +----------------------------------------------------------------------
// | Author:zy_cwind<391321232@qq.com>
// +----------------------------------------------------------------------

package com.zed1.luaservice;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.VpnService;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

@SuppressLint("SdCardPath")
public class MainVpnActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		startService(new Intent(this, MainVpnService.class));

		/**
		 * 获取 VPN 权限
		 * 
		 * 
		 */
		Intent intent = VpnService.prepare(this);
		if (intent != null)
			startActivityForResult(intent, 0);

		setContentView(R.layout.activity_main);

		((Button) findViewById(R.id.button1))
				.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						/**
						 * 启动代理
						 * 
						 * 
						 */
						LocalSocket socket = new LocalSocket();
						try {
							socket.connect(new LocalSocketAddress(
									"/data/data/com.zed1.luaservice/luaservice_path",
									LocalSocketAddress.Namespace.FILESYSTEM));
							OutputStream os = socket.getOutputStream();
							os.write("START,proxy.zed1.cn,9000,3f42f6770e2c4a3b91bfd140447e6b65"
									.getBytes());
							os.flush();
						} catch (IOException e) {
						}

						try {
							socket.close();
						} catch (IOException e) {
						}
					}

				});

		/**
		 * 释放白名单
		 * 
		 * 
		 */
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					InputStream is = getAssets().open("whitelist.txt");
					RandomAccessFile os = new RandomAccessFile(
							"/data/data/com.zed1.luaservice/whitelist.txt",
							"rw");
					byte[] buffer = new byte[4096];
					int size;
					while ((size = is.read(buffer)) > 0) {
						os.write(buffer, 0, size);
					}
					is.close();
					os.close();
				} catch (IOException e) {
				}
			}

		}).start();
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			/**
			 * 代理中需要填的参数有
			 * 
			 * -r 127.0.0.1 -l 8889 -s 203.156.199.168 -p 5000
			 * 
			 */
			String params = getIntent().getStringExtra("params");

			if (params != null) {
				Log.d("test", "start(prepared)" + params);

				Intent intent = new Intent("com.zed1.luaservice.START");
				intent.putExtra("params", params);
				sendBroadcast(intent);
			}
		}
	}
}
