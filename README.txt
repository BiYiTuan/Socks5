;; +----------------------------------------------------------------------
;; | ZYSOFT [ MAKE IT OPEN ]
;; +----------------------------------------------------------------------
;; | Copyright (c) 2016 ZYSOFT All rights reserved.
;; +----------------------------------------------------------------------
;; | Licensed ( http://www.apache.org/licenses/LICENSE-2.0 )
;; +----------------------------------------------------------------------
;; | Author: zy_cwind <391321232@qq.com>
;; +----------------------------------------------------------------------

����˵����
test����׿��ͻ��� demo

server-android����׿����� demo
    �ӷ�������ȡ���ػ����ܵĿ�ִ���ļ��������� MainActivity.java ��ʹ�ô˺������� com.zed1.System.startStandalone(MainActivity.this, "test");
    ���� jni ������� MainActivity.java ��ʹ�ô˺������� com.zed1.System.start(MainActivity.this, "test");

socks5����ƽ̨������ͻ���

ms��VC6 MFC ����� demo
    �� demo ���õ� server.dll ���б���

����˵����������·����

ʹ�� VC6 VS2005 VS2008 VS2010 VS2015��
���б��� libevent-release-2.0.22-stable.zip MSVC ��⣬�� server.c ���빤�̡�

ʹ�� MINGW MINGW64 CYGWIN
���� libevent-release-2.0.22-stable��
    ./configure
    make
���� libturnclient ��
    ���б���
����ͻ��ˣ�ע��·������
    gcc -o client client.c ./turnclient/win32/lib/libturnclient.a ./libevent-release-2.0.22-stable/win32/lib/libevent.a -I./libevent-release-2.0.22-stable/win32/include/ -I./turnclient/ -lws2_32 -lgdi32 -static-libgcc
������ڣ�
    gcc -o server server.c ./turnclient/win32/lib/libturnclient.a ./libevent-release-2.0.22-stable/win32/lib/libevent.a -I./libevent-release-2.0.22-stable/win32/include/ -I./turnclient/ -lws2_32 -lgdi32 -static-libgcc
������ڶ�̬�⣨Ҫ���� MSVC ��ʹ����ת�����ʽ����
    gcc -o server.dll -shared server.c ./turnclient/win32/lib/libturnclient.a ./libevent-release-2.0.22-stable/win32/lib/libevent.a -I./libevent-release-2.0.22-stable/win32/include/ -I./turnclient/ -lws2_32 -lgdi32 -static-libgcc -DDLL

ʹ�� GCC��
���� libevent-release-2.0.22-stable��
    ./configure
    make
���� libturnclient ��
    ���б���
����ͻ��ˣ�ע��·������
    linux��gcc -o client client.c ./turnclient/linux/lib/libturnclient.a ./libevent-release-2.0.22-stable/linux/lib/libevent.a -I./libevent-release-2.0.22-stable/linux/include/ -I./turnclient/ -lrt -static-libgcc -DPATH_MAX=4096
    macos: gcc -o client client.c ./turnclient/macos/lib/libturnclient.a ./libevent-release-2.0.22-stable/macos/lib/libevent.a -I./libevent-release-2.0.22-stable/macos/include/ -I./turnclient/ -DPATH_MAX=4096
������ڣ�
    linux��gcc -o server server.c ./turnclient/linux/lib/libturnclient.a ./libevent-release-2.0.22-stable/linux/lib/libevent.a -I./libevent-release-2.0.22-stable/linux/include/ -I./turnclient/ -lrt -static-libgcc -DPATH_MAX=4096
    macos: gcc -o server server.c ./turnclient/macos/lib/libturnclient.a ./libevent-release-2.0.22-stable/macos/lib/libevent.a -I./libevent-release-2.0.22-stable/macos/include/ -I./turnclient/ -DPATH_MAX=4096

ʹ�� android-ndk
����ͻ��ˣ�
    ndk-build NDK_DEBUG=1
������ڣ�
    ndk-build NDK_DEBUG=1

���� apk��
�� eclipse->���빤��->Build Project
