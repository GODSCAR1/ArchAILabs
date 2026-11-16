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
"$PY" - "$XML" "$OUT" <<'PY'
import sys, xml.etree.ElementTree as ET
xml, out = sys.argv[1], sys.argv[2]
ms = ET.parse(xml).getroot().findall('mutation')
alive = [m for m in ms if m.get('status') in ('SURVIVED', 'NO_COVERAGE')]
killed = sum(m.get('status') == 'KILLED' for m in ms)
t = lambda m, k: (m.findtext(k) or '').strip()
rows = sorted(alive, key=lambda m: (t(m, 'mutatedClass'), int(t(m, 'lineNumber') or 0)))
with open(out, 'w', encoding='utf-8', newline='\n') as f:
    f.write('# Supervivientes de PIT\n\n')
    f.write('_Generado por `scripts/pit-survivors.sh` desde `%s`. No editar a mano._\n\n' % xml)
    f.write('Score: %d/%d (%d %%). Para cada fila: identifica la regla de negocio que revela y escribe un test de ESA regla '
            '(resultado observable, sin Mockito en `pricing`, sin `verify`). Si no corresponde a ninguna regla, no escribas test: '
            'clasifícalo en `docs/lab-s15.md` como sobrante, equivalente o defensivo.\n\n'
            % (killed, len(ms), 100 * killed // max(len(ms), 1)))
    if not rows:
        f.write('Ninguno.\n')
    else:
        f.write('| Clase:línea | Método | Mutador | Qué cambia | Estado |\n|---|---|---|---|---|\n')
        for m in rows:
            cls = t(m, 'mutatedClass').rsplit('.', 1)[-1]
            mut = t(m, 'mutator').rsplit('.', 1)[-1].replace('Mutator', '')
            desc = t(m, 'description').replace('|', '/')
            f.write('| `%s:%s` | `%s` | %s | %s | %s |\n' % (cls, t(m, 'lineNumber'), t(m, 'mutatedMethod'), mut, desc, m.get('status')))
PY
echo "pit-survivors: $OUT"
