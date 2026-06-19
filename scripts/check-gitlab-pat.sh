#!/usr/bin/env bash
# ============================================================================
# check-gitlab-pat.sh
#
# НАЗНАЧЕНИЕ: проверить Personal Access Token GitLab (IntelliJ IDEA plugin).
# ЗАЧЕМ:     убедиться, что токен для IDE существует и не отозван.
# ГДЕ:       ТОЛЬКО на сервере GitLab Omnibus (devtools) — там есть gitlab-rails.
#             С локального ПК напрямую НЕ запускается.
# БЕЗОПАСНО: только чтение БД GitLab, ничего не меняет.
#
# Запуск с ПК:
#   ssh root@DEVTOOLS_IP 'bash -s' < scripts/check-gitlab-pat.sh
#
# Альтернатива: GitLab UI → Edit profile → Access tokens
# ============================================================================
set -euo pipefail

NAME="IntelliJ IDEA GitLab Integration Plugin"

if ! command -v gitlab-rails >/dev/null 2>&1; then
  echo "[ERROR] gitlab-rails не найден. Запускайте этот скрипт НА сервере GitLab (SSH)."
  echo "        ssh root@<DEVTOOLS_IP> 'bash -s' < scripts/check-gitlab-pat.sh"
  exit 1
fi

gitlab-rails runner "
t = PersonalAccessToken.active.find_by(name: '${NAME}')
if t.nil?
  puts 'TOKEN: not found (maybe different name or revoked)'
else
  puts 'TOKEN: found id=' + t.id.to_s
  puts 'scopes=' + t.scopes.join(',')
  puts 'expires_at=' + t.expires_at.to_s
  puts 'user=' + t.user.username
end
tokens = PersonalAccessToken.active.where('name LIKE ?', '%IntelliJ%')
puts '--- all IntelliJ tokens: ' + tokens.count.to_s
tokens.each { |x| puts x.id.to_s + ' ' + x.name + ' scopes=' + x.scopes.join(',') }
"
