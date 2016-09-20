package nablarch.common.mail;

import nablarch.core.util.annotation.Published;

/**
 * メールのデフォルト設定を保持するデータオブジェクト。
 * 
 * @author Shinsuke Yoshio
 */
@Published(tag = "architect")
public class MailRequestConfig {

    /** デフォルトの返信先メールアドレス */
    private String defaultReplyTo;

    /** デフォルトの差し戻し先メールアドレス */
    private String defaultReturnPath;

    /** デフォルトの文字セット */
    private String defaultCharset;

    /** 最大宛先数 */
    private int maxRecipientCount = 100;

    /** 最大添付ファイルサイズ */
    private int maxAttachedFileSize = 2097152;

    /**
     * デフォルトの返信先メールアドレスを取得する。
     * 
     * @return デフォルトの返信先メールアドレス
     */
    public String getDefaultReplyTo() {
        return defaultReplyTo;
    }

    /**
     * デフォルトの返信先メールアドレスを設定する。
     * 
     * @param defaultReplyTo
     *            デフォルトの返信先メールアドレス
     */
    public void setDefaultReplyTo(String defaultReplyTo) {
        this.defaultReplyTo = defaultReplyTo;
    }

    /**
     * デフォルトの差し戻し先メールアドレスを取得する。
     * 
     * @return デフォルトの差し戻し先メールアドレス
     */
    public String getDefaultReturnPath() {
        return defaultReturnPath;
    }

    /**
     * デフォルトの差し戻し先メールアドレスを設定する。
     * 
     * @param defaultReturnPath
     *            デフォルトのメール差し戻し先
     */
    public void setDefaultReturnPath(String defaultReturnPath) {
        this.defaultReturnPath = defaultReturnPath;
    }

    /**
     * デフォルトの文字セットを取得する。
     * 
     * @return デフォルトの文字セット
     */
    public String getDefaultCharset() {
        return defaultCharset;
    }

    /**
     * デフォルトの文字セットを設定する。
     * 
     * @param defaultCharset
     *            デフォルトの文字セット
     */
    public void setDefaultCharset(String defaultCharset) {
        this.defaultCharset = defaultCharset;
    }

    /**
     * 最大宛先数を取得する。
     * 
     * @return 最大宛先数
     */
    public int getMaxRecipientCount() {
        return maxRecipientCount;
    }

    /**
     * 最大宛先数を設定する。
     * 
     * @param maxRecipientCount
     *            最大宛先数
     */
    public void setMaxRecipientCount(int maxRecipientCount) {
        this.maxRecipientCount = maxRecipientCount;
    }

    /**
     * 最大添付ファイルサイズを取得する。
     * 
     * @return 最大添付ファイルサイズ
     */
    public int getMaxAttachedFileSize() {
        return maxAttachedFileSize;
    }

    /**
     * 最大添付ファイルサイズを設定する。
     * 
     * @param maxAttachedFileSize
     *            最大添付ファイルサイズ
     */
    public void setMaxAttachedFileSize(int maxAttachedFileSize) {
        this.maxAttachedFileSize = maxAttachedFileSize;
    }

}
