package nablarch.common.mail;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * メールレシピエント
 */
@Entity
@Table(name = "MAIL_RECIPIENT")
public class MailRecipient {

    public MailRecipient() {
    }

    public MailRecipient(String mailRequestId, Long serialNumber, String recipientType,
            String mailAddress) {
        this.mailRequestId = mailRequestId;
        this.serialNumber = serialNumber;
        this.recipientType = recipientType;
        this.mailAddress = mailAddress;
    }

    @Id
    @Column(name = "MAIL_REQUEST_ID", length = 20, nullable = false)
    public String mailRequestId;

    @Id
    @Column(name = "SERIAL_NUMBER", length = 10, nullable = false)
    public Long serialNumber;

    @Column(name = "RECIPIENT_TYPE", length = 1, nullable = false)
    public String recipientType;

    @Column(name = "MAIL_ADDRESS", length = 100, nullable = false)
    public String mailAddress;

}