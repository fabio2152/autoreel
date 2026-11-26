#!/usr/bin/env bash
# Publica AutoReel en la instancia EC2. Se ejecuta desde Git Bash en la PC de desarrollo, en cualquier carpeta:
#
#   bash deploy/publicar.sh <ip-o-dns-de-la-instancia> <ruta-de-la-clave-ssh.pem>
#
# 1. Construye la imagen (linux/amd64) con backend/Dockerfile: compila el frontend y el backend dentro de Docker.
# 2. Copia a /opt/autoreel la imagen comprimida y docker-compose.yml. Nunca copia un archivo de variables:
#    autoreel.env se crea una vez en el servidor a partir de deploy/autoreel.env.example.
# 3. En el servidor: carga la imagen (la anterior queda como autoreel-backend:anterior), levanta el contenedor y
#    espera a que /actuator/health responda.
#
# Requisitos: Docker Desktop en marcha y la instancia preparada con deploy/preparar-servidor.sh.
# Variables opcionales: USUARIO_SSH (por defecto ubuntu). Se puede repetir: cada vez reemplaza la versión.
set -euo pipefail

if [ $# -ne 2 ]; then
    echo "Uso: bash deploy/publicar.sh <ip-o-dns-de-la-instancia> <ruta-de-la-clave-ssh.pem>" >&2
    exit 2
fi

SERVIDOR="${USUARIO_SSH:-ubuntu}@$1"
CLAVE="$2"
IMAGEN="autoreel-backend:latest"
DESTINO="/opt/autoreel"

if [ ! -f "$CLAVE" ]; then
    echo "No existe la clave SSH: $CLAVE" >&2
    exit 2
fi

# La raíz del repositorio, entre comillas en todo el script: la ruta tiene espacios y una "ñ". Se trabaja con rutas
# relativas a ella para que Git Bash no tenga que convertir rutas al llamar a docker (un programa de Windows).
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

TEMPORAL="$(mktemp -d)"
trap 'rm -rf "$TEMPORAL"' EXIT

SSH_OPCIONES=(-i "$CLAVE" -o StrictHostKeyChecking=accept-new -o ServerAliveInterval=30 -o ConnectTimeout=15)

# Los comandos remotos se arman aquí a propósito: $DESTINO se expande en la PC.
# shellcheck disable=SC2029
remoto() {
    ssh "${SSH_OPCIONES[@]}" "$SERVIDOR" "$@"
}

paso() {
    echo
    echo "==> $*"
}

paso "Comprobando el servidor $SERVIDOR"
remoto "test -d $DESTINO || { echo 'Falta $DESTINO: ejecuta antes deploy/preparar-servidor.sh' >&2; exit 1; }
    test -f $DESTINO/autoreel.env || { echo 'Falta $DESTINO/autoreel.env: créalo a partir de deploy/autoreel.env.example' >&2; exit 1; }
    docker compose version >/dev/null"

paso "Construyendo la imagen $IMAGEN (linux/amd64, con el frontend)"
docker build --platform linux/amd64 --provenance=false --sbom=false -f backend/Dockerfile -t "$IMAGEN" .

paso "Comprimiendo la imagen"
docker save "$IMAGEN" | gzip -1 > "$TEMPORAL/autoreel-backend.tar.gz"
du -h "$TEMPORAL/autoreel-backend.tar.gz"

paso "Copiando la imagen y docker-compose.yml a $DESTINO"
scp "${SSH_OPCIONES[@]}" "$TEMPORAL/autoreel-backend.tar.gz" deploy/docker-compose.yml "$SERVIDOR:$DESTINO/"

paso "Cargando la imagen y levantando el contenedor"
remoto "bash -s" <<'REMOTO'
set -euo pipefail
cd /opt/autoreel

# La versión que está corriendo queda como "anterior" para poder volver atrás.
if docker image inspect autoreel-backend:latest >/dev/null 2>&1; then
    docker image tag autoreel-backend:latest autoreel-backend:anterior
fi
gunzip -c autoreel-backend.tar.gz | docker load
rm -f autoreel-backend.tar.gz

docker compose up -d --pull never --remove-orphans

echo "Esperando a que el backend responda en /actuator/health (hasta 5 minutos)..."
for _ in $(seq 1 60); do
    if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then
        echo "Backend en marcha."
        docker image prune -f >/dev/null
        docker compose ps
        exit 0
    fi
    sleep 5
done

echo "El backend no respondió a tiempo. Últimas líneas del registro:" >&2
docker compose logs --tail 80 backend >&2
exit 1
REMOTO

paso "Listo. Revisa https://autoreel.sapps.lat (el balanceador tarda unos segundos en ver el destino sano)."
