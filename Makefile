.PHONY: up down logs test test-integration reindex export-model

up:
	docker compose up --build

down:
	docker compose down

logs:
	docker compose logs -f --tail=200

test:
	mvn test

test-integration:
	mvn -Dtest=IntegrationFlowTest test

reindex:
	./scripts/reindex-embeddings.sh

export-model:
	python -m venv .venv
	. .venv/bin/activate && pip install -r requirements.txt
	. .venv/bin/activate && python scripts/export-clip-onnx.py --output models/clip-vitb32.onnx
