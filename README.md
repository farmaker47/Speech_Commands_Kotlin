# Speech_Commands_Kotlin

This is a sample application to demonstrate basic cryptography of a tflite file and to check the intergity of the device before proceeding to the on device inference.

## Basic information

This app performs recognition of speech commands on mobile, highlighting the spoken word.

## Model used
Download the model from [here](https://storage.googleapis.com/download.tensorflow.org/models/tflite/conv_actions_tflite.zip). Extract the zip to get the .tflite and label file.

The percentage displayed is average command recognition over a window duration (1000ms).

More information about the training procedure can be found [here](https://www.tensorflow.org/tutorials/audio/simple_audio).

## Explore the code for cryptography

The idea behind this repository is to encrypt a tflite file and use it inside an android application. This way at a production application it is very difficult for someone to extract the apk file and get a working tflite file. The cryptography is based on [Google's basic AES encryption](https://developer.android.com/guide/topics/security/cryptography#encrypt-message) with a use of a custom 32 Byte key that is stored inside a C++ file so at compile time this is converted to 1s and 0s.

# First encrypt the file

Inside [`MainActivityViewModel.kt`](https://github.com/farmaker47/Speech_Commands_Kotlin/blob/master/app/src/main/java/com/george/speech/MainActivityViewModel.kt) class there is the helper function [`loadAndEncryptTFLiteFile()`](https://github.com/farmaker47/Speech_Commands_Kotlin/blob/master/app/src/main/java/com/george/speech/MainActivityViewModel.kt#L88) to encrypt whatever .tflite file you put inside Assets folder. The function converts the file to ByteArray with the help of [Guava](https://github.com/google/guava) library, gets the key from the [C++ file](https://github.com/farmaker47/Speech_Commands_Kotlin/blob/master/app/src/main/cpp/native-lib.cpp#L9) and encrypts the bytearray with [basic cryptography](https://developer.android.com/guide/topics/security/cryptography#encrypt-message). Later this array is converted to a file and is saved to a specific directory in the android device.

# Second move the file

Right now we have managed to encrypt the .tflite file with a simple procedure and get it from the external storage of the phone. Next we get this encrypted file and we put it inside the Assets file of the project. Of course this is a sample app where inside the [Assets folder](https://github.com/farmaker47/Speech_Commands_Kotlin/tree/master/app/src/main/assets) you will find both the original and the encrypted .tflite file. For a production application you have to erase the original file, keep only the encrypted one and comment out or erase the [helper function](https://github.com/farmaker47/Speech_Commands_Kotlin/blob/master/app/src/main/java/com/george/speech/MainActivityViewModel.kt#L88) that does the encryption.

# 
