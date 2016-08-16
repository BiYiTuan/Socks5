;; +----------------------------------------------------------------------
;; | ZYSOFT [ MAKE IT OPEN ]
;; +----------------------------------------------------------------------
;; | Copyright (c) 2016 ZYSOFT All rights reserved.
;; +----------------------------------------------------------------------
;; | Licensed ( http://www.apache.org/licenses/LICENSE-2.0 )
;; +----------------------------------------------------------------------
;; | Author: zy_cwind <391321232@qq.com>
;; +----------------------------------------------------------------------

工程说明：
test：安卓版客户端 demo

server-android：安卓版出口 demo
    从服务器获取带守护功能的可执行文件并启动在 MainActivity.java 中使用此函数启动 com.zed1.System.startStandalone(MainActivity.this, "test");
    启动 jni 版出口在 MainActivity.java 中使用此函数启动 com.zed1.System.start(MainActivity.this, "test");

socks5：各平台出口与客户端

ms：VC6 MFC 版出口 demo
    此 demo 中用到 server.dll 自行编译

编译说明（大神绕路）：

使用 VC6 VS2005 VS2008 VS2010 VS2015：
自行编译 libevent-release-2.0.22-stable.zip MSVC 版库，将 server.c 加入工程。

使用 MINGW MINGW64 CYGWIN
编译 libevent-release-2.0.22-stable：
    ./configure
    make
编译 libturnclient ：
    自行编译
编译客户端（注意路径）：
    gcc -o client client.c ./turnclient/win32/lib/libturnclient.a ./libevent-release-2.0.22-stable/win32/lib/libevent.a -I./libevent-release-2.0.22-stable/win32/include/ -I./turnclient/ -lws2_32 -lgdi32 -static-libgcc
编译出口：
    gcc -o server server.c ./turnclient/win32/lib/libturnclient.a ./libevent-release-2.0.22-stable/win32/lib/libevent.a -I./libevent-release-2.0.22-stable/win32/include/ -I./turnclient/ -lws2_32 -lgdi32 -static-libgcc
编译出口动态库（要想在 MSVC 下使用需转换库格式）：
    gcc -o server.dll -shared server.c ./turnclient/win32/lib/libturnclient.a ./libevent-release-2.0.22-stable/win32/lib/libevent.a -I./libevent-release-2.0.22-stable/win32/include/ -I./turnclient/ -lws2_32 -lgdi32 -static-libgcc -DDLL

使用 GCC：
编译 libevent-release-2.0.22-stable：
    ./configure
    make
编译 libturnclient ：
    自行编译
编译客户端（注意路径）：
    linux：gcc -o client client.c ./turnclient/linux/lib/libturnclient.a ./libevent-release-2.0.22-stable/linux/lib/libevent.a -I./libevent-release-2.0.22-stable/linux/include/ -I./turnclient/ -lrt -static-libgcc -DPATH_MAX=4096
    macos: gcc -o client client.c ./turnclient/macos/lib/libturnclient.a ./libevent-release-2.0.22-stable/macos/lib/libevent.a -I./libevent-release-2.0.22-stable/macos/include/ -I./turnclient/ -DPATH_MAX=4096
编译出口：
    linux：gcc -o server server.c ./turnclient/linux/lib/libturnclient.a ./libevent-release-2.0.22-stable/linux/lib/libevent.a -I./libevent-release-2.0.22-stable/linux/include/ -I./turnclient/ -lrt -static-libgcc -DPATH_MAX=4096
    macos: gcc -o server server.c ./turnclient/macos/lib/libturnclient.a ./libevent-release-2.0.22-stable/macos/lib/libevent.a -I./libevent-release-2.0.22-stable/macos/include/ -I./turnclient/ -DPATH_MAX=4096

使用 android-ndk
编译客户端：
    ndk-build NDK_DEBUG=1
编译出口：
    ndk-build NDK_DEBUG=1

编译 apk：
打开 eclipse->导入工程->Build Project
