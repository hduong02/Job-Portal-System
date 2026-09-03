#!/usr/bin/env bash
# Live API runner for the VPS host, not the offline contract tests.
set +x
set -euo pipefail
umask 077

usage() {
  printf '%s\n' 'Usage:
  bash run-vps.sh                     Run the live API smoke suite
  bash run-vps.sh --preflight         Check infrastructure, then run the live suite
  bash run-vps.sh --cleanup MANIFEST  Recover cleanup from a saved manifest

Defaults on the VPS:
  BASE_URL=http://127.0.0.1:5000
  MAILHOG_URL=http://127.0.0.1:8025

Prompts for missing ADMIN_EMAIL, ADMIN_PASSWORD, and SMOKE_PASSWORD.
Set these environment variables beforehand for noninteractive runs.
Uses Node.js 22+, or an isolated node:22-bookworm-slim Docker container.
No host installation, npm install, or k6 is needed when Docker is available.
Exit codes: 0 passed, 1 failed, 2 blocked.'
}

mode=run
manifest=
case "${1:-}" in
  --help|-h) usage; exit 0 ;;
  --preflight) mode=preflight; [[ $# == 1 ]] || { usage >&2; exit 2; } ;;
  --cleanup) mode=cleanup; [[ $# == 2 ]] || { usage >&2; exit 2; }; manifest=$2 ;;
  '') [[ $# == 0 ]] || { usage >&2; exit 2; } ;;
  *) usage >&2; exit 2 ;;
esac

script_path=${BASH_SOURCE[0]}
if [[ "$script_path" == */* ]]; then
  script_dir=$(cd -- "${script_path%/*}" && pwd)
else
  script_dir=$PWD
fi
runtime=node
if ! command -v node >/dev/null 2>&1 || ! node -e 'process.exit(Number(process.versions.node.split(".")[0]) >= 22 ? 0 : 1)'; then
  runtime=docker
fi
if [[ "$runtime" == docker ]] && ! command -v docker >/dev/null 2>&1; then
  printf 'BLOCKED: Node.js 22+ or Docker is required.\n' >&2
  exit 2
fi

export BASE_URL=${BASE_URL:-http://127.0.0.1:5000}
export MAILHOG_URL=${MAILHOG_URL:-http://127.0.0.1:8025}

prompt() {
  local key=$1 label=$2 secret=$3
  if [[ -z "${!key:-}" ]]; then
    if [[ ! -t 0 ]]; then
      printf 'BLOCKED: set %s in the environment or run from an interactive VPS terminal.\n' "$key" >&2
      exit 2
    fi
    if [[ "$secret" == yes ]]; then
      IFS= read -r -s -p "$label: " "$key" || exit 2
      printf '\n'
    else
      IFS= read -r -p "$label: " "$key" || exit 2
    fi
  fi
  if [[ -z "${!key}" ]]; then
    printf 'BLOCKED: %s cannot be empty.\n' "$key" >&2
    exit 2
  fi
  export "$key"
}

prompt ADMIN_EMAIL 'Existing admin email' no
prompt ADMIN_PASSWORD 'Admin password (hidden)' yes
prompt SMOKE_PASSWORD 'Disposable-account password (8+ characters; keep for cleanup recovery)' yes

run_node() {
  local entry=$1
  shift
  if [[ "$runtime" == node ]]; then
    exec node "$script_dir/$entry" "$@"
  fi
  printf 'Using an isolated Node.js Docker runtime; reports stay in %s/results.\n' "$script_dir"
  exec docker run --rm --network host --cpus 0.5 --memory 256m --read-only \
    --cap-drop ALL --security-opt no-new-privileges \
    --mount "type=bind,src=$script_dir,dst=/work" --workdir /work \
    --env BASE_URL --env MAILHOG_URL --env ADMIN_EMAIL --env ADMIN_PASSWORD \
    --env SMOKE_PASSWORD --env SMOKE_EMAIL_DOMAIN \
    node:22-bookworm-slim node "$entry" "$@"
}

if [[ "$mode" == cleanup ]]; then
  if [[ "$runtime" == docker ]]; then
    if [[ "$manifest" == */* ]]; then
      manifest_abs=$(cd -- "${manifest%/*}" && printf '%s/%s' "$PWD" "${manifest##*/}")
    else
      manifest_abs=$PWD/$manifest
    fi
    case "$manifest_abs" in
      "$script_dir"/*) manifest=/work/${manifest_abs#"$script_dir"/} ;;
      *) printf 'BLOCKED: Docker recovery manifest must be inside the smoke-suite folder.\n' >&2; exit 2 ;;
    esac
  fi
  run_node cleanup.mjs "$manifest"
fi
if [[ "$mode" == preflight ]]; then
  bash "$script_dir/preflight.sh"
fi
printf 'Running live API smoke test against %s; mailbox %s\n' "$BASE_URL" "$MAILHOG_URL"
run_node run.mjs
