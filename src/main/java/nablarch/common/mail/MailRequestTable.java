package nablarch.common.mail;

import nablarch.core.date.SystemTimeUtil;
import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.db.statement.SqlResultSet;
import nablarch.core.db.statement.SqlRow;
import nablarch.core.db.transaction.SimpleDbTransactionExecutor;
import nablarch.core.db.transaction.SimpleDbTransactionManager;
import nablarch.core.repository.SystemRepository;
import nablarch.core.repository.initialization.Initializable;
import nablarch.core.util.StringUtil;
import nablarch.core.util.annotation.Published;

/**
 * メール送信要求管理テーブルのスキーマを保持するデータオブジェクト。
 *
 * @author Shinsuke Yoshio
 */
@Published(tag = "architect")
public class MailRequestTable implements Initializable {

    /** テーブル名 */
    private String tableName;

    /** メールリクエストIDカラム名 */
    private String mailRequestIdColumnName;

    /** 件名のカラム名 */
    private String subjectColumnName;

    /** 送信者アドレスのカラム名 */
    private String fromColumnName;

    /** 返信先アドレスのカラム名 */
    private String replyColumnName;

    /** 差し戻し先アドレスのカラム名 */
    private String returnPathColumnName;

    /** メール本文のカラム名 */
    private String mailBodyColumnName;

    /** charsetのカラム名 */
    private String charsetColumnName;

    /** ステータスのカラム名 */
    private String statusColumnName;

    /** リクエスト要求日時のカラム名 */
    private String requestDateTimeColumnName;

    /** 送信日時のカラム名 */
    private String sendDateTimeColumnName;

    /** メール送信パターンIDカラム名 */
    private String mailSendPatternIdColumnName;

    /** メール送信バッチのプロセスIDのカラム名 */
    private String sendProcessIdColumnName;

    /** メール送信要求を登録するSQL */
    private String insertSql;

    /** 未送信のメール送信要求の件数を取得するSQL */
    private String countUnsentSql;

    /** 未送信のメール送信要求を取得するSQL */
    private String selectUnsentSql;

    /** メール送信要求のステータスを更新するSQL */
    private String updateStatusSql;

    /** メール送信失敗時のステータスを更新するSQL */
    private String updateFailureStatusSql;

    /** メール送信バッチのプロセスIDを更新するSQL */
    private String updateSendProcessIdSql;

    /** メール関連のコード値を保持するデータオブジェクト */
    private MailConfig mailConfig;

    /**
     * メール送信要求管理テーブルの名前を設定する。
     *
     * @param tableName メール送信要求管理テーブルの名前
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * メール送信要求管理テーブルの要求IDカラムの名前を設定する。
     *
     * @param mailRequestIdColumnName メール送信要求管理テーブルの要求IDカラムの名前
     */
    public void setMailRequestIdColumnName(String mailRequestIdColumnName) {
        this.mailRequestIdColumnName = mailRequestIdColumnName;
    }

    /**
     * メール送信要求管理テーブルの件名カラムの名前を設定する。
     *
     * @param subjectColumnName メール送信要求管理テーブルの件名カラムの名前
     */
    public void setSubjectColumnName(String subjectColumnName) {
        this.subjectColumnName = subjectColumnName;
    }

    /**
     * メール送信要求管理テーブルの送信者メールアドレスカラムの名前を設定する。
     *
     * @param fromColumnName メール送信要求管理テーブルの送信者メールアドレスカラムの名前
     */
    public void setFromColumnName(String fromColumnName) {
        this.fromColumnName = fromColumnName;
    }

    /**
     * メール送信要求管理テーブルの返信先メールアドレスカラムの名前を設定する。
     *
     * @param replyColumnName メール送信要求管理テーブルの返信先メールアドレスカラムの名前
     */
    public void setReplyToColumnName(String replyColumnName) {
        this.replyColumnName = replyColumnName;
    }

    /**
     * メール送信要求管理テーブルの差し戻し先メールアドレスカラムの名前を設定する。
     *
     * @param returnPathColumnName メール送信要求管理テーブルの差し戻し先メールアドレスカラムの名前
     */
    public void setReturnPathColumnName(String returnPathColumnName) {
        this.returnPathColumnName = returnPathColumnName;
    }

