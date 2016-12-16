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
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

@SuppressLint("SdCardPath")
public class LuaVpnService extends VpnService {

	public static final String CONFIG_DIR = "/data/data/com.zed2.luaservice/";
	public static final String TAG = "VPN";
	
	public static final int TIMEOUT = 1000;
	
	boolean mProtectorRunning;
	boolean mVpnRunning;
	
	ParcelFileDescriptor mDescriptor;
	
	Thread mProtector = new Thread() {
		
		@Override
		public void run() {
			LocalSocket b = null;
			LocalServerSocket localSocket = null;
			mProtectorRunning = false;
			
			try {
				b = new LocalSocket();
				b.bind(new LocalSocketAddress(CONFIG_DIR + "protect_path", LocalSocketAddress.Namespace.FILESYSTEM));
				b.setSoTimeout(TIMEOUT);
				localSocket = new LocalServerSocket(b.getFileDescriptor());
				mProtectorRunning = true;
			} catch (IOException e) {
			}
			
			while (mProtectorRunning) {
				try {
					LocalSocket socket = localSocket.accept();
					InputStream in;
					
					if (socket != null) {
						in = socket.getInputStream();
						in.read();
						{
							FileDescriptor[] fds = socket.getAncillaryFileDescriptors();
							if (fds != null && fds.length != 0) {
								int fd = (Integer) fds[0].getClass().getDeclaredMethod("getInt$").invoke(fds[0]);
								OutputStream out;
								
								out = socket.getOutputStream();
								if (protect(fd)) {
									out.write(0);
								} else {
									out.write(1);
								}
								out.close();
								com.zed2.System.jniclose(fd);
							}
						}
					}
				} catch (Exception e) {
				}
			}
			
			try {
				b.close();
			} catch (IOException e) {
			}
			
			Log.d(TAG, "protector exited.");
		}
		
	};
	
