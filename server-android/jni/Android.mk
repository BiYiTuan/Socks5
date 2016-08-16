# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#
LOCAL_PATH := $(call my-dir)

########################################################
## libancillary
########################################################

include $(CLEAR_VARS)

ANCILLARY_SOURCE := fd_recv.c fd_send.c
LOCAL_MODULE := libancillary
LOCAL_CFLAGS += -O2 -I$(LOCAL_PATH)/libancillary
LOCAL_SRC_FILES := $(addprefix libancillary/, $(ANCILLARY_SOURCE))

include $(BUILD_STATIC_LIBRARY)

########################################################
## libturnclient
########################################################

include $(CLEAR_VARS)

LOCAL_CFLAGS := -DPJ_IS_LITTLE_ENDIAN=1 -DPJ_IS_BIG_ENDIAN=0
LOCAL_CFLAGS += -DANDROID_TURN=1

LOCAL_C_INCLUDES:= \
		$(LOCAL_PATH)/turnclient
TURNCLIENT_SOURCE := \
		common.c \
		hmac_sha1.c \
		protocol.c \
		sha1.c \
		turn_client.c \
		util_sys.c
LOCAL_MODULE := libturnclient
LOCAL_CFLAGS += -O2
LOCAL_SRC_FILES := $(addprefix turnclient/, $(TURNCLIENT_SOURCE))

include $(BUILD_STATIC_LIBRARY)

########################################################
## libevent
########################################################

include $(CLEAR_VARS)

LOCAL_C_INCLUDES:= \
		$(LOCAL_PATH)/libevent-release-2.0.22-stable/include \
        $(LOCAL_PATH)/libevent-release-2.0.22-stable
EVENT_SOURCE := event.c \
		evthread.c \
		buffer.c \
		bufferevent.c \
		bufferevent_filter.c \
		bufferevent_pair.c \
		listener.c \
		bufferevent_ratelim.c \
		evmap.c \
		log.c \
		evutil.c \
		evutil_rand.c \
		select.c \
		poll.c \
		epoll.c \
		signal.c \
		event_tagging.c \
		http.c \
		evdns.c \
		evrpc.c \
		bufferevent_sock.c
LOCAL_MODULE := libevent
LOCAL_CFLAGS += -O2
LOCAL_SRC_FILES := $(addprefix libevent-release-2.0.22-stable/, $(EVENT_SOURCE))

include $(BUILD_STATIC_LIBRARY)

########################################################
## server-jni
########################################################

include $(CLEAR_VARS)

LOCAL_CFLAGS := -DLINUX -DJNI
LOCAL_STATIC_LIBRARIES := libturnclient libancillary libevent
LOCAL_C_INCLUDES:= \
		$(LOCAL_PATH)/libevent-release-2.0.22-stable \
		$(LOCAL_PATH)/libevent-release-2.0.22-stable/include \
		$(LOCAL_PATH)/libevent-release-2.0.22-stable/include/event2
LOCAL_SRC_FILES := server.c
LOCAL_MODULE := server
LOCAL_LDLIBS := -ldl -llog

include $(BUILD_SHARED_LIBRARY)

########################################################
## server
########################################################

include $(CLEAR_VARS)

LOCAL_CFLAGS := -DLINUX
LOCAL_STATIC_LIBRARIES := libturnclient libancillary libevent
LOCAL_C_INCLUDES:= \
		$(LOCAL_PATH)/libevent-release-2.0.22-stable \
		$(LOCAL_PATH)/libevent-release-2.0.22-stable/include \
		$(LOCAL_PATH)/libevent-release-2.0.22-stable/include/event2
LOCAL_SRC_FILES := server.c
LOCAL_MODULE := server-standalone
LOCAL_LDLIBS := -ldl -llog

include $(BUILD_EXECUTABLE)


