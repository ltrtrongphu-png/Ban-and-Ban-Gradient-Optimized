package com.example.bab.checks;

/**
 * Cho cac check CHI co 1 co "enabled" duy nhat quyet dinh toan bo hoat dong
 * (khac voi vd MovementCheck/KillAuraCheck/RotationCheck co NHIEU tinh nang
 * doc lap, moi tinh nang tu bat/tat rieng - khong implement interface nay).
 *
 * Dung boi CheckRegistry de QUYET DINH CO DANG KY LISTENER HAY KHONG ngay tu
 * dau, thay vi luon dang ky roi de handler tu kiem tra enabled va return som -
 * cach cu van ton chi phi goi ham + dispatch event moi lan, du check dang tat.
 */
public interface Toggleable {

    /** True neu check dang BAT theo gia tri config da cache (xem ConfigReloadable). */
    boolean isEnabled();
}