	Thread mVpn = new Thread() {
		/**
		 * 连接信息 
		 * 
		 * 
		 */
		String mVpnParams;
		String mUid;
		String mManage;
		String mManagePort;
		
		@Override
		public void run() {
			LocalSocket b = null;
			LocalServerSocket localSocket = null;
			mVpnRunning = false;

			try {
				b = new LocalSocket();
				b.bind(new LocalSocketAddress(CONFIG_DIR + "luaservice_path", LocalSocketAddress.Namespace.FILESYSTEM));
				b.setSoTimeout(TIMEOUT);
				localSocket = new LocalServerSocket(b.getFileDescriptor());
				mVpnRunning = true;
			} catch (IOException e) {
			}
			
			while (mVpnRunning) {
				try {
					LocalSocket socket = localSocket.accept();
					InputStream in;
					if (socket != null) {
						in = socket.getInputStream();
						{
							BufferedReader reader = new BufferedReader(new InputStreamReader(in));
							String params = reader.readLine();
							
							if (params.startsWith("START")) {
								String[] p = params.split(",");
								if (p.length == 4) {
									startVpn(p[3], p[1], p[2]);
								}
							} else if (params.startsWith("RESTART")) {
								startVpn();
							} else if (params.startsWith("STOP")) {
								releaseAllResources();
							}
							
							/**
							 * 返回结果
							 * 
							 * 
							 */
							try {
								if (mDescriptor == null) {
									socket.getOutputStream().write(0);
								} else {
									socket.getOutputStream().write(1);
								}
							} catch (IOException e) {
								Log.d(TAG, "failed to send back results.");
							}
						}
					}
				} catch (Exception e) {
				}
			}
			
			try {
				b.close();
			} catch (IOException e) {
			}
			
			Log.d(TAG, "vpn exited.");
		}
		
		void startVpn() {
			if (mUid != null &&
				mManage != null &&
				mManagePort != null) {
				Socket socket = new Socket();
				try {
					socket.setTcpNoDelay(true);
					protect(socket);
					socket.connect(new InetSocketAddress(mManage, Integer.parseInt(mManagePort)));
					socket.getOutputStream().write(String.format("GET /manage/cgi/api!getProxyByUid.action?uid=%s HTTP/1.1\r\nHost: %s:%d\r\nConnection: Keep-Alive\r\n\r\n", mUid, mManage, Integer.parseInt(mManagePort)).getBytes());
					{
						InputStream in = socket.getInputStream();
						int size;
						
						in.read();
						if ((size = in.available()) > 0) {
							byte[] buffer = new byte[size];
							
							in.read(buffer);
							{
								String content = new String(buffer);
								
								if (content.indexOf("\r\n\r\n") != -1) {
									String[] params = content.substring(content.indexOf("\r\n\r\n") + 4).split(":");
									
									if (params != null && params.length == 5) {
										startVpn("-s " + params[1] + " -p " + params[2] + " -r " + params[3] + " -l " + params[4]);
									}
								} else {
									Log.d(TAG, "peer is offline.");
								}
							}
						}
					}
				} catch (IOException e) {
					Log.d(TAG, "unable to connect to manage server.");
				}
				try {
					socket.close();
				} catch (IOException e) {
				}
			}	
		}
		
		void startVpn(String uid, String manage, String managePort) {
			mUid = uid;
			mManage = manage;
			mManagePort = managePort;
			startVpn();
		}
		
		void startVpn(String vpnParams) {
			Log.d(TAG, "start vpn.");
			
			mVpnParams = vpnParams;
			
			if (mDescriptor != null) {
				Log.d(TAG, "client restarted.");
				restart();
			} else {
				Builder builder = new Builder().setMtu(1500).addAddress("26.26.26.1", 24);
				String[] p;
				/**
				 * 绕过局域网
				 * 
				 * 
				 */
				for (String bypass : getResources().getStringArray(R.array.bypass_private_route)) {
					p = bypass.split("/");
					builder.addRoute(p[0], Integer.parseInt(p[1]));
				}
				mDescriptor = builder.establish();
				if (mDescriptor != null) {
					int fd = mDescriptor.getFd();
					String[] params;
					
					killProcess(CONFIG_DIR + "tun2socks.pid");
					params = String.format(CONFIG_DIR + "tun2socks --netif-ipaddr 26.26.26.2 --netif-netmask 255.255.255.0 --socks-server-addr 127.0.0.1:1080 --tunfd %d --tunmtu 1500 --loglevel 3 --enable-udprelay", fd).split(" ");
					com.zed2.System.tun2socks(params.length, params);
					if (fd != -1) {
						for (int i = 0; i < 5; i++) {
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
							}
							if (com.zed2.System.sendfd(fd) != -1) {
								restart();
								return;
							}
						}
					}
					/**
					 * 没有启动执行清理
					 * 
					 * 
					 */
					killProcess(CONFIG_DIR + "tun2socks.pid");
					try {
						mDescriptor.close();
						mDescriptor = null;
					} catch (IOException e) {
					}
				}
				Log.d(TAG, "unable to start vpn.");
			}
		}
		
		void restart() {
			String[] params;
			
			killProcess(CONFIG_DIR + "client.pid");
			params = (CONFIG_DIR + "client -d " + CONFIG_DIR + " -i 1080 " + mVpnParams).split(" ");
			com.zed2.System.client(params.length, params);
		}
		
	};
	
	@Override
	public void onCreate() {
		super.onCreate();
		/**
		 * 启动服务
		 * 
		 * 
		 */
		mProtector.start();
		mVpn.start();
		Log.d(TAG, "vpn service started.");
	}

	@Override
	public void onRevoke() {
		/**
		 * 服务退出需要重新授权启动
		 * 
		 * 
		 * 
		 */
		mProtectorRunning = false;
		mVpnRunning = false;
		
		releaseAllResources();
		stopSelf();
	}
	
	void releaseAllResources() {
		killProcess(CONFIG_DIR + "tun2socks.pid");
		killProcess(CONFIG_DIR + "client.pid");
		if (mDescriptor != null) {
			try {
				mDescriptor.close();
				mDescriptor = null;
			} catch (IOException e) {
			}
		}
	}
	
	void killProcess(String pid) {
		try {
			InputStream is = new FileInputStream(pid);
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
	
}
