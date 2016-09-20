package nablarch.common.mail;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import nablarch.core.util.annotation.Published;

/**
 * メール送信要求を表す抽象クラス。
 * </p>
 * 以下の項目は、指定しない場合デフォルト値が使用される。
 * <ul>
 *     <li>返信先メールアドレス：{@link MailRequestConfig#defaultReturnPath}の設定値</li>
 *     <li>差し戻し先メールアドレス：{@link MailRequestConfig#defaultReplyTo}の設定値</li>
 *     <li>Content-Typeヘッダに指定する文字セット：{@link MailRequestConfig#defaultCharset}の設定値</li>
 * </ul>
 * 
 * @author Shinsuke Yoshio
 */
public abstract class MailContext {

    /** 送信元メールアドレス */
    private String from;

    /** 送信先(TO)メールアドレスのリスト */
    private final Set<String> toList = new LinkedHashSet<String>();

    /** 送信先(CC)メールアドレスのリスト */
    private final Set<String> ccList = new LinkedHashSet<String>();

    /** 送信先 (BCC)メールアドレスのリスト */
    private final Set<String> bccList = new LinkedHashSet<String>();

    /** 差し戻し先メールアドレス(Return-Path) */
    private String returnPath;

    /** 返信先メールアドレス(Reply-To) */
    private String replyTo;

    /** 件名 */
    private String subject;

    /** 本文 */
    private String mailBody;

    /** 文字コード */
    private String charset;

    /** 添付ファイルのリスト */
    private final List<AttachedFile> attachedFileList = new ArrayList<AttachedFile>();

    /** メール送信パターンID */
    private String mailSendPatternId;

    /**
     * メール送信要求をバリデーションする。<br/>
     * バリデーション内容は以下の2つ。下記以外は業務アプリにてバリデーション済みの前提。<br/>
     * <ul>
     * <li>宛先の数が0でないか、もしくは上限値を超えていないか。</li>
     * <li>添付ファイルのファイルサイズが上限値を超えていないか。</li>
     * </ul>
     * 
     * @param mailRequestConfig
     *            メールのデフォルト設定
     */
    public void validate(MailRequestConfig mailRequestConfig) {

        // 宛先数チェック
        int maxRecipientCount = mailRequestConfig.getMaxRecipientCount();
        int actualRecipientCount = toList.size() + ccList.size()
                + bccList.size();

        if (actualRecipientCount > maxRecipientCount
                || actualRecipientCount == 0) {
            throw new RecipientCountException(maxRecipientCount,
                    actualRecipientCount);
        }

        // ファイルサイズ上限チェック
        long maxAttachedFileSize = mailRequestConfig.getMaxAttachedFileSize();
        long sumFileSize = 0;
        for (AttachedFile attachedFile : attachedFileList) {
            sumFileSize = sumFileSize + attachedFile.getFile().length();
        }

        if (sumFileSize > maxAttachedFileSize) {
            throw new AttachedFileSizeOverException(maxAttachedFileSize,
                    sumFileSize);
        }

    }

    /**
     * 送信元メールアドレスを取得する。
     * 
     * @return 送信元メールアドレス
     */
    @Published(tag = "architect")
    public String getFrom() {
        return from;
    }

    /**
     * 送信元メールアドレスを設定する。
     * 
     * @param from
     *            送信元メールアドレス
     */
    @Published
    public void setFrom(String from) {
        this.from = from;
    }

    /**
     * 送信先(TO)メールアドレスのリストを取得する。
     * 
     * @return 送信先(TO)メールアドレスのリスト
     */
    @Published(tag = "architect")
    public Set<String> getToList() {
        return toList;
    }

    /**
     * 送信先(TO)メールアドレスを追加する。
     * 
     * @param to
     *            送信先(TO)メールアドレス
     */
    @Published
    public void addTo(String to) {
        this.toList.add(to);
    }

    /**
     * 送信先(CC)メールアドレスのリストを取得する。
     * 
     * @return 送信先(CC)メールアドレスのリスト
     */
    @Published(tag = "architect")
    public Set<String> getCcList() {
        return ccList;
    }

    /**
     * 送信先(CC)メールアドレスを追加する。
     * 
     * @param cc
     *            送信先(CC)メールアドレス
     */
    @Published
    public void addCc(String cc) {
        this.ccList.add(cc);
    }

    /**
     * 送信先(BCC)メールアドレスのリストを取得する。
     * 
     * @return 送信先(BCC)メールアドレス
     */
    @Published(tag = "architect")
    public Set<String> getBccList() {
        return bccList;
    }

    /**
     * 送信先(BCC)メールアドレスを追加する。
     * 
     * @param bcc
     *            送信先(BCC)メールアドレス
     */
    @Published
    public void addBcc(String bcc) {
        this.bccList.add(bcc);
    }

    /**
     * 差し戻し先メールアドレスを取得する。
     * 
     * @return 差し戻し先メールアドレス
     */
    @Published(tag = "architect")
    public String getReturnPath() {
        return returnPath;
    }

    /**
     * 差し戻し先メールアドレスを設定する。
     * 
     * @param returnPath 差し戻し先メールアドレス
     */
    @Published
    public void setReturnPath(String returnPath) {
        this.returnPath = returnPath;
    }

    /**
     * 返信先メールアドレスを取得する。
     *
     * @return 返信先メールアドレス
     */
    @Published(tag = "architect")
    public String getReplyTo() {
        return replyTo;
    }

    /**
     * 返信先メールアドレスを設定する。
     * 
     * @param replyTo 返信先メールアドレス
     */
    @Published
    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    /**
     * 件名を取得する。
     * 
     * @return 件名
     */
    @Published(tag = "architect")
    protected String getSubject() {
        return subject;
    }

    /**
     * 件名を設定する。
     * 
     * @param subject 件名
     */
    @Published
    protected void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * 本文を取得する。
     * 
     * @return 本文
     */
    @Published(tag = "architect")
    protected String getMailBody() {
        return mailBody;
    }

    /**
     * 本文を設定する。
     * 
     * @param mailBody 本文
     */
    @Published
    protected void setMailBody(String mailBody) {
        this.mailBody = mailBody;
    }

    /**
     * Content-Typeヘッダに指定する文字セットを取得する。
     * 
     * @return Content-Typeヘッダに指定する文字セット
     */
    @Published(tag = "architect")
    protected String getCharset() {
        return charset;
    }

    /**
     * Content-Typeヘッダに指定する文字セットを設定する。
     * 
     * @param charset Content-Typeヘッダに指定する文字セット
     */
    @Published
    protected void setCharset(String charset) {
        this.charset = charset;
    }

    /**
     * 添付ファイルのリストを取得する。
     * 
     * @return 添付ファイルのリスト
     */
    @Published(tag = "architect")
    public List<AttachedFile> getAttachedFileList() {
        return attachedFileList;
    }

    /**
     * 添付ファイルを追加する。
     * 
     * @param attachedFile 添付ファイル
     */
    @Published
    public void addAttachedFile(AttachedFile attachedFile) {
        this.attachedFileList.add(attachedFile);
    }

    /**
     * メール送信パターンIDを取得する。
     *
     * @return メール送信パターンID
     */
    @Published(tag = "architect")
    public String getMailSendPatternId() {
        return mailSendPatternId;
    }

    /**
     * メール送信パターンIDを設定する。
     * <p/>
     * メール送信パターンIDを指定して未送信データを送信する場合は必須。
     *
     * @param mailSendPatternId メール送信パターンID
     */
    @Published
    public void setMailSendPatternId(String mailSendPatternId) {
        this.mailSendPatternId = mailSendPatternId;
    }
}

