package nablarch.common.mail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * メールテンプレート
 */
@Entity
@Table(name = "MAIL_TEMPLATE")
public class MailTemplate {

    public MailTemplate() {
    }

    public MailTemplate(String mailTemplateId, String lang, String subject, String charset,
            String mailBody) {
        this.mailTemplateId = mailTemplateId;
        this.lang = lang;
        this.subject = subject;
        this.charset = charset;
        this.mailBody = mailBody;
    }

    @Id
    @Column(name = "MAIL_TEMPLATE_ID", length = 10, nullable = false)
    public String mailTemplateId;

    @Id
    @Column(name = "LANG", length = 2, nullable = false)
    public String lang;

    @Column(name = "SUBJECT", length = 150)
    public String subject;

    @Column(name = "CHARSET", length = 50)
    public String charset;

    @Column(name = "MAIL_BODY", length = 4000)
    public String mailBody;
}
