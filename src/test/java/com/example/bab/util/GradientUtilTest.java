package com.example.bab.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test cho GradientUtil. Vi ChatColor.of(hex) sinh ra ma mau dang "§x§R§R§G§G§B§B"
 * (ky tu section '§' + 1 ky tu, lap lai nhieu lan), cach kiem tra ben duoi la
 * BOC HET moi cap "§<ky tu>" khoi chuoi ket qua - phan con lai PHAI khop chinh
 * xac voi text goc. Cach nay xac minh dung THU TU/DO DAI ky tu hien thi duoc
 * giu nguyen, ma khong phu thuoc vao dinh dang ma mau chinh xac cua bungee.
 */
class GradientUtilTest {

    /** Bo het ma mau (moi ma la 1 cap "section-sign + 1 ky tu") khoi chuoi. */
    private static String stripColorCodes(String s) {
        return s.replaceAll("\u00A7.", "");
    }

    @Test
    void nullText_returnsNull() {
        assertNull(GradientUtil.gradient(null, "#FF0000", "#FFFF00"));
    }

    @Test
    void emptyText_returnsEmpty() {
        assertEquals("", GradientUtil.gradient("", "#FF0000", "#FFFF00"));
    }

    @Test
    void fewerThanTwoStops_returnsTextUnchanged() {
        // Chi 1 mau (hoac 0) khong du de tao gradient - phai tra ve nguyen van,
        // KHONG chen bat ky ma mau nao.
        String result = GradientUtil.gradient("XAC MINH", "#FF0000");
        assertEquals("XAC MINH", result);
    }

    @Test
    void twoColorGradient_preservesCharacterOrderAndSpaces() {
        String input = "XAC MINH";
        String result = GradientUtil.gradient(input, "#FF0000", "#FFFF00");

        assertEquals(input, stripColorCodes(result),
                "Sau khi bo ma mau, ky tu hien thi (ke ca dau cach) phai khop y het text goc");
    }

    @Test
    void threeColorGradient_preservesCharacterOrder() {
        String input = "DO CAM VANG";
        String result = GradientUtil.gradient(input, "#FF0000", "#FF8800", "#FFFF00");

        assertEquals(input, stripColorCodes(result));
    }

    @Test
    void singleCharacterText_doesNotThrow() {
        // len <= 1 la truong hop bien (chia cho len-1 = 0) - phai duoc xu ly rieng,
        // khong duoc nem ArithmeticException/chia cho 0.
        String result = GradientUtil.gradient("A", "#FF0000", "#FFFF00");
        assertEquals("A", stripColorCodes(result));
    }

    @Test
    void sameStartAndEndColor_stillPreservesText() {
        String input = "ABC";
        String result = GradientUtil.gradient(input, "#00FF00", "#00FF00");
        assertEquals(input, stripColorCodes(result));
    }
}
