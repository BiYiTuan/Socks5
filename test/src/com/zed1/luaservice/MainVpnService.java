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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

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
public class MainVpnService extends VpnService {

	/**
	 * 运行状态
	 * 
	 * 
	 * 
	 */
	boolean mRunning;
	boolean mStart;

	/**
	 * 管理信息
	 * 
	 * 
	 */
	int mManagePort;
	String mManageAddress;
	String mUid;

	/**
	 * VPN 连接
	 * 
	 */
	ParcelFileDescriptor conn = null;

	/**
	 * 全局侦听器，用于启停代理
	 * 
	 */
	BroadcastReceiver mainVpnServiceReceiver = new BroadcastReceiver() {

		/**
		 * 服务被启动后长期在后台运行
		 * 
		 * 
		 */
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Log.d("vpnService", "receive " + action);

			if (action.equals("com.zed1.luaservice.START")) {
				startProxy(intent.getStringExtra("params"));
			} else {
				stop();
			}
		}

	};

	public int startVpn() {
		conn = new Builder().addAddress("26.26.26.1", 24)
				.addRoute("0.0.0.0", 0).addRoute("8.8.0.0", 16).setMtu(1500)
				.establish();

		if (conn == null) {
			Log.d("vpnService", "unable to start vpn");
			return -1;
		}

		int fd = conn.getFd();

		/**
		 * 此处对 DNS 不进行代理
		 * 
		 * 
		 */
		killProcess("/data/data/com.zed1.luaservice/tun2socks.pid");
		String[] args = String
				.format("/data/data/com.zed1.luaservice/tun2socks --netif-ipaddr 26.26.26.2 --netif-netmask 255.255.255.0 --socks-server-addr 127.0.0.1:1080 --tunfd %d --tunmtu 1500 --loglevel 3 --enable-udprelay",
						fd).split(" ");
		com.zed1.System.tun2socks(args.length, args);
		Log.d("vpnService", "tun2socks started");
		return fd;
	}

	public void startProxy(String params) {
		if (mRunning) {
			killProcess("/data/data/com.zed1.luaservice/client.pid");
			String[] args = ("/data/data/com.zed1.luaservice/client -d /data/data/com.zed1.luaservice/ -i 1080 " + params)
					.split(" ");
			com.zed1.System.client(args.length, args);
			Log.d("vpnService", "client restarted");
		} else {

			/**
			 * 确保已经请求 VPN
			 * 
			 * 
			 */
			if (VpnService.prepare(this) != null) {
				Intent intent = new Intent(this, MainVpnActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				intent.putExtra("params", params);
				startActivity(intent);

				Log.d("vpnService", "getting vpn authority");
				return;
			}

			int fd = startVpn();
			if (fd != -1) {

				/**
				 * TUN2SOCKS启动后将 VPN 文件描述符发送过去，在参数中设置是不奏效的
				 * 
				 * 
				 */
				for (int i = 0; i < 5000; i += 1000) {
					try {
						Thread.sleep(i);
						if (com.zed1.System.sendfd(fd) != -1) {
							/**
							 * 启动代理
							 * 
							 * 
							 */
							killProcess("/data/data/com.zed1.luaservice/client.pid");
							String[] args = ("/data/data/com.zed1.luaservice/client -d /data/data/com.zed1.luaservice/ -i 1080 " + params)
									.split(" ");
							com.zed1.System.client(args.length, args);
							Log.d("vpnService", "client started");
							mRunning = true;
							return;
						}
					} catch (InterruptedException e) {
					}
				}
			}
			Log.d("vpnService", "no response from tun2socks");
			stop();
		}
	}

	public void stop() {
		killProcess("/data/data/com.zed1.luaservice/tun2socks.pid");
		killProcess("/data/data/com.zed1.luaservice/client.pid");

		/**
		 * 关闭 VPN 连接
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
		mRunning = false;
	}

	public void killProcess(String pid) {
		try {
			InputStream is = new FileInputStream(pid);
			int size;
			if ((size = is.available()) > 0) {
				byte[] buffer = new byte[size];
				is.read(buffer);
				is.close();
				new ProcessBuilder().command(
						String.format("/system/bin/kill -9 %s",
								new String(buffer)).split(" ")).start();
			}
		} catch (IOException e) {
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();

		IntentFilter filter = new IntentFilter();
		filter.addAction("com.zed1.luaservice.START");
		/**
		 * 征询启动
		 * 
		 * 
		 */
		registerReceiver(mainVpnServiceReceiver, filter);

		/**
		 * 命令线程
		 * 
		 * 
		 */
		new Thread(new Runnable() {

			@Override
			public void run() {
				/**
				 * 修正异常
				 * 
				 * 
				 */
				LocalServerSocket server = null;
				LocalSocket localSocket = null;
				boolean running = false;

				try {
					localSocket = new LocalSocket();
					localSocket.bind(new LocalSocketAddress(
							"/data/data/com.zed1.luaservice/luaservice_path",
							LocalSocketAddress.Namespace.FILESYSTEM));

					server = new LocalServerSocket(localSocket
							.getFileDescriptor());
					running = true;
				} catch (IOException e) {
				}
				while (running) {
					try {
						LocalSocket socket = server.accept();
						BufferedReader reader = new BufferedReader(
								new InputStreamReader(socket.getInputStream()));
						String params = reader.readLine();
						if (params.startsWith("START")) {
							String[] p = params.split(",");
							if (p.length == 4) {
								/**
								 * 重设参数
								 * 
								 * 
								 */
								mManageAddress = p[1];
								mManagePort = Integer.parseInt(p[2]);
								mUid = p[3];

								Log.d("vpnService", "start");
								mStart = true;
								socket.getOutputStream()
										.write(mRunning ? 1 : 0);
							}
						} else if (params.startsWith("STOP")) {
							stop();
							/**
							 * 结束链接
							 * 
							 * 
							 */
							mStart = false;
							socket.getOutputStream().write(mRunning ? 1 : 0);
						} else if (params.startsWith("RESTART")) {
							/**
							 * 更新对端
							 * 
							 * 
							 */
							mStart = true;
						} else if (params.startsWith("STATE")) {
							/**
							 * 状态查询
							 * 
							 * 
							 */
							socket.getOutputStream().write(mRunning ? 1 : 0);
						}

					} catch (IOException e) {
					}
				}

				try {
					localSocket.close();
				} catch (IOException e) {
				}
			}

		}).start();

		/**
		 * 代理线程
		 * 
		 * 
		 */
		new Thread(new Runnable() {
			/**
			 * 代理中创建的链接不能走 VPN 形成环路，启动一个线程对代理中的 SOCKET 进行设置
			 * 
			 * 
			 */
			@Override
			public void run() {
				LocalSocket b = null;
				LocalServerSocket localSocket = null;
				boolean running = false;
				try {
					b = new LocalSocket();
					b.bind(new LocalSocketAddress(
							"/data/data/com.zed1.luaservice/protect_path",
							LocalSocketAddress.Namespace.FILESYSTEM));

					localSocket = new LocalServerSocket(b.getFileDescriptor());
					running = true;
				} catch (IOException e) {
				}
				/**
				 * 启动接收
				 * 
				 */
				while (running) {
					InputStream is = null;
					try {
						LocalSocket l = localSocket.accept();
						is = l.getInputStream();
						is.read();
						FileDescriptor[] fds = l.getAncillaryFileDescriptors();

						if (fds != null && fds.length != 0) {

							/**
							 * 通过反射获取了文件描述符的 INT 值
							 * 
							 * 
							 */
							int fd = (Integer) fds[0].getClass()
									.getDeclaredMethod("getInt$")
									.invoke(fds[0]);
							OutputStream os = l.getOutputStream();

							/**
							 * 将代理使用的 SOCKET 分离出来，从而走默认的网关
							 * 
							 * 
							 */
							os.write(protect(fd) ? 0 : 1);
							com.zed1.System.jniclose(fd);
							os.close();

						}
					} catch (Exception e) {
					}

					try {
						is.close();
					} catch (IOException e) {
					}
				}

				try {
					b.close();
				} catch (IOException e) {
				}
			}

		}).start();

		/**
		 * 获取对端信息并连接
		 * 
		 * 
		 */
		new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {
					if (mStart) {
						Socket socket = null;
						try {
							socket = new Socket();
							/**
							 * 要先建立再保护
							 * 
							 * 
							 */
							socket.setTcpNoDelay(true);
							protect(socket);
							socket.connect(new InetSocketAddress(
									mManageAddress, mManagePort));
							socket.getOutputStream()
									.write(String
											.format("GET /manage/cgi/api!getProxyByUid.action?uid=%s HTTP/1.1\r\nHost: %s:%d\r\nConnection: Keep-Alive\r\n\r\n",
													mUid, mManageAddress,
													mManagePort).getBytes());

							InputStream is = socket.getInputStream();
							is.read();
							int size = is.available();
							if (size > 0) {
								byte[] buffer = new byte[size];
								is.read(buffer);

								String content = new String(buffer);
								if (content.indexOf("\r\n\r\n") != -1) {
									/**
									 * 已获取对端信息
									 * 
									 * 
									 */
									String[] params = content.substring(
											content.indexOf("\r\n\r\n") + 4)
											.split(":");
									if (params != null && params.length == 5)
										startProxy("-s " + params[1] + " -p "
												+ params[2] + " -r "
												+ params[3] + " -l "
												+ params[4]);
									else {
										Log.d("vpnService", "peer is offline");
									}
									mStart = false;
								}
							} else {
								Log.d("vpnService", "get peer failed");
							}
						} catch (IOException e) {
							Log.d("vpnService", String.format(
									"unable to connect %s:%d", mManageAddress,
									mManagePort));
						}

						try {
							socket.close();
						} catch (IOException e) {
						}
					}
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
					}
				}
			}

		}).start();

		Log.d("vpnService", "service created");
	}

	public int onStartCommand(Intent intent, int flags, int startId) {
		/**
		 * 防止系统销毁服务
		 * 
		 * 
		 */
		return super.onStartCommand(intent, START_STICKY, startId);
	}

	@Override
	public void onRevoke() {
		/**
		 * 防止VPN 服务关闭时销毁服务
		 * 
		 */
		stop();
	}
}
