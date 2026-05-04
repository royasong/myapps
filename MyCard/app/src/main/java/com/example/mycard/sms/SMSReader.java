package com.example.mycard.sms;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    // 마스킹된 카드번호 뒷 4자리 후보 토큰 (숫자 + '*' 4글자, 적어도 한 자리는 '*')
    private static final Pattern MASKED_LAST4 =
            Pattern.compile("[0-9*]{4}");

    /**
     * Phase 0 probe: try to read Samsung RCS / MaaP store (content://im/chat) from this app's
     * context. Logs row count or exception under tag "SACH" so the result can be inspected via
     * `adb logcat -s SACH`. No data side-effects.
     */
    public static void probeRcsAccess(Context context) {
        final String TAG = "SACH";
        Uri uri = Uri.parse("content://im/chat");
        Log.i(TAG, "RCS probe begin: uri=" + uri + ", pkg=" + context.getPackageName());
        try {
            ContentResolver cr = context.getContentResolver();
            try (Cursor c = cr.query(
                    uri,
                    new String[]{"_id", "address", "date", "content_type"},
                    null,
                    null,
                    "date DESC"
            )) {
                if (c == null) {
                    Log.i(TAG, "RCS probe: cursor=null (provider rejected without throwing)");
                    return;
                }
                int total = c.getCount();
                Log.i(TAG, "RCS probe: rowCount=" + total + ", colCount=" + c.getColumnCount());
                int sampled = 0;
                while (c.moveToNext() && sampled < 3) {
                    StringBuilder sb = new StringBuilder("RCS probe row[" + sampled + "]: ");
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        sb.append(c.getColumnName(i)).append('=');
                        try {
                            sb.append(c.getString(i));
                        } catch (Exception e) {
                            sb.append("<err:").append(e.getClass().getSimpleName()).append('>');
                        }
                        sb.append(' ');
                    }
                    Log.i(TAG, sb.toString());
                    sampled++;
                }
            }
        } catch (SecurityException se) {
            Log.i(TAG, "RCS probe SecurityException: " + se.getMessage());
        } catch (Exception e) {
            Log.i(TAG, "RCS probe " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public static List<SmsGroup> readCardApprovalGrouped(Context context) {
        Map<String, List<SmsItem>> groupMap = new LinkedHashMap<>();

        // 설정에서 카드그룹 가져오기
        String cardGroupStr = getCardGroup(context);
        String[] cardGroups = cardGroupStr.isEmpty() ? new String[0] : cardGroupStr.split("\n");

        Uri uri = Uri.parse("content://sms/inbox");
        ContentResolver cr = context.getContentResolver();

        // 이번 달 1일 00:00:00의 타임스탬프 구하기
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long startTime = cal.getTimeInMillis();

        // 쿼리 조건 설정
        String selection = "address = ? AND date >= ? AND (body LIKE '[Web발신]%' OR body LIKE '네이버 %')";
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
        
        for (String group : cardGroups) {
            String trimmed = group.trim();
            if (!trimmed.isEmpty()) {
                String[] parts = trimmed.split(",");
                if (parts.length >= 2) {
                    String targetNumber = parts[0].trim();
                    String configId = parts[1].trim();
                    String cardLast4 = parts.length >= 3 ? parts[2].trim() : "";
                    String[] selectionArgs = new String[]{ targetNumber, String.valueOf(startTime)};

                    try (Cursor c = cr.query(
                                        uri,
                                        new String[]{"address", "date", "body"},
                                        selection,      // 조건절 추가
                                        selectionArgs,  // 파라미터 추가
                                        "date DESC"
                    )) {
                        if (c != null) {
                            while (c.moveToNext()) {

                                String address = c.getString(0);
                                long timeStamp = c.getLong(1);
                                String body = c.getString(2);

                                // [Web발신] 이후 모든 공백 제거
                                String trimmedBody = body
                                        .replaceFirst("^\\[Web발신\\]\\s*", "")
                                        .replaceFirst("^네이버\\s+", "")
                                        .replaceAll("\\s+", "");
                                
                                if(!trimmedBody.contains(configId.trim())) continue;

                                if (!cardLast4.isEmpty() && !maskedCardLast4Matches(trimmedBody, cardLast4)) continue;

                                // "자동결제" 또는 "자동 결제" → "승인"으로 대체
                                String processedBody = trimmedBody.replace("자동결제", "승인").replace("자동 결제", "승인");

                                // "승인", "취소", 또는 금액(숫자+원)이 포함된 문자만
                                boolean hasApproval = processedBody.contains("승인");
                                boolean hasCancel = processedBody.contains("취소");
                                boolean hasAmount = Pattern.compile("\\d[\\d,]*원").matcher(processedBody).find();

                                if (!hasApproval && !hasCancel && !hasAmount) continue;

                                // "취소" 문구 포함 여부 확인
                                boolean isCancel = processedBody.contains("취소");

                                // 금액 추출 (취소→취소패턴, 승인/자동결제→승인패턴)
                                Pattern amountPattern;
                                if (isCancel) {
                                    amountPattern = CANCEL_AMOUNT_PATTERN;
                                } else {
                                    amountPattern = AMOUNT_PATTERN;  // "자동결제" → "승인" 대체 후 승인 패턴 사용
                                }

                                Matcher amountMatcher = amountPattern.matcher(processedBody);
                                if (!amountMatcher.find()) continue;
                                long amount = Long.parseLong(amountMatcher.group(1).replace(",", ""));

                                // 취소 금액은 음수로 저장
                                long finalAmount = isCancel ? -amount : amount;
                                String date = fmt.format(new Date(timeStamp));
                                SmsItem item = new SmsItem(address, date, body, finalAmount);

                                Log.i("SACH", "processedBody=" + processedBody + ", finalAmount=" + finalAmount);

                                String key = groupKeyFor(configId, cardLast4);
                                if (!groupMap.containsKey(key)) {
                                    groupMap.put(key, new ArrayList<>());
                                }
                                groupMap.get(key).add(item);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    // RCS / MaaP openrichcard (Samsung im/chat) 동일 발신번호 추가 조회
                    addRcsApprovals(cr, fmt, targetNumber, configId, cardLast4, startTime, groupMap);
                }
            }
        }

        return toGroupList(groupMap);
    }

    private static final String RCS_OPENRICHCARD = "application/vnd.gsma.openrichcard.v1.0+json";
    private static final Uri RCS_URI = Uri.parse("content://im/chat");

    /**
     * Samsung MaaP openrichcard 형식의 RCS 카드 승인 메시지를 읽어 groupMap에 합산한다.
     * 본문 JSON의 layout 트리에서 TextView text를 깊이우선 수집한 뒤, "금액" 라벨 다음 값에서
     * 금액을 추출. 모든 RCS 카드는 "승인"으로 가정 (취소는 SMS로만 들어오는 것으로 확인됨).
     */
    private static void addRcsApprovals(ContentResolver cr,
                                        SimpleDateFormat fmt,
                                        String targetNumber,
                                        String configId,
                                        String cardLast4,
                                        long startTime,
                                        Map<String, List<SmsItem>> groupMap) {
        String selection = "address = ? AND date >= ? AND content_type = ?";
        String[] args = new String[]{ targetNumber, String.valueOf(startTime), RCS_OPENRICHCARD };
        try (Cursor c = cr.query(
                RCS_URI,
                new String[]{"address", "date", "body"},
                selection, args, "date DESC"
        )) {
            if (c == null) return;
            while (c.moveToNext()) {
                String address = c.getString(0);
                long timeStamp = c.getLong(1);
                String body = c.getString(2);

                List<String> texts = extractRichCardTexts(body);
                if (texts.isEmpty()) continue;

                String concat = String.join("", texts).replaceAll("\\s+", "");
                if (!concat.contains(configId)) continue;

                if (!cardLast4.isEmpty()) {
                    String cardField = findValueAfterLabel(texts, "카드");
                    String haystack = !cardField.isEmpty() ? cardField : concat;
                    if (!maskedCardLast4Matches(haystack, cardLast4)) continue;
                }

                Long labelAmount = findAmountAfterLabel(texts, "금액");
                long finalAmount;
                String displayBody;

                if (labelAmount != null) {
                    String merchant = findValueAfterLabel(texts, "사용처");
                    String txTime = findValueAfterLabel(texts, "거래시간");
                    String cardLabel = findValueAfterLabel(texts, "카드");
                    String txKind = findValueAfterLabel(texts, "거래구분");
                    String accumulated = findValueAfterLabel(texts, "누적금액");

                    StringBuilder sb = new StringBuilder("[Web발신] ");
                    if (!cardLabel.isEmpty()) sb.append(cardLabel).append(' ');
                    sb.append("승인 ");
                    if (!txKind.isEmpty()) sb.append(txKind).append(' ');
                    sb.append(String.format(Locale.KOREA, "%,d", labelAmount)).append("원");
                    if (!txTime.isEmpty()) sb.append(' ').append(txTime);
                    if (!merchant.isEmpty()) sb.append(' ').append(merchant);
                    if (!accumulated.isEmpty()) sb.append(" 누적 ").append(accumulated);
                    displayBody = sb.toString();
                    finalAmount = labelAmount;
                } else {
                    boolean concatHasCancel = concat.contains("취소");
                    String processedConcat = concat.replace("자동결제", "승인").replace("자동 결제", "승인");
                    Pattern p = concatHasCancel ? CANCEL_AMOUNT_PATTERN : AMOUNT_PATTERN;
                    Matcher m = p.matcher(processedConcat);
                    if (!m.find()) continue;
                    long parsed;
                    try {
                        parsed = Long.parseLong(m.group(1).replace(",", ""));
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    finalAmount = concatHasCancel ? -parsed : parsed;
                    displayBody = !texts.isEmpty() ? "[Web발신] " + texts.get(0) : concat;
                }

                String date = fmt.format(new Date(timeStamp));
                SmsItem item = new SmsItem(address, date, displayBody, finalAmount);

                String key = groupKeyFor(configId, cardLast4);

                Log.i("SACH", "RCS approval: key=" + key
                        + " amount=" + finalAmount
                        + " merchant=" + findValueAfterLabel(texts, "사용처")
                        + " time=" + findValueAfterLabel(texts, "거래시간"));

                if (!groupMap.containsKey(key)) {
                    groupMap.put(key, new ArrayList<SmsItem>());
                }
                groupMap.get(key).add(item);
            }
        } catch (Exception e) {
            Log.i("SACH", "RCS query failed for " + configId + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static List<String> extractRichCardTexts(String body) {
        List<String> result = new ArrayList<>();
        try {
            JsonElement root = JsonParser.parseString(body);
            collectTextDfs(root, result);
        } catch (Exception ignored) {
        }
        return result;
    }

    private static void collectTextDfs(JsonElement el, List<String> out) {
        if (el == null || el.isJsonNull()) return;
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("widget") && obj.has("text")) {
                JsonElement w = obj.get("widget");
                JsonElement t = obj.get("text");
                if (w.isJsonPrimitive() && t.isJsonPrimitive()
                        && "TextView".equals(w.getAsString())) {
                    out.add(t.getAsString());
                }
            }
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                collectTextDfs(e.getValue(), out);
            }
        } else if (el.isJsonArray()) {
            for (JsonElement child : el.getAsJsonArray()) {
                collectTextDfs(child, out);
            }
        }
    }

    private static Long findAmountAfterLabel(List<String> texts, String label) {
        for (int i = 0; i < texts.size() - 1; i++) {
            if (label.equals(texts.get(i))) {
                String value = texts.get(i + 1).replaceAll("\\s+", "");
                Matcher m = DEFAULT_AMOUNT_PATTERN.matcher(value);
                if (m.find()) {
                    try {
                        return Long.parseLong(m.group(1).replace(",", ""));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return null;
    }

    private static String findValueAfterLabel(List<String> texts, String label) {
        for (int i = 0; i < texts.size() - 1; i++) {
            if (label.equals(texts.get(i))) return texts.get(i + 1);
        }
        return "";
    }

    /**
     * 본문에 등장하는 4글자 마스킹 카드번호 후보 토큰 중 하나라도, 사용자가 입력한 last4(예: "2222")와
     * 자릿수별로 일치하는지 검사. 마스킹 토큰의 '*' 위치는 wildcard로 간주한다.
     * 예) masked="0*7*", last4="1111" → pos0 '0'='0', pos1 '*' wild, pos2 '7'='7', pos3 '*' wild → match.
     */
    private static boolean maskedCardLast4Matches(String text, String last4) {
        if (last4 == null || last4.length() != 4 || text == null) return false;
        Matcher m = MASKED_LAST4.matcher(text);
        while (m.find()) {
            String token = m.group();
            int stars = 0;
            for (int i = 0; i < token.length(); i++) if (token.charAt(i) == '*') stars++;
            if (stars < 2) continue;                    // 정상 카드 마스킹은 2-2 비율 (별 2개 이상만 유효)
            if (positionMatch(token, last4)) return true;
        }
        return false;
    }

    /** 그룹 표시 키. last4가 있으면 "id(last4)" 형태로 분리 표시. */
    private static String groupKeyFor(String configId, String cardLast4) {
        if (cardLast4 == null || cardLast4.isEmpty()) return configId;
        return configId + "(" + cardLast4 + ")";
    }

    private static boolean positionMatch(String masked, String last4) {
        for (int i = 0; i < 4; i++) {
            char mc = masked.charAt(i);
            if (mc == '*') continue;
            if (mc != last4.charAt(i)) return false;
        }
        return true;
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
