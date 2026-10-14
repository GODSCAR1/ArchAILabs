#!/usr/bin/env bash
# .claude/hooks/protect-verify.sh
F=$(jq -r '.tool_input.file_path // empty')
case "$F" in
  */config/*|*/.claude/*|*/.opencode/*|*/.github/*|*pom.xml|*.env*)
    echo "AC/DC: $F está protegido. Pide permiso a un humano." >&2
    exit 2 ;;
esac
exit 0