    /**
     * メール送信要求管理テーブルの本文カラムの名前を設定する。
     *
     * @param mailBodyColumnName メール送信要求管理テーブルの本文カラムの名前
     */
    public void setMailBodyColumnName(String mailBodyColumnName) {
        this.mailBodyColumnName = mailBodyColumnName;
    }

    /**
     * メール送信要求管理テーブルの文字セットカラムの名前を設定する。
     *
     * @param charsetColumnName メール送信要求管理テーブルの文字セットカラムの名前
     */
    public void setCharsetColumnName(String charsetColumnName) {
        this.charsetColumnName = charsetColumnName;
    }

    /**
     * メール送信要求管理テーブルのステータスカラムの名前を設定する。
     *
     * @param statusColumnName メール送信要求管理テーブルのステータスカラムの名前
     */
    public void setStatusColumnName(String statusColumnName) {
        this.statusColumnName = statusColumnName;
    }

    /**
     * メール送信要求管理テーブルの要求日時カラムの名前を設定する。
     *
     * @param requestDateTimeColumnName メール送信要求管理テーブルの要求日時カラムの名前
     */
    public void setRequestDateTimeColumnName(String requestDateTimeColumnName) {
        this.requestDateTimeColumnName = requestDateTimeColumnName;
    }

    /**
     * メール送信要求管理テーブルの送信日時カラムの名前を設定する。
     *
     * @param sendDateTimeColumnName メール送信要求管理テーブルの送信日時カラムの名前
     */
    public void setSendDateTimeColumnName(String sendDateTimeColumnName) {
        this.sendDateTimeColumnName = sendDateTimeColumnName;
    }

    /**
     * メール送信要求管理テーブルのメール送信パターンIDをのカラム名を設定する。
     *
     * @param mailSendPatternIdColumnName メール送信要求管理テーブルのメール送信パターンIDのカラム名
     */
    public void setMailSendPatternIdColumnName(String mailSendPatternIdColumnName) {
        this.mailSendPatternIdColumnName = mailSendPatternIdColumnName;
    }

    /**
     * 送信するバッチのプロセスIDのカラム名を設定する。
     *
     * @param sendProcessIdColumnName 送信するバッチのプロセスIDのカラム名
     */
    public void setSendProcessIdColumnName(String sendProcessIdColumnName) {
        this.sendProcessIdColumnName = sendProcessIdColumnName;
    }

    /**
     * メール関連のコード値を保持するデータオブジェクトを設定する。
     *
     * @param mailConfig メール関連のコード値を保持するデータオブジェクト
     */
    public void setMailConfig(MailConfig mailConfig) {
        this.mailConfig = mailConfig;
    }

    /**
     * メール送信要求管理テーブルにレコードを登録する。
     *
     * @param mailRequestId メールリクエストID
     * @param context メール送信要求情報
     */
    public void insert(String mailRequestId, MailContext context) {
        AppDbConnection connection = DbConnectionContext.getConnection();
        executeInsertSQL(mailRequestId, context, mailConfig, connection);
    }

    /**
     * 指定されたトランザクション名を用いてメール送信要求管理テーブルにレコードを登録する。
     *
     * @param mailRequestId メールリクエストID
     * @param context メール送信要求情報
     * @param transactionName トランザクション名
     */
    public void insert(String mailRequestId, MailContext context, String transactionName) {
        AppDbConnection connection = DbConnectionContext.getConnection(transactionName);
        executeInsertSQL(mailRequestId, context, mailConfig, connection);
    }

