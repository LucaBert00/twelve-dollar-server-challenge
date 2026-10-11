SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c
.DEFAULT_GOAL := help
.NOTPARALLEL:

# Anchor paths even when invoked with make -f from another directory.
SUBMISSION_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
DOCKER ?= docker
PYTHON ?= python3
COMPOSE_FILE ?= $(SUBMISSION_DIR)/compose.yaml
COMPOSE_PROJECT ?= twelve-dollar-java
COMPOSE = $(DOCKER) compose -p "$(COMPOSE_PROJECT)" -f "$(COMPOSE_FILE)"
LOCAL_PORT ?= 3000
WAIT_TIMEOUT ?= 120
BUILD_ARGS ?=
SUITE_BUILD ?= true
MAVEN ?= /opt/maven-challenge-3.9.11/bin/mvn
JAVA_HOME ?= /opt/java-challenge-25.0.1
MAVEN_ARGS ?= --batch-mode --no-transfer-progress

BENCH_PORT ?= 3001
BENCH_START_VUS ?= 2500
BENCH_MAX_VUS ?= 20000
BENCH_STEP ?= 250
BENCH_WARMUP_VUS ?= 1000
BENCH_WARMUP_SECONDS ?= 120
BENCH_PROBE_SECONDS ?= 60
BENCH_CONFIRM_SECONDS ?= 300
BENCH_CONFIRM_RUNS ?= 1
BENCH_RAMP_SECONDS ?= 60
BENCH_BUDGET_SECONDS ?= 3600
BENCH_REUSE_IMAGES ?= false
BENCH_OUTPUT ?=
BENCH_ARGS ?=
REPORT_DIR ?= $(BENCH_OUTPUT)
export LOCAL_PORT

.PHONY: help all check-tools build build-local image test test-java \
        test-suite up down logs ps health benchmark benchmark-smoke report

help:
	@printf '%s\n' \
	  'make all              Build JAR/images, run all tests, benchmark, generate reports' \
	  'make build            Build/test app in Docker and export target/server.jar' \
	  'make build-local      Build/test JAR with local Java and Maven' \
	  'make image            Build app, seed, and correctness-suite images' \
	  'make test             Run Java and fresh-database challenge tests' \
	  'make test-java        Run formatter and Java tests through the image build' \
	  'make test-suite       Run challenge suite in a disposable Compose project' \
	  'make up               Build and start regular Compose stack; wait for readiness' \
	  'make down             Stop regular stack without deleting its database' \
	  'make logs / ps / health Inspect regular stack' \
	  'make benchmark        Autonomous limit search with reports and cleanup' \
	  'make benchmark-smoke  Short container/report smoke test, not a benchmark score' \
	  'make report           Regenerate latest reports, or set REPORT_DIR=<run-directory>' \
	  '' \
	  'Overrides: LOCAL_PORT=3000 COMPOSE_PROJECT=twelve-dollar-java WAIT_TIMEOUT=120' \
	  'Benchmark: BENCH_PORT=3001 BENCH_START_VUS=2500 BENCH_MAX_VUS=20000 BENCH_STEP=250' \
	  'BENCH_WARMUP_VUS=1000 BENCH_WARMUP_SECONDS=120 BENCH_PROBE_SECONDS=60' \
	  'BENCH_CONFIRM_SECONDS=300 BENCH_CONFIRM_RUNS=1 BENCH_RAMP_SECONDS=60' \
	  'BENCH_BUDGET_SECONDS=3600 BENCH_REUSE_IMAGES=false BENCH_OUTPUT=<new-directory>' \
	  'Extra flags: BUILD_ARGS="--no-cache" BENCH_ARGS="..." MAVEN_ARGS="..."' \
	  'The full search can take an hour, plus image builds and startup. See README.md.'

# Recursive invocations keep the pipeline ordered, including with make -j.
all:
	$(MAKE) -C "$(SUBMISSION_DIR)" check-tools
	$(MAKE) -C "$(SUBMISSION_DIR)" build
	$(MAKE) -C "$(SUBMISSION_DIR)" test-suite SUITE_BUILD=false
	$(MAKE) -C "$(SUBMISSION_DIR)" benchmark BENCH_REUSE_IMAGES=true

