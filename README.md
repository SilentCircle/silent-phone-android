## Introduction

These are the sources for Silent Circle's Silent Phone for Android project.

### Overview

Silent Phone is Peer-to-peer encrypted calling and video. No keys are stored.

### What's New In This Update

The sources are updated for version 6.1 of the project.

This version of the project includes Silent Contacts as a subproject.

### Prerequisites

To build Silent Phone for Android you will need the following resources:

- Java Development Kit (JDK) 8
- the Android Native Development Kit (NDK)
- Android SDK Tools

  - Android SDK Tools           26.1.1
  - Android SDK Platform-tools  26.0.1
  - Android SDK Build-tools     26.0.0

  - SDK Platform                 26 2
  - SDK Platform                 25 3

  - Google Play Services         45
  - Android Support Repository   47 
  - Google Repository            58

### How to Build

- download the repository
- create a terminal window
- cd to the top of the repository
- edit build.sh to reference the correct locations of the prerequisites
- bash build.sh 2>&1 | tee -a build.log

The build produces silentphone.apk which can be used on an Android device.
