"""missing.json 중 '알림 없음' 건을 MyCard 수동 항목 형식으로 raw 파일 끝에 덧붙인다.

사용: python append_manual.py --work <작업폴더> --raw <pull한 raw_notifications_YYYY.jsonl> --out <push할 파일>
형식은 notif/ManualEntryActivity.kt saveManualEntry()와 동일 (pkg=com.example.mycard.manual).
알림이 있는 건(notification_found=true)은 필터로 고쳐야 하므로 넣지 않는다.
"""
import argparse, datetime as dt, json, os, sys

sys.stdout.reconfigure(encoding='utf-8')
KST = dt.timezone(dt.timedelta(hours=9))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--work', required=True)
    ap.add_argument('--raw', required=True)
    ap.add_argument('--out', required=True)
    args = ap.parse_args()

    missing = [m for m in json.load(open(os.path.join(args.work, 'missing.json'), encoding='utf-8'))
               if not m['notification_found']]
    per_day = {}
    entries = []
    for item in missing:
        day = dt.date.fromisoformat(item['date'])
        order = per_day.get(item['date'], 0)
        per_day[item['date']] = order + 1
        when = dt.datetime(day.year, day.month, day.day, 12, 0, tzinfo=KST) + dt.timedelta(minutes=order)
        ts = int(when.timestamp() * 1000)
        amount = item['amount']
        type_text = '취소' if amount < 0 else '승인'
        text = f"{item['company']} {type_text} {abs(amount):,}원 {item['merchant']}"
        entries.append({
            'ts': ts, 'pkg': 'com.example.mycard.manual', 'title': f"[수동입력] {item['company']}",
            'text': text, 'bigText': f"{text} @ {when:%Y-%m-%d %H:%M}", 'subText': '', 'category': '',
            'channelId': 'manual', 'rawExtras': '{}', 'amount': amount, 'merchant': item['merchant'],
            'filterId': item['filterId'], 'parsedAt': ts,
        })

    src = open(args.raw, encoding='utf-8').read()
    with open(args.out, 'w', encoding='utf-8', newline='\n') as out:
        out.write(src if src.endswith('\n') or not src else src + '\n')
        for entry in entries:
            out.write(json.dumps(entry, ensure_ascii=False, indent=2) + '\n')
    print(f"수동 항목 {len(entries)}건 / 합계 {sum(e['amount'] for e in entries):,}원 → {args.out}")
    for entry in entries:
        print(f"  {dt.datetime.fromtimestamp(entry['ts'] / 1000, KST):%m-%d %H:%M} {entry['filterId']} {entry['amount']:,} {entry['merchant']}")


if __name__ == '__main__':
    main()