    /**
     * メール送信要求管理テーブルにレコードを登録する。
     * @param mailRequestId メールリクエストID
     * @param context メール送信先情報を持つオブジェクト
     * @param mailConfig メールの設定情報を持つオブジェクト
     * @param connection コネクション
     */
    private void executeInsertSQL(String mailRequestId, MailContext context, MailConfig mailConfig,
                                  AppDbConnection connection) {
        SqlPStatement statement = connection.prepareStatement(insertSql);
        statement.setString(1, mailRequestId);
        statement.setString(2, context.getSubject());
        statement.setString(3, context.getFrom());
        statement.setString(4, context.getReplyTo());
        statement.setString(5, context.getReturnPath());
        statement.setString(6, context.getMailBody());
        statement.setString(7, context.getCharset());
        statement.setString(8, mailConfig.getStatusUnsent());
        statement.setTimestamp(9, SystemTimeUtil.getTimestamp());
        if (StringUtil.hasValue(mailSendPatternIdColumnName)) {
            statement.setString(10, context.getMailSendPatternId());
        }
        statement.executeUpdate();
    }

    /**
     * 処理対象件数を取得する。
     * <p/>
     * 本クラスのスキーマ設定にメール送信パターンIDを設定した場合には、
     * 処理対象のメール送信パターンIDの設定が必須となる。
     * スキーマ定義にメール送信パターンIDが設定されているのに、
     * 処理対象のメール送信パターンIDが設定されていない場合にはSQL実行時エラーとなる。
     *
     * @param mailRequestPatternId 処理対象のメール送信パターンID
     * @return 処理対象件数
     */
    public int getTargetCount(String mailRequestPatternId) {
        AppDbConnection connection = DbConnectionContext.getConnection();
        SqlPStatement statement = connection.prepareStatement(countUnsentSql);
        statement.setString(1, mailConfig.getStatusUnsent());
        if (StringUtil.hasValue(mailRequestPatternId)) {
            statement.setString(2, mailRequestPatternId);
        }
        SqlResultSet rs = statement.retrieve();
        return rs.get(0).getBigDecimal("COUNT").intValue();
    }

    /**
     * 処理対象データを取得する{@link SqlPStatement}を生成する。
     *
     * @param mailSendPatternId メール送信パターンID
     * @return 処理対象データを取得するステートメント
     */
    public SqlPStatement createReaderStatement(String mailSendPatternId) {
        return createReaderStatement(mailSendPatternId, null);
    }

    /**
     * 処理対象データを取得する{@link SqlPStatement}を生成する。
     *
     * @param mailSendPatternId メール送信パターンID
     * @param sendProcessId メール送信バッチのプロセスID
     * @return 処理対象データを取得するステートメント
     */
    public SqlPStatement createReaderStatement(String mailSendPatternId, String sendProcessId) {
        AppDbConnection connection = DbConnectionContext.getConnection();
        SqlPStatement statement = connection.prepareStatement(selectUnsentSql);
        int paramPosition = 1;
        statement.setString(paramPosition++, mailConfig.getStatusUnsent());
        if (StringUtil.hasValue(mailSendPatternId)) {
            statement.setString(paramPosition++, mailSendPatternId);
        }
        if (StringUtil.hasValue(sendProcessIdColumnName)) {
            if (StringUtil.hasValue(sendProcessId)) {
                statement.setString(paramPosition++, sendProcessId);
            } else {
                throw new IllegalArgumentException("sendProcessId must not be null if you use in multi process.");
            }
        }
        return statement;
    }

    /**
     * ステータスを更新する。
     * <p/>
     * 指定されたメールリクエストIDに紐付くレコードのステータスを指定された値に更新する。
     *
     * @param mailRequestId メールリクエストID
     * @param status ステータス
     */
    public void updateStatus(final String mailRequestId, final String status) {
        final SimpleDbTransactionManager transaction = SystemRepository.get("statusUpdateTransaction");
        new SimpleDbTransactionExecutor<Void>(transaction) {
            @Override
            public Void execute(final AppDbConnection connection) {
                final SqlPStatement statement = connection.prepareStatement(updateStatusSql);
                statement.setString(1, status);
                statement.setTimestamp(2, SystemTimeUtil.getTimestamp());
                statement.setString(3, mailRequestId);
                statement.setString(4, mailConfig.getStatusUnsent());
                statement.executeUpdate();
                return null;
            }
        }.doTransaction();
    }

