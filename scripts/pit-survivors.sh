#!/usr/bin/env bash
# scripts/pit-survivors.sh — genera .acdc/pit-survivors.md a partir de target/pit-reports/mutations.xml (lab S15).
# Uso: scripts/pit-survivors.sh [mutations.xml]
# guide-context.sh lo inyecta al empezar la sesión del agente y verify-quality.sh lo cita cuando la capa 3 falla.
set -euo pipefail
XML=${1:-target/pit-reports/mutations.xml}
OUT=.acdc/pit-survivors.md
[[ -s "$XML" ]] || { echo "pit-survivors: no existe $XML (ejecuta antes PIT)" >&2; exit 2; }
PY=""
for c in python3 python; do command -v "$c" >/dev/null && "$c" -c 'import sys' 2>/dev/null && { PY=$c; break; }; done
[[ -n "$PY" ]] || { echo "pit-survivors: falta Python" >&2; exit 2; }
mkdir -p .acdc
ACCEPTED=scripts/pit-accepted-survivors.txt
"$PY" - "$XML" "$OUT" "$ACCEPTED" <<'PY'
import os, sys, xml.etree.ElementTree as ET
xml, out, accepted_file = sys.argv[1], sys.argv[2], sys.argv[3]
accepted = {}
if os.path.exists(accepted_file):
    for raw in open(accepted_file, encoding='utf-8'):
        line = raw.split('#', 1)
        key = line[0].strip()
        if key:
            accepted[key] = line[1].strip() if len(line) > 1 else ''
ms = ET.parse(xml).getroot().findall('mutation')
alive = [m for m in ms if m.get('status') in ('SURVIVED', 'NO_COVERAGE')]
killed = sum(m.get('status') == 'KILLED' for m in ms)
t = lambda m, k: (m.findtext(k) or '').strip()
key = lambda m: '|'.join([t(m, 'mutatedClass').rsplit('.', 1)[-1], t(m, 'mutatedMethod'),
                          t(m, 'mutator').rsplit('.', 1)[-1], t(m, 'lineNumber')])
unclassified = [m for m in alive if key(m) not in accepted]
rows = sorted(alive, key=lambda m: (t(m, 'mutatedClass'), int(t(m, 'lineNumber') or 0)))
with open(out, 'w', encoding='utf-8', newline='\n') as f:
    f.write('# Supervivientes de PIT\n\n')
    f.write('_Generado por `scripts/pit-survivors.sh` desde `%s`. No editar a mano._\n\n' % xml)
    f.write('Score: %d/%d (%d %%). Para cada fila: identifica la regla de negocio que revela y escribe un test de ESA regla '
            '(resultado observable, sin Mockito en `pricing`, sin `verify`). Si no corresponde a ninguna regla, no escribas test: '
            'explica a la persona si es sobrante, equivalente o defensivo; solo ella lo clasifica en `scripts/pit-accepted-survivors.txt`.\n\n'
            % (killed, len(ms), 100 * killed // max(len(ms), 1)))
    if not rows:
        f.write('Ninguno.\n')
    else:
        f.write('Sin clasificar: **%d** (el gate falla si hay alguno). Clasificados por una persona en `%s`.\n\n'
                % (len(unclassified), accepted_file))
        f.write('| Clase:línea | Método | Mutador | Qué cambia | Estado | Clasificado |\n|---|---|---|---|---|---|\n')
        for m in rows:
            cls = t(m, 'mutatedClass').rsplit('.', 1)[-1]
            mut = t(m, 'mutator').rsplit('.', 1)[-1].replace('Mutator', '')
            desc = t(m, 'description').replace('|', '/')
            cl = accepted.get(key(m))
            f.write('| `%s:%s` | `%s` | %s | %s | %s | %s |\n' % (cls, t(m, 'lineNumber'), t(m, 'mutatedMethod'), mut, desc,
                    m.get('status'), (cl.replace('|', '/') if cl else '**NO**')))
print('unclassified=%d' % len(unclassified))
PY
echo "pit-survivors: $OUT" >&2
