#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
hooks_dir="${repo_root}/.git/hooks"
source_dir="${repo_root}/scripts/git-hooks"

if [[ ! -d "${hooks_dir}" ]]; then
	echo "FAIL: .git/hooks not found — run from a git checkout" >&2
	exit 1
fi

for hook in "${source_dir}"/*; do
	hook_name="$(basename "${hook}")"
	install_path="${hooks_dir}/${hook_name}"
	cp "${hook}" "${install_path}"
	chmod +x "${install_path}"
	echo "installed ${hook_name}"
done
