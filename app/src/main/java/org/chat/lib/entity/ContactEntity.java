package org.chat.lib.entity;

public class ContactEntity extends BaseIndexPinyinBean {
    private String contact;
    private String iconUrl;
    private String tokenAddress;
    private String friendCode;
    private boolean isTop;

    public String getFriendCode() {
        return friendCode;
    }

    public void setFriendCode(String friendCode) {
        this.friendCode = friendCode;
    }

    public boolean isShowBottom() {
        return showBottom;
    }

    public void setShowBottom(boolean showBottom) {
        this.showBottom = showBottom;
    }

    private boolean showBottom;

    public ContactEntity() {

    }

    public ContactEntity(String contact) {
        this.contact = contact;
    }


    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public String getTokenAddress() {
        return tokenAddress;
    }

    public void setTokenAddress(String tokenAddress) {
        this.tokenAddress = tokenAddress;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public boolean isTop() {
        return isTop;
    }

    @Override
    public String getTarget() {
        return contact;
    }

    public ContactEntity setTop(boolean top) {
        isTop = top;
        return this;
    }

    @Override
    public boolean isNeedToPinyin() {
        return !isTop;
    }


    @Override
    public boolean isShowSuspension() {
        return !isTop;
    }
}
