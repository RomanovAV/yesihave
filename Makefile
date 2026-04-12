.PHONY: up down logs ps infra-up infra-down test test-integration reindex export-model
COMPOSE := docker compose

up:
	$(COMPOSE) up --build

down:
	$(COMPOSE) down

logs:
	$(COMPOSE) logs -f --tail=200

ps:
	$(COMPOSE) ps

infra-up:
	$(COMPOSE) up -d postgres minio minio-init

infra-down:
	$(COMPOSE) stop postgres minio

test:
	mvn test

test-integration:
	mvn -Dtest=IntegrationFlowTest test

reindex:
	./scripts/reindex-embeddings.sh

export-model:
	python3 -m venv .venv
	. .venv/bin/activate && pip install -r requirements.txt
	. .venv/bin/activate && python scripts/export-clip-onnx.py --output models/clip-vitb32.onnx
