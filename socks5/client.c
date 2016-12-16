﻿// +----------------------------------------------------------------------
// | ZYSOFT [ MAKE IT OPEN ]
// +----------------------------------------------------------------------
// | Copyright (c) 2016 ZYSOFT All rights reserved.
// +----------------------------------------------------------------------
// | Licensed ( http://www.apache.org/licenses/LICENSE-2.0 )
// +----------------------------------------------------------------------
// | Author: zy_cwind <391321232@qq.com>
// +----------------------------------------------------------------------

/**
 * $ gcc -o client client.c ./turnclient/win32/lib/libturnclient.a ./libevent-release-2.0.22-stable/win32/lib/libevent.a -I./libevent-release-2.0.22-stable/win32/include/ -I./turnclient/ -lws2_32 -lgdi32 -static-libgcc
 * $ gcc -o client client.c ./turnclient/linux/lib/libturnclient.a ./libevent-release-2.0.22-stable/linux/lib/libevent.a -I./libevent-release-2.0.22-stable/linux/include/ -I./turnclient/ -lrt -static-libgcc -DPATH_MAX=4096
 * $ gcc -o client client.c ./turnclient/macos/lib/libturnclient.a ./libevent-release-2.0.22-stable/macos/lib/libevent.a -I./libevent-release-2.0.22-stable/macos/include/ -I./turnclient/ -DPATH_MAX=4096
 *
 */

#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <event2/listener.h>
#include <event2/bufferevent.h>
#include <unistd.h>
#include <string.h>
#include <event.h>

#include <turn_client.h>

#define MAX_BUF_SIZE 512
#define CLOSETIME 5

/**
 * windows 下要是用 closesocket 函数关闭连接
 *
 *
 */
#ifndef WIN32
#include <signal.h>
#include <netdb.h>
#include <netinet/in.h>
#include <arpa/inet.h>

/**
 * 安卓下使用 logcat
 *
 * 编译的时候需要加 debug
 *
 * ndk-build NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=./Android.mk NDK_DEBUG=1
 *
 */
#ifdef ANDROID
#include<android/log.h>

#define fprintf(f, ...) __android_log_print(ANDROID_LOG_DEBUG, "client", __VA_ARGS__)
#endif

#define closesocket close
#endif

#define SETTIMEOUT(ev, sec, cb, arg) do {struct timeval tv = {sec}; if (ev) event_free(ev); ev = evtimer_new(base, cb, arg); evtimer_add(ev, &tv);} while (0);

/**
 * 白名单处理
 *
 *
 */
struct context_t {
    long status;
    /**
     * 处理后剩下的数据
     *
     *
     */
    char buf[MAX_BUF_SIZE];
    long pos;
    
    struct event *tick;
    struct bufferevent *server;
    struct bufferevent *remote;
    
    /**
     * 响应数据
     *
     *
     */
    char new_buf[MAX_BUF_SIZE];
    long new_pos;
};

struct socks5_request_t {
    char ver;
    char cmd;
    char rsv;
    
    /**
     * 地址类型
     *
     *
     */
    char atyp;
};

struct event_base *base;

char *server_address = "203.156.199.168";
char *server_port = "5000";

char *listen_port = "8888";

/**
 * TURN 虚拟地址
 *
 *
 */
char *report_address;
char *report_port;

int g_argc;
char **g_argv;
char *dir;

/**
 * 链接数
 *
 *
 */
static int connected = 0;

/**
 * 检查地址是不是在白名单中
 *
 *
 */
long inipsegment(char *address, int prefix, char *host) {
    unsigned long mask = 0;
    unsigned long m = 0;
    int i = 0;
    for (; i < 4; i++) {
        mask >>= 8;
        if (prefix - 8 > 0) {
            prefix -= 8;
            mask |= 0xFF000000;
        } else if (prefix > 0) {
            while (prefix--) {
                m >>= 1;
                m |= 0x80000000;
            }
            mask |= m;
        }
    }
    if ((inet_addr(address) & mask) == (inet_addr(host) & mask))
        return 0;
    return -1;
}

