.PHONY: all dep clean

all: dep
	./gradlew assembleDevelop

dep:
	git submodule update --init --recursive
	./buildNativeLibs.sh DEVELOP
	./build_ndk
	echo "android.useDeprecatedNdk=true" > gradle.properties

clean:
	git clean -fdx
	git submodule deinit -f .
