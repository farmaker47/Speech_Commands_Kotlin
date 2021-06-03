#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_george_speech_MainActivityKt_getAPIKey(JNIEnv *env, jclass clazz) {
    std::string api_key = "E1N2C3R4Y5P6T7";
    return env->NewStringUTF(api_key.c_str());
}