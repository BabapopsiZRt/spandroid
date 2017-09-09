LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := royale
LOCAL_SRC_FILES := ../libs/libroyale.so
LOCAL_EXPORT_C_INCLUDES := \
    ../libs \
    ../libs/royale

include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE           := royaleSample
CODE_PATH              := $(LOCAL_PATH)
LOCAL_CFLAGS    :=

LOCAL_C_INCLUDES := libs

LOCAL_SRC_FILES := \
    sample.cpp

LOCAL_CFLAGS := -DTARGET_PLATFORM_ANDROID

LOCAL_LDLIBS :=  -llog -ldl -ljnigraphics

LOCAL_SHARED_LIBRARIES += royale

include $(BUILD_SHARED_LIBRARY)