long inwhitelist(char *host) {
    static char buf[2048];
    static long loaded = -1;
    if (loaded) {
        char wtl_file[PATH_MAX] = "whitelist.txt";
        FILE *fp;
        if (dir)
            sprintf(wtl_file, "%s/whitelist.txt", dir);
        if (fp = fopen(wtl_file, "rb")) {
            fread(buf, sizeof(buf), 1, fp);
            loaded = 0;
            fclose(fp);
        }
    }
    char tmp[MAX_BUF_SIZE];
    char *p = buf;
    char *s;
    while (sscanf(p, "%s\n", tmp) > 0) {
        if (!memcmp(host, tmp, strlen(host)))
            return 0;
        if ((s = strstr(tmp, "/")) != NULL) {
            s[0] = 0;
            if (!inipsegment(tmp, atoi(&s[1]), host))
                return 0;
            s[0] = '/';
        }
        if (p - buf < sizeof(buf))
            p += strlen(tmp);
        else
            break;
    }
    return -1;
}

void freecontext(struct context_t *context) {
    fprintf(stdout, "connection closed\n");

    /**
     * 修正内存问题
     *
     *
     */
    if (context->tick)
        event_free(context->tick);
    
    /**
     * 记录链接数
     *
     *
     */
    if (connected > 0)
        connected--;
    fprintf(stdout, "current connections: %d\n", connected);

    bufferevent_free(context->server);
    if (context->remote)
        bufferevent_free(context->remote);
    free(context);
}

void close_later(int fd, short events, void *arg) {
    struct context_t *context = (struct context_t *) arg;
    freecontext(context);
}

/**
 * 出错时断开
 *
 *
 */
void status_quit(struct bufferevent *bev, short events, void *arg) {
    struct context_t *context = (struct context_t *) arg;

    if (events & (BEV_EVENT_ERROR | BEV_EVENT_EOF))
        freecontext(context);
    return ;
}

void remote_quit(struct bufferevent *bev, short events, void *arg) {
    struct context_t *context = (struct context_t *) arg;

    if (events & (BEV_EVENT_ERROR | BEV_EVENT_EOF)) {
        /**
         * 等待 server 数据完成
         *
         *
         */
        SETTIMEOUT(context->tick, CLOSETIME, close_later, arg);
        context->status = 4;
    }
    return ;
}
    
void remote_read(struct bufferevent *bev, void *arg) {
    struct context_t *context = (struct context_t *) arg;

    if (context->status == 4) {
        /**
         * 跳过 VER METHOD
         *
         *
         */
        long size;
        if ((size = bufferevent_read(bev, &context->new_buf[context->new_pos], sizeof(context->new_buf) - context->new_pos)) < 0) {
            freecontext(context);
            return ;
        }
        context->new_pos += size;
        
        if (context->new_pos >= 2) {
            if (context->new_buf[1]!= 0) {
                freecontext(context);
                return ;
            }
            
            context->status = 2;
            context->new_pos -= 2;
            if (context->new_pos)
                bufferevent_write(context->server, &context->new_buf[2], context->new_pos);
        } else
            return ;
    } else
        bufferevent_write_buffer(context->server, bufferevent_get_input(bev));
}

/**
 * 创建一个新连接
 *
 *
 */
