## Introduction

These are the sources for Silent Circle's Silent Phone for Android project.

### Overview

Silent Phone is Peer-to-peer encrypted calling and video. No keys are stored.

### What's New In This Update

The sources are updated for version 4.3.1 of the project.

This version of the project includes Silent Contacts as a subproject.

### Prerequisites

To build Silent Phone for Android you will need the following resources:

- Java Development Kit (JDK) 7
- the Android Native Development Kit (NDK)
- the stand-alone Android SDK Tools

Using the Android SDK the following command will install the required additional packages:

```
$ android  update sdk --all --no-ui --filter tools,platform-tools,build-tools-22.0.1,\
  android-22,extra-android-m2repository,extra-android-support,extra-google-m2repository
```

### How to Build

- download the repository
- create a terminal window
- cd to the top of the repository
- edit build.sh to reference the correct locations of the prerequisites
- bash build.sh 2>&1 | tee -a build.log

The build produces silentphone.apk which can be used on an Android device.
