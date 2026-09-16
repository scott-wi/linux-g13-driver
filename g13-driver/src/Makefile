# Build first; installation consumes an existing release and never compiles.
ROOT := $(abspath ../..)
PYTHON ?= python3
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
	$(PYTHON) scripts/release.py

# Compatibility alias for the former staging target.
stage: release

install:
	$(PYTHON) "$(RELEASE)/install.py" install --scope system $(if $(DESTDIR),--destdir "$(DESTDIR)") $(DEPLOY_FLAGS)

install-user:
	$(PYTHON) "$(RELEASE)/install.py" install --scope user $(if $(DESTDIR),--destdir "$(DESTDIR)") $(DEPLOY_FLAGS)

rollback:
	$(PYTHON) "$(RELEASE)/install.py" rollback --scope system $(if $(DESTDIR),--destdir "$(DESTDIR)") $(DEPLOY_FLAGS)

rollback-user:
	$(PYTHON) "$(RELEASE)/install.py" rollback --scope user $(if $(DESTDIR),--destdir "$(DESTDIR)") $(DEPLOY_FLAGS)

status:
	$(PYTHON) "$(RELEASE)/install.py" status --scope system $(if $(DESTDIR),--destdir "$(DESTDIR)")

status-user:
	$(PYTHON) "$(RELEASE)/install.py" status --scope user $(if $(DESTDIR),--destdir "$(DESTDIR)")

hardware:
	$(PYTHON) "$(RELEASE)/install.py" hardware $(if $(DESTDIR),--destdir "$(DESTDIR)")

test:
	$(PYTHON) -m unittest discover -s tests -v

clean-build:
	$(RM) -r ../build

clean: clean-build
	cd ../../g13-config-tool && mvn -B -ntp clean
# Release archives are deliberately retained by clean.
