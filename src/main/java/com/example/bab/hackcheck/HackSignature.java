package com.example.bab.hackcheck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dai dien cho 1 "chu ky" hack can quet: ten hien thi + DANH SACH translation
 * key ma chi client co cai mod tuong ung moi "dich" duoc thanh chu that.
 *
 * NANG CAP (do chinh xac): ban truoc CHI co 1 key duy nhat/mod. Neu 1 ban cap
 * nhat cua mod do doi/xoa dung key ma minh dang kiem tra (hoac minh chi biet 1
 * trong nhieu key mod do dang ky), scan se BO LOT hoan toan mod that su dang
 * chay. Gio moi HackSignature co the giu NHIEU key (vi du Meteor Client dang ky
 * ca "key.meteor-client.open-gui" LAN "key.meteor-client.open-commands" - da
 * xac minh truc tiep tu file lang/en_us.json that trong ban mod nguon mo) -
 * CHI CAN 1 TRONG SO CAC KEY khop la du de flag mod do, tang do bao phu ma
 * khong giam do chinh xac (van la khop CHINH XAC voi 1 key co that).
 */
public class HackSignature {

    private final String id;
    private final String displayName;
    private final List<String> translateKeys;

    public HackSignature(String id, String displayName, String translateKey) {
        this(id, displayName, Collections.singletonList(translateKey));
    }

    public HackSignature(String id, String displayName, List<String> translateKeys) {
        this.id = id;
        this.displayName = displayName;
        this.translateKeys = new ArrayList<>(translateKeys);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Giu lai de tuong thich code cu - tra ve key DAU TIEN. */
    public String getTranslateKey() {
        return translateKeys.get(0);
    }

    public List<String> getTranslateKeys() {
        return Collections.unmodifiableList(translateKeys);
    }
}

