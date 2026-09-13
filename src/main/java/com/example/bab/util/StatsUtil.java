package com.example.bab.util;

/**
 * Toan hoc thuan tuy (khong phu thuoc Bukkit), tach ra tu logic tinh he so bien
 * thien (CV - coefficient of variation) truoc day BI TRUNG LAP nguyen van giua
 * KillAuraCheck (do deu khoang cach click) va RotationCheck (do deu goc xoay).
 * Tach rieng giup: (1) khong con trung lap code, (2) VIET DUOC UNIT TEST that su
 * cho phan toan hoc nay ma khong can gia lap Player/Bukkit server.
 */
public final class StatsUtil {

    private StatsUtil() {
    }

    /** Ket qua thong ke: trung binh cong va he so bien thien (do lech chuan / trung binh). */
    public static final class Result {
        public final double mean;
        public final double coefficientOfVariation;

        public Result(double mean, double coefficientOfVariation) {
            this.mean = mean;
            this.coefficientOfVariation = coefficientOfVariation;
        }
    }

    /**
     * Tinh trung binh cong va he so bien thien (CV) cua mot day gia tri.
     * CV thap = cac gia tri gan nhu bang nhau (qua deu, dang ngo voi hanh vi con
     * nguoi von luon co dao dong nho); CV cao = dao dong tu nhien binh thuong.
     *
     * @throws IllegalArgumentException neu values rong, hoac trung binh <= 0
     *         (CV khong co y nghia/chia cho 0 khi trung binh bang 0).
     */
    public static Result coefficientOfVariation(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values khong duoc rong");
        }

        double mean = 0;
        for (double v : values) mean += v;
        mean /= values.length;

        if (mean <= 0) {
            throw new IllegalArgumentException("trung binh phai > 0 de tinh CV");
        }

        double variance = 0;
        for (double v : values) variance += Math.pow(v - mean, 2);
        variance /= values.length;

        double cv = Math.sqrt(variance) / mean;
        return new Result(mean, cv);
    }
}
