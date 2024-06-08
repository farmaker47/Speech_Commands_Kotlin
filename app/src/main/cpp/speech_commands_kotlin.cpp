#include <jni.h>
#include <string>

// Write C++ code here.
//
// Do not forget to dynamically load the C++ library into your application.
//
// For instance,
//
// In MainActivity.java:
//    static {
//       System.loadLibrary("speech_commands_kotlin");
//    }
//
// Or, in MainActivity.kt:
//    companion object {
//      init {
//         System.loadLibrary("speech_commands_kotlin")
//      }
//    }
extern "C"
JNIEXPORT jstring JNICALL
Java_com_george_speech_MainActivityKt_getAPIKey(JNIEnv *env, jclass clazz) {
    std::string api_key = "and4E3LinXdzQ4pc3QGpCJOLgGkGeIsq";
    return env->NewStringUTF(api_key.c_str());
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_george_speech_MainActivityKt_getIVKey(JNIEnv *env, jclass clazz) {
    std::string api_key = "FXj943G5QoRsGRHm";
    return env->NewStringUTF(api_key.c_str());
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_george_speech_MainActivityKt_getSafetyAPIKey(JNIEnv *env, jclass clazz) {
    std::string api_key = "AIzaSyA-5sdA1G4wZtnsXKNDM1Txstl";
    return env->NewStringUTF(api_key.c_str());
}