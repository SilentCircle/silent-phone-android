#! /bin/sh
if ndk-build; then
    echo "Android build OK"
else
    exit 1
fi
