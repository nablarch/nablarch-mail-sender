package nablarch.common.mail;

import java.util.ArrayList;
import java.util.List;

import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.statement.ResultSetIterator;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.db.statement.SqlRow;
import nablarch.core.repository.initialization.Initializable;
import nablarch.core.util.annotation.Published;

/**
 * メール送信先管理テーブルのスキーマ情報を保持するデータオブジェクト。
 *
 * @author Shinsuke Yoshio
 */
@Published(tag = "architect")
public class MailRecipientTable implements Initializable {

    /** テーブル名 */
    private String tableName;

    /** メールリクエストIDカラム名 */
    private String mailRequestIdColumnName;

    /** シリアル番号カラム名 */
    private String serialNumberColumnName;

    /** 送信先区分カラム名 */
    private String recipientTypeColumnName;

    /** 送信先メールアドレスのカラム名 */
    private String mailAddressColumnName;

    /** メール送信先を登録するSQL */
    private String insertSql;

    /** 送信先を取得するSQL */
    private String findSql;

    /**
     * メール送信先テーブルの名前を設定する。
     *
     * @param tableName メール送信先テーブルの名前
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * メール送信先テーブルの名前を取得する。
     *
     * @return メール送信先テーブルの名前
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * メール送信先テーブルの要求IDカラムの名前を設定する。
     *
     * @param mailRequestIdColumnName メール送信先テーブルの要求IDカラムの名前
     */
    public void setMailRequestIdColumnName(String mailRequestIdColumnName) {
        this.mailRequestIdColumnName = mailRequestIdColumnName;
    }

    /**
     * メール送信先テーブルの連番カラムの名前を設定する。
     *
     * @param serialNumberColumnName メール送信先テーブルの連番カラムの名前
     */
    public void setSerialNumberColumnName(String serialNumberColumnName) {
        this.serialNumberColumnName = serialNumberColumnName;
    }

    /**
     * メール送信先テーブルの送信先区分カラムの名前を設定する。
     *
     * @param recipientTypeColumnName メール送信先テーブルの送信先区分カラムの名前
     */
    public void setRecipientTypeColumnName(String recipientTypeColumnName) {
        this.recipientTypeColumnName = recipientTypeColumnName;
    }

    /**
     * メール送信先テーブルの送信先メールアドレスカラムの名前を設定する。
     *
     * @param mailAddressColumnName メール送信先テーブルの送信先メールアドレスカラムの名前
     */
    public void setMailAddressColumnName(String mailAddressColumnName) {
        this.mailAddressColumnName = mailAddressColumnName;
    }

    /**
     * 送信先テーブルに送信先情報のデータを追加する。
     *
     * @param mailRequestId メールリクエストID
     * @param context メール送信先情報を持つオブジェクト
     * @param mailConfig メールの設定情報を持つオブジェクト
     */
    public void insert(String mailRequestId, MailContext context, MailConfig mailConfig) {
        AppDbConnection connection = DbConnectionContext.getConnection();
        executeInsertSQL(mailRequestId, context, mailConfig, connection);
    }

    /**
     * 指定されたトランザクション名を用いて送信先テーブルに送信先情報のデータを追加する
     *
     * @param mailRequestId メールリクエストID
     * @param context メール送信先情報を持つオブジェクト
     * @param mailConfig メールの設定情報を持つオブジェクト
     * @param transactionName トランザクション名
     */
    public void insert(String mailRequestId, MailContext context, MailConfig mailConfig, String transactionName) {
        AppDbConnection connection = DbConnectionContext.getConnection(transactionName);
        executeInsertSQL(mailRequestId, context, mailConfig, connection);
    }

