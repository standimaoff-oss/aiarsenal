#!/usr/bin/env python3
"""Render an honest per-registry-entry coverage ledger from retained runtime evidence.
Missing rows stay NOT_RUN; generic smoke success is never promoted to feature success.
"""
import argparse
from collections import Counter
import json
from pathlib import Path


def render(folder):
    inventory=json.loads((folder/'catalogue-inventory.json').read_text())
    result=json.loads((folder/'catalogue-gameplay.json').read_text())
    rows={row['id']:row for row in result['items']}
    assert len(rows)==len(result['items']), 'duplicate item test records'
    ids=[row['id'] for row in inventory['items']]
    assert len(ids)==len(set(ids)), 'duplicate registry IDs'
    assert not set(rows)-set(ids), 'report contains an item not in this runtime registry'
    counts=Counter(rows.get(id,{}).get('smoke','NOT_RUN') for id in ids)
    functions=Counter(rows.get(id,{}).get('functional','NOT_VERIFIED') for id in ids)
    lines=['# Реестр и результаты проверки', '',
        '**Это журнал проверенных действий, а не заявление, что все функции мода завершены.**', '',
        'Итог процесса (не только отдельных действий): `'+json.dumps(result.get('runOutcome', {'status':'NOT_RECORDED — см. итог CI'}),ensure_ascii=False)+'`.','',
        f'Живой реестр: **{len(ids)} предметов**. Общий smoke: '+', '.join(f'{k}={v}' for k,v in sorted(counts.items()))+'.', '',
        'Функциональные результаты: '+', '.join(f'`{k}`={v}' for k,v in sorted(functions.items()))+'.', '',
        '- [Полный runtime-каталог](catalogue-inventory.json)',
        '- [Пошаговые результаты, ошибки и ограничения](catalogue-gameplay.json)',
        '- [Происхождение исходников](provenance.txt)',
        '- [Установленный JAR и SHA-256](installed-provenance.json)', '',
        '## Предметы', '', '| ID | Класс | Smoke | Проверенное действие |', '|---|---|---|---|']
    for item in inventory['items']:
        row=rows.get(item['id'],{})
        action=row.get('functional','NOT_VERIFIED')
        if row.get('error'):
            action+='; **'+row['error'].replace('|','/').replace('\n',' ')[:240]+'**'
        lines.append(f"| `{item['id']}` | {item['class'].split('.')[-1]} | {row.get('smoke','NOT_RUN')} | {action} |")
    lines+=['','## Свободнопадающие бомбы','','| Тип | Результат | HP живой цели после взрыва | Изменённые блоки (счётчик RealWar) |','|---|---|---|---|']
    bombs={row['id']:row for row in result['bombs']}
    for id in inventory['freeFallBombs']:
        row=bombs.get(id,{})
        lines.append(f"| `{id}` | {row.get('status','NOT_RUN')} | {row.get('witnessHealth','—')} | {row.get('changedBlocks','—')} |")
    lines+=['','Счётчик блоков RealWar не учитывает отдельный алгоритм кратеров TuBombers; ноль в этом столбце сам по себе не означает отсутствие взрыва.','',
        '## Сохранение и повторный вход','',
        json.dumps(result.get('worldSaveReopen') or {'status':'NOT_RUN'},ensure_ascii=False),'',
        '## Ограничения','']
    lines+=['- '+text for text in result.get('limitations',[])]
    lines+=['','Серверные hook-вызовы use/useOn не являются проверкой всех клиентских UI/управлений. R/ЛКМ/ПКМ в оружейных случаях отправлялись настоящими событиями ввода.','']
    return '\n'.join(lines)


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('folder',type=Path)
    args=parser.parse_args()
    (args.folder/'CATALOGUE.md').write_text(render(args.folder))
