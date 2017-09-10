#include <royale/CameraManager.hpp>
#include <royale/ICameraDevice.hpp>
#include <iostream>
#include <jni.h>
#include <android/log.h>
#include <thread>
#include <chrono>

#ifdef __cplusplus
extern "C"
{
#endif

#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, "ROYALE_NDK", __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, "ROYALE_NDK", __VA_ARGS__))

using namespace royale;
using namespace std;

JavaVM *m_vm;
jmethodID m_amplitudeCallbackID;
jobject m_obj;

uint16_t width, height;

// this represents the main camera device object
static std::unique_ptr<ICameraDevice> cameraDevice;

const float DEFAULT_MIN_DISTANCE = 10;
int SEGMENT_MIN_STRENGTH = 300;
float MAX_DIST_TO_STRENGTHEN = 0.1; // 5 centimeters
const int SEGMENT_COUNT = 6;
const int MAX_RETRY_COUNT = 10;
const int SEGMENT_BAD_DISTANCE = 1100;

class MyListener : public IDepthDataListener
{
    void onNewData (const DepthData *data)
    {
        /* Demonstration of how to retrieve exposureTimes
        * There might be different ExposureTimes per RawFrameSet resulting in a vector of
        * exposureTimes, while however the last one is fixed and purely provided for further
        * reference. */

        auto sampleVector (data->exposureTimes);

        if (sampleVector.size() > 2)
        {
            LOGI ("ExposureTimes: %d, %d, %d", sampleVector.at (0), sampleVector.at (1), sampleVector.at (2));
        }

        int i;
        // Determine min and max value and calculate span
        float max = 0;
        float min = 65535;
        for (i = 0; i < width * height; i++)
        {
            if (data->points.at (i).z < min)
            {
                min = data->points.at (i).z;
            }
            if (data->points.at (i).z > max)
            {
                max = data->points.at (i).z;
            }
        }

        float span = max - min;

        // Prevent division by zero
        if (!span)
        {
            span = 1;
        }

        // fill a temp structure to use to populate the java int array
        jint fill[width * height];
        jfloat rawFill[width * height];

        for (i = 0; i < width * height; i++)
        {
            // use min value and span to have values between 0 and 255 (for visualisation)
            fill[i] = (int) (((data->points.at (i).z - min) / span) * 255.0f);
            rawFill[i] = data->points.at (i).z;

            // set same value for red, green and blue; alpha to 255; to create gray image
            if (fill[i] > 255 || fill[i] < 0 || fill[i] == 0) {
                fill[i] = 0 | 0 << 8 | 0 << 16 | 255 << 24;
            } else {
                fill[i] = 0 | (255 - fill[i]) << 8 | fill[i] << 16 | 255 << 24;
            }
        }

        // filter stray pixels
        for (i = 1; i < width * height - 1; i++) {
            if (rawFill[i] != 0.0f && rawFill[i-1] <= 0.0 && rawFill[i+1] <= 0.0) {
                fill[i] = 255 | 0 << 8 | 0 << 16 | 255 << 24;
                rawFill[i] = 0;
            }
        } 

        jfloat segmentCloseness[SEGMENT_COUNT];
        jfloat segmentMins[SEGMENT_COUNT];
        for (i = 0; i < SEGMENT_COUNT; i++) {
            segmentMins[i] = DEFAULT_MIN_DISTANCE;
        }
        jint segmentMinStrengths[SEGMENT_COUNT];
        for (i = 0; i < SEGMENT_COUNT; i++) {
            segmentMinStrengths[i] = 0;
        }

        float segmentWidth = width / 6;
        int badDataCount = 0;
        int segmentRetryCount = 0;
        bool gotIt = false;

        while (!gotIt && segmentRetryCount < MAX_RETRY_COUNT){
            for (i = 0; i < width * height; i++)
            {
                int segmentIndex = (int) ((i % width) / segmentWidth);
                if (segmentMinStrengths[segmentIndex] > SEGMENT_MIN_STRENGTH) {
                    continue; // we already have this segment down, YEAH!!
                }
                if (rawFill[i] <= 0) {
                    badDataCount++;
                    continue; // skip bad data
                }
                if (rawFill[i] == segmentMins[segmentIndex]) {
                    rawFill[i] = 0; // mark not strong enough minimum as bad, delete! :(
                }
                if (rawFill[i] < segmentMins[segmentIndex]) {
                    segmentMins[segmentIndex] = rawFill[i];
                }
            }

            for (i = 0; i < width * height; i++)
            {
                if (rawFill[i] == 0) {
                    continue; // skip bad data
                }
                int segmentIndex = (int) ((i % width) / segmentWidth);
                if (rawFill[i] - MAX_DIST_TO_STRENGTHEN < segmentMins[segmentIndex]) {
                    segmentMinStrengths[segmentIndex] += 1;
                }
            }

            int okCount = 0;
            for (i = 0; i < SEGMENT_COUNT; i++) {
                if (segmentMinStrengths[i] > SEGMENT_MIN_STRENGTH) {
                    okCount++;
                }
            }
            gotIt = okCount == SEGMENT_COUNT;
            segmentRetryCount++;
        }

        jint segmentMinCentis[SEGMENT_COUNT];
        for (i = 0; i < SEGMENT_COUNT; i++) {
            if (segmentMinStrengths[i] < SEGMENT_MIN_STRENGTH) {
                segmentMinCentis[i] = SEGMENT_BAD_DISTANCE;
            } else {
                segmentMinCentis[i] = (int) (segmentMins[i] * 100);
            }
        }

        // attach to the JavaVM thread and get a JNI interface pointer
        JNIEnv *env;
        m_vm->AttachCurrentThread ( (JNIEnv **) &env, NULL);

        // create java int array
        jintArray intArray = env->NewIntArray (width * height);
        jfloatArray rawFloatArray = env->NewFloatArray (width * height);
        jintArray minDistanceIntArray = env->NewIntArray (6);
        jintArray minDistanceStrengthIntArray = env->NewIntArray (6);

        // populate java int array with fill data
        env->SetIntArrayRegion (intArray, 0, width * height, fill);
        env->SetFloatArrayRegion (rawFloatArray, 0, width * height, rawFill);
        env->SetIntArrayRegion (minDistanceIntArray, 0, 6, segmentMinCentis);
        env->SetIntArrayRegion (minDistanceStrengthIntArray, 0, 6, segmentMinStrengths);

        // call java method and pass amplitude array
        env->CallVoidMethod (m_obj, m_amplitudeCallbackID, intArray, rawFloatArray, minDistanceIntArray, minDistanceStrengthIntArray, min, max, badDataCount, segmentRetryCount);

        // detach from the JavaVM thread
        m_vm->DetachCurrentThread();
    }
};

MyListener listener;

royale::Vector<royale::String> opModes;

jintArray Java_com_royale_royaleandroidexample_MainActivity_OpenCameraNative (JNIEnv *env, jobject thiz, jint fd, jint vid, jint pid, jint mode)
{
    // the camera manager will query for a connected camera
    {
        CameraManager manager;

        auto camlist = manager.getConnectedCameraList (fd, vid, pid);
        LOGI ("Detected %zu camera(s).", camlist.size());

        if (!camlist.empty())
        {
            cameraDevice = manager.createCamera (camlist.at (0));
        }
    }
    // the camera device is now available and CameraManager can be deallocated here

    if (cameraDevice == nullptr)
    {
        LOGI ("Cannot create the camera device");
        jintArray intArray();
    }

    // IMPORTANT: call the initialize method before working with the camera device
    CameraStatus ret = cameraDevice->initialize();
    if (ret != CameraStatus::SUCCESS)
    {
        LOGI ("Cannot initialize the camera device, CODE %d", (int) ret);
    }

    royale::String cameraName;
    royale::String cameraId;

    ret = cameraDevice->getUseCases (opModes);
    if (ret != CameraStatus::SUCCESS)
    {
        LOGI ("Failed to get use cases, CODE %d", (int) ret);
    }

    ret = cameraDevice->getMaxSensorWidth (width);
    if (ret != CameraStatus::SUCCESS)
    {
        LOGI ("Failed to get max sensor width, CODE %d", (int) ret);
    }

    ret = cameraDevice->getMaxSensorHeight (height);
    if (ret != CameraStatus::SUCCESS)
    {
        LOGI ("Failed to get max sensor height, CODE %d", (int) ret);
    }

    ret = cameraDevice->getId (cameraId);
    if (ret != CameraStatus::SUCCESS)
    {
        LOGI ("Failed to get camera ID, CODE %d", (int) ret);
    }

    ret = cameraDevice->getCameraName (cameraName);
    if (ret != CameraStatus::SUCCESS)
    {
        LOGI ("Failed to get camera name, CODE %d", (int) ret);
    }

    // display some information about the connected camera
    LOGI ("====================================");
    LOGI ("        Camera information");
    LOGI ("====================================");
    LOGI ("Id:              %s", cameraId.c_str());
    LOGI ("Type:            %s", cameraName.c_str());
    LOGI ("Width:           %d", width);
    LOGI ("Height:          %d", height);
    LOGI ("Operation modes: %zu", opModes.size());

    for (int i = 0; i < opModes.size(); i++)
    {
        LOGI ("    %s", opModes.at (i).c_str());
    }

    // register a data listener
    ret = cameraDevice->registerDataListener (&listener);
    if (ret != CameraStatus::SUCCESS)
    {
        LOGI ("Failed to register data listener, CODE %d", (int) ret);
    }

    // set an operation mode
    ret = cameraDevice->setUseCase (opModes[mode]);
    if (ret != CameraStatus::SUCCESS)
    {
        LOGI ("Failed to set use case, CODE %d", (int) ret);
    }

    cameraDevice->setExposureMode(royale::ExposureMode::AUTOMATIC);

    ret = cameraDevice->startCapture();
    if (ret != CameraStatus::SUCCESS)
    {
        LOGI ("Failed to start capture, CODE %d", (int) ret);
    }

    jint fill[2];
    fill[0] = width;
    fill[1] = height;

    jintArray intArray = env->NewIntArray (2);

    env->SetIntArrayRegion (intArray, 0, 2, fill);

    return intArray;
}

void Java_com_royale_royaleandroidexample_MainActivity_setCameraParams (JNIEnv *env, jobject thiz, jint tresholdCm, jint minStrength) {
    MAX_DIST_TO_STRENGTHEN = tresholdCm / 100.0f;
    SEGMENT_MIN_STRENGTH = minStrength;
}

void Java_com_royale_royaleandroidexample_MainActivity_RegisterCallback (JNIEnv *env, jobject thiz)
{
    // save JavaVM globally; needed later to call Java method in the listener
    env->GetJavaVM (&m_vm);

    m_obj = env->NewGlobalRef (thiz);

    // save refs for callback
    jclass g_class = env->GetObjectClass (m_obj);
    if (g_class == NULL)
    {
        std::cout << "Failed to find class" << std::endl;
    }

    // save method ID to call the method later in the listener
    m_amplitudeCallbackID = env->GetMethodID (g_class, "amplitudeCallback", "([I[F[I[IFFII)V");
}

void Java_com_royale_royaleandroidexample_MainActivity_CloseCameraNative (JNIEnv *env, jobject thiz)
{
    cameraDevice->stopCapture();
}

#ifdef __cplusplus
}
#endif
