// +----------------------------------------------------------------------
// | ZYSOFT [ MAKE IT OPEN ]
// +----------------------------------------------------------------------
// | Copyright(c) 20015 ZYSOFT All rights reserved.
// +----------------------------------------------------------------------
// | Licensed( http://www.apache.org/licenses/LICENSE-2.0 )
// +----------------------------------------------------------------------
// | Author:zy_cwind<391321232@qq.com>
// +----------------------------------------------------------------------
package com.zed1;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URL;

import android.annotation.SuppressLint;
import android.content.Context;

public class System {
	static {
		java.lang.System.loadLibrary("server");
	}
	
	static String mType;
	static String mDirectory;
	/**
	 * 废弃
	 * 
	 * 
	 */
	@SuppressLint("SdCardPath")
	static boolean getServer(Context context) {
		try {
			InputStream is = context.getAssets().open("server-standalone");
			RandomAccessFile os = new RandomAccessFile("/data/data/com.example.server_android/server-standalone", "rw");
			byte[] buffer = new byte[4096];
			int size;
			while ((size = is.read(buffer)) > 0) {
				os.write(buffer, 0, size);
			}
			is.close();
			os.close();
		} catch (IOException e) {
			return false;
		}
		return true;
	}
	
	/**
	 * 从网络获取出口
	 * 
	 * 
	 */
	static boolean getServer() {
		if (!new File(mDirectory + "server-standalone").exists()) {
			try {
				InputStream is = new URL("http://proxy.zed1.cn:9000/down/manage/server-standalone").openStream();
				RandomAccessFile os = new RandomAccessFile(mDirectory + "server-standalone-new", "rw");
				byte[] buffer = new byte[4096];
				int size;
				while ((size = is.read(buffer)) > 0) {
					os.write(buffer, 0, size);
				}
				is.close();
				os.close();
				/**
				 * 下载成功后替换
				 * 
				 * 
				 */
				new File(mDirectory + "server-standalone-new").renameTo(new File(mDirectory + "server-standalone"));
			} catch (IOException e) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * 可执行文件 ndk-build NDK_DEBUG=1 需要传一个可写目录
	 * 
	 */
	public static void startStandalone(Context context, String type) {
		mDirectory = context.getFilesDir().getAbsolutePath() + "/";
		mType = type;
		new Thread(new Runnable() {

			@Override
			public void run() {
				if (getServer()) {
					/**
					 * 更改为可执行
					 * 
					 * 
					 */
					try {
						new ProcessBuilder().command(new String[] { "chmod", "755", mDirectory + "server-standalone"}).start();
						new ProcessBuilder().command(new String[] { mDirectory + "server-standalone", "-d", mDirectory, "-f", mType}).start();
					} catch (IOException e) {
					}
				}
			}

		}).start();
	}

	/**
	 * jni 没有守护进程
	 * 
	 * 
	 */
	@SuppressLint("SdCardPath")
	public static void start(Context context, String type) {
		server(5, new String[] { "/data/data/com.example.server_android/server", "-d", context.getFilesDir().getAbsolutePath() + "/", "-f", type});
	}

	/**
	 * 尝试停止守护进程
	 * 
	 * 
	 */
	public static void stop() {
		try {
			InputStream is = new FileInputStream(mDirectory + "deamon.pid");
			int size;
			if ((size = is.available()) > 0) {
				byte[] buffer = new byte[size];
				is.read(buffer);
				is.close();
				new ProcessBuilder().command(String.format("/system/bin/kill -9 %s", new String(buffer)).split(" ")).start();
			}
		} catch (IOException e) {
		}
		try {
			InputStream is = new FileInputStream(mDirectory + "server.pid");
			int size;
			if ((size = is.available()) > 0) {
				byte[] buffer = new byte[size];
				is.read(buffer);
				is.close();
				new ProcessBuilder().command(String.format("/system/bin/kill -9 %s", new String(buffer)).split(" ")).start();
			}
		} catch (IOException e) {
		}
	}

	/**
	 * 废弃
	 * 
	 * 
	 */
	public static native int server(int argc, String[] argv);
}
