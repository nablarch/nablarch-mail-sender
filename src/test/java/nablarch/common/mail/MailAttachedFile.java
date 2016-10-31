package nablarch.common.mail;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

/**
 * メール添付ファイル
 */
@Entity
@Table(name = "MAIL_ATTACHED_FILE")
public class MailAttachedFile {

    public MailAttachedFile() {
    }

    public MailAttachedFile(String mailRequestId, Long serialNumber, String fileName,
            String contentType, byte[] attachedFile) {
        this.mailRequestId = mailRequestId;
        this.serialNumber = serialNumber;
        this.fileName = fileName;
        this.contentType = contentType;
        this.attachedFile = attachedFile;
    }

    @Id
    @Column(name = "MAIL_REQUEST_ID", length = 20, nullable = false)
    public String mailRequestId;

    @Id
    @Column(name = "SERIAL_NUMBER", length = 10, nullable = false)
    public Long serialNumber;

    @Column(name = "FILE_NAME", length = 150, nullable = false)
    public String fileName;

    @Column(name = "CONTENT_TYPE", length = 50, nullable = false)
    public String contentType;

    @Lob
    @Column(name = "ATTACHED_FILE")
    public byte[] attachedFile;
}
