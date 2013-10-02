#!/bin/bash

# perhaps something could be done to make this one command
git log -n 1 --pretty=format:"%cd :: %h" > build-header.txt
echo >> build-header.txt
git log -n 1 --pretty=format:"%cn" >> build-header.txt
echo >> build-header.txt
git log -n 1 --pretty=format:"%s" >> build-header.txt
echo >> build-header.txt

# Jenkins build variable - has format of timestamp
echo $BUILD_ID > build-id.txt

# add a vacuous comment
