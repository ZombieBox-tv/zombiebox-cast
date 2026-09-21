
.PHONY: format format-check
format:
	python3 scripts/format.py
format-check:
	python3 scripts/format.py --check

.PHONY: deps deps-check build check test
deps:
	python3 scripts/dependencies.py fetch zombiebox-protocol
deps-check:
	python3 scripts/dependencies.py check zombiebox-protocol
build:
	bash scripts/android.sh assembleDebug lintDebug
test:
	bash scripts/android.sh testDebugUnitTest
check: build test

.PHONY: architecture-check
architecture-check:
	python3 scripts/check-architecture.py