void open_remote(struct context_t *context) {
    char domain_name[MAX_BUF_SIZE];
    char *host;
    long len = 10;
    unsigned short port;
    struct socks5_request_t *request = (struct socks5_request_t *) &context->buf[0];
                
    if (request->atyp == 1)
        host = inet_ntoa(* (struct in_addr *) &context->buf[4]);
    else {
        memcpy(domain_name, &context->buf[5],  context->buf[4]);
        domain_name[context->buf[4]] = 0;
        host = domain_name; len = context->buf[4] + 7;
    }

    port = ntohs(* (unsigned short *) &context->buf[len - 2]);
    fprintf(stdout, "connect to %s:%d\n", host, port);
    
    int fd;
    if (!inwhitelist(host)) {
        fd = socket(AF_INET, SOCK_STREAM, 0);
        if (protect_socket(fd) != -1) {
            struct bufferevent *bev = bufferevent_socket_new(base, fd,  BEV_OPT_CLOSE_ON_FREE);
            
            context->remote = bev;
            bufferevent_setcb(bev, remote_read, NULL, remote_quit, context);
            bufferevent_enable(bev, EV_READ | EV_PERSIST);
            
            /**
             * 发送一个 REP
             *
             *
             */
            fprintf(stdout, "bypass\n");
            
            bufferevent_socket_connect_hostname(bev, NULL, AF_INET, host, port);
            bufferevent_write(context->server, "\x05\x00\x00\x01\x00\x00\x00\x00\x00\x00", 10);
            context->pos -= len;
            if (context->pos)
                memmove(&context->buf[0], &context->buf[len], context->pos);
            context->status = 2;
        } else
            closesocket(fd);
    } else {
        int new_fd;
        if (init_turn_client(server_address, atoi(server_port), &fd, NULL, NULL) == 0) {
            if (turnclient_connect_peer(fd, server_address, atoi(server_port), report_address, atoi(report_port), &new_fd) == 0 && protect_socket(new_fd) != -1) {
                struct bufferevent *bev = bufferevent_socket_new(base, new_fd, BEV_OPT_CLOSE_ON_FREE);
                
                context->remote = bev;
                bufferevent_setcb(bev, remote_read, NULL, remote_quit, context);
                bufferevent_setwatermark(bev, EV_READ, 0, MAX_BUF_SIZE);
                bufferevent_enable(bev, EV_READ | EV_PERSIST);
                
                /**
                 * 忽略 VER METHOD 响应连续发送 VER NMETHOD METHOD VER CMD RSV ATYP DST.ADDR DST.PORT
                 *
                 *
                 */
                bufferevent_write(context->remote, "\x05\x01\x00", 3);
#ifdef RESOLVE
                /**
                 * 在本地解析域名
                 *
                 *
                 */
                if (request->atyp == 3) {
                    struct hostent *h;
                    if (h = gethostbyname(host)) {
                        request->atyp = 1;
                        memcpy(&context->buf[8], &context->buf[len - 2], 2);
                        memcpy(&context->buf[4], h->h_addr, 4);
                        context->pos = 10;
                    }
                }
#endif
                bufferevent_write(context->remote, context->buf, context->pos);
                context->pos = 0;
                context->status = 4;
            } else
                closesocket(fd);
        }
#ifdef ANDROID
        /**
         * 对端掉线
         *
         *
         *
         */
        if (!context->remote) {
            fprintf(stdout, "unnable to connect peer\n");
            if (!send_restart())
                exit(0);
        }
#endif
    }
    return ;
}

