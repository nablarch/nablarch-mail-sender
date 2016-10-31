package nablarch.common.mail;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import nablarch.core.repository.SystemRepository;
import nablarch.core.repository.di.DiContainer;
import nablarch.core.repository.di.config.xml.XmlComponentDefinitionLoader;
import nablarch.test.support.tool.HereisDb;

public class MailTestUtil {

    /**
     * メール送信パターンIDに対応したテースト用テーブルを作成する。
     *
     * @param connection DB接続
     * @throws SQLException
     */
    protected static void setUpMailTableSupportMailSendPatternId(Connection connection) throws SQLException {
        dropTable(connection, "MAIL_REQUEST_PATTERN");

        // テーブル作成
        HereisDb.sql(connection);
        /*
        CREATE TABLE MAIL_REQUEST_PATTERN
        (
            MAIL_REQUEST_ID                VARCHAR2(20 CHAR) NOT NULL,
            SUBJECT                        VARCHAR2(150 CHAR) NOT NULL,
            MAIL_FROM                      VARCHAR2(100 CHAR) NOT NULL,
            REPLY_TO                       VARCHAR2(100 CHAR) NOT NULL,
            RETURN_PATH                    VARCHAR2(100 CHAR) NOT NULL,
            CHARSET                        VARCHAR2(50 CHAR) NOT NULL,
            STATUS                         CHAR(1 CHAR) NOT NULL,
            REQUEST_DATETIME               TIMESTAMP NOT NULL,
            SEND_DATETIME                  TIMESTAMP,
            MAIL_BODY                      CLOB NOT NULL,
            MAIL_SEND_PATTERN_ID           CHAR(2 CHAR) NOT NULL
        )
        ;
        ALTER TABLE MAIL_REQUEST_PATTERN
            ADD(PRIMARY KEY (MAIL_REQUEST_ID) USING INDEX)
        ;
        */

    }

    /**
     * 出力ライブラリ(メール送信)関連のテーブルを作成する。
     *
     * @param connection コネクション
     * @throws SQLException
     */
    protected static void setUpMailTable(Connection connection) throws SQLException {

        dropTable(connection, "MAIL_RECIPIENT", "MAIL_ATTACHED_FILE", "MAIL_REQUEST", "MAIL_SBN_TBL", "MAIL_TEMPLATE");

        // テーブル作成
        HereisDb.sql(connection);
        /*
        CREATE TABLE MAIL_REQUEST
        (
            MAIL_REQUEST_ID                NVARCHAR2(20) NOT NULL,
            SUBJECT                        NVARCHAR2(150) NOT NULL,
            MAIL_FROM                      NVARCHAR2(100) NOT NULL,
            REPLY_TO                       NVARCHAR2(100) NOT NULL,
            RETURN_PATH                    NVARCHAR2(100) NOT NULL,
            CHARSET                        NVARCHAR2(50) NOT NULL,
            STATUS                         NCHAR(1) NOT NULL,
            REQUEST_DATETIME               TIMESTAMP NOT NULL,
            SEND_DATETIME                  TIMESTAMP,
            MAIL_BODY                      NCLOB NOT NULL
        )
        ;
        ALTER TABLE MAIL_REQUEST
            ADD(PRIMARY KEY (MAIL_REQUEST_ID) USING INDEX)
        ;
        CREATE TABLE MAIL_RECIPIENT
        (
            MAIL_REQUEST_ID                NVARCHAR2(20) NOT NULL,
            SERIAL_NUMBER                  NUMBER(10,0) NOT NULL,
            RECIPIENT_TYPE                 NCHAR(1) NOT NULL,
            MAIL_ADDRESS                   NVARCHAR2(100) NOT NULL
        )
        ;
        ALTER TABLE MAIL_RECIPIENT
            ADD(PRIMARY KEY (MAIL_REQUEST_ID, SERIAL_NUMBER) USING INDEX)
        ;
        CREATE TABLE MAIL_ATTACHED_FILE
        (
            MAIL_REQUEST_ID                NVARCHAR2(20) NOT NULL,
            SERIAL_NUMBER                  NUMBER(10,0) NOT NULL,
            FILE_NAME                      NVARCHAR2(150) NOT NULL,
            CONTENT_TYPE                   NVARCHAR2(50) NOT NULL,
            ATTACHED_FILE                  BLOB NOT NULL
        )
        ;
        ALTER TABLE MAIL_ATTACHED_FILE
            ADD(PRIMARY KEY (MAIL_REQUEST_ID, SERIAL_NUMBER) USING INDEX)
        ;
        CREATE TABLE MAIL_TEMPLATE
        (
            MAIL_TEMPLATE_ID               NVARCHAR2(10) NOT NULL,
            LANG                           NCHAR(2) NOT NULL,
            SUBJECT                        NVARCHAR2(150) NOT NULL,
            CHARSET                        NVARCHAR2(50) NOT NULL,
            MAIL_BODY                      NCLOB NOT NULL
        )
        ;
        ALTER TABLE MAIL_TEMPLATE
            ADD(PRIMARY KEY (MAIL_TEMPLATE_ID, LANG) USING INDEX)
        ;
        CREATE TABLE MAIL_SBN_TBL
        (
            ID_COL                         CHAR(2) NOT NULL,
            NO_COL                         NUMBER(20,0) NOT NULL
        )
        ;
        ALTER TABLE MAIL_SBN_TBL
            ADD(PRIMARY KEY (ID_COL) USING INDEX)
        ;
        */

        HereisDb.table(connection);
        /*
        MAIL_SBN_TBL
        ============
        ID_COL    NO_COL
        --------- --------
        '99'      '0'
        */
    }

