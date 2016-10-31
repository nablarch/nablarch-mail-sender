package nablarch.common.mail;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * メール採番テーブル
 */
@Entity
@Table(name = "MAIL_SBN_TABLE")
public class MailSbnTable {

    public MailSbnTable() {
    }
    
    public MailSbnTable(String idCol, Long noCol) {
        this.idCol = idCol;
        this.noCol = noCol;
    }

    @Id
    @Column(name = "ID_COL", length = 2, nullable = false)
    public String idCol;

    @Column(name = "NO_COL", length = 20, nullable = false)
    public Long noCol;
}