    /**
     * ステータスを更新する。
     * <p/>
     * 指定されたメールリクエストIDに紐付くレコードのステータスを指定された値に更新する。
     *
     * @param mailRequestId メールリクエストID
     * @param status ステータス
     */
    public void updateFailureStatus(final String mailRequestId, final String status) {
        final SimpleDbTransactionManager transaction = SystemRepository.get("statusUpdateTransaction");
        new SimpleDbTransactionExecutor<Void>(transaction) {
            @Override
            public Void execute(final AppDbConnection connection) {
                final SqlPStatement statement = connection.prepareStatement(updateFailureStatusSql);
                statement.setString(1, status);
                statement.setString(2, mailRequestId);
                statement.setString(3, mailConfig.getStatusSent());
                statement.executeUpdate();
                return null;
            }
        }.doTransaction();
    }

    /**
     * メール送信バッチのプロセスIDを更新する。<p/>
     * マルチプロセス用の設定がされている場合のみ更新し、
     * 別トランザクションで実行する。
     *
     * @param mailSendPatternId メール送信パターンID
     * @param sendProcessId 更新するメール送信バッチのプロセスID
     */
    public void updateSendProcessId(final String mailSendPatternId, final String sendProcessId) {
        if (StringUtil.hasValue(sendProcessIdColumnName)) {
            SimpleDbTransactionManager manager = SystemRepository.get("mailMultiProcessTransaction");
            new SimpleDbTransactionExecutor<Void>(manager) {
                @Override
                public Void execute(AppDbConnection appDbConnection) {
                    SqlPStatement statement = appDbConnection.prepareStatement(updateSendProcessIdSql);
                    statement.setString(1, sendProcessId);
                    statement.setString(2, mailConfig.getStatusUnsent());
                    if (StringUtil.hasValue(mailSendPatternId)) {
                        statement.setString(3, mailSendPatternId);
                    }
                    statement.executeUpdate();
                    return null;
                }
            }.doTransaction();
        }
    }

    /**
     * SQLの取得結果の1レコードをMailRequestTable.MailRequestに変換する。
     * @param data メール送信要求1レコード
     * @return メール送信要求
     */
    public MailRequestTable.MailRequest getMailRequest(SqlRow data) {
        return new MailRequestTable.MailRequest(data);
    }

    /** SQLを初期化する。 */
    public void initialize() {
        insertSql = createInsertSql();
        countUnsentSql = createCountUnsentSql();
        selectUnsentSql = createSelectUnsentSql();
        updateStatusSql = createUpdateStatus();
        updateFailureStatusSql = createUpdateFailureStatusSql();
        updateSendProcessIdSql = createUpdateSendProcessIdSql();
    }

    /**
     * 未処理データを取得するためのSELECT文を生成する。
     *
     * @return 未処理データを取得するためのSQL文
     */
    private String createSelectUnsentSql() {
        String sql = "SELECT "
                + mailRequestIdColumnName + " MAIL_REQUEST_ID, "
                + subjectColumnName + " SUBJECT, "
                + fromColumnName + " FROM_ADDRESS, "
                + replyColumnName + " REPLY_ADDRESS, "
                + returnPathColumnName + " RETURN_PATH, "
                + mailBodyColumnName + " MAIL_BODY, "
                + charsetColumnName + " CHARSET "
                + "FROM " + tableName
                + " WHERE " + statusColumnName + " = ? ";

        if (StringUtil.hasValue(mailSendPatternIdColumnName)) {
            sql += "AND " + mailSendPatternIdColumnName + " = ? ";
        }
        if (StringUtil.hasValue(sendProcessIdColumnName)) {
            sql += "AND " + sendProcessIdColumnName + " = ? ";
        }
        sql += "ORDER BY " + mailRequestIdColumnName;
        return sql;
    }

    /**
     * 未処理の件数を取得するためのSELECT文を取得する。
     *
     * @return 生成したSELECT文
     */
    private String createCountUnsentSql() {
        String sql = "SELECT COUNT(*) AS COUNT "
                + "FROM "
                + tableName
                + " WHERE "
                + statusColumnName + " = ? ";
        if (StringUtil.hasValue(mailSendPatternIdColumnName)) {
            sql += "AND " + mailSendPatternIdColumnName + " = ? ";
        }
        if (StringUtil.hasValue(sendProcessIdColumnName)) {
            sql += "AND " + sendProcessIdColumnName + " IS NULL ";
        }
        return sql;
    }

