package nablarch.common.mail;

import nablarch.core.util.annotation.Published;

/**
 * 出力ライブラリ(メール送信)のコード値を保持するデータオブジェクト。
 * 
 * @author Shinsuke Yoshio
 */
@Published(tag = "architect")
public class MailConfig {

    /** メール送信要求IDの採番対象識別ID */
    private String mailRequestSbnId;

    /** メール送信区分(TO) */
    private String recipientTypeTO = "1";
    /** メール送信区分(CC) */
    private String recipientTypeCC = "2";
    /** メール送信区分(BCC) */
    private String recipientTypeBCC = "3";

    /** メール送信ステータス（未送信） */
    private String statusUnsent = "0";
    /** メール送信ステータス（送信済） */
    private String statusSent = "1";
    /** メール送信ステータス（送信失敗） */
    private String statusFailure = "9";

    /** メール送信要求件数出力時のメッセージID */
    private String mailRequestCountMessageId;

    /** メール送信成功時のメッセージID */
    private String sendSuccessMessageId;

    /** メール送信失敗時の障害コード */
    private String sendFailureCode;

    /** 異常終了時のリターンコード */
    private int abnormalEndExitCode;

    /**
     * メール送信要求IDの採番対象識別IDを取得する。
     * 
     * @return メール送信要求IDの採番対象識別ID
     */
    public String getMailRequestSbnId() {
        return mailRequestSbnId;
    }

    /**
     * メール送信要求IDの採番対象識別IDを設定する。
     * 
     * @param mailRequestSbnId
     *            メール送信要求IDの採番対象識別ID
     */
    public void setMailRequestSbnId(String mailRequestSbnId) {
        this.mailRequestSbnId = mailRequestSbnId;
    }

    /**
     * メール送信区分(TO)のコード値を取得する。
     * 
     * @return メール送信区分(TO)のコード値
     */
    public String getRecipientTypeTO() {
        return recipientTypeTO;
    }

    /**
     * メール送信区分(TO)のコード値を設定する。
     * 
     * @param recipientTypeTO
     *            メール送信区分(TO)のコード値
     */
    public void setRecipientTypeTO(String recipientTypeTO) {
        this.recipientTypeTO = recipientTypeTO;
    }

    /**
     * メール送信区分(CC)のコード値を取得する。
     * 
     * @return メール送信区分(CC)のコード値
     */
    public String getRecipientTypeCC() {
        return recipientTypeCC;
    }

    /**
     * メール送信区分(CC)のコード値を設定する。
     * 
     * @param recipientTypeCC
     *            メール送信区分(CC)のコード値
     */
    public void setRecipientTypeCC(String recipientTypeCC) {
        this.recipientTypeCC = recipientTypeCC;
    }

    /**
     * メール送信区分(BCC)のコード値を取得する。
     * 
     * @return メール送信区分(BCC)のコード値
     */
    public String getRecipientTypeBCC() {
        return recipientTypeBCC;
    }

    /**
     * メール送信区分(BCC)のコード値を設定する。
     * 
     * @param recipientTypeBCC
     *            メール送信区分(BCC)のコード値
     */
    public void setRecipientTypeBCC(String recipientTypeBCC) {
        this.recipientTypeBCC = recipientTypeBCC;
    }

    /**
     * メール送信ステータス（未送信）のコード値を取得する。
     * 
     * @return メール送信ステータス（未送信）
     */
    public String getStatusUnsent() {
        return statusUnsent;
    }

    /**
     * メール送信ステータス（未送信）のコード値を設定する。
     * 
     * @param statusUnsent
     *            メール送信ステータス（未送信）のコード値
     */
    public void setStatusUnsent(String statusUnsent) {
        this.statusUnsent = statusUnsent;
    }

    /**
     * メール送信ステータス（送信済）のコード値を取得する。
     * 
     * @return メール送信ステータス（送信済）のコード値
     */
    public String getStatusSent() {
        return statusSent;
    }

