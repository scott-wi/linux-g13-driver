.PHONY: all dependencies build-driver build-gui release stage install install-user rollback rollback-user status status-user hardware clean clean-build test
all dependencies build-driver build-gui release stage install install-user rollback rollback-user status status-user hardware clean clean-build test:
	$(MAKE) -C g13-driver/src $@
