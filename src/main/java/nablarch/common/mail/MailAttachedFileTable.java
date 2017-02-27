package nablarch.common.mail;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.statement.ResultSetIterator;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.db.statement.SqlRow;
import nablarch.core.repository.initialization.Initializable;
import nablarch.core.util.FileUtil;
import nablarch.core.util.annotation.Published;

/**
 * 添付ファイル管理テーブルのスキーマ情報を保持するデータオブジェクト。
 *
 * @author Shinsuke Yoshio
 */
@Published(tag = "architect")
public class MailAttachedFileTable implements Initializable {

    /** テーブル名 */
    private String tableName;

    /** メールリクエストIDのカラム名 */
    private String mailRequestIdColumnName;

    /** 連番のカラム名 */
    private String serialNumberColumnName;

    /** ファイル名のカラム名 */
    private String fileNameColumnName;

    /** Content-Typeのカラム名 */
    private String contentTypeColumnName;

    /** ファイルデータのカラム名 */
    private String fileColumnName;

    /** 添付ファイルを登録するSQL */
    private String insertSql;

    /** 添付ファイルを取得するSQL */
    private String findSql;

    /**
     * 添付ファイル管理テーブルの名前を設定する。
     *
     * @param tableName 添付ファイル管理テーブルの名前
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * 添付ファイル管理テーブルの要求IDカラムの名前を設定する。
     *
     * @param mailRequestIdColumnName 添付ファイル管理テーブルの要求IDカラムの名前
     */
    public void setMailRequestIdColumnName(String mailRequestIdColumnName) {
        this.mailRequestIdColumnName = mailRequestIdColumnName;
    }

    /**
     * 添付ファイル管理テーブルの連番カラムの名前を設定する。
     *
     * @param serialNumberColumnName 添付ファイル管理テーブルの連番カラムの名前
     */
    public void setSerialNumberColumnName(String serialNumberColumnName) {
        this.serialNumberColumnName = serialNumberColumnName;
    }

    /**
     * 添付ファイル管理テーブルの添付ファイル名カラムの名前を設定する。
     *
     * @param fileNameColumnName 添付ファイル管理テーブルの添付ファイル名カラムの名前
     */
    public void setFileNameColumnName(String fileNameColumnName) {
        this.fileNameColumnName = fileNameColumnName;
    }

    /**
     * 添付ファイル管理テーブルのContent-Typeカラムの名前を設定する。
     *
     * @param contentTypeColumnName 添付ファイル管理テーブルのContent-Typeカラムの名前
     */
    public void setContentTypeColumnName(String contentTypeColumnName) {
        this.contentTypeColumnName = contentTypeColumnName;
    }

    /**
     * 添付ファイル管理テーブルの添付ファイルカラムの名前を設定する。
     *
     * @param fileColumnName 添付ファイル管理テーブルの添付ファイルカラムの名前
     */
    public void setFileColumnName(String fileColumnName) {
        this.fileColumnName = fileColumnName;
    }

    /**
     * 添付ファイル管理テーブルに添付ファイルの情報を登録する。
     *
     * @param mailRequestId メールリクエストID
     * @param context 添付ファイルの情報
     */
    public void insert(String mailRequestId, MailContext context) {
        AppDbConnection connection = DbConnectionContext.getConnection();

        int serialNo = 1;
        SqlPStatement statement = connection.prepareStatement(insertSql);
        statement.setString(1, mailRequestId);
        for (AttachedFile attachedFile : context.getAttachedFileList()) {
            statement.setInt(2, serialNo);
            statement.setString(3, attachedFile.getName());
            statement.setString(4, attachedFile.getContentType());

            InputStream stream = null;
            try {
                stream = new FileInputStream(attachedFile.getFile());
                statement.setBinaryStream(5, stream, (int) attachedFile.getFile().length());
                statement.executeUpdate();
            } catch (IOException e) {
                throw new RuntimeException(
                        "an error occurred while reading file:", e);
            } finally {
                FileUtil.closeQuietly(stream);
            }
            serialNo++;
        }
    }

    /**
     * 添付ファイルデータを取得する。
     *
     *
     * @param mailRequestId メールリクエストID
     * @return 取得した添付ファイルデータ
     */
    public List<MailAttachedFileTable.MailAttachedFile> find(String mailRequestId) {
        AppDbConnection connection = DbConnectionContext.getConnection();
        SqlPStatement statement = connection.prepareStatement(findSql);
        statement.setString(1, mailRequestId);

        ResultSetIterator sqlRows = statement.executeQuery();
        List<MailAttachedFileTable.MailAttachedFile> result = new ArrayList<MailAttachedFileTable.MailAttachedFile>();
        for (SqlRow record : sqlRows) {
            result.add(new MailAttachedFileTable.MailAttachedFile(record));
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
     * <li>添付ファイル管理へレコードを追加するINSERT文</li>
     * <li>添付ファイル管理からメールリクエストIDを元にレコードを取得するSELECT文(連番の昇順でソート)</li>
     * </ul>
     */
    public void initialize() {
        insertSql = "INSERT INTO " + tableName + " ( "
                + mailRequestIdColumnName + ", "
                + serialNumberColumnName + ", "
                + fileNameColumnName + ", "
                + contentTypeColumnName + ", "
                + fileColumnName
                + ") VALUES (?,?,?,?,?)";

        findSql = "SELECT "
                + serialNumberColumnName + " SERIAL_NUMBER, "
                + fileNameColumnName + " FILE_NAME, "
                + contentTypeColumnName + " CONTENT_TYPE, "
                + fileColumnName + " FILE_DATA "
                + "FROM " + tableName
                + " WHERE "
                + mailRequestIdColumnName + " = ? "
                + "ORDER BY " + serialNumberColumnName;
    }

    /**
     * 添付ファイル管理の1レコード分の情報を保持するクラス。
     *
     * @author hisaaki shioiri
     */
    @Published(tag = "architect")
    public static class MailAttachedFile {

        /** 添付ファイル管理の1レコード分の情報を保持するオブジェクト */
        private final SqlRow record;

        /**
         * 添付ファイル管理の1レコード文の情報を保持するインスタンスを生成する。
         *
         * @param record 1レコードを表すレコード
         */
        public MailAttachedFile(SqlRow record) {
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
         * ファイル名を取得する。
         *
         * @return ファイル名
         */
        public String getFileName() {
            return record.getString("FILE_NAME");
        }

        /**
         * Context-typeを取得する。
         *
         * @return Context-type
         */
        public String getContextType() {
            return record.getString("CONTENT_TYPE");
        }

        /**
         * ファイルのデータを取得する。
         *
         * @return ファイルのデータ
         */
        public byte[] getFile() {
            return record.getBytes("FILE_DATA");
        }
    }
}
