// +----------------------------------------------------------------------
// | ZYSOFT [ MAKE IT OPEN ]
// +----------------------------------------------------------------------
// | Copyright (c) 2016 ZYSOFT All rights reserved.
// +----------------------------------------------------------------------
// | Licensed ( http://www.apache.org/licenses/LICENSE-2.0 )
// +----------------------------------------------------------------------
// | Author: zy_cwind <391321232@qq.com>
// +----------------------------------------------------------------------

/**
 * 解决签名导出问题
 *
 *
 */
#define LOG_TAG "client"

#include "jni.h"
#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <cpu-features.h>

#include <sys/un.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <ancillary.h>

#define LOGI(...) do { __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__); } while(0)
#define LOGW(...) do { __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__); } while(0)
#define LOGE(...) do { __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__); } while(0)

JNIEXPORT jstring JNICALL Java_com_zed2_System_getABI(JNIEnv *env, jclass obj) {
  AndroidCpuFamily family = android_getCpuFamily();
  uint64_t features = android_getCpuFeatures();
  const char *abi;

  if (family == ANDROID_CPU_FAMILY_X86) {
    abi = "x86";
  } else if (family == ANDROID_CPU_FAMILY_MIPS) {
    abi = "mips";
  } else if (family == ANDROID_CPU_FAMILY_ARM) {
    // if (features & ANDROID_CPU_ARM_FEATURE_ARMv7) {
    abi = "armeabi-v7a";
    // } else {
    //   abi = "armeabi";
    // }
  }
  return (*env)->NewStringUTF(env, abi);
}

JNIEXPORT jint JNICALL Java_com_zed2_System_exec(JNIEnv *env, jclass obj, jstring cmd) {
    const char *cmd_str  = (*env)->GetStringUTFChars(env, cmd, 0);

    pid_t pid;

    /*  Fork off the parent process */
    pid = fork();
    if (pid < 0) {
        (*env)->ReleaseStringUTFChars(env, cmd, cmd_str);
        return -1;
    }

    if (pid > 0) {
        (*env)->ReleaseStringUTFChars(env, cmd, cmd_str);
        return pid;
    }

    execl("/system/bin/sh", "sh", "-c", cmd_str, NULL);
    (*env)->ReleaseStringUTFChars(env, cmd, cmd_str);

    return 1;
}

JNIEXPORT void JNICALL Java_com_zed2_System_jniclose(JNIEnv *env, jclass obj, jint fd) {
    close(fd);
}

JNIEXPORT jint JNICALL Java_com_zed2_System_sendfd(JNIEnv *env, jclass obj, jint tun_fd) {
    int fd;
    struct sockaddr_un addr;

    if ( (fd = socket(AF_UNIX, SOCK_STREAM, 0)) == -1) {
        LOGE("socket() failed: %s (socket fd = %d)\n", strerror(errno), fd);
        return (jint)-1;
    }

    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, "/data/data/com.zed2.luaservice/sock_path", sizeof(addr.sun_path)-1);

    if (connect(fd, (struct sockaddr*)&addr, sizeof(addr)) == -1) {
        LOGE("connect() failed: %s (fd = %d)\n", strerror(errno), fd);
        close(fd);
        return (jint)-1;
    }

    if (ancil_send_fd(fd, tun_fd)) {
        LOGE("ancil_send_fd: %s", strerror(errno));
        close(fd);
        return (jint)-1;
    }

    close(fd);
    return 0;
}
