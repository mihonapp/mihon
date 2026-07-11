#!/usr/bin/env bash
set -euo pipefail

output_path="${1:?Usage: create-public-dev-keystore.sh <output-keystore>}"
output_dir="$(dirname "$output_path")"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT

mkdir -p "$output_dir"

# This is an intentionally public test key used only for the
# io.github.kamui2040.yomori.debug development package.
public_test_private_key_base64='MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAGhRANCAARrF9Hy4SxCR/i85uVjpEDydwN9gS3rM6D0oTlF2JjClk/jQuL+Gn+bjufrSnwPnhYrzjNXazFezsu2QGg3v1H1'
public_test_certificate_base64='MIIB1zCCAX6gAwIBAgIGWU9NT1JJMAoGCCqGSM49BAMCMGkxIjAgBgNVBAMMGVlvbW9yaSBQdWJsaWMgRGV2ZWxvcG1lbnQxEjAQBgNVBAoMCUthbXVpMjA0MDEiMCAGA1UECwwZUHVibGljIERldmVsb3BtZW50IEJ1aWxkczELMAkGA1UEBhMCREUwIBcNMjYwMTAxMDAwMDAwWhgPMjEyNTEyMzEwMDAwMDBaMGkxIjAgBgNVBAMMGVlvbW9yaSBQdWJsaWMgRGV2ZWxvcG1lbnQxEjAQBgNVBAoMCUthbXVpMjA0MDEiMCAGA1UECwwZUHVibGljIERldmVsb3BtZW50IEJ1aWxkczELMAkGA1UEBhMCREUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARrF9Hy4SxCR/i85uVjpEDydwN9gS3rM6D0oTlF2JjClk/jQuL+Gn+bjufrSnwPnhYrzjNXazFezsu2QGg3v1H1oxAwDjAMBgNVHRMBAf8EAjAAMAoGCCqGSM49BAMCA0cAMEQCIEPq6ao2jRjrc5xHpTyI2gO41mSwX6UWIiP7Ex91lZxhAiBgSNp9LRurPQeaFiGozSwxgxsCWfrR8IhfP4uGHPgCUg=='
expected_certificate_sha256='08db929c3863a587963a3d72668622c9f464cbb3612cc2f4df29cdcb63750625'

printf '%s' "$public_test_private_key_base64" | base64 --decode > "$temporary_dir/key.der"
printf '%s' "$public_test_certificate_base64" | base64 --decode > "$temporary_dir/certificate.der"

actual_certificate_sha256="$(sha256sum "$temporary_dir/certificate.der" | cut -d ' ' -f 1)"
if [[ "$actual_certificate_sha256" != "$expected_certificate_sha256" ]]; then
    echo "Unexpected public development certificate digest" >&2
    exit 1
fi

openssl pkey \
    -inform DER \
    -in "$temporary_dir/key.der" \
    -out "$temporary_dir/key.pem"

openssl x509 \
    -inform DER \
    -in "$temporary_dir/certificate.der" \
    -out "$temporary_dir/certificate.pem"

openssl pkcs12 \
    -export \
    -inkey "$temporary_dir/key.pem" \
    -in "$temporary_dir/certificate.pem" \
    -out "$output_path" \
    -name yomori-development \
    -passout pass:android

chmod 600 "$output_path"
