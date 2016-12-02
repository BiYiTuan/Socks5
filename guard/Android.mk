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
## guard
########################################################

include $(CLEAR_VARS)

LOCAL_STATIC_LIBRARIES := libevent
LOCAL_SRC_FILES := guard.c
LOCAL_C_INCLUDES:= \
		$(LOCAL_PATH)/libevent-release-2.0.22-stable \
		$(LOCAL_PATH)/libevent-release-2.0.22-stable/include \
		$(LOCAL_PATH)/libevent-release-2.0.22-stable/include/event2
LOCAL_MODULE    := guard
LOCAL_LDLIBS    := -ldl -llog
LOCAL_LDFLAGS   += -pie -fPIE

include $(BUILD_EXECUTABLE)
