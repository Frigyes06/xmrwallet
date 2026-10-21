FLAVOUR ?= monero
NPROC ?= $(shell nproc 2>/dev/null || getconf _NPROCESSORS_ONLN 2>/dev/null || echo 1)

# Fedora commonly provides Podman instead of Docker.
CONTAINER_RUNTIME ?= $(shell \
	if command -v docker >/dev/null 2>&1; then echo docker; \
	elif command -v podman >/dev/null 2>&1; then echo podman; \
	else echo docker; fi)

all: arm64-v8a armeabi-v7a x86_64 include/wallet2_api.h VERSION
debug: arm64-v8a armeabi-v7a include/wallet2_api.h VERSION

check-monero: $(FLAVOUR)
	@test -f "$(FLAVOUR)/Makefile" || { \
		echo "$(FLAVOUR) does not contain a Monero source checkout" >&2; \
		echo "Clone release-v0.18.2.2-monerujo and initialize its submodules" >&2; \
		exit 1; \
	}

arm64-v8a: check-monero android64.Dockerfile
	-rm -rf arm64-v8a
	-$(CONTAINER_RUNTIME) container rm $(FLAVOUR)-android-arm64 -f
	$(CONTAINER_RUNTIME) build -f android64.Dockerfile -t $(FLAVOUR)-android-arm64 $(FLAVOUR) --build-arg NPROC=$(NPROC)
	$(CONTAINER_RUNTIME) create -it --name $(FLAVOUR)-android-arm64 $(FLAVOUR)-android-arm64 bash
	$(CONTAINER_RUNTIME) cp $(FLAVOUR)-android-arm64:/opt/android/prefix/lib/. arm64-v8a/
	$(CONTAINER_RUNTIME) cp $(FLAVOUR)-android-arm64:/src/build/release/lib/. arm64-v8a/$(FLAVOUR)

armeabi-v7a: check-monero android32.Dockerfile
	-rm -rf armeabi-v7a
	-$(CONTAINER_RUNTIME) container rm $(FLAVOUR)-android-arm32 -f
	$(CONTAINER_RUNTIME) build -f android32.Dockerfile -t $(FLAVOUR)-android-arm32 $(FLAVOUR) --build-arg NPROC=$(NPROC)
	$(CONTAINER_RUNTIME) create -it --name $(FLAVOUR)-android-arm32 $(FLAVOUR)-android-arm32 bash
	$(CONTAINER_RUNTIME) cp $(FLAVOUR)-android-arm32:/opt/android/prefix/lib/. armeabi-v7a
	$(CONTAINER_RUNTIME) cp $(FLAVOUR)-android-arm32:/src/build/release/lib/. armeabi-v7a/$(FLAVOUR)

x86_64: check-monero android64_x86.Dockerfile
	-rm -rf x86_64
	-$(CONTAINER_RUNTIME) container rm $(FLAVOUR)-android-x86_64 -f
	$(CONTAINER_RUNTIME) build -f android64_x86.Dockerfile -t $(FLAVOUR)-android-x86_64 $(FLAVOUR) --build-arg NPROC=$(NPROC)
	$(CONTAINER_RUNTIME) create -it --name $(FLAVOUR)-android-x86_64 $(FLAVOUR)-android-x86_64 bash
	$(CONTAINER_RUNTIME) cp $(FLAVOUR)-android-x86_64:/opt/android/prefix/lib/. x86_64
	$(CONTAINER_RUNTIME) cp $(FLAVOUR)-android-x86_64:/src/build/release/lib/. x86_64/$(FLAVOUR)

x86: check-monero android32_x86.Dockerfile
	-rm -rf x86
	-$(CONTAINER_RUNTIME) container rm $(FLAVOUR)-android-x86 -f
	$(CONTAINER_RUNTIME) build -f android32_x86.Dockerfile -t $(FLAVOUR)-android-x86 $(FLAVOUR) --build-arg NPROC=$(NPROC)
	$(CONTAINER_RUNTIME) create -it --name $(FLAVOUR)-android-x86 $(FLAVOUR)-android-x86 bash
	$(CONTAINER_RUNTIME) cp $(FLAVOUR)-android-x86:/opt/android/prefix/lib/. x86
	$(CONTAINER_RUNTIME) cp $(FLAVOUR)-android-x86:/src/build/release/lib/. x86/$(FLAVOUR)

include/wallet2_api.h: $(FLAVOUR) include $(FLAVOUR)/src/wallet/api/wallet2_api.h
	cp $(FLAVOUR)/src/wallet/api/wallet2_api.h include/wallet2_api.h

include:
	mkdir include

VERSION:
	echo MONERUJO_$(FLAVOUR) `git -C . branch | grep "^\*" | sed 's/^..//'` with $(FLAVOUR) `git -C $(FLAVOUR) branch | grep "^\*" | sed 's/^..//'` > VERSION

clean:
	-rm -rf arm64-v8a
	-rm -rf armeabi-v7a
	-rm -rf x86_64
	-rm -rf x86
	-rm -rf include
	-rm -f VERSION

distclean: clean
	-$(CONTAINER_RUNTIME) container rm $(FLAVOUR)-android-arm64 -f
	-$(CONTAINER_RUNTIME) container rm $(FLAVOUR)-android-arm32 -f
	-$(CONTAINER_RUNTIME) container rm $(FLAVOUR)-android-x86_64 -f
	-$(CONTAINER_RUNTIME) container rm $(FLAVOUR)-android-x86 -f
	-$(CONTAINER_RUNTIME) image rm $(FLAVOUR)-android-arm64 -f
	-$(CONTAINER_RUNTIME) image rm $(FLAVOUR)-android-arm32 -f
	-$(CONTAINER_RUNTIME) image rm $(FLAVOUR)-android-x86_64 -f
	-$(CONTAINER_RUNTIME) image rm $(FLAVOUR)-android-x86 -f
	-rm $(FLAVOUR)

$(FLAVOUR):
	$(error Please ln -s $(FLAVOUR))

.PHONY: all debug check-monero clean distclean arm64-v8a armeabi-v7a x86_64 x86
