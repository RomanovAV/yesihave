#!/usr/bin/env python3
"""Replay an exported JSONL snapshot using only references confirmed before each query.

Inputs: snapshot.jsonl (coaster/check records), vectors/ (<photo filename>.json),
queries/ (normalized audit photos). Outputs under --output include scores, metrics,
and an idempotent cache-backfill payload. No database writes are performed.
"""
import argparse
import hashlib
import json
import re
from collections import Counter
from datetime import datetime
from pathlib import Path

import numpy as np


def at(value):
    value = re.sub(r'\.(\d+)(?=[+-])', lambda match: '.' + match.group(1).ljust(6, '0'),
                   value.replace('Z', '+00:00'))
    return datetime.fromisoformat(value)


def pair_key(first, second):
    return ':'.join(sorted((first, second)))


def decision(candidates):
    if not candidates:
        return 'NO_MATCH'
    c = candidates[0]
    if c['pair'] >= .90 and min(c['front'], c['back']) >= .86:
        return 'MATCH'
    if c['pair'] >= .80 or (len(candidates) > 1 and c['pair'] >= .75
                            and c['pair'] - candidates[1]['pair'] >= .05):
        return 'UNCERTAIN'
    return 'NO_MATCH'


def rank(query, pool):
    if not pool:
        return []
    fv, bv = query['vectors']
    fronts = np.asarray([p['vectors'][0] for p in pool], dtype=np.float64)
    backs = np.asarray([p['vectors'][1] for p in pool], dtype=np.float64)
    scores = np.column_stack((fronts @ fv, backs @ bv, backs @ fv, fronts @ bv))
    # Four retrievals, each returning up to 20 unique coasters.
    shortlist = set()
    for side in range(4):
        by_coaster = {}
        for p, s in zip(pool, scores[:, side]):
            by_coaster[p['id']] = max(by_coaster.get(p['id'], -2), float(s))
        shortlist.update(sorted(by_coaster, key=lambda c: (-by_coaster[c], c))[:20])
    best = {}
    for p, (ff, bb, fb, bf) in zip(pool, scores):
        if p['id'] not in shortlist:
            continue
        f, b = (fb, bf) if fb + bf > ff + bb else (ff, bb)
        candidate = dict(id=p['id'], name=p['name'], front=float(f), back=float(b),
                         pair=float((f+b)/2), source=p.get('source'))
        if p['id'] not in best or candidate['pair'] > best[p['id']]['pair']:
            best[p['id']] = candidate
    return sorted(best.values(), key=lambda c: (-c['pair'], c['id']))[:3]


def normalized(value):
    v = np.asarray(json.loads(value) if isinstance(value, str) else value, dtype=np.float64)
    if len(v) != 512 or not np.isfinite(v).all() or np.linalg.norm(v) == 0:
        raise ValueError('Invalid embedding vector')
    return v / np.linalg.norm(v)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('snapshot', type=Path)
    parser.add_argument('vectors', type=Path)
    parser.add_argument('queries', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    rows = [json.loads(x) for x in args.snapshot.read_text().splitlines()]
    coasters = [r for r in rows if r['type'] == 'coaster']
    checks = [r for r in rows if r['type'] == 'check' and r['verdict']]
    ids = {c['id']: c for c in coasters}
    for c in coasters:
        c['vectors'] = [normalized(c[s]) for s in ('front', 'back')]
        c['key'] = pair_key(c['front_sha'], c['back_sha'])
    for r in checks:
        if r['added']:
            c = ids[r['added']]
            r['vectors'], r['key'] = c['vectors'], c['key']
        else:
            r['vectors'] = [normalized(json.loads((args.vectors / (Path(r[s+'_key']).name+'.json')).read_text()))
                            for s in ('front', 'back')]
            hashes = [hashlib.sha256((args.queries/Path(r[s+'_key']).name).read_bytes()).hexdigest()
                      for s in ('front', 'back')]
            r['key'] = pair_key(*hashes)
    confirmed = [r for r in checks if r['verdict'] == 'MATCH']
    metrics = {'baseline': Counter(), 'references': Counter()}
    output = []
    duplicates = []
    for r in checks:
        originals = [c for c in coasters if at(c['at']) < at(r['at'])]
        original_keys = {c['key'] for c in originals}
        is_duplicate = r['key'] in original_keys
        if is_duplicate:
            duplicates.append(r['id'])
        known = [c for c in confirmed if c['id'] != r['id'] and c['confirmed_at']
                 and at(c['confirmed_at']) < at(r['at']) and c['key'] not in original_keys]
        labels = {}
        for c in known:
            labels.setdefault(c['key'], set()).add(c['actual'])
        references = {}
        for c in sorted(known, key=lambda c: at(c['confirmed_at'])):
            if len(labels[c['key']]) == 1:
                references.setdefault(c['key'], dict(id=c['actual'], name=ids[c['actual']]['name'],
                                                     vectors=c['vectors'], source=c['id']))
        row = {k: r[k] for k in ('id', 'at', 'actual', 'verdict')}
        row['exact_retry'] = is_duplicate
        for mode, pool in (('baseline', originals), ('references', originals+list(references.values()))):
            ranked = rank(r, pool)
            predicted = decision(ranked)
            correct = bool(ranked) and ranked[0]['id'] == r['actual']
            row[mode] = dict(decision=predicted, candidates=ranked, correct_top1=correct)
            if not is_duplicate:
                window = 'new' if at(r['at']) >= at('2026-10-24T13:33:21+03:00') else 'earlier'
                metrics[mode][(window, r['verdict'], predicted, correct)] += 1
        output.append(row)
    (args.output/'replay.json').write_text(json.dumps(output, ensure_ascii=False, indent=2)+'\n')
    for mode, counts in metrics.items():
        print(mode)
        for k, n in sorted(counts.items()):
            print(' ', k, n)
    print('Exact retries excluded:', duplicates)
    # The raw vectors are normalized again here only to compensate float serialization
    # rounding, not to change the embedding pipeline. Production uses pgvector cosine.
    with (args.output/'backfill.jsonl').open('w') as target:
        for r in confirmed:
            target.write(json.dumps(dict(check_request_id=r['id'], image_pair_key=r['key'],
                                         front_image_key=r['front_key'], back_image_key=r['back_key'],
                                         front=r['vectors'][0].tolist(), back=r['vectors'][1].tolist()))+'\n')
    (args.output/'metrics.json').write_text(json.dumps({m: [dict(window=k[0], actual=k[1], predicted=k[2],
                       correct_top1=k[3], count=n) for k, n in sorted(cs.items())]
                       for m, cs in metrics.items()}, indent=2)+'\n')


if __name__ == '__main__':
    main()
