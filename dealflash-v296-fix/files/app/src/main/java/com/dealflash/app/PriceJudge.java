package com.dealflash.app;

/**
 * "Is it really cheap?" verdict from the server's price history (min / average / count / first sighting over 30 days).
 * Pure logic (no Android types). The history is event-based (a row when the price changes plus a 6-hourly survival row),
 * so the average is a "record average", not a time-weighted one; the wording says "기록" accordingly.
 */
public final class PriceJudge {
    private PriceJudge() {}
    public static final int NONE = 0, COLLECTING = 1, LOWEST = 2, CHEAP = 3, NORMAL = 4, PRICEY = 5, INFLATED = 6;
    public static final int MIN_ROWS = 3;
    public static final long MIN_SPAN_MS = 12L * 60 * 60 * 1000;

    public static final class Verdict {
        public int level; public String label = ""; public String shortLabel = ""; public String detail = ""; public int vsAvgPct;
    }

    public static Verdict judge(double price, double histMin, double histAvg, int histN, long histFirst, long now, int declaredPct) {
        Verdict v = new Verdict();
        if (!(price > 0)) { v.level = NONE; return v; }
        long span = histFirst > 0 ? Math.max(0, now - histFirst) : 0;
        if (histN < MIN_ROWS || !(histAvg > 0) || span < MIN_SPAN_MS) {
            v.level = COLLECTING; v.label = "가격 기록 수집 중"; v.shortLabel = "기록 수집 중";
            v.detail = histN > 0 ? "기록 " + histN + "회 · 쌓일수록 정확해져요" : "아직 기록이 없어요";
            return v;
        }
        int days = (int) Math.max(1, Math.round(span / 86_400_000.0));
        String period = span >= 2 * 86_400_000L ? "최근 " + days + "일" : "기록 " + histN + "회";
        int vs = (int) Math.round((1.0 - price / histAvg) * 100.0);
        v.vsAvgPct = vs;
        if (price <= histMin * 1.0001 && histMin < histAvg * 0.97) {
            v.level = LOWEST; v.label = "기록상 최저가"; v.shortLabel = "기록상 최저"; v.detail = period + " 중 가장 낮아요 · 평균보다 " + vs + "% 저렴";
        } else if (vs >= 10) {
            v.level = CHEAP; v.label = "평소보다 " + vs + "% 저렴"; v.shortLabel = "평소 -" + vs + "%"; v.detail = period + " 기록 평균 " + Math.round(histAvg) + "원 대비";
        } else if (vs > -10) {
            if (declaredPct >= 50) { v.level = INFLATED; v.label = "표시 할인율은 크지만 평소 가격과 비슷해요"; v.shortLabel = "평소 가격과 비슷"; v.detail = "표시 " + declaredPct + "% 할인 · " + period + " 기록 평균과 거의 같아요"; }
            else { v.level = NORMAL; v.label = "평소 가격 수준"; v.shortLabel = "평소 수준"; v.detail = period + " 기록 평균 " + Math.round(histAvg) + "원 부근"; }
        } else {
            v.level = PRICEY; v.label = "평소보다 " + (-vs) + "% 비싸요"; v.shortLabel = "평소 +" + (-vs) + "%"; v.detail = period + " 기록 평균 " + Math.round(histAvg) + "원 대비";
        }
        return v;
    }
}
