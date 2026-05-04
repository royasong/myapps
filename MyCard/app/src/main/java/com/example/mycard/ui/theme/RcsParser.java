package com.example.mycard.ui.theme;


import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

public class RcsParser {

    public static class Result {
        public String amount;
        public String place;
        public String date;
        public String type; // 승인/취소
    }

    public static String parse(String jsonStr) {
        Result result = new Result();

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject layout = root.optJSONObject("layout");
            if (layout == null) return "";

            JSONArray childrenLv1 = layout.optJSONArray("children");
            if (childrenLv1 == null) return "";

            for (int i = 0; i < childrenLv1.length(); i++) {
                JSONObject lv1 = childrenLv1.getJSONObject(i);
                JSONArray childrenLv2 = lv1.optJSONArray("children");
                if (childrenLv2 == null) continue;

                for (int j = 0; j < childrenLv2.length(); j++) {
                    JSONObject row = childrenLv2.getJSONObject(j);
                    JSONArray pair = row.optJSONArray("children");
                    if (pair == null || pair.length() < 2) continue;

                    String key = pair.getJSONObject(0).optString("text");
                    String value = pair.getJSONObject(1).optString("text");

                    if (key == null || value == null) continue;

                    switch (key) {
                        case "금액":
                            result.amount = value;
                            break;
                        case "사용처":
                            result.place = value;
                            break;
                        case "거래시간":
                            result.date = value;
                            break;
                        case "거래종류":
                            result.type = value; // 보통 "신용"
                            break;
                    }
                }
            }

            // 승인/취소 판별 로직 (보강)
            if (result.type != null) {
                if (result.type.contains("취소")) {
                    result.type = "취소";
                } else {
                    result.type = "승인";
                }
            } else {
                result.type = "승인"; // 기본값
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
/*
        Log.d("ROYA_RESULT", "금액: " + r.amount);
        Log.d("ROYA_RESULT", "사용처: " + r.place);
        Log.d("ROYA_RESULT", "날짜: " + r.date);
        Log.d("ROYA_RESULT", "구분: " + r.type);
*/
        if (!result.amount.isEmpty() && !result.place.isEmpty() && !result.date.isEmpty() && !result.type.isEmpty()) {
            String body = "[Web발신]금액: " + result.type + " " + result.amount + " 사용처: " + result.place + "날짜: " + result.date;
            return body;
        }
        return "";
    }
}
