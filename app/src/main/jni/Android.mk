LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := asmlibrary
LOCAL_SRC_FILES := native/libs/$(TARGET_ARCH_ABI)/libasmlibrary.so
include $(PREBUILT_SHARED_LIBRARY) 

include $(CLEAR_VARS)
LOCAL_MODULE := opencv_java
LOCAL_SRC_FILES := native/libs/$(TARGET_ARCH_ABI)/libopencv_java.so
include $(PREBUILT_SHARED_LIBRARY) 

include $(CLEAR_VARS)

#OPENCV_CAMERA_MODULES:=off
#OPENCV_INSTALL_MODULES:=off
#OPENCV_LIB_TYPE:=SHARED
include native/jni/OpenCV.mk

LOCAL_SRC_FILES  := DemoFit.cpp AAM_IC.cpp AAM_PDM.cpp AAM_Shape.cpp AAM_TDM.cpp AAM_PAW.cpp AAM_Util.cpp
LOCAL_C_INCLUDES += $(LOCAL_PATH)
LOCAL_CFLAGS    += -DOPENCV_OLDER_VISION
LOCAL_LDLIBS     += -llog -ldl  

LOCAL_MODULE     := jni-asmlibrary

LOCAL_SHARED_LIBRARIES := asmlibrary

include $(BUILD_SHARED_LIBRARY)
