"""카드사 앱 실적(<work>/<key>.tsv) ↔ MyCard notifications.db 대조.

사용: python recon.py --work <작업폴더> [--month YYYY-MM]
작업폴더에 필요한 파일:
  <key>.tsv                         카드사 앱에서 수집한 건 (YYYY-MM-DD<TAB>가맹점<TAB>금액, 취소는 음수)
  notifications.db(+ -wal, -shm)    run-as로 복사한 MyCard DB
  raw_notifications_all_<YYYY>_<MM>.jsonl   (선택) 원인 힌트용
출력: 카드별 차이표 + <work>/missing.json (카드사에만 있는 건)
"""
import argparse, datetime as dt, json, os, sqlite3, sys

sys.stdout.reconfigure(encoding='utf-8')
KST = dt.timezone(dt.timedelta(hours=9))
CONFIG = os.path.join(os.path.dirname(__file__), '..', '..', '..', '..', 'config', 'card_filters.json')

# (MyCard card_company, tsv key, 카드사 앱 pkg, 승인 filterId, 취소 filterId)
CARDS = [
    ('하나 매직 9207', 'hana_9207', 'com.hanaskcard.paycla', 'hanacard_magic_9207_approval_v1', 'hanacard_magic_9207_cancel_v1'),
    ('하나 브로드 0179', 'hana_0179', 'com.hanaskcard.paycla', 'hanacard_broad_0179_approval_v1', 'hanacard_broad_0179_cancel_v1'),
    ('네이버 현대카드', 'hyundai_naver', 'com.hyundaicard.appcard', 'hyundaicard_naver_approval_v2', None),
    ('SK 현대카드', 'hyundai_sk', 'com.hyundaicard.appcard', 'hyundaicard_sk_m_approval_v1', None),
    ('신한카드 8423', 'shinhan_8423', 'com.shcard.smartpay', 'shinhancard_8423_approval_v1', 'shinhancard_8423_cancel_v1'),
]


def read_objects(path):
    dec = json.JSONDecoder()
    text = open(path, encoding='utf-8').read()
    pos = 0
    while pos < len(text):
        while pos < len(text) and text[pos].isspace():
            pos += 1
        if pos >= len(text):
            break
        obj, pos = dec.raw_decode(text, pos)
        yield obj


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--work', required=True)
    ap.add_argument('--month', default=dt.datetime.now(KST).strftime('%Y-%m'))
    args = ap.parse_args()

    year, month = map(int, args.month.split('-'))
    start = dt.datetime(year, month, 1, tzinfo=KST)
    end = dt.datetime(year + (month == 12), month % 12 + 1, 1, tzinfo=KST)
    start_ms, end_ms = int(start.timestamp() * 1000), int(end.timestamp() * 1000)

    filters = json.load(open(CONFIG, encoding='utf-8'))['filters']
    fid_to_company = {f['id']: f['card_company'] for f in filters}
    pkg_to_company = {f['package']: f['card_company'] for f in filters}

    db = sqlite3.connect(os.path.join(args.work, 'notifications.db'))
    rows = db.execute(
        "select id, ts, pkg, title, text, bigText, amount, merchant, filterId from notifications "
        "where ts >= ? and ts < ? order by ts", (start_ms, end_ms)).fetchall()
    first_ts = db.execute("select min(ts) from notifications where pkg != 'com.example.mycard.manual'").fetchone()[0]
    if first_ts:
        print(f"DB 최초 ts: {dt.datetime.fromtimestamp(first_ts / 1000, KST):%Y-%m-%d %H:%M} (이전 기간은 수집 자체가 없음)")

    raw_all = []
    raw_path = os.path.join(args.work, f'raw_notifications_all_{year}_{month:02d}.jsonl')
    if os.path.exists(raw_path):
        raw_all = list(read_objects(raw_path))

    by_company = {}
    for row in rows:
        if row[6] is None:
            continue
        company = fid_to_company.get(row[8]) or pkg_to_company.get(row[2]) or row[2]
        by_company.setdefault(company, []).append(row)

    missing = []
    for company, key, pkg, approval_fid, cancel_fid in CARDS:
        tsv = os.path.join(args.work, key + '.tsv')
        if not os.path.exists(tsv):
            print(f"\n=== {company}: {key}.tsv 없음 — 건너뜀")
            continue
        truth = []
        for line in open(tsv, encoding='utf-8'):
            if line.strip():
                day, merchant, amount = line.rstrip('\n').split('\t')
                truth.append((dt.date.fromisoformat(day), merchant, int(amount)))
        mine = [(dt.datetime.fromtimestamp(r[1] / 1000, KST).date(), r[7] or '', r[6], r)
                for r in by_company.get(company, [])]

        used, only_truth = set(), []
        for item in truth:
            best = None
            for idx, cand in enumerate(mine):
                if idx in used or cand[2] != item[2]:
                    continue
                gap = abs((cand[0] - item[0]).days)
                if gap <= 2 and (best is None or gap < best[0]):
                    best = (gap, idx)
            if best:
                used.add(best[1])
            else:
                only_truth.append(item)
        only_mine = [cand for idx, cand in enumerate(mine) if idx not in used]

        truth_sum = sum(t[2] for t in truth)
        mine_sum = sum(m[2] for m in mine)
        print(f"\n=== {company}: 카드사 {truth_sum:,} ({len(truth)}건) / MyCard {mine_sum:,} ({len(mine)}건) / 차이 {mine_sum - truth_sum:+,}")
        for day, merchant, amount in only_truth:
            needle = f"{abs(amount):,}"
            hits = [o for o in raw_all if o.get('pkg') == pkg and needle in (o.get('bigText') or o.get('text') or '')]
            hint = f"알림 있음 {len(hits)}건 → 필터 문제" if hits else "알림 없음 → 수동 항목 후보"
            print(f"  [카드사에만] {day} {merchant} {amount:,}  ({hint})")
            for o in hits[:3]:
                body = (o.get('bigText') or o.get('text') or '').replace('\n', ' | ')
                print(f"      ts={o['ts']} title={o.get('title')!r} body={body[:160]!r}")
            missing.append({'company': company, 'date': day.isoformat(), 'merchant': merchant, 'amount': amount,
                            'filterId': cancel_fid if amount < 0 and cancel_fid else approval_fid,
                            'notification_found': bool(hits)})
        for day, merchant, amount, row in only_mine:
            print(f"  [MyCard에만] {day} {merchant} {amount:,} filter={row[8]} id={row[0]}")

    with open(os.path.join(args.work, 'missing.json'), 'w', encoding='utf-8') as out:
        json.dump(missing, out, ensure_ascii=False, indent=2)
    print(f"\nmissing.json: {len(missing)}건 (알림 없음 {sum(not m['notification_found'] for m in missing)}건)")


if __name__ == '__main__':
    main()
