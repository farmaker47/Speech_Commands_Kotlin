# Speech_Commands_Kotlin

This is a sample application to demonstrate basic cryptography of a tflite file and check of the intergity of the device before proceeding to the on device inference.

## Basic information

This app performs recognition of speech commands on mobile, highlighting the spoken word.

## Model used
Download the model from [here](https://storage.googleapis.com/download.tensorflow.org/models/tflite/conv_actions_tflite.zip). Extract the zip to get the .tflite and label file.

The percentage displayed is average command recognition over a window duration (1000ms).

More information about the training procedure can be found [here](https://www.tensorflow.org/tutorials/audio/simple_audio).

## Explore the code for cryptography

The idea behind this repository is to encrypt a tflite file and use it inside an android studio project. This way at a production application it is very difficult for someone to extract the apk file and get a working tflite file. The cryptography is based on [Google's basic AES encryption](https://developer.android.com/guide/topics/security/cryptography#encrypt-message) with a use of a custom 32 Byte key that is stored inside a C++ file so at compile time this is converted to 1s and 0s.
