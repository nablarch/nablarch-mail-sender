package nablarch.common.mail;

import java.util.HashMap;
import java.util.Map;

/**
 * メール送信用設定値を保持するデータオブジェクト。
 * 
 * @author Shinsuke Yoshio
 */
public class MailSessionConfig {

    /** 接続先SMTPサーバー */
    private String mailSmtpHost;

    /** ホスト名。 Message-IDヘッダ生成時に、ドメイン名として使用される。 */
    private String mailHost;

    /** SMTPポート */
    private String mailSmtpPort;

    /** 接続タイムアウト値 */
    private String mailSmtpConnectionTimeout;

    /** 送信タイムアウト値 */
    private String mailSmtpTimeout;

    /** その他javax.mail.Sessionのオプション */
    private Map<String, String> option = new HashMap<String, String>();

    /**
     * SMTPサーバー名を取得する。
     * 
     * @return SMTPサーバー名
     */
    public String getMailSmtpHost() {
        return mailSmtpHost;
    }

    /**
     * SMTPサーバー名を設定する。
     * 
     * @param mailSmtpHost
     *            SMTPサーバー名
     */
    public void setMailSmtpHost(String mailSmtpHost) {
        this.mailSmtpHost = mailSmtpHost;
    }

    /**
     * 接続ホスト名を取得する。
     * 
     * @return 接続ホスト名
     */
    public String getMailHost() {
        return mailHost;
    }

    /**
     * 接続ホスト名を設定する。
     * 
     * @param mailHost
     *            接続ホスト名
     */
    public void setMailHost(String mailHost) {
        this.mailHost = mailHost;
    }

    /**
     * SMTPポートを取得する。
     * 
     * @return SMTPポート
     */
    public String getMailSmtpPort() {
        return mailSmtpPort;
    }

    /**
     * SMTPポートを設定する。
     * 
     * @param mailSmtpPort
     *            SMTPポート
     */
    public void setMailSmtpPort(String mailSmtpPort) {
        this.mailSmtpPort = mailSmtpPort;
    }

    /**
     * 接続タイムアウト値を取得する。
     * 
     * @return 接続タイムアウト値
     */
    public String getMailSmtpConnectionTimeout() {
        return mailSmtpConnectionTimeout;
    }

    /**
     * 接続タイムアウト値を設定する。
     * 
     * @param mailSmtpConnectionTimeout
     *            接続タイムアウト値
     */
    public void setMailSmtpConnectionTimeout(String mailSmtpConnectionTimeout) {
        this.mailSmtpConnectionTimeout = mailSmtpConnectionTimeout;
    }

    /**
     * 送信タイムアウト値を取得する。
     * 
     * @return 送信タイムアウト値
     */
    public String getMailSmtpTimeout() {
        return mailSmtpTimeout;
    }

    /**
     * 送信タイムアウト値を設定する。
     * 
     * @param mailSmtpTimeout
     *            送信タイムアウト値
     */
    public void setMailSmtpTimeout(String mailSmtpTimeout) {
        this.mailSmtpTimeout = mailSmtpTimeout;
    }

    /**
     * javax.mail.Sessionのオプションを取得する。
     * 
     * @return javax.mail.Sessionのオプション
     */
    public Map<String, String> getOption() {
        return option;
    }

    /**
     * その他のjavax.mail.Sessionのオプションを設定する。
     * 
     * @param option オプション名と値のMap
     */
    public void setOption(Map<String, String> option) {
        this.option = option;
    }
}