    /**
     * テスト対象のテーブルを削除する。
     * <p/>
     * ここで発生した例外は、テーブルが存在しない可能性があるので無視する。
     *
     * @param tables
     */
    private static void dropTable(Connection connection, String... tables) {

        for (String table : tables) {
            try {
                HereisDb.sql(connection, table);
                /*
                DROP TABLE ${table} CASCADE CONSTRAINTS;
                */
            } catch (Exception e) {
                // do nothing.
            }
        }
    }

    /**
     * バッチリクエストテーブルを作成する。
     *
     * @param testConnection コネクション
     */
    protected static void setUpBatchRequestTable(Connection connection) {
        try {

            HereisDb.sql(connection);
            /*
            DROP TABLE BATCH_REQUEST CASCADE CONSTRAINTS;
             */
        } catch (Exception e) {
            //do nothing.
        }


        HereisDb.sql(connection);
        /*
        CREATE TABLE BATCH_REQUEST
        (
            REQUEST_ID                      CHAR(10) NOT NULL,
            REQUEST_NAME                    NVARCHAR2(100) NOT NULL,
            PROCESS_HALT_FLG                CHAR(1) NOT NULL,
            PROCESS_ACTIVE_FLG              CHAR(1) NOT NULL,
            SERVICE_AVAILABLE               CHAR(1) NOT NULL
        )
        ;
        ALTER TABLE BATCH_REQUEST
                ADD(PRIMARY KEY (REQUEST_ID) USING INDEX)
        ;
        */

        HereisDb.sql(connection);
        /*
        INSERT INTO BATCH_REQUEST(
            REQUEST_ID,
            REQUEST_NAME,
            PROCESS_HALT_FLG,
            PROCESS_ACTIVE_FLG,
            SERVICE_AVAILABLE)
        VALUES('SENDMAIL00','メール送信バッチ','0', '0', '1');
        */
    }

    protected static void setUpMessageTable(Connection connection) throws Exception {
        try {

            HereisDb.sql(connection);
            /*
            DROP TABLE MESSAGE CASCADE CONSTRAINTS;
             */
        } catch (Exception e) {
            //do nothing.
        }


        HereisDb.sql(connection);
        /*
        CREATE TABLE MESSAGE
        (
            MESSAGE_ID  CHAR(10),
            LANG        CHAR(2),
            MESSAGE     VARCHAR2(200)
        )
        ;
        ALTER TABLE MESSAGE
                ADD(PRIMARY KEY (MESSAGE_ID, LANG))
        ;
        */

        HereisDb.table(connection);
        /*
        MESSAGE
        ========
        MESSAGE_ID    LANG   MESSAGE
        ------------- ------ --------------------------------------------------
        'SEND_FAIL0'  'ja'   'メール送信に失敗しました。 mailRequestId=[{0}]'
        'SEND_FAIL0'  'en'   'send mail failed. mailRequestId=[{0}]'
        'SEND_OK000'  'ja'   'メールを送信しました。 mailRequestId=[{0}]'
        'SEND_OK000'  'en'   'send mail. mailRequestId=[{0}]'
        'REQ_COUNT0'  'ja'   'メール送信要求が {0} 件あります。'
        'REQ_COUNT0'  'en'   '{0} records of mail request selected.'
        */

    }

    protected static void cleanUpMailRequestSupportPatternId(Connection connection) {
        HereisDb.sql(connection);
        /*
        DELETE FROM MAIL_REQUEST_PATTERN
         */
    }

    protected static void cleanUpTables(Connection connection) {
        HereisDb.sql(connection);
        /*
        DELETE FROM MAIL_RECIPIENT
         */

        HereisDb.sql(connection);
        /*
        DELETE FROM MAIL_ATTACHED_FILE
         */

        HereisDb.sql(connection);
        /*
        DELETE FROM MAIL_REQUEST
         */

        HereisDb.sql(connection);
        /*
        DELETE FROM MAIL_TEMPLATE
         */
    }

    /**
     * メール送信パターンIDに対応したメール送信要求テーブルからレコードを取得する。
     *
     * @param connection
     * @return
     */
    protected static List<Map<String, Object>> getMailRequestPatternRecords(Connection connection) {
        return HereisDb.query(connection);
        /*
        select * from MAIL_REQUEST_PATTERN order by MAIL_REQUEST_ID
         */
    }

    protected static List<Map<String, Object>> getMailRequestRecord(Connection connection,
            String mailRequestId) {
        List<Map<String, Object>> result = HereisDb.query(connection, mailRequestId);
        /*
        SELECT * FROM MAIL_REQUEST WHERE MAIL_REQUEST_ID = '${mailRequestId}'
         */

        return result;
    }

    /**
     * コネクションをクローズする。
     *
     * @param connection コネクション
     * @throws SQLException
     */
    protected static void closeConnection(Connection connection) throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    /**
     * リポジトリを初期化する。
     *
     * @param path 設定ファイルのパス
     */
    protected static void initializeRepository(String path) {
        SystemRepository.clear();
        XmlComponentDefinitionLoader loader = new XmlComponentDefinitionLoader(path);
        DiContainer container = new DiContainer(loader);
        SystemRepository.load(container);
    }
}