void server_read(struct bufferevent *bev, void *arg) {
    struct context_t *context = (struct context_t *) arg;

    long size;
    if ((size = bufferevent_read(bev, &context->buf[context->pos], sizeof(context->buf) - context->pos)) < 0) {
        freecontext(context);
        return ;
    }
    context->pos += size;
    while (1) {
        long len;
        if (context->status == 0) {
            /**
             * VER NMETHOD METHOD
             *
             *
             */
            if (context->pos >= 2 && context->pos >= (len = context->buf[1] + 2)) {
                if (context->buf[0]!= 5) {
                    freecontext(context);
                    return ;
                }
                
                unsigned long i;
                context->status = 3;
                for (i = 0; i <(unsigned)  context->buf[1]; i++)
                    if (context->buf[2 + i] == 0) {
                        context->status = 1;
                        break;
                    }
                /**
                 * 没有支持的方法
                 *
                 *
                 */
                if (context->status == 3)
                    bufferevent_write(bev, "\x05\xFF", 2);
                else
                    bufferevent_write(bev, "\x05\x00", 2);
                
                context->pos -= len;
                if (context->pos)
                    memmove(&context->buf[0],&context->buf[len], context->pos);
            } else
                return ;
        } else if (context->status == 1) {
            /**
             * VER CMD RSV ATYP DST.ADDR DST.PORT
             *
             *
             */
            if (context->pos >= 4) {
                if (context->buf[0]!= 5) {
                    freecontext(context);
                    return ;
                }
                
                struct socks5_request_t *request = (struct socks5_request_t *) &context->buf[0];
                if (request->cmd == 1) {
                    /**
                     * CONNECT
                     *
                     *
                     */
                    if (request->atyp == 1) {
                        if (context->pos >= 10) {
                            open_remote(context);
                            if (!context->remote) {
                                freecontext(context);
                                return;
                            }
                        } else
                            return ;
                    } else if (request->atyp == 3) {
                        if (context->pos >= 4 && context->pos >= context->buf[4] + 7)
                            open_remote(context);
                        if (!context->remote) {
                            freecontext(context);
                            return ;
                        }
                        else
                            return ;
                    } else {
                        /**
                         * 不支持的地址类型
                         *
                         *
                         */
                        bufferevent_write(bev, "\x05\x08\x00\x01\x00\x00\x00\x00\x00\x00", 10);
                        context->status = 3;
                    }
                } else {
                    /**
                     * 不支持的命令
                     *
                     *
                     */
                    {
                        bufferevent_write(bev, "\x05\x07\x00\x01\x00\x00\x00\x00\x00\x00", 10);
                        context->status = 3;
                    }
                }
            } else
                return ;
        } else if (context->status == 2) {
            if (context->pos > 0) {
                bufferevent_write(context->remote, context->buf, context->pos);
                context->pos = 0;
            }
            return ;
        } else if (context->status == 3) {
            /**
             * 返回协议错误
             *
             *
             */
            SETTIMEOUT(context->tick, CLOSETIME, close_later, context);
            context->status = 4;
        } else if (context->status == 4)
            return ;
    }
}

void open_server(struct evconnlistener *listener, evutil_socket_t new_fd, struct sockaddr *sin, int slen, void *arg) {
    fprintf(stdout, "accept a connection\n");
    
    struct context_t *context = (struct context_t *) malloc(sizeof(struct context_t));
    if (context) {
        memset(context, 0, sizeof(struct context_t));
        struct bufferevent *bev = bufferevent_socket_new(base, new_fd, BEV_OPT_CLOSE_ON_FREE);

        connected++;
        fprintf(stdout, "current connections: %d\n", connected);
        
        context->server = bev;
        bufferevent_setcb(bev, server_read, NULL, status_quit, context);
        bufferevent_setwatermark(bev, EV_READ, 0, MAX_BUF_SIZE);
        bufferevent_enable(bev, EV_READ | EV_PERSIST);
    } else
        closesocket(new_fd);
}

void update_read(struct bufferevent *bev, void *arg) {
    char buf[MAX_BUF_SIZE];
    long size;
    static char params[4][MAX_BUF_SIZE];
    
    if ((size = bufferevent_read(bev, buf, sizeof(buf))) >= 0) {
        buf[size] = 0;
        if (sscanf(
                buf,
                "%[^:]:%[^:]:%[^:]:%[^\r\n]",
                params[0],
                params[1],
                params[2],
                params[3]
            ) >= 4) {
            /**
             * 更新参数
             *
             *
             */
            server_address = params[0];
            server_port    = params[1];
            report_address = params[2];
            report_port    = params[3];
            fprintf(stdout, "turn: %s:%s\nrelay: %s:%s\n", server_address, server_port, report_address, report_port);
        }
    }
}

void open_update(struct evconnlistener *listener, evutil_socket_t new_fd, struct sockaddr *sin, int slen, void *arg) {
    struct bufferevent *bev = bufferevent_socket_new(base, new_fd, BEV_OPT_CLOSE_ON_FREE);
    /**
     * 更新参数
     *
     *
     */
    if (bev) {
        bufferevent_setcb(bev, update_read, NULL, NULL, NULL);
        bufferevent_setwatermark(bev, EV_READ, 0, MAX_BUF_SIZE);
        bufferevent_enable(bev, EV_READ | EV_PERSIST);
    } else
        closesocket(new_fd);
}

/**
 * 输出帮助,必要参数为管理服务器地址
 *
 *
 */
