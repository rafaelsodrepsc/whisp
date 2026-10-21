#!/usr/bin/env bash
# Smoke test de deploy. Roda contra uma stack já no ar (nginx na frente).
# Uso: scripts/smoke-test.sh [BASE_URL]        (padrão http://localhost)
# Variáveis: EXPECT_SECURE_COOKIE=true|false (padrão true), CHECK_CLOSED_PORTS=true|false (padrão true)
set -u

BASE="${1:-http://localhost}"
EXPECT_SECURE_COOKIE="${EXPECT_SECURE_COOKIE:-true}"
CHECK_CLOSED_PORTS="${CHECK_CLOSED_PORTS:-true}"
HERE="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

FAILS=0
check() { # check "descrição" comando...
  local name="$1"; shift
  if "$@"; then echo "PASS  $name"; else echo "FAIL  $name"; FAILS=$((FAILS + 1)); fi
}
status() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
json_field() { sed -n "s/.*\"$1\":\"\\([^\"]*\\)\".*/\\1/p"; }
cookie_value() { grep -i '^set-cookie: refresh_token=' "$1" | head -1 | sed 's/^[^=]*=\([^;]*\);.*/\1/' | tr -d '\r'; }

STAMP="$(date +%s)$RANDOM"
EMAIL="smoke$STAMP@example.com"
PASS="Smoke-$STAMP-pass"

login() { # grava headers em $TMP/login.h e corpo em $TMP/login.b
  curl -s -D "$TMP/login.h" -o "$TMP/login.b" -X POST "$BASE/auth/login" \
    -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}"
}

echo "== SPA e roteamento ($BASE)"
check "SPA é servido na raiz" bash -c "curl -s '$BASE/' | grep -q '<app-root'"
check "/actuator/env não vaza configuração" bash -c "! curl -s '$BASE/actuator/env' | grep -q 'propertySources'"
check "/actuator/health não é roteado para fora" bash -c "! curl -s '$BASE/actuator/health' | grep -q '\"status\"'"

echo "== Cadastro e login"
check "cadastro retorna 200" test "$(status -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"smoke$STAMP\",\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")" = 200
login
ACCESS="$(json_field accessToken < "$TMP/login.b")"
REFRESH="$(cookie_value "$TMP/login.h")"
check "login devolve access token e cookie de refresh" test -n "$ACCESS" -a -n "$REFRESH"
ACTIVE_COOKIE="$(grep -i '^set-cookie: refresh_token=[^;]' "$TMP/login.h" | head -1 | tr -d '\r')"
check "cookie é HttpOnly, SameSite=Strict e Path=/auth" bash -c "echo '$ACTIVE_COOKIE' | grep -qi 'HttpOnly' && echo '$ACTIVE_COOKIE' | grep -qi 'SameSite=Strict' && echo '$ACTIVE_COOKIE' | grep -Eqi 'Path=/auth(;|\$)'"
if [ "$EXPECT_SECURE_COOKIE" = true ]; then
  check "cookie tem o atributo Secure" bash -c "echo '$ACTIVE_COOKIE' | grep -qi '; Secure'"
fi
check "cookie legado (Path=/auth/refresh) é expirado" bash -c "grep -i '^set-cookie: refresh_token=;' '$TMP/login.h' | grep -qi 'Path=/auth/refresh'"

echo "== Separação access e refresh token"
check "access token abre /rooms" test "$(status "$BASE/rooms" -H "Authorization: Bearer $ACCESS")" = 200
check "refresh token NÃO abre /rooms (chat-service)" bash -c "code=\$(curl -s -o /dev/null -w '%{http_code}' '$BASE/rooms' -H 'Authorization: Bearer $REFRESH'); [ \"\$code\" = 401 ] || [ \"\$code\" = 403 ]"
ROOM_ID="$(curl -s -X POST "$BASE/rooms" -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' -d '{"name":"smoke-room"}' | json_field id)"
check "sala criada" test -n "$ROOM_ID"
check "refresh token NÃO abre o histórico (message-service)" bash -c "code=\$(curl -s -o /dev/null -w '%{http_code}' '$BASE/rooms/$ROOM_ID/messages' -H 'Authorization: Bearer $REFRESH'); [ \"\$code\" = 401 ] || [ \"\$code\" = 403 ]"
check "histórico limita o tamanho da página a 100" bash -c "curl -s '$BASE/rooms/$ROOM_ID/messages?size=100000' -H 'Authorization: Bearer $ACCESS' | grep -q '\"size\":100'"

echo "== Rotação, reuso e logout"
curl -s -D "$TMP/refresh.h" -o "$TMP/refresh.b" -X POST "$BASE/auth/refresh" -H "Cookie: refresh_token=$REFRESH"
check "refresh com o cookie devolve novo access token" test -n "$(json_field accessToken < "$TMP/refresh.b")"
check "refresh token antigo não é aceito de novo (reuso)" test "$(status -X POST "$BASE/auth/refresh" -H "Cookie: refresh_token=$REFRESH")" = 401
login
REFRESH2="$(cookie_value "$TMP/login.h")"
check "logout devolve 204" test "$(status -X POST "$BASE/auth/logout" -H "Cookie: refresh_token=$REFRESH2")" = 204
check "refresh token revogado pelo logout não renova" test "$(status -X POST "$BASE/auth/refresh" -H "Cookie: refresh_token=$REFRESH2")" = 401

echo "== WebSocket"
check "handshake de origem não permitida é recusado (403)" test "$(status --max-time 3 "$BASE/ws/websocket" -H 'Connection: Upgrade' -H 'Upgrade: websocket' -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' -H 'Origin: https://evil.example')" = 403 # gitleaks:allow
if command -v node >/dev/null 2>&1; then
  login
  ACCESS3="$(json_field accessToken < "$TMP/login.b")"; REFRESH3="$(cookie_value "$TMP/login.h")"
  check "cenários STOMP" env BASE_URL="$BASE" ACCESS_TOKEN="$ACCESS3" REFRESH_TOKEN="$REFRESH3" ROOM_ID="$ROOM_ID" node "$HERE/stomp-smoke.mjs"
else
  echo "SKIP  cenários STOMP (node não encontrado)"
fi

echo "== Limite de requisições em /auth"
CODES="$(for _ in $(seq 1 60); do status -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d '{"email":"nobody@example.com","password":"wrong-password"}'; echo; done | sort | uniq -c | tr '\n' ' ')"
echo "      respostas: $CODES"
check "excesso de tentativas de login recebe 429" bash -c "echo '$CODES' | grep -q '429'"

if [ "$CHECK_CLOSED_PORTS" = true ]; then
  echo "== Portas internas fechadas no host"
  for port in 5432 6379 9092 8081 8082 8083; do
    check "porta $port não está aberta em 127.0.0.1" bash -c "! (exec 3<>/dev/tcp/127.0.0.1/$port) 2>/dev/null"
  done
fi

echo
if [ "$FAILS" -eq 0 ]; then echo "Smoke test OK"; else echo "$FAILS verificação(ões) falharam"; fi
exit "$FAILS"
