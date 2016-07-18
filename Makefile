.PHONY: all dep

all: dep
	./gradlew assembleDevelop

dep:
	git submodule update --init --recursive
	./buildNativeLibs.sh DEVELOP
	./build_ndk
	echo "android.useDeprecatedNdk=true" > gradle.properties
