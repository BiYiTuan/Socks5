// +----------------------------------------------------------------------
// | ZYSOFT [ MAKE IT OPEN ]
// +----------------------------------------------------------------------
// | Copyright(c) 20015 ZYSOFT All rights reserved.
// +----------------------------------------------------------------------
// | Licensed( http://www.apache.org/licenses/LICENSE-2.0 )
// +----------------------------------------------------------------------
// | Author:zy_cwind<391321232@qq.com>
// +----------------------------------------------------------------------

package com.zed2;

public class System {
	static {
		java.lang.System.loadLibrary("system");
		java.lang.System.loadLibrary("tun2socks");
		java.lang.System.loadLibrary("client");
	}

	public static native int exec(String cmd);

	public static native String getABI();

	public static native int sendfd(int fd);

	public static native void jniclose(int fd);

	public static native int tun2socks(int argc, String[] argv);

	public static native int client(int argc, String[] argv);
}