    /**
     * メール送信ステータス（送信済）のコード値を設定する。
     * 
     * @param statusSent
     *            メール送信ステータス（送信済）のコード値
     */
    public void setStatusSent(String statusSent) {
        this.statusSent = statusSent;
    }

    /**
     * メール送信ステータス（送信失敗）のコード値を取得する。
     * 
     * @return メール送信ステータス（送信失敗）のコード値
     */
    public String getStatusFailure() {
        return statusFailure;
    }

    /**
     * メール送信ステータス（送信失敗）のコード値を設定する。
     * 
     * @param statusFailure
     *            メール送信ステータス（送信失敗）のコード値
     */
    public void setStatusFailure(String statusFailure) {
        this.statusFailure = statusFailure;
    }

    /**
     * 送信失敗時の障害コードを取得する。
     * 
     * @return 送信失敗時の障害コード
     */
    public String getSendFailureCode() {
        return sendFailureCode;
    }

    /**
     * メール送信成功時のメッセージIDを取得する。
     * 
     * @return メール送信成功時のメッセージID
     */
    public String getSendSuccessMessageId() {
        return sendSuccessMessageId;
    }

    /**
     * メール送信成功時のメッセージIDを設定する。
     * 
     * <pre>
     * ログ出力時に、メール送信要求IDが渡されるため、
     * メッセージテーブルにこのメッセージIDに対応する以下のようなメッセージを登録すれば、
     * メール送信要求IDをログに含めることが出来る。
     * 
     * メッセージ例）”メールを送信しました。 mailRequestId=[{0}]”
     * </pre>
     * 
     * @param sendSuccessMessageId
     *            メール送信成功時のメッセージID
     */
    public void setSendSuccessMessageId(String sendSuccessMessageId) {
        this.sendSuccessMessageId = sendSuccessMessageId;
    }

    /**
     * メール送信要求件数出力時のメッセージIDを取得する。
     * 
     * @return メール送信要求件数出力時のメッセージID
     */
    public String getMailRequestCountMessageId() {
        return mailRequestCountMessageId;
    }

    /**
     * メール送信要求件数出力時のメッセージIDを設定する。 *
     * 
     * <pre>
     * ログ出力時に、メール送信要求の件数が渡されるため、
     * メッセージテーブルにこのメッセージIDに対応する以下のようなメッセージを登録すれば、
     * メール送信要求の件数をログに含めることが出来る。
     * 
     * メッセージ例）”メール送信要求が {0} 件あります。”
     * </pre>
     * 
     * @param mailRequestCountMessageId
     *            メール送信要求件数出力時のメッセージID
     */
    public void setMailRequestCountMessageId(String mailRequestCountMessageId) {
        this.mailRequestCountMessageId = mailRequestCountMessageId;
    }

    /**
     * 送信失敗時の障害コードを設定する。
     * 
     * <pre>
     * ログ出力時に、メール送信要求IDが渡されるため、
     * メッセージテーブルにこの障害コードに対応する以下のようなメッセージを登録すれば、
     * メール送信要求IDをログに含めることが出来る。
     * 
     * メッセージ例）”メール送信に失敗しました。 mailRequestId=[{0}]”
     * </pre>
     * 
     * @param sendFailureCode
     *            送信失敗時の障害コード
     */
    public void setSendFailureCode(String sendFailureCode) {
        this.sendFailureCode = sendFailureCode;
    }

    /**
     * 送信失敗時の終了コードを取得する。
     * 
     * @return 送信失敗時の終了コード
     */
    public int getAbnormalEndExitCode() {
        return abnormalEndExitCode;
    }

    /**
     * 送信失敗時の終了コードを設定する。
     * 
     * @param abnormalEndExitCode
     *            送信失敗時の終了コード
     */
    public void setAbnormalEndExitCode(int abnormalEndExitCode) {
        this.abnormalEndExitCode = abnormalEndExitCode;
    }

}
