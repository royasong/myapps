package com.example.mycard.ui.theme;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SMSReader {

    public static class SmsItem {
        public final String address;
        public final String date;
        public final String body;
        public final long amount;

        public SmsItem(String address, String date, String body, long amount) {
            this.address = address;
            this.date = date;
            this.body = body;
            this.amount = amount;
        }
    }

    public static class SmsGroup {
        public final String id;
        public final long totalAmount;
        public final List<SmsItem> items;

        public SmsGroup(String id, long totalAmount, List<SmsItem> items) {
            this.id = id;
            this.totalAmount = totalAmount;
            this.items = items;
        }
    }

    // "승인" 이후의 첫 번째 금액(숫자+콤마+원) 추출
    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("승인[^\\d]*(\\d[\\d,]*)원");

    // "취소" 이후의 첫 번째 금액(숫자+콤마+원) 추출
    private static final Pattern CANCEL_AMOUNT_PATTERN =
            Pattern.compile("취소[^\\d]*(\\d[\\d,]*)원");

    // 기본 금액 패턴 (자동결제 등 "승인"/"취소" 없는 경우)
    private static final Pattern DEFAULT_AMOUNT_PATTERN =
            Pattern.compile("(\\d[\\d,]*)원");

    // 조회할 받은 문자함 URI 목록
    private static final List<Uri> INBOX_URIS = Arrays.asList(
            Uri.parse("content://im/chat")
    );

    public static List<SmsGroup> readCardApprovalGrouped(Context context) {
        Map<String, List<SmsItem>> groupMap = new LinkedHashMap<>();

        // 설정에서 카드그룹 가져오기
        String cardGroupStr = getCardGroup(context);
        String[] cardGroups = cardGroupStr.isEmpty() ? new String[0] : cardGroupStr.split("\n");

        ContentResolver cr = context.getContentResolver();

        // 이번 달 1일 00:00:00의 타임스탬프 구하기
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long startTime = cal.getTimeInMillis();

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);

        for (String group : cardGroups) {
            String trimmed = group.trim();
            if (!trimmed.isEmpty()) {
                String[] parts = trimmed.split(",");
                if (parts.length >= 2) {
                    String targetNumber = parts[0].trim();
                    String configId = parts[1].trim();
                    String[] selectionArgs = new String[]{ targetNumber, String.valueOf(startTime)};

                    Set<String> seenKeys = new HashSet<>();

                    for (Uri uri : INBOX_URIS) {
                        boolean isRcs = "im".equals(uri.getAuthority());
                        // RCS body는 JSON 포맷 → [Web발신] 포함 여부는 코드에서 처리
                        String uriSelection = isRcs
                                ? "address = ? AND date >= ?"
                                : "address = ? AND date >= ? AND body LIKE '%[Web발신]%'";

                        try (Cursor c = cr.query(uri, null, uriSelection, selectionArgs, "date DESC")) {
                            if (c == null) continue;

                            int addrIdx = c.getColumnIndex("address");
                            int dateIdx = c.getColumnIndex("date");
                            int bodyIdx = -1;
                            for (String col : new String[]{"body", "text", "content", "message"}) {
                                int idx = c.getColumnIndex(col);
                                if (idx >= 0) { bodyIdx = idx; break; }
                            }
                            if (addrIdx < 0 || dateIdx < 0 || bodyIdx < 0) continue;

                            while (c.moveToNext()) {
                                String address = c.getString(addrIdx);
                                long timeStamp = c.getLong(dateIdx);
                                String body = c.getString(bodyIdx);
                                if (body == null) continue;

                                // URI 간 중복 제거
                                String dedupKey = address + "_" + timeStamp;
                                if (!seenKeys.add(dedupKey)) continue;

                                // [Web발신] 미포함 메시지 제외
                                if (!body.contains("[Web발신]")) continue;

                                // [Web발신] 이후 모든 공백 제거
                                String trimmedBody = body.replaceFirst("\\[Web발신\\]\\s*", "").replaceAll("\\s+", "");

                                if (!trimmedBody.contains(configId.trim())) continue;

                                // "자동결제" 또는 "자동 결제" → "승인"으로 대체
                                String processedBody = trimmedBody.replace("자동결제", "승인").replace("자동 결제", "승인");

                                // "승인", "취소", 또는 금액(숫자+원)이 포함된 문자만
                                boolean hasApproval = processedBody.contains("승인");
                                boolean hasCancel = processedBody.contains("취소");
                                boolean hasAmount = Pattern.compile("\\d[\\d,]*원").matcher(processedBody).find();

                                if (!hasApproval && !hasCancel && !hasAmount) continue;

                                boolean isCancel = processedBody.contains("취소");

                                Pattern amountPattern = isCancel ? CANCEL_AMOUNT_PATTERN : AMOUNT_PATTERN;
                                Matcher amountMatcher = amountPattern.matcher(processedBody);
                                if (!amountMatcher.find()) continue;
                                long amount = Long.parseLong(amountMatcher.group(1).replace(",", ""));

                                long finalAmount = isCancel ? -amount : amount;
                                String date = fmt.format(new Date(timeStamp));
                                SmsItem item = new SmsItem(address, date, body, finalAmount);

                                if (!groupMap.containsKey(configId)) {
                                    groupMap.put(configId, new ArrayList<>());
                                }
                                groupMap.get(configId).add(item);
                            }
                        } catch (Exception e) {
                            // ignored
                        }
                    }
                }
            }
        }

        List<SmsGroup> result = toGroupList(groupMap);

        // 설정 입력 순서대로 명시적 정렬
        Map<String, Integer> orderMap = new LinkedHashMap<>();
        for (int i = 0; i < cardGroups.length; i++) {
            String t = cardGroups[i].trim();
            if (!t.isEmpty()) {
                String[] p = t.split(",");
                if (p.length >= 2) orderMap.put(p[1].trim(), i);
            }
        }
        result.sort((a, b) ->
            Integer.compare(
                orderMap.getOrDefault(a.id, Integer.MAX_VALUE),
                orderMap.getOrDefault(b.id, Integer.MAX_VALUE)
            )
        );
        return result;
    }

    // 설정에서 카드그룹 문자열 가져오기 (전화번호,ID 형식)
    private static String getCardGroup(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("mycard_prefs", Context.MODE_PRIVATE);
        return prefs.getString("cardGroup", "");
    }

    // 전화번호로 ID 찾기 (구분 키워드 포함)
    private static String findCardIdByPhone(String phone, String body, String[] cardGroups) {
        String lowerBody = body.toLowerCase();
        String firstMatchId = null;
        
        for (String group : cardGroups) {
            String trimmed = group.trim();
            if (trimmed.isEmpty()) continue;
            
            String[] parts = trimmed.split(",");
            if (parts.length >= 2) {
                String configPhone = parts[0].trim();
                String configId = parts[1].trim();
                String keyword = parts.length >= 3 ? parts[2].trim().toLowerCase() : "";
                
                // 전화번호 매칭 (부분 일치)
                boolean phoneMatch = phone.contains(configPhone) || configPhone.contains(phone);
                
                if (phoneMatch) {
                    // 구분 키워드가 있으면 본문에서 확인
                    if (!keyword.isEmpty()) {
                        if (lowerBody.contains(keyword)) {
                            return configId;
                        }
                        // 키워드가 없으면 일단 첫 번째 매칭으로 저장
                        if (firstMatchId == null) {
                            firstMatchId = configId;
                        }
                    } else {
                        // 키워드 없으면 바로 반환
                        return configId;
                    }
                }
            }
        }
        
        // 키워드가 있는 경우, 매칭되는 키워드가 없으면 첫 번째 매칭 반환
        return firstMatchId;
    }

    // SMS 본문에서 카드그룹 찾기 (하위 호환)
    private static String findCardGroup(String body, String[] cardGroups, String defaultId) {
        // 대소문자 구분 없이 비교
        String lowerBody = body.toLowerCase();

        for (String group : cardGroups) {
            String trimmed = group.trim();
            if (!trimmed.isEmpty() && lowerBody.contains(trimmed.toLowerCase())) {
                return trimmed;
            }
        }
        return null;
    }

    private static List<SmsGroup> toGroupList(Map<String, List<SmsItem>> groupMap) {
        List<SmsGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<SmsItem>> entry : groupMap.entrySet()) {
            long total = 0;
            for (SmsItem item : entry.getValue()) {
                total += item.amount;
            }
            result.add(new SmsGroup(entry.getKey(), total, entry.getValue()));
        }
        return result;
    }
}
