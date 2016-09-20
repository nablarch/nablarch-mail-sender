package nablarch.common.mail;

import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.db.statement.SqlResultSet;
import nablarch.core.db.statement.SqlRow;
import nablarch.core.repository.initialization.Initializable;
import nablarch.core.util.annotation.Published;

/**
 * メールテンプレート管理テーブルのスキーマ情報を保持するデータオブジェクト。
 *
 * @author Shinsuke Yoshio
 */
@Published(tag = "architect")
public class MailTemplateTable implements Initializable {

    /** テーブル名 */
    private String tableName;

    /** メールテンプレートIDにのカラム名 */
    private String mailTemplateIdColumnName;

    /** 言語のカラム名 */
    private String langColumnName;

    /** 件名のカラム名 */
    private String subjectColumnName;

    /** メール本文のカラム名 */
    private String mailBodyColumnName;

    /** 文字セットのカラム名 */
    private String charsetColumnName;

    /** メールテンプレートを取得するSQL */
    private String findSql;

    /**
     * メールテンプレート管理テーブルの名前を設定する。
     *
     * @param tableName メールテンプレート管理テーブルの名前
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * メールテンプレート管理テーブルのテンプレートIDカラムの名前を設定する。
     *
     * @param mailTemplateIdColumnName メールテンプレート管理テーブルのテンプレートIDカラムの名前
     */
    public void setMailTemplateIdColumnName(String mailTemplateIdColumnName) {
        this.mailTemplateIdColumnName = mailTemplateIdColumnName;
    }

    /**
     * メールテンプレート管理テーブルの言語カラムの名前を設定する。
     *
     * @param langColumnName メールテンプレート管理テーブルの言語カラムの名前
     */
    public void setLangColumnName(String langColumnName) {
        this.langColumnName = langColumnName;
    }

    /**
     * メールテンプレート管理テーブルの件名カラムの名前を設定する。
     *
     * @param subjectColumnName メールテンプレート管理テーブルの件名カラムの名前
     */
    public void setSubjectColumnName(String subjectColumnName) {
        this.subjectColumnName = subjectColumnName;
    }

    /**
     * メールテンプレート管理テーブルの本文カラムの名前を設定する。
     *
     * @param mailBodyColumnName メールテンプレート管理テーブルの本文カラムの名前
     */
    public void setMailBodyColumnName(String mailBodyColumnName) {
        this.mailBodyColumnName = mailBodyColumnName;
    }

    /**
     * メールテンプレート管理テーブルの文字セットカラムの名前を設定する。
     *
     * @param charsetColumnName メールテンプレート管理テーブルの文字セットカラムの名前
     */
    public void setCharsetColumnName(String charsetColumnName) {
        this.charsetColumnName = charsetColumnName;
    }

    /**
     * メールテンプレート情報を取得する。
     *
     * @param templateId メールテンプレートID
     * @param lang 言語
     * @return 取得したテンプレート情報
     */
    public MailTemplateTable.MailTemplate find(String templateId, String lang) {
        AppDbConnection connection = DbConnectionContext.getConnection();

        SqlPStatement statement = connection.prepareStatement(findSql);
        statement.setString(1, templateId);
        statement.setString(2, lang);
        SqlResultSet resultSet = statement.retrieve();
        if (resultSet.isEmpty()) {
            throw new IllegalArgumentException(
                    "mail template was not found. mailTemplateId = ["
                            + templateId + "], lang = [" + lang + ']');
        }
        return new MailTemplateTable.MailTemplate(resultSet.get(0));
    }

    /** SQLを初期化する。 */
    public void initialize() {
        findSql = "SELECT "
                + subjectColumnName + " SUBJECT, "
                + charsetColumnName + " CHARSET, "
                + mailBodyColumnName + " MAIL_BODY"
                + " FROM " + tableName
                + " WHERE "
                + mailTemplateIdColumnName + " = ? "
                + " AND "
                + langColumnName + " = ?";
    }

    /**
     * メールテンプレートの1レコード分の情報を保持するクラス。
     *
     * @author hisaaki shioiri
     */
    public static class MailTemplate {

        /** メール送信先1の1レコードを表すオブジェクト */
        private final SqlRow record;

        /**
         * メールテンプレートの1レコード文の情報を保持するインスタンスを生成する。
         *
         * @param record 1レコードを表すレコード
         */
        public MailTemplate(SqlRow record) {
            this.record = record;
        }

        /**
         * 件名を取得する。
         *
         * @return 件名
         */
        public String getSubject() {
            return record.getString("SUBJECT");
        }

        /**
         * 文字セットを取得する。
         *
         * @return 文字セット
         */
        public String getCharset() {
            return record.getString("CHARSET");
        }

        /**
         * メール本文を取得する。
         *
         * @return メール本文
         */
        public String getMailBody() {
            return record.getString("MAIL_BODY");
        }
    }
}

