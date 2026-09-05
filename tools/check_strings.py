#!/usr/bin/env python3
"""
Verifica os arquivos de string antes de compilar.

O AAPT2 recusa recursos com apóstrofo ou aspas sem escape e só informa
"Can not extract resource from ParsedResource", sem dizer o arquivo. Este
script aponta a linha exata. Rode com: python3 tools/check_strings.py
"""
import glob
import os
import re
import sys

RAIZ = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res')


def verificar() -> int:
    problemas = 0
    for caminho in sorted(glob.glob(os.path.join(RAIZ, 'values*', 'strings.xml'))):
        for numero, linha in enumerate(open(caminho, encoding='utf-8'), 1):
            achado = re.search(r'<(string|item)[^>]*>([^<]*)</\1>', linha)
            if not achado:
                continue
            valor = achado.group(2)
            for i, ch in enumerate(valor):
                if ch in ("'", '"') and (i == 0 or valor[i - 1] != '\\'):
                    print(f'{caminho}:{numero}: {ch} sem escape -> {valor[:70]}')
                    problemas += 1
                    break
            if valor and valor[0] in '@?':
                print(f'{caminho}:{numero}: valor comeca com {valor[0]}')
                problemas += 1
    print('nenhum problema' if problemas == 0 else f'{problemas} problema(s)')
    return 1 if problemas else 0


if __name__ == '__main__':
    sys.exit(verificar())
