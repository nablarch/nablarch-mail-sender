package nablarch.common.mail;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * メールリクエスト
 */
@Entity
@Table(name = "MAIL_REQUEST")
public class MailRequest {

    public MailRequest() {
    }

    public MailRequest(String mailRequestId, String subject, String mailFrom, String replyTo,
            String returnPath, String charset, String status, Timestamp requestDatetime,
            Timestamp sendDatetime, String mailBody) {
        super();
        this.mailRequestId = mailRequestId;
        this.subject = subject;
        this.mailFrom = mailFrom;
        this.replyTo = replyTo;
        this.returnPath = returnPath;
        this.charset = charset;
        this.status = status;
        this.requestDatetime = requestDatetime;
        this.sendDatetime = sendDatetime;
        this.mailBody = mailBody;
    }

    @Id
    @Column(name = "MAIL_REQUEST_ID", length = 20, nullable = false)
    public String mailRequestId;

    @Column(name = "SUBJECT", length = 150, nullable = false)
    public String subject;

    @Column(name = "MAIL_FROM", length = 100, nullable = false)
    public String mailFrom;

    @Column(name = "REPLY_TO", length = 100, nullable = false)
    public String replyTo;

    @Column(name = "RETURN_PATH", length = 100, nullable = false)
    public String returnPath;

    @Column(name = "CHARSET", length = 50, nullable = false)
    public String charset;

    @Column(name = "STATUS", length = 1, nullable = false)
    public String status;

    @Column(name = "REQUEST_DATETIME", nullable = false)
    public Timestamp requestDatetime;

    @Column(name = "SEND_DATETIME")
    public Timestamp sendDatetime;

    @Column(name = "MAIL_BODY", length = 4000, nullable = false)
    public String mailBody;
}