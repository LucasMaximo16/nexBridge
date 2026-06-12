#!/bin/bash
set -euo pipefail

echo "=== NexBridge Mini DC — Instalação ==="

# Verifica dependências
command -v docker >/dev/null 2>&1 || { echo "Docker não encontrado. Instale Docker primeiro."; exit 1; }
command -v docker-compose >/dev/null 2>&1 || command -v "docker compose" >/dev/null 2>&1 || { echo "Docker Compose não encontrado."; exit 1; }

# Cria estrutura de diretórios
mkdir -p certs data/vault logs

# Cria .env se não existir
if [ ! -f .env ]; then
  cp .env.example .env
  echo ".env criado a partir de .env.example — configure as variáveis antes de iniciar."
fi

# Build do projeto
echo "Compilando NexBridge..."
./mvnw clean package -DskipTests -q

# Inicia os serviços
echo "Iniciando serviços..."
docker-compose up -d

echo ""
echo "=== NexBridge iniciado ==="
echo "API: http://localhost:8080/health"
echo "Docs: http://localhost:8080/swagger-ui.html"
