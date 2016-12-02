// +----------------------------------------------------------------------
// | ZYSOFT [ MAKE IT OPEN ]
// +----------------------------------------------------------------------
// | Copyright(c) 20015 ZYSOFT All rights reserved.
// +----------------------------------------------------------------------
// | Licensed( http://www.apache.org/licenses/LICENSE-2.0 )
// +----------------------------------------------------------------------
// | Author:zy_cwind<391321232@qq.com>
// +----------------------------------------------------------------------

/**
 * 安卓下使用 logcat
 *
 *
 * ndk-build NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=./Android.mk NDK_DEBUG=1
 *
 */
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <unistd.h>
#include <string.h>
#include <event.h>
#include <netinet/in.h>
#include <event2/listener.h>
#include <event2/bufferevent.h>

#include<android/log.h>

typedef int sint;

#define fprintf(f, ...) __android_log_print(ANDROID_LOG_DEBUG, "GUARD", __VA_ARGS__)

#define SETTIMEOUT(ev, sec, cb, arg) do {struct timeval tv = {sec}; if (ev) event_free(ev); ev = evtimer_new(base, cb, arg); evtimer_add(ev, &tv);} while (0)

#define MAX_BUF_SIZE 32768
#define TIMEOUT 180

#define PACKAGE "com.zed1.luaservice"
#define PACKAGE_NAME PACKAGE "/.MainActivity"
#define PORT 8896

#define PS "/system/bin/ps"
#define AM "/system/bin/am"
#define SU "/system/bin/su"
#define REBOOT "/system/bin/reboot"
#define KILL   "/system/bin/kill"

struct event_base *base = NULL;
struct event *tick;

unsigned int timeout = 0;

/**
 * 执行 shell 命令并返回结果
 *
 * @param cmd 命令
 * @param buf 结果
 * @param buf_size 缓冲区大小
 * @return 执行成功/失败
 *
 */
sint exec_shell_command(char *cmd, char *buf, int buf_size) {
    int fd[2];
    int fd_dup[2];
	
    if(!pipe(fd)) {
        fd_dup[0] = dup(STDOUT_FILENO);
        fd_dup[1] = dup2(fd[1], STDOUT_FILENO);
    
        if (!system(cmd)) {
            read(fd[0], buf, buf_size);
            dup2(fd_dup[0], fd_dup[1]);
            return 0;
        }
    }
    return -1;
}

/**
 * 获取进程 pid
 *
 * @param process 进程名称
 * @return pid
 *
 */
sint get_process_pid(char *process) {
    char buf[MAX_BUF_SIZE] = {0};
    char row[MAX_BUF_SIZE];
    char * p;
	
    if (!exec_shell_command(PS " -x", buf, sizeof(buf))) {
        p = buf;
        do {
            if (sscanf(p, "%[^\n]", row) > 0) {
                p += strlen(row);
                p ++;
                if (strstr(row, process)) {
                    int pid;
                    if (sscanf(row, "%*[^ ]%*[ ]%d", &pid) > 0)
                        return pid;
                }
            } else
                break;
        } while (p < buf + strlen(buf));
    }
    return -1;
}

/**
 * 判断某一进程是否存在
 * 等同于 ps x | grep process
 *
 * @param process 进程名称
 * @return 存在/不存在
 *
 */
sint is_alive(char *process) {
    return get_process_pid(process) != -1 ? 0 : -1;
}

/**
 * 使用 android 中 activitymanager 启动包
 *
 * @param package_name 包名及 activity 路径
 * @return 启动成功/失败
 *
 */
sint start_package(char *package_name) {
    char cmd[MAX_BUF_SIZE] = {0};
    char buf[MAX_BUF_SIZE] = {0};

    sprintf(cmd, AM " start -n %s", package_name);
    if (!exec_shell_command(cmd, buf, sizeof(buf)))
        if (!strstr(buf, "Error"))
            return 0;
    return -1;
}

/**
 * 重启需要 root 权限
 *
 * 重启成功/失败
 *
 */
sint reboot_sys() {
    return system(SU " -c \"" REBOOT "\"");
}

/**
 * 结束进程
 * 会结束所有包含进程名称的进程
 *
 * @param process 进程名称
 * @return 成功/失败
 *
 */
sint kill_all_process(char *process) {
    int pid;
    char buf[MAX_BUF_SIZE];
	
    while ((pid = get_process_pid(process)) != -1) {
        sprintf(buf, SU " -c \"" KILL "\" -9 %d", pid);
        if (system(buf))
            return -1;
    }
    return 0;
}

void step(int fd, short events, void *arg) {
    if (timeout++ >= TIMEOUT) {
        timeout = 0;
        fprintf(stdout, "timeout, restarting\n");
        {
            kill_all_process(PACKAGE);
            start_package(PACKAGE_NAME);
        }
    }
    SETTIMEOUT(tick, 1, step, NULL);
}

/**
 * 超时没有收到心跳或收到重启指令重启
 * 
 *
 */
void on_command(struct bufferevent *bev, void *arg) {
    char buf[MAX_BUF_SIZE];
    int size;
	
    if ((size = bufferevent_read(bev, buf, sizeof(buf))) > 0) {
        buf[size] = 0;
        timeout = 0;
        fprintf(stdout, "command received: %s\n", buf);
        if (!strcmp(buf, "RESTART\n")) {
            fprintf(stdout, "restarting\n");
            /**
             * 收到重启指令
             *
             *
             */
            kill_all_process(PACKAGE);
            start_package(PACKAGE_NAME);
        }
    }
}

/**
 * 监听连接
 *
 *
 *
 */
void conn(struct evconnlistener *listener, evutil_socket_t new_fd, struct sockaddr *sin, int slen, void *arg) {
    struct bufferevent *bev;

    fprintf(stdout, "accept a connection\n");
    if ((bev = bufferevent_socket_new(base, new_fd, BEV_OPT_CLOSE_ON_FREE))) {
        bufferevent_setcb(bev, on_command, NULL, NULL, NULL);
        bufferevent_setwatermark(bev, EV_READ, 0, MAX_BUF_SIZE);
        bufferevent_enable(bev, EV_READ | EV_PERSIST);
    }
}

/**
 * 程序入口
 * 通过对指定端口发送心跳维持存活
 *
 *
 */
sint main(int argc, char *argv[]) {
    base = event_base_new();
    assert(base != NULL);
    {
        struct sockaddr_in sin;
		
        sin.sin_family = AF_INET;
        sin.sin_addr.s_addr = htonl(INADDR_ANY);
        sin.sin_port = htons(PORT);
        assert(evconnlistener_new_bind(base, conn, NULL, LEV_OPT_CLOSE_ON_FREE | LEV_OPT_REUSEABLE, 5, (struct sockaddr *) &sin, sizeof(struct sockaddr)));
        /**
         * 定时检查
         *
         *
         */
        SETTIMEOUT(tick, 1, step, NULL);
        event_base_dispatch(base);
    }
    fprintf(stdout, "event loop quited\n");
    return 0;
}