    /**
     * 送信先テーブルに送信先情報のデータを追加する
     * @param mailRequestId メールリクエストID
     * @param context メール送信先情報を持つオブジェクト
     * @param mailConfig メールの設定情報を持つオブジェクト
     * @param connection コネクション
     */
    private void executeInsertSQL(String mailRequestId, MailContext context, MailConfig mailConfig, AppDbConnection connection) {
        SqlPStatement statement = connection.prepareStatement(insertSql);
        statement.setString(1, mailRequestId);

        int serialNo = 1;
        // to
        statement.setString(3, mailConfig.getRecipientTypeTO());
        for (String to : context.getToList()) {
            statement.setInt(2, serialNo);
            statement.setString(4, to);
            serialNo++;
            statement.addBatch();
        }
        statement.executeBatch();

        // cc
        statement.setString(3, mailConfig.getRecipientTypeCC());
        for (String cc : context.getCcList()) {
            statement.setInt(2, serialNo);
            statement.setString(4, cc);
            serialNo++;
            statement.addBatch();
        }
        statement.executeBatch();
        // bcc
        statement.setString(3, mailConfig.getRecipientTypeBCC());
        for (String bcc : context.getBccList()) {
            statement.setInt(2, serialNo);
            statement.setString(4, bcc);
            serialNo++;
            statement.addBatch();
        }
        statement.executeBatch();
    }

    /**
     * 送信先情報を取得する。
     * <p/>
     * 指定されたメールリクエストIDと宛先区分に紐付く送信先の情報を取得する。
     *
     *
     * @param mailRequestId メールリクエストID
     * @param recipientType 宛先区分
     * @return 取得した送信先情報
     */
    public List<MailRecipientTable.MailRecipient> find(String mailRequestId, String recipientType) {
        AppDbConnection connection = DbConnectionContext.getConnection();
        SqlPStatement statement = connection.prepareStatement(findSql);
        statement.setString(1, mailRequestId);
        statement.setString(2, recipientType);
        ResultSetIterator sqlRows = statement.executeQuery();
        List<MailRecipientTable.MailRecipient> result = new ArrayList<MailRecipientTable.MailRecipient>();
        for (SqlRow record : sqlRows) {
            result.add(new MailRecipientTable.MailRecipient(record));
        }
        return result;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * 本クラスで使用するSQL文を各セッターで設定されたテーブル名及びカラム名から構築する。
     * <p/>
     * 構築するSQL文は、以下の2種類
     * <ul>
     * <li>メール送信先へレコードを追加するINSERT文</li>
     * <li>メール送信先からメールリクエストIDを元にレコードを取得するSELECT文(連番の昇順でソート)</li>
     * </ul>
     */
    public void initialize() {

        insertSql = "INSERT INTO " + tableName
                + " ("
                + mailRequestIdColumnName + ", "
                + serialNumberColumnName + ", "
                + recipientTypeColumnName + ", "
                + mailAddressColumnName
                + ") VALUES (?,?,?,?)";

        findSql = "SELECT "
                + serialNumberColumnName + " SERIAL_NUMBER, "
                + mailAddressColumnName + " MAIL_ADDRESS "
                + "FROM "
                + tableName + ' '
                + "WHERE "
                + mailRequestIdColumnName + " = ? "
                + "AND "
                + recipientTypeColumnName + " = ? "
                + "ORDER BY "
                + serialNumberColumnName;
    }

    /**
     * メール送信先の1レコード分の情報を保持するクラス。
     *
     * @author hisaaki shioiri
     */
    public static class MailRecipient {

        /** メール送信先1の1レコードを表すオブジェクト */
        private final SqlRow record;

        /**
         * メール送信先の1レコード文の情報を保持するインスタンスを生成する。
         *
         * @param record 1レコードを表すレコード
         */
        public MailRecipient(SqlRow record) {
            this.record = record;
        }

        /**
         * 連番を取得する。
         *
         * @return 連番
         */
        public int getSerialNumber() {
            return record.getInteger("SERIAL_NUMBER");
        }

        /**
         * 送信先アドレスを取得する。
         *
         * @return 送信先メールアドレス
         */
        public String getMailAddress() {
            return record.getString("MAIL_ADDRESS");
        }
    }
}

