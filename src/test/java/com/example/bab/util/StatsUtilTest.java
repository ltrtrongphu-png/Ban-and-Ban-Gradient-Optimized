package com.example.bab.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test cho phan toan hoc thuan tuy trong StatsUtil - tach ra tu logic phat
 * hien AutoClicker (KillAuraCheck) va Aim-Assist xoay qua deu (RotationCheck),
 * nen cac gia tri thu trong test nay mo phong truc tiep 2 tinh huong do:
 * - Con nguoi that: khoang cach/goc xoay giua cac lan luon dao dong it nhieu.
 * - Macro/aim-assist: gia tri gan nhu giong het nhau moi lan (CV rat thap).
 */
class StatsUtilTest {

    @Test
    void perfectlyUniformValues_haveZeroCv() {
        // Macro ly tuong: click cach nhau dung 50ms moi lan - CV phai bang 0.
        double[] values = {50, 50, 50, 50, 50};
        StatsUtil.Result result = StatsUtil.coefficientOfVariation(values);

        assertEquals(50.0, result.mean, 1e-9);
        assertEquals(0.0, result.coefficientOfVariation, 1e-9);
    }

    @Test
    void humanLikeJitter_hasNonZeroCv() {
        // Con nguoi that: dao dong nho quanh ~50ms (macro that su khong the deu bang 0).
        double[] values = {48, 53, 47, 55, 49, 51};
        StatsUtil.Result result = StatsUtil.coefficientOfVariation(values);

        assertTrue(result.coefficientOfVariation > 0.0,
                "Du lieu dao dong tu nhien phai co CV > 0");
    }

    @Test
    void moreUniformSeries_hasLowerCvThanNoisierSeries() {
        // So sanh tuong doi: chuoi deu hon PHAI cho CV thap hon chuoi nhieu hon,
        // du 2 chuoi co cung trung binh - day chinh la thuoc tinh ma anticheat
        // dua vao de phan biet macro (deu) voi nguoi that (dao dong).
        double[] uniform = {50, 50, 50, 50, 50};
        double[] noisy = {40, 60, 45, 55, 50};

        double cvUniform = StatsUtil.coefficientOfVariation(uniform).coefficientOfVariation;
        double cvNoisy = StatsUtil.coefficientOfVariation(noisy).coefficientOfVariation;

        assertTrue(cvUniform < cvNoisy,
                "Chuoi deu hon phai co CV thap hon chuoi dao dong nhieu hon");
    }

    @Test
    void emptyArray_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> StatsUtil.coefficientOfVariation(new double[0]));
    }

    @Test
    void nullArray_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> StatsUtil.coefficientOfVariation(null));
    }

    @Test
    void zeroMean_throwsIllegalArgumentException() {
        // Trung binh = 0 (vd tat ca khoang cach = 0, 2 su kien trung 1 tick) khong
        // co y nghia thong ke - phai nem loi ro rang thay vi chia cho 0 ra NaN/Infinity
        // roi vo tinh so sanh sai lech trong logic flag() cua caller.
        double[] values = {0, 0, 0};
        assertThrows(IllegalArgumentException.class,
                () -> StatsUtil.coefficientOfVariation(values));
    }

    @Test
    void singleValue_hasZeroCv() {
        // 1 mau duy nhat: trung binh = chinh no, phuong sai = 0 -> CV = 0.
        double[] values = {42};
        StatsUtil.Result result = StatsUtil.coefficientOfVariation(values);

        assertEquals(42.0, result.mean, 1e-9);
        assertEquals(0.0, result.coefficientOfVariation, 1e-9);
    }
}
