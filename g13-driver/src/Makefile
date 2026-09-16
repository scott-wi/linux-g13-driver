# Build first; installation consumes an existing release and never compiles.
ROOT := $(abspath ../..)
RELEASE ?= $(ROOT)/dist/latest
DESTDIR ?=
DEPLOY_FLAGS ?=

.PHONY: all dependencies build-driver build-gui release stage install install-user rollback rollback-user status status-user hardware clean clean-build test

all: release

dependencies:
	bash scripts/install_deps.sh

build-driver:
	cmake -S . -B ../build
	cmake --build ../build

build-gui:
	cd ../../g13-config-tool && mvn -B -ntp package -DskipTests

release: build-driver build-gui
	bash scripts/release.sh

# Compatibility alias for the former staging target.
stage: release

install:
	bash "$(RELEASE)/install.sh" install --scope system $(if $(DESTDIR),--destdir "$(DESTDIR)") $(DEPLOY_FLAGS)

install-user:
	bash "$(RELEASE)/install.sh" install --scope user $(if $(DESTDIR),--destdir "$(DESTDIR)") $(DEPLOY_FLAGS)

rollback:
	bash "$(RELEASE)/install.sh" rollback --scope system $(if $(DESTDIR),--destdir "$(DESTDIR)") $(DEPLOY_FLAGS)

rollback-user:
	bash "$(RELEASE)/install.sh" rollback --scope user $(if $(DESTDIR),--destdir "$(DESTDIR)") $(DEPLOY_FLAGS)

status:
	bash "$(RELEASE)/install.sh" status --scope system $(if $(DESTDIR),--destdir "$(DESTDIR)")

status-user:
	bash "$(RELEASE)/install.sh" status --scope user $(if $(DESTDIR),--destdir "$(DESTDIR)")

hardware:
	bash "$(RELEASE)/install.sh" hardware $(if $(DESTDIR),--destdir "$(DESTDIR)")

test:
	bash tests/test_deploy.sh
	bash tests/test_preview.sh

clean-build:
	$(RM) -r ../build

clean: clean-build
	cd ../../g13-config-tool && mvn -B -ntp clean
# Release archives are deliberately retained by clean.