check-tools:
	@$(DOCKER) compose version
	@$(DOCKER) info --format 'Docker host: {{.OSType}}, cgroup {{.CgroupVersion}}'
	@$(PYTHON) -c 'import sys; assert sys.version_info >= (3, 10), "Python 3.10+ is required"'

image:
	$(COMPOSE) --profile test build $(BUILD_ARGS) app seed test

build: image
	@mkdir -p "$(SUBMISSION_DIR)/target"
	@set -euo pipefail; container=$$($(DOCKER) create twelve-dollar-java-app:latest); \
	trap '$(DOCKER) rm "$$container" >/dev/null' EXIT; \
	$(DOCKER) cp "$$container:/app/target/server.jar" "$(SUBMISSION_DIR)/target/server.jar"

build-local:
	cd "$(SUBMISSION_DIR)" && JAVA_HOME="$(JAVA_HOME)" PATH="$(JAVA_HOME)/bin:$$PATH" \
	  "$(MAVEN)" $(MAVEN_ARGS) clean verify

test: test-java test-suite

test-java:
	$(COMPOSE) build $(BUILD_ARGS) app

# Never reset or reuse the regular database. Each invocation owns its resources.
test-suite:
	@set -euo pipefail; project="twelve-dollar-java-test-$$$$-$$RANDOM"; \
	compose=($(DOCKER) compose -p "$$project" -f "$(COMPOSE_FILE)"); \
	export LOCAL_PORT=0; \
	trap 'status=$$?; trap - EXIT; "$${compose[@]}" --profile test down --volumes --remove-orphans || exit 2; exit "$$status"' EXIT; \
	trap 'exit 130' INT; trap 'exit 143' TERM; trap 'exit 129' HUP; \
	if [[ "$(SUITE_BUILD)" == true ]]; then "$${compose[@]}" --profile test build $(BUILD_ARGS) app seed test; fi; \
	"$${compose[@]}" up --no-build -d --wait --wait-timeout "$(WAIT_TIMEOUT)"; \
	"$${compose[@]}" --profile test run --rm --no-deps test

up:
	$(COMPOSE) up --build -d --wait --wait-timeout "$(WAIT_TIMEOUT)"

down:
	$(COMPOSE) down

logs:
	$(COMPOSE) logs --follow app nginx seed

ps:
	$(COMPOSE) ps --all

health:
	$(COMPOSE) exec -T nginx wget -q -O - http://127.0.0.1/health

benchmark:
	cd "$(SUBMISSION_DIR)" && $(PYTHON) benchmark.py \
	  --port "$(BENCH_PORT)" --start-vus "$(BENCH_START_VUS)" \
	  --max-vus "$(BENCH_MAX_VUS)" --step "$(BENCH_STEP)" \
	  --warmup-vus "$(BENCH_WARMUP_VUS)" --warmup-seconds "$(BENCH_WARMUP_SECONDS)" \
	  --probe-seconds "$(BENCH_PROBE_SECONDS)" --confirm-seconds "$(BENCH_CONFIRM_SECONDS)" \
	  --confirm-runs "$(BENCH_CONFIRM_RUNS)" --ramp-seconds "$(BENCH_RAMP_SECONDS)" \
	  --budget-seconds "$(BENCH_BUDGET_SECONDS)" \
	  $(if $(filter true,$(BENCH_REUSE_IMAGES)),--reuse-images) \
	  $(if $(BENCH_OUTPUT),--output "$(BENCH_OUTPUT)") $(BENCH_ARGS)

benchmark-smoke:
	$(MAKE) -C "$(SUBMISSION_DIR)" benchmark BENCH_WARMUP_VUS=50 \
	  BENCH_START_VUS=50 BENCH_MAX_VUS=100 BENCH_STEP=50 BENCH_WARMUP_SECONDS=8 \
	  BENCH_PROBE_SECONDS=8 BENCH_CONFIRM_SECONDS=10 BENCH_RAMP_SECONDS=1 BENCH_BUDGET_SECONDS=400

report:
	cd "$(SUBMISSION_DIR)" && $(PYTHON) benchmark_report.py $(if $(REPORT_DIR),"$(REPORT_DIR)")
