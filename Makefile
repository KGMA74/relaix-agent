# The app is built and signed here, where the release key is: see
# scripts/release.sh, which does the work (make only names it).

.PHONY: help apk release
.DEFAULT_GOAL := help

help: ## List the targets
	@echo apk      build the signed release APKs into build/release
	@echo release  publish them as GitHub release v^<versionName^>

apk: ## Build the signed release APKs into build/release
	bash scripts/release.sh apk

release: ## Publish build/release as a GitHub release
	bash scripts/release.sh publish
