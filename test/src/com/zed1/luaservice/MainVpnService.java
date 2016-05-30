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

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

@SuppressLint("SdCardPath")
public class MainVpnService extends VpnService implements Runnable {

	boolean mRunning;

	/**
	 * ȫ����������������ͣ����
	 * 
	 */
	BroadcastReceiver mainVpnServiceReceiver = new BroadcastReceiver() {

		/**
		 * �������������ں�̨����
		 * 
		 * 
		 */
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Log.d("<<<---", "receive " + action);

			if (action.equals("com.zed1.luaservice.START")) {
				if (mRunning)
					stop();
				start(intent.getStringExtra("params"));
			} else {
				stop();
			}
		}

	};

	Process p1 = null;
	Process p2 = null;

	/**
	 * VPN ����
	 * 
	 */
	ParcelFileDescriptor conn = null;

	Thread vpnThread = null;
	LocalServerSocket vpnThreadSocket = null;
	boolean vpnThreadRunning;

	/**
	 * ����ͻ��������в��� -r 127.0.0.1 -l 8889 -s 203.156.199.168 -p 5000
	 * 
	 * 
	 */
	public void startShadowsocksDeamon(String params) {
		try {
			p1 = new ProcessBuilder()
					.command(
							("/data/data/com.zed1.luaservice/client -b 127.0.0.1 -i 1080 " + params)
									.split(" ")).redirectErrorStream(true)
					.start();
		} catch (IOException e) {
			Log.d("<<<---", "unable to start client");
		}
	}

	public int startVpn() {
		/**
		 * ���� VPN ���ӣ������ᴴ��һ�� TUN �豸����ַ��26.26.26.1������������255.255.255.0
		 * ���һ��·�ɣ����е����ݰ���ת������ TUN �豸
		 * 
		 * 
		 */
		conn = new Builder().addAddress("26.26.26.1", 24)
				.addRoute("0.0.0.0", 0).addRoute("8.8.0.0", 16).setMtu(1500)
				.establish();

		if (conn == null) {
			Log.d("<<<---", "unable to start vpn");
			return -1;
		}

		int fd = conn.getFd();

		/**
		 * �˴��� DNS �����д���
		 * 
		 * 
		 */
		try {
			p2 = new ProcessBuilder()
					.command(
							String.format(
									"/data/data/com.zed1.luaservice/tun2socks --netif-ipaddr 26.26.26.2 --netif-netmask 255.255.255.0 --socks-server-addr 127.0.0.1:1080 --tunfd %d --tunmtu 1500 --loglevel 3 --enable-udprelay",
									fd).split(" ")).redirectErrorStream(true)
					.start();

		} catch (IOException e) {
			Log.d("<<<---", "unable to start tun2socks");
		}
		return fd;
	}

	public void start(String params) {

		/**
		 * ȷ���Ѿ����� VPN
		 * 
		 * 
		 */
		if (VpnService.prepare(this) != null) {
			Intent intent = new Intent(this, MainVpnActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

			/**
			 * ���� ACTIVITY �ڻ�ȡ�� VPN Ȩ��֮���Զ���������
			 * 
			 * 
			 */
			intent.putExtra("params", params);
			startActivity(intent);
			return;
		}

		/**
		 * �޸Ķ������ļ�Ϊ��ִ��
		 * 
		 * 
		 */
		try {

			new ProcessBuilder()
					.command(
							"/system/bin/chmod 755 /data/data/com.zed1.luaservice/tun2socks"
									.split(" ")).redirectErrorStream(true)
					.start();

			new ProcessBuilder()
					.command(
							"/system/bin/chmod 755 /data/data/com.zed1.luaservice/client"
									.split(" ")).redirectErrorStream(true)
					.start();
		} catch (IOException e) {
			Log.d("<<<---", "chmod failed");
		}

		vpnThread = new Thread(this);
		vpnThread.start();

		startShadowsocksDeamon(params);
		int fd = startVpn();

		if (fd != -1) {

			/**
			 * TUN2SOCKS������ VPN �ļ����������͹�ȥ���ڲ����������ǲ���Ч��
			 * 
			 * 
			 */
			for (int i = 0; i < 5000; i += 1000) {
				try {
					Thread.sleep(i);
					if (com.zed1.proxy.System.sendfd(fd) != -1) {
						Intent intent = new Intent("com.zed1.luaservice.STATE");
						intent.putExtra("STATE", 1);
						sendBroadcast(intent);
						mRunning = true;
						return;
					}
				} catch (InterruptedException e) {
				}
			}
		}

		stop();
	}

	public void stop() {
		/**
		 * �رպ�̨����
		 * 
		 * 
		 */
		if (vpnThread != null) {
			vpnThreadRunning = false;
			try {
				vpnThreadSocket.close();
			} catch (IOException e) {
			}
			vpnThread = null;
		}

		if (p1 != null) {
			p1.destroy();
			p1 = null;
		}
		if (p2 != null) {
			p2.destroy();
			p2 = null;
		}

		/**
		 * �ر� VPN ����
		 * 
		 * 
		 */
		if (conn != null) {
			try {
				conn.close();
			} catch (IOException e) {
			}
			conn = null;
		}
		Intent intent = new Intent("com.zed1.luaservice.STATE");
		intent.putExtra("STATE", 0);
		sendBroadcast(intent);
		mRunning = false;
	}

	@Override
	public void onCreate() {
		android.os.Debug.waitForDebugger();

		super.onCreate();

		/**
		 * ע��һ��ȫ�ֹ㲥��Ϣ������
		 * 
		 * 
		 */
		IntentFilter filter = new IntentFilter();
		filter.addAction("com.zed1.luaservice.START");
		filter.addAction("com.zed1.luaservice.STOP");
		registerReceiver(mainVpnServiceReceiver, filter);

		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					LocalSocket localSocket = new LocalSocket();
					localSocket.bind(new LocalSocketAddress(
							"/data/data/com.zed1.luaservice/luaservice_path",
							LocalSocketAddress.Namespace.FILESYSTEM));

					LocalServerSocket server = new LocalServerSocket(
							localSocket.getFileDescriptor());

					boolean running = true;
					while (running) {
						LocalSocket socket = server.accept();
						BufferedReader reader = new BufferedReader(
								new InputStreamReader(socket.getInputStream()));
						String params = reader.readLine();
						if (params.startsWith("START")) {
							String[] p = params.split(",");
							if (p.length == 5) {
								start("-r " + p[1] + " -l " + p[2] + " -s "
										+ p[3] + " -p " + p[4]);
								socket.getOutputStream()
										.write(mRunning ? 1 : 0);
							}
						} else if (params.startsWith("STOP")) {
							stop();
							socket.getOutputStream().write(mRunning ? 1 : 0);
						}
					}

					localSocket.close();
				} catch (IOException e) {
				}
			}

		}).start();

		Log.d("<<<---", "service created");
	}

	/**
	 * �����д��������Ӳ����� VPN �γɻ�·������һ���̶߳Դ����е� SOCKET ��������
	 * 
	 * 
	 */
	@Override
	public void run() {

		try {
			LocalSocket b = new LocalSocket();
			b.bind(new LocalSocketAddress(
					"/data/data/com.zed1.luaservice/protect_path",
					LocalSocketAddress.Namespace.FILESYSTEM));

			vpnThreadSocket = new LocalServerSocket(b.getFileDescriptor());
			vpnThreadRunning = true;
			/**
			 * ��������
			 * 
			 */
			while (vpnThreadRunning) {
				LocalSocket l = vpnThreadSocket.accept();
				InputStream is = l.getInputStream();
				is.read();
				FileDescriptor[] fds = l.getAncillaryFileDescriptors();

				if (fds != null && fds.length != 0) {
					try {
						/**
						 * ͨ�������ȡ���ļ��������� INT ֵ
						 * 
						 * 
						 */
						int fd = (Integer) fds[0].getClass()
								.getDeclaredMethod("getInt$").invoke(fds[0]);
						OutputStream os = l.getOutputStream();

						/**
						 * ������ʹ�õ� SOCKET ����������Ӷ���Ĭ�ϵ�����
						 * 
						 * 
						 */
						os.write(protect(fd) ? 0 : 1);
						com.zed1.proxy.System.jniclose(fd);
						os.close();
					} catch (Exception e) {
					}
				}
				is.close();
			}

			b.close();
		} catch (IOException e) {
		}
	}

	@Override
	public void onRevoke() {
		/**
		 * ��ֹVPN ����ر�ʱ���ٷ���
		 * 
		 */
		stop();
	}
}
