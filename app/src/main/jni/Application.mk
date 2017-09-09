NDK_TOOLCHAIN_VERSION=4.8
APP_ABI := arm64-v8a
APP_PLATFORM := android-13
#APP_STL := stlport_static
APP_STL := gnustl_static
APP_OPTM := release
APP_CPPFLAGS += -std=c++11 -fexceptions -frtti
