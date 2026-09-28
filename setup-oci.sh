#!/usr/bin/env bash
# ==============================================================================
# LifeOS — Script de Instalación Automatizada para Oracle Cloud Always Free
# Compatible con Ubuntu 22.04/24.04 LTS y Oracle Linux 8/9
# ==============================================================================

set -euo pipefail

echo "=========================================================="
echo "🚀 Iniciando despliegue de LifeOS en Oracle Cloud Always Free"
echo "=========================================================="

# 1. Detectar sistema operativo
OS=$(grep -E '^ID=' /etc/os-release | cut -d= -f2 | tr -d '"')

# 2. Configurar Firewall del Sistema Operativo (Crucial en Oracle Cloud)
echo "🔓 Configurando reglas de firewall local (puertos 80, 443 y 22)..."
if command -v iptables >/dev/null 2>&1; then
    sudo iptables -I INPUT 1 -p tcp --dport 80 -j ACCEPT || true
    sudo iptables -I INPUT 1 -p tcp --dport 443 -j ACCEPT || true
    sudo iptables -I INPUT 1 -p tcp --dport 22 -j ACCEPT || true
fi

if [ "$OS" = "ubuntu" ] || [ "$OS" = "debian" ]; then
    sudo apt-get update -y
    sudo apt-get install -y iptables-persistent netfilter-persistent curl git
    sudo netfilter-persistent save || true
elif [ "$OS" = "ol" ] || [ "$OS" = "centos" ] || [ "$OS" = "rhel" ]; then
    if command -v firewall-cmd >/dev/null 2>&1; then
        sudo firewall-cmd --zone=public --permanent --add-port=80/tcp || true
        sudo firewall-cmd --zone=public --permanent --add-port=443/tcp || true
        sudo firewall-cmd --reload || true
    fi
fi

# 3. Instalar Docker y Docker Compose si no están instalados
if ! command -v docker >/dev/null 2>&1; then
    echo "🐳 Instalando Docker..."
    curl -fsSL https://get.docker.com -o get-docker.sh
    sudo sh get-docker.sh
    sudo usermod -aG docker "$USER" || true
    rm -f get-docker.sh
    echo "✅ Docker instalado exitosamente."
else
    echo "✅ Docker ya se encuentra instalado."
fi

# 4. Asegurar servicio Docker iniciado
sudo systemctl enable docker
sudo systemctl start docker

# 5. Configurar archivo .env de producción
if [ ! -f .env ]; then
    echo "📝 Generando archivo .env con credenciales seguras..."
    cp .env.production.example .env
    
    # Generar claves secretas seguras y aleatorias
    RANDOM_SECRET=$(openssl rand -hex 32)
    RANDOM_CRON=$(openssl rand -hex 16)
    RANDOM_DB_PASS=$(openssl rand -hex 16)
    
    sed -i "s|SECRET_KEY=.*|SECRET_KEY=${RANDOM_SECRET}|" .env
    sed -i "s|CRON_SECRET=.*|CRON_SECRET=${RANDOM_CRON}|" .env
    sed -i "s|POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=${RANDOM_DB_PASS}|" .env
    
    echo "✅ Archivo .env configurado automáticamente."
fi

# 6. Levantar contenedores
echo "📦 Construyendo y levantando contenedores Docker..."
docker compose down || true
docker compose up -d --build

echo "⏳ Esperando 10 segundos a que los servicios inicien..."
sleep 10

# 7. Verificar estado de los contenedores
docker compose ps

# 8. Obtener IP pública
PUBLIC_IP=$(curl -s https://api.ipify.org || echo "TU_IP_PUBLICA")

echo ""
echo "=========================================================="
echo "🎉 ¡DESPLIEGUE COMPLETADO EXITOSAMENTE!"
echo "=========================================================="
echo "Backend API & Documentación:"
echo "👉 Health Check: http://${PUBLIC_IP}/health"
echo "👉 Swagger Docs: http://${PUBLIC_IP}/docs"
echo "👉 Redoc:         http://${PUBLIC_IP}/redoc"
echo ""
echo "Para conectar tu aplicación Android:"
echo "En app/.../NetworkModule.kt cambia BASE_URL a:"
echo "http://${PUBLIC_IP}/api/v1/"
echo "=========================================================="
