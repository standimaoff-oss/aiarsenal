"""Shared catalogue for stage2 resource generation, Blender and gameplay QA."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SETS={}
for army,ru,en,color in [('ww2_su','СССР','Soviet',(112,105,67)),('ww2_de','Германия','German',(77,87,77))]:
 for role,r,e in [('infantry','пехотинец','infantry'),('officer','офицер','officer'),('tanker','танкист','tank crew'),('pilot','лётчик','pilot'),('general','генерал','general')]:
  c=color
  if role=='tanker':c=(43,46,46) if army.endswith('de') else (70,73,64)
  if role=='pilot':c=(70,83,97) if army.endswith('de') else (104,72,48)
  SETS[army+'_'+role]={'army':army,'role':role,'ru':f'{ru} · {r} · 1943–45','en':f'{en} {e} · 1943–45','color':c}
for army,ru,en,color in [('nap_fr','Франция','French',(30,45,78)),('nap_ri','Российская империя','Russian Imperial',(34,63,44))]:
 for role,r,e in [('infantry','пехотинец','line infantry'),('officer','офицер','officer'),('general','генерал','general')]:
  SETS[army+'_'+role]={'army':army,'role':role,'ru':f'{ru} · {r} · 1812','en':f'{en} {e} · 1812','color':color}
