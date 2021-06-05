#include <jni.h>
#include <string>


//AES allows 128, 192 and 256 bit of key length. In other words 16, 24 or 32 byte
//Generate keys: http://www.unit-conversion.info/texttools/random-string-generator/
extern "C"
JNIEXPORT jstring JNICALL
Java_com_george_speech_MainActivityKt_getAPIKey(JNIEnv *env, jclass clazz) {
    std::string api_key = "and4E3LinXdzQ4pc3QGpCJOLgGkGeIsq";
    return env->NewStringUTF(api_key.c_str());
}

//expected IV length of 16
extern "C"
JNIEXPORT jstring JNICALL
Java_com_george_speech_MainActivityKt_getIVKey(JNIEnv *env, jclass clazz) {
    std::string api_key = "FXj943G5QoRsGRHm";
    return env->NewStringUTF(api_key.c_str());
}