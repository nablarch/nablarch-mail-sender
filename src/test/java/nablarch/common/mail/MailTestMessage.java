package nablarch.common.mail;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * メール用テストメッセージ
 */
@Entity
@Table(name = "MAIL_TEST_MESSAGE")
public class MailTestMessage {

    public MailTestMessage() {
    }

    public MailTestMessage(String messageId, String lang, String message) {
        this.messageId = messageId;
        this.lang = lang;
        this.message = message;
    }

    @Id
    @Column(name = "MESSAGE_ID", length = 10, nullable = false)
    public String messageId;

    @Id
    @Column(name = "LANG", length = 2, nullable = false)
    public String lang;

    @Column(name = "MESSAGE", length = 200)
    public String message;
}