void show_useage() {
    static const char *useage = \
        "useage:\n" \
        "\t -r peer address\n" \
        "\t -l peer port\n" \
        "\t[-d working dir]\n" \
        "\t[-s turnserver address], default is 203.156.199.168\n" \
        "\t[-p turnserver port], default is 5000\n" \
        "\t[-i listen port], default is 8888\n";
    fprintf(stdout, "%s", useage);
    return ;
}

long client() {
    int c;

#ifndef WIN32
    signal(SIGPIPE, SIG_IGN);
#else
    WSADATA wsaData;
    assert(WSAStartup(MAKEWORD(1, 1), &wsaData) == 0);
#endif

    /**
     * 参数配置
     *
     *
     */
    opterr = 0;
    while ((c = getopt(g_argc, g_argv, "d:r:l:s:p:i:")) != -1) {
        switch(c) {
        case 'd':
            dir              = optarg;
            break;
        case 'r':
            report_address   = optarg;
            break;
        case 'l':
            report_port      = optarg;
            break;
        case 's':
            server_address   = optarg;
            break;
        case 'p':
            server_port      = optarg;
            break;
        case 'i':
            listen_port      = optarg;
            break;
        }
    }
    if (opterr)
        exit(0);

#ifdef ANDROID
    /**
     * 防止重复启动
     *
     *
     */
    if (dir) {
        char buf[16] = {0};
        char pid_file[PATH_MAX];
        FILE *fp;
        
        sprintf(pid_file, "%s/client.pid", dir);
        if (fp = fopen(pid_file, "rb")) {
            if (fgets(buf, sizeof(buf), fp))
                kill(atoi(buf), SIGTERM);
            fclose(fp);
        }
        if (fp = fopen(pid_file, "wb")) {
            sprintf(buf, "%d", getpid());
            fputs(buf, fp);
            fclose(fp);
        }
    }
#endif

    /**
     * TURN 地址,管理服务器地址均有默认值
     *
     *
     */
    if (report_address == NULL || report_port == NULL) {
        show_useage();
        exit(0);
    }
    
    /**
     * 启动服务
     *
     *
     */
    base = event_base_new();
    assert(base != NULL);
    
    fprintf(stdout, "turn: %s:%s\nrelay: %s:%s\n", server_address, server_port, report_address, report_port);
    
    /**
     * 监听连接
     *
     *
     */
    struct sockaddr_in sin;
    sin.sin_family = AF_INET;
    sin.sin_addr.s_addr = htonl(INADDR_ANY);
    sin.sin_port = htons(atoi(listen_port));
    assert(evconnlistener_new_bind(base, open_server, NULL, LEV_OPT_CLOSE_ON_FREE | LEV_OPT_REUSEABLE, 5, (struct sockaddr *) &sin, sizeof(struct sockaddr)));
    
    {
        /**
         * 更新参数
         *
         *
         */
        struct sockaddr_in sin;
        sin.sin_family = AF_INET;
        sin.sin_addr.s_addr = htonl(INADDR_ANY);
        sin.sin_port = htons(atoi(listen_port) + 1);
        evconnlistener_new_bind(
            base,
            open_update,
            NULL,
            LEV_OPT_CLOSE_ON_FREE | LEV_OPT_REUSEABLE,
            5,
            (struct sockaddr *) &sin,
            sizeof(struct sockaddr)
        );
    }
    event_base_dispatch(base);
    
    fprintf(stdout, "event loop quited\n");
    return 0;
}

long main(int argc, char *argv[]) {
    g_argc = argc;
    g_argv = argv;
    /**
     * 无守护进程
     *
     *
     */
    exit(client());
    
    return 0;
}

#ifdef JNI

#include "jni.h"

jint Java_com_zed1_System_client(JNIEnv* env, jobject thiz, jint argc, jobjectArray args) {
    /**
     * 转换参数
     *
     *
     */
    char *argv[16];
    long  i;
    for ( i = 0; i < (*env)->GetArrayLength(env, args); i++)
        argv[i] = (*env)->GetStringUTFChars(env, (jstring) (*env)->GetObjectArrayElement(env, args, i), 0);
    pid_t pid = fork();
    if (pid > 0);
    else if (pid == 0)
        exit(main(argc, argv));
    return 0;
}
#endif
