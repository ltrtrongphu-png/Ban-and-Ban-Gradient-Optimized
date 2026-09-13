package com.example.bab.checks;

/**
 * TOI UU HIEU NANG: cac check chay tren su kien qua day dac (PlayerMoveEvent,
 * PlayerAnimationEvent...) truoc day doc lai plugin.getConfig().getX(...) MOI
 * LAN su kien xay ra - voi server dong nguoi, day la hang tram/nghin lan doc
 * YAML moi giay chi de lay lai 1 gia tri hau nhu khong doi.
 *
 * Cac check trien khai interface nay cache gia tri config vao field ngay tu
 * constructor, va chi doc lai khi loadConfigValues() duoc goi tuong minh (vd
 * tu lenh /bab reload) - thay vi doc lai tu getConfig() moi lan xu ly event.
 *
 * Danh doi: gia tri cache se KHONG tu dong cap nhat neu admin sua config.yml
 * ma quen chay /bab reload. Day la danh doi chap nhan duoc vi /bab reload da
 * la quy trinh chuan de ap dung thay doi config (xem BabCommand).
 */
public interface ConfigReloadable {

    /** Doc lai toan bo gia tri config lien quan va ghi de vao cache noi bo. */
    void loadConfigValues();
}