    /**
     * ステータスと送信日時を更新するSQL文を生成する。
     *
     * @return ステータスと送信日時を更新するSQL文
     */
    private String createUpdateStatus() {
        return "UPDATE " + tableName
                + " SET "
                + statusColumnName + " = ?, "
                + sendDateTimeColumnName + " = ? "
                + " WHERE " + mailRequestIdColumnName + " = ?"
                + " AND " + statusColumnName + " = ?";
    }

    /**
     * ステータスのみを更新するSQL文を生成する。
     * <p/>
     * 障害などで、ステータスを送信失敗に更新する用途で使用する。
     *
     * @return ステータスを更新する（障害用）SQL文
     */
    private String createUpdateFailureStatusSql() {
        return "UPDATE " + tableName
                + " SET "
                + statusColumnName + " = ?, "
                + sendDateTimeColumnName + " = null "
                + "WHERE " + mailRequestIdColumnName + " = ?"
                + " AND " + statusColumnName + " = ?";
    }

    /**
     * レコードを登録するためのINSERT文を生成する。
     *
     * @return 生成したINSERT文
     */
    private String createInsertSql() {
        String insert = "INSERT INTO " + tableName + " ("
                + mailRequestIdColumnName + ", "
                + subjectColumnName + ", "
                + fromColumnName + ", "
                + replyColumnName + ", "
                + returnPathColumnName + ", "
                + mailBodyColumnName + ", "
                + charsetColumnName + ", "
                + statusColumnName + ", "
                + requestDateTimeColumnName + "";

        String values = "?,?,?,?,?,?,?,?,?";
        if (StringUtil.hasValue(mailSendPatternIdColumnName)) {
            insert += ", " + mailSendPatternIdColumnName;
            values += ",?";
        }
        return insert + ") values (" + values + ')';
    }

    /**
     * 未処理データのメール送信バッチのプロセスIDを更新するSQLを生成する。
     *
     * @return 未処理データのメール送信バッチのプロセスIDを更新するSQL
     */
    private String createUpdateSendProcessIdSql() {
        String update = "UPDATE " + tableName
                + " SET " + sendProcessIdColumnName + " = ?"
                + " WHERE " + statusColumnName + " = ? ";
		if (StringUtil.hasValue(mailSendPatternIdColumnName)) {
			update += "AND " + mailSendPatternIdColumnName + " = ? ";
		}
		update += " AND " + sendProcessIdColumnName + " IS NULL ";
        return update;
    }

    /**
     * メール送信要求の1レコード分の情報を保持するクラス。
     *
     * @author hisaaki shioiri
     */
    public static class MailRequest {

        /** メール送信要求の1レコード分の情報を保持するオブジェクト */
        private final SqlRow record;

        /**
         * メール送信要求の1レコード文の情報を保持するインスタンスを生成する。
         *
         * @param record 1レコードを表すレコード
         */
        public MailRequest(SqlRow record) {
            this.record = record;
        }

        /**
         * メールリクエストIDを取得する。
         *
         * @return メールリクエストID
         */
        public String getMailRequestId() {
            return record.getString("MAIL_REQUEST_ID");
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
         * 送信者のアドレスを取得する。
         *
         * @return From 送信者のアドレス
         */
        public String getFrom() {
            return record.getString("FROM_ADDRESS");
        }

        /**
         * 返信先のアドレスを取得する。
         *
         * @return 返信先のアドレス
         */
        public String getReplyAddress() {
            return record.getString("REPLY_ADDRESS");
        }

        /**
         * 差し戻し先のアドレスを取得する。
         *
         * @return 差し戻し先のアドレス
         */
        public String getReturnPath() {
            return record.getString("RETURN_PATH");
        }

        /**
         * メール本文を取得する。
         *
         * @return メール本文
         */
        public String getMailBody() {
            return record.getString("MAIL_BODY");
        }

        /**
         * 文字セットを取得する。
         *
         * @return 文字セット
         */
        public String getCharset() {
            return record.getString("CHARSET");
        }
    }
}

