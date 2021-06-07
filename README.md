# Speech_Commands_Kotlin

This is a sample application to demonstrate basic cryptography of a tflite file and to check the intergity of the device before proceeding to the on device inference.

## Basic information

This app performs recognition of speech commands on mobile, highlighting the spoken word.

## Model used
Download the model from [here](https://storage.googleapis.com/download.tensorflow.org/models/tflite/conv_actions_tflite.zip). Extract the zip to get the .tflite and label file.

The percentage displayed is average command recognition over a window duration (1000ms).

More information about the training procedure can be found [here](https://www.tensorflow.org/tutorials/audio/simple_audio).

## Explore the code for cryptography

The idea behind this repository is to encrypt a tflite file and use it inside an android application. This way at a production application it is very difficult for someone to extract the apk file and get a working tflite file. The cryptography is based on [Google's basic AES encryption](https://developer.android.com/guide/topics/security/cryptography#encrypt-message) with a use of a custom 32 Byte key that is stored inside a C++ file so at compile time this is converted to 1s and 0s. To create a C++ file inside an Android Studio project follow [this](https://blog.mindorks.com/securing-api-keys-using-android-ndk) detailed guide for existing or new projects.

### First encrypt the file:

Inside [`MainActivityViewModel.kt`](https://github.com/farmaker47/Speech_Commands_Kotlin/blob/master/app/src/main/java/com/george/speech/MainActivityViewModel.kt) class there is the helper function [`loadAndEncryptTFLiteFile()`](https://github.com/farmaker47/Speech_Commands_Kotlin/blob/master/app/src/main/java/com/george/speech/MainActivityViewModel.kt#L88) to encrypt whatever .tflite file you put inside Assets folder. The function converts the file to ByteArray with the help of [Guava](https://github.com/google/guava) library, gets the key from the [C++ file](https://github.com/farmaker47/Speech_Commands_Kotlin/blob/master/app/src/main/cpp/native-lib.cpp#L9) and encrypts the bytearray with [basic cryptography](https://developer.android.com/guide/topics/security/cryptography#encrypt-message). Later this array is converted to a file and is saved to a specific directory in the android device.

### Second move the file:

Right now we have managed to encrypt the .tflite file with a simple procedure and get it from the external storage of the phone. Next we get this encrypted file and we put it inside the Assets file of the project. Of course this is a sample app where inside the [Assets folder](https://github.com/farmaker47/Speech_Commands_Kotlin/tree/master/app/src/main/assets) you will find both the original and the encrypted .tflite file. For a production application you have to erase the original file, keep only the encrypted one and comment out or erase the [helper function](https://github.com/farmaker47/Speech_Commands_Kotlin/blob/master/app/src/main/java/com/george/speech/MainActivityViewModel.kt#L88) that does the encryption.

### Third use the encrypted file:

Like the function for encryption we have one for decryption. This [function](https://github.com/farmaker47/Speech_Commands_Kotlin/blob/master/app/src/main/java/com/george/speech/MainActivityViewModel.kt#L248) loads the encrypted .tflite file, does the decryption and returns a ByteBuffer from the decoded ByteArray. Later the TensorFlow Lite Interpreter [loads the ByteBuffer](https://github.com/farmaker47/Speech_Commands_Kotlin/blob/master/app/src/main/java/com/george/speech/MainActivityViewModel.kt#L181) as stated in the [documentation](https://www.tensorflow.org/lite/api_docs/java/org/tensorflow/lite/Interpreter#public-interpreter-bytebuffer-bytebuffer).

And that way we have a working application that uses an encrypted .tflite file!

## Check the integrity of the device

Inside the application it is demonstrated how you can check the integrity of the device. There is an offline way to check if the device is rooted and two online procedures to check the overall integrity of the device. You can use whatever method suits your needs and it is not mandatory to use all of them.

### 1. Check rooted device with RootBeer

This handy library does the below root checks:

Java checks

- checkRootManagementApps
- checkPotentiallyDangerousApps
- checkRootCloakingApps
- checkTestKeys
- checkForDangerousProps
- checkForBusyBoxBinary
- checkForSuBinary
- checkSuExists
- checkForRWSystem

Native checks

- checkForSuBinary

You can find the usage of this library at [`MainActivity.kt`](https://github.com/farmaker47/Speech_Commands_Kotlin/blob/master/app/src/main/java/com/george/speech/MainActivity.kt#L102) on line 102. If the library finds that the device is rooted then it finishes the application.

For a root check you can use also a [custom method](http://www.codeplayon.com/2020/07/android-how-to-check-phone-rooted-or-not/) that searches for several folders inside the device's internal storage.

### 2. Check the integrity with SafetyNet API

The SafetyNet Attestation API is an anti-abuse API that allows app developers to assess the Android device their app is running on. The API should be used as a part of your abuse detection system to help determine whether your servers are interacting with your genuine app running on a genuine Android device. You can learn more information at [this](https://developer.android.com/training/safetynet/attestation) web page. At [`MainActivity.kt`](https://github.com/farmaker47/Speech_Commands_Kotlin/blob/master/app/src/main/java/com/george/speech/MainActivity.kt#L292) class line 292 you can see the implementation of calling this API. The result is a cryptographically-signed attestation that you have to send to your servers for further processing. Check complete workflow [here](https://developer.android.com/training/safetynet/attestation#overview). Based on the result the application closes or continues. 

### 3. Check and get a final response with SafetyNet Helper library

SafetyNet Helper wraps the Google Play Services SafetyNet.API and verifies Safety Net API response with the [Android Device Verification API](https://developer.android.com/google/play/safetynet/start.html#verify-compat-check). It features:

- Calls Google play services Safety Net test
- Local verification of request
- Verifies Safety Net API response with the Android Device Verification API (over SSL pinned connection)

It requires:

- Google Play services (specifically the SafetyNet API 'com.google.android.gms:play-services-safetynet:17.0.0')
- Requires Internet permission
- Google API key for the Android Device Verification API

More info at the [github page](https://github.com/scottyab/safetynethelper).






