package nablarch.common.mail;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * メール用バッチリクエスト
 */
@Entity
@Table(name = "MAIL_BATCH_REQUEST")
public class MailBatchRequest {

    public MailBatchRequest() {
    }

    public MailBatchRequest(String requestId, String requestName, String processHaltFlg,
            String processActiveFlg, String serviceAvailable) {
        this.requestId = requestId;
        this.requestName = requestName;
        this.processHaltFlg = processHaltFlg;
        this.processActiveFlg = processActiveFlg;
        this.serviceAvailable = serviceAvailable;
    }

    @Id
    @Column(name = "REQUEST_ID", length = 10, nullable = false)
    public String requestId;

    @Column(name = "REQUEST_NAME", length = 100, nullable = false)
    public String requestName;

    @Column(name = "PROCESS_HALT_FLG", length = 1, nullable = false)
    public String processHaltFlg;

    @Column(name = "PROCESS_ACTIVE_FLG", length = 1, nullable = false)
    public String processActiveFlg;

    @Column(name = "SERVICE_AVAILABLE", length = 1, nullable = false)
    public String serviceAvailable;
}
