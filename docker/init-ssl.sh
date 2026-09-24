#!/bin/bash
# =================================================================
# Let's Encrypt SSL 证书初始化脚本
# 用法: bash /root/guangying/docker/init-ssl.sh
#
# 流程：创建临时自签名证书 → 启用 SSL 配置 → 删除临时证书
#       → 申请正式 Let's Encrypt 证书 → 重载 Nginx
# =================================================================
set -e

DOMAIN="${DEPLOY_DOMAIN:-example.com}"
EMAIL="${CERT_EMAIL:-admin@example.com}"
RSA_KEY_SIZE="${RSA_KEY_SIZE:-2048}"
COMPOSE_DIR="${COMPOSE_DIR:-/root/guangying}"
CERT_PATH="/etc/letsencrypt/live/$DOMAIN"
NGINX_DIR="$COMPOSE_DIR/docker/nginx"

cd "$COMPOSE_DIR"

echo "======================================"
echo " Let's Encrypt SSL 证书初始化"
echo " 域名: $DOMAIN"
echo "======================================"
echo ""

# ---------- 检查证书是否已存在 ----------
EXISTING=$(docker exec guangying-nginx test -f "$CERT_PATH/fullchain.pem" 2>/dev/null && echo "yes" || echo "no")

if [ "$EXISTING" = "yes" ]; then
    echo "✓ 证书已存在，尝试续期..."
    docker-compose run --rm --entrypoint "certbot renew --quiet" certbot || true
    # 切换到 SSL 配置
    if [ -f "$NGINX_DIR/nginx-ssl.conf" ]; then
        cp "$NGINX_DIR/nginx-ssl.conf" "$NGINX_DIR/nginx.conf"
    fi
    docker exec guangying-nginx nginx -s reload
    echo "✓ 续期完成！"
    exit 0
fi

echo "首次申请证书，开始 6 步流程..."
echo ""

# ---------- Step 1: 创建临时证书目录 ----------
echo "[1/6] 创建证书目录..."
docker-compose run --rm --entrypoint "mkdir -p $CERT_PATH" certbot

# ---------- Step 2: 创建临时自签名证书 ----------
echo "[2/6] 创建临时自签名证书（让 Nginx 能以 SSL 模式启动）..."
docker-compose run --rm --entrypoint "\
  openssl req -x509 -nodes -newkey rsa:$RSA_KEY_SIZE -days 1 \
    -keyout '$CERT_PATH/privkey.pem' \
    -out '$CERT_PATH/fullchain.pem' \
    -subj '/CN=localhost'" certbot

# ---------- Step 3: 切换到 SSL 配置并重载 Nginx ----------
echo "[3/6] 切换到 HTTPS 模式..."
if [ -f "$NGINX_DIR/nginx-ssl.conf" ]; then
    cp "$NGINX_DIR/nginx-ssl.conf" "$NGINX_DIR/nginx.conf"
fi
# 重载 Nginx（使用临时自签名证书）
docker exec guangying-nginx nginx -s reload 2>/dev/null || docker-compose restart nginx
sleep 3

# ---------- Step 4: 删除临时证书 ----------
echo "[4/6] 删除临时证书..."
docker-compose run --rm --entrypoint "\
  rm -rf /etc/letsencrypt/live/$DOMAIN && \
  rm -rf /etc/letsencrypt/archive/$DOMAIN && \
  rm -rf /etc/letsencrypt/renewal/$DOMAIN.conf" certbot

# ---------- Step 5: 申请正式 Let's Encrypt 证书 ----------
echo "[5/6] 正在申请 Let's Encrypt 证书（可能需要 60 秒）..."
docker-compose run --rm --entrypoint "\
  certbot certonly --webroot -w /var/www/certbot \
    -d $DOMAIN \
    --email $EMAIL \
    --rsa-key-size $RSA_KEY_SIZE \
    --agree-tos \
    --no-eff-email \
    --force-renewal" certbot

# ---------- Step 6: 重载 Nginx（加载正式证书） ----------
echo "[6/6] 重载 Nginx（加载正式证书）..."
docker exec guangying-nginx nginx -s reload

echo ""
echo "======================================"
echo "✓ SSL 证书申请完成！"
echo "  访问: https://$DOMAIN"
echo "  证书 90 天后过期"
echo "  Certbot 容器每 12 小时自动检查续期"
echo "======================================"
