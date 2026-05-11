#!/usr/bin/env bash
# Fetches the upstream `proc_macro` crate sources into tmp/proc-macro/.
#
# Upstream lives in rust-lang/rust under library/proc_macro/. We do a
# shallow sparse-checkout so we don't pull the rest of the rust compiler
# tree (~1 GB).
#
# Idempotent: re-running cleans and re-fetches.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP_DIR="$REPO_ROOT/tmp"
WORK_DIR="$TMP_DIR/proc-macro"
RUST_REPO_URL="${RUST_REPO_URL:-https://github.com/rust-lang/rust.git}"
# Pin the upstream sha. Bump explicitly when refreshing — never let this
# script drift to whatever the rust-lang/rust master happens to be on the
# day someone fetches.
RUST_REPO_REF="${RUST_REPO_REF:-master}"

echo "Fetching proc_macro source from $RUST_REPO_URL @ $RUST_REPO_REF"

rm -rf "$TMP_DIR"
mkdir -p "$TMP_DIR"

CLONE_DIR="$(mktemp -d -t proc-macro-fetch-XXXXXX)"
trap 'rm -rf "$CLONE_DIR"' EXIT

git -C "$CLONE_DIR" init --quiet
git -C "$CLONE_DIR" remote add origin "$RUST_REPO_URL"
git -C "$CLONE_DIR" config core.sparseCheckout true
git -C "$CLONE_DIR" sparse-checkout init --cone
git -C "$CLONE_DIR" sparse-checkout set library/proc_macro
git -C "$CLONE_DIR" fetch --depth=1 origin "$RUST_REPO_REF"
git -C "$CLONE_DIR" checkout FETCH_HEAD

mv "$CLONE_DIR/library/proc_macro" "$WORK_DIR"

# Record the exact sha we fetched so commits can reference it.
FETCHED_SHA="$(git -C "$CLONE_DIR" rev-parse FETCH_HEAD)"
printf '%s\n' "$FETCHED_SHA" > "$WORK_DIR/.upstream-sha"
echo "Fetched proc_macro at $FETCHED_SHA into $WORK_DIR"
echo
echo "Tree:"
find "$WORK_DIR" -maxdepth 3 -name '*.rs' -o -name 'Cargo.toml' | sort
