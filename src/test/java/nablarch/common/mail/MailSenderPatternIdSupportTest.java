package nablarch.common.mail;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

import javax.mail.Address;
import javax.mail.Authenticator;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage.RecipientType;

import nablarch.core.date.SystemTimeUtil;
import nablarch.core.util.FileUtil;
import nablarch.fw.launcher.CommandLine;
import nablarch.fw.launcher.Main;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;
import nablarch.test.support.log.app.OnMemoryLogWriter;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import mockit.Mock;
import mockit.MockUp;

/**
 * {@link nablarch.common.mail.MailSender}のテストクラス。
 * <p/>
 * 本クラスでは、メール送信パターンID毎に送信されることを確認するテストを行う。
 * メール送信パターンIDを使用しないパターンのテストは、{@link MailSender}にて行う。
 */
@RunWith(DatabaseTestRunner.class)
public class MailSenderPatternIdSupportTest extends MailTestSupport {

    @Rule
    public SystemRepositoryResource repositoryResource = new SystemRepositoryResource(
            "nablarch/common/mail/MailSenderPatternIdSupportTest.xml");

    /** 出力ライブラリ(メール送信)のコード値を保持するデータオブジェクト */
    private static MailConfig mailConfig;

    @Override
    @Before
    public void before() throws Exception {
        super.before();
        mailConfig = repositoryResource.getComponent("mailConfig");

        VariousDbTestHelper.setUpTable(
                new MailTestMessage("SEND_FAIL0", "ja", "メール送信に失敗しました。 mailRequestId=[{0}]"),
                new MailTestMessage("SEND_FAIL0", "en", "send mail failed. mailRequestId=[{0}]"),
                new MailTestMessage("SEND_OK000", "ja", "メールを送信しました。 mailRequestId=[{0}]"),
                new MailTestMessage("SEND_OK000", "en", "send mail. mailRequestId=[{0}]"),
                new MailTestMessage("REQ_COUNT0", "ja", "メール送信要求が {0} 件あります。"),
                new MailTestMessage("REQ_COUNT0", "en", "{0} records of mail request selected."));

        VariousDbTestHelper.setUpTable(new MailBatchRequest("SENDMAIL00", "メール送信バッチ", "0", "0", "1"));
        OnMemoryLogWriter.clear();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        System.out.println("MailSenderPatternIdSupportTest.tearDown");
    }

    /**
     * {@link nablarch.fw.launcher.Main#execute(nablarch.fw.launcher.CommandLine)}のテスト。
     * <p/>
     * 処理が正常終了する場合のテスト<br/>
     * <br/>
     * TO:0件,CC:複数,BCC1件,添付ファイルなし
     *
     * @throws Exception
     */
    @Test
    public void testExecuteNormalEnd1() throws Exception {

        // データ準備
        String mailRequestId = "1";
        String subject = "正常系1";

        VariousDbTestHelper.setUpTable(
                new MailRequestPattern(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody, "02"),
                new MailRequestPattern("2", subject, from, replyTo, returnPath, charset, mailConfig.getStatusUnsent(),
                        SystemTimeUtil.getTimestamp(), null, mailBody, "01"),
                new MailRequestPattern("3", subject, from, replyTo, returnPath, charset, mailConfig.getStatusUnsent(),
                        SystemTimeUtil.getTimestamp(), null, mailBody, "03"));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeCC(), cc1),
                new MailRecipient(mailRequestId, 2L, mailConfig.getRecipientTypeCC(), cc2),
                new MailRecipient(mailRequestId, 3L, mailConfig.getRecipientTypeCC(), cc3),
                new MailRecipient(mailRequestId, 4L, mailConfig.getRecipientTypeBCC(), bcc1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderPatternIdSupportTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge", "-mailSendPatternId", "02");
        int execute = Main.execute(commandLine);
        assertThat("正常終了なので戻り値は0となる。", execute, is(0));

        // ログ出力確認
        assertLog("メール送信要求が 1 件あります。");
        assertLog("メールを送信しました。 mailRequestId=[" + mailRequestId + "]");

        // bcc1でメールを受信
        Session session = Session.getInstance(sessionProperties, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("bcc1", "default");
            }
        });
        session.setDebug(true);

        Store store = session.getStore("pop3");
        store.connect();
        Folder folder = openFolder(store);
        Message[] messages = folder.getMessages();
        assertThat("メールが1通とどいているはず", messages.length, is(1));

        // メールの検証
        Message message = messages[0];
        Address[] messageFrom = message.getFrom();
        assertThat("fromアドレスの数", messageFrom.length, is(1));
        assertThat("fromアドレス", ((InternetAddress) messageFrom[0]).getAddress(), is(from));

        Address[] messageTO = message.getRecipients(RecipientType.TO);
        assertNull("TOアドレス", messageTO);

        Address[] messageCC = message.getRecipients(RecipientType.CC);
        assertThat("CCアドレスの数", messageCC.length, is(3));
        assertThat("CCアドレス1", ((InternetAddress) messageCC[0]).getAddress(), is(cc1));
        assertThat("CCアドレス2", ((InternetAddress) messageCC[1]).getAddress(), is(cc2));
        assertThat("CCアドレス3", ((InternetAddress) messageCC[2]).getAddress(), is(cc3));

        // BCCはアサートできない

        Address[] messageReplyTo = message.getReplyTo();
        assertThat("ReplyToの数", messageReplyTo.length, is(1));
        assertThat("ReplyToアドレス", ((InternetAddress) messageReplyTo[0]).getAddress(), is(replyTo));

        String[] messageReturnPath = message.getHeader("Return-Path");
        assertThat("RetrunPathの数", messageReturnPath.length, is(1));
        assertThat("RetrunPathアドレス", messageReturnPath[0], is("<" + returnPath + ">"));

        String messageSubject = message.getSubject();
        assertThat("件名", messageSubject, is(subject));

        assertThat("Content-Type", message.getContentType(), containsString("text/plain"));
        assertThat("charset", message.getContentType(), containsString(charset));

        assertThat("添付ファイルなしなので", message.getContent(), is(instanceOf(String.class)));
        assertThat("本文", (String) message.getContent(), is(mailBody));

        // DBの検証（ステータスと送信日時）
        List<MailRequestPattern> mailRequestPatternList = VariousDbTestHelper.findAll(MailRequestPattern.class,
                "mailRequestId");
        assertThat("レコード取得数", mailRequestPatternList.size(), is(3));

        //----------------------------------------------------------------------
        // 送信対象のパターンID
        //----------------------------------------------------------------------
        assertThat("ステータスが更新されているはず", mailRequestPatternList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestPatternList.get(0).sendDatetime, is(notNullValue()));

        //----------------------------------------------------------------------
        // 送信対象外のパターンID
        //----------------------------------------------------------------------
        assertThat("ステータスが更新されているはず", mailRequestPatternList.get(1).status, is(mailConfig.getStatusUnsent()));
        assertThat("送信日時が登録されているはず", mailRequestPatternList.get(1).sendDatetime, is(nullValue()));

        assertThat("ステータスが更新されているはず", mailRequestPatternList.get(2).status, is(mailConfig.getStatusUnsent()));
        assertThat("送信日時が登録されているはず", mailRequestPatternList.get(2).sendDatetime, is(nullValue()));

    }

    /**
     * {@link nablarch.fw.launcher.Main#execute(nablarch.fw.launcher.CommandLine)}のテスト。
     * <p/>
     * 処理が正常終了する場合のテスト<br/>
     * <br/>
     * TO:1件、CC:0件、BCC：複数件、添付ファイル1つ
     *
     * @throws Exception
     */
    @Test
    public void testExecuteNormalEnd2() throws Exception {

        // データ準備
        String mailRequestId = "2";
        String subject = "正常系2";

        VariousDbTestHelper.setUpTable(
                new MailRequestPattern(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody, "AA"));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId, 2L, mailConfig.getRecipientTypeBCC(), bcc1),
                new MailRecipient(mailRequestId, 3L, mailConfig.getRecipientTypeBCC(), bcc2),
                new MailRecipient(mailRequestId, 4L, mailConfig.getRecipientTypeBCC(), bcc3));

        File file = new File(FileUtil.getClasspathResourceURL("nablarch/common/mail/mailAttachedFile.txt")
                                     .toURI());
        VariousDbTestHelper.setUpTable(
                new MailAttachedFile(mailRequestId, 1L, file.getName(), "text/plain", convertToByteArray(file)));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderPatternIdSupportTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge", "-mailSendPatternId", "AA");
        int execute = Main.execute(commandLine);
        assertThat("正常終了なので戻り値は0となる。", execute, is(0));

        // ログ出力確認
        assertLog("メール送信要求が 1 件あります。");
        assertLog("メールを送信しました。 mailRequestId=[" + mailRequestId + "]");

        // bcc1でメールを受信
        Session session = Session.getInstance(sessionProperties, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("bcc1", "default");
            }
        });
        Store store1 = session.getStore("pop3");
        store1.connect();
        Folder folder1 = openFolder(store1);
        Message[] messages = folder1.getMessages();
        assertThat("メールが1通とどいているはず", messages.length, is(1));

        // メールの検証
        Message message1 = messages[0];
        Address[] messageFrom = message1.getFrom();
        assertThat("fromアドレスの数", messageFrom.length, is(1));
        assertThat("fromアドレス", ((InternetAddress) messageFrom[0]).getAddress(), is(from));

        Address[] messageTO = message1.getRecipients(RecipientType.TO);

        assertThat("TOアドレスの数", messageTO.length, is(1));
        assertThat("TOアドレス1", ((InternetAddress) messageTO[0]).getAddress(), is(to1));

        Address[] messageReplyTo = message1.getReplyTo();
        assertThat("ReplyToの数", messageReplyTo.length, is(1));
        assertThat("ReplyToアドレス", ((InternetAddress) messageReplyTo[0]).getAddress(), is(replyTo));

        String[] messageReturnPath = message1.getHeader("Return-Path");
        assertThat("RetrunPathの数", messageReturnPath.length, is(1));
        assertThat("RetrunPathアドレス", messageReturnPath[0], is("<" + returnPath + ">"));

        String messageSubject = message1.getSubject();
        assertThat("件名", messageSubject, is(subject));

        assertThat("Content-Type", message1.getContentType(), containsString("multipart/mixed"));

        assertThat("添付ファイル付きなので", message1.getContent(), is(instanceOf(Multipart.class)));

        Multipart multiPart = (Multipart) message1.getContent();
        assertThat("添付ファイル一つなので", multiPart.getCount(), is(2));

        for (int i = 0; i < multiPart.getCount(); i++) {
            Part part = multiPart.getBodyPart(i);
            String disposition = part.getDisposition();
            if (Part.ATTACHMENT.equals(disposition) || Part.INLINE.equals(disposition)) {
                assertThat("添付ファイルのコンテンツタイプ", part.getContentType(),
                        containsString(aFileContentType));
                assertThat("添付ファイルのファイル名", part.getFileName(), is(file.getName()));
                assertAttachedFile(part, file);
            } else {
                assertThat("本文のcharset", part.getContentType(), containsString(charset));
                assertThat("本文", part.getContent()
                                     .toString(), is(mailBody));
            }
        }

        // bcc2と3も届いていることだけ確認
        Session session2 = Session.getInstance(sessionProperties, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("bcc2", "default");
            }
        });
        Store store2 = session2.getStore("pop3");
        store2.connect();
        Folder folder2 = openFolder(store2);
        Message[] messages2 = folder2.getMessages();
        assertThat("メールが1通とどいているはず", messages2.length, is(1));

        Session session3 = Session.getInstance(sessionProperties, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("bcc3", "default");
            }
        });
        Store store3 = session3.getStore("pop3");
        store3.connect();
        Folder folder3 = openFolder(store3);
        Message[] messages3 = folder3.getMessages();
        assertThat("メールが1通とどいているはず", messages3.length, is(1));

        // DBの検証（ステータスと送信日時）
        List<MailRequestPattern> mailRequestPatternList = VariousDbTestHelper.findAll(MailRequestPattern.class);
        assertThat("レコード取得数", mailRequestPatternList.size(), is(1));
        assertThat("ステータスが更新されているはず", mailRequestPatternList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestPatternList.get(0).sendDatetime, notNullValue());
    }

    /**
     * {@link nablarch.fw.launcher.Main#execute(nablarch.fw.launcher.CommandLine)}のテスト。
     * <p/>
     * 処理が正常終了する場合のテスト<br/>
     * <br/>
     * TO:複数件、CC:1件、BCC：0件、添付ファイル複数
     *
     * @throws Exception
     */
    @Test
    public void testExecuteNormalEnd3() throws Exception {

        // データ準備
        String mailRequestId = "3";
        String subject = "正常系3";

        VariousDbTestHelper.setUpTable(
                new MailRequestPattern(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody, "CC"));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId, 2L, mailConfig.getRecipientTypeTO(), to2),
                new MailRecipient(mailRequestId, 3L, mailConfig.getRecipientTypeTO(), to3),
                new MailRecipient(mailRequestId, 4L, mailConfig.getRecipientTypeCC(), cc1));

        File file1 = new File(FileUtil.getClasspathResourceURL("nablarch/common/mail/mailAttachedFile.txt")
                                      .toURI());
        File file2 = new File(FileUtil.getClasspathResourceURL("nablarch/common/mail/mailAttachedFile2.txt")
                                      .toURI());
        VariousDbTestHelper.setUpTable(
                new MailAttachedFile(mailRequestId, 1L, file1.getName(), "text/plain", convertToByteArray(file1)),
                new MailAttachedFile(mailRequestId, 2L, file2.getName(), "text/plain", convertToByteArray(file2)));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderPatternIdSupportTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge", "-mailSendPatternId", "CC");
        int execute = Main.execute(commandLine);
        assertThat("正常終了なので戻り値は0となる。", execute, is(0));

        // ログ出力確認
        assertLog("メール送信要求が 1 件あります。");
        assertLog("メールを送信しました。 mailRequestId=[" + mailRequestId + "]");

        // to1でメールを受信
        Session session1 = Session.getInstance(sessionProperties, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("to1", "default");
            }
        });
        Store store1 = session1.getStore("pop3");
        store1.connect();
        Folder folder1 = openFolder(store1);
        Message[] messages = folder1.getMessages();
        assertThat("メールが1通とどいているはず", messages.length, is(1));

        // メールの検証
        Message message1 = messages[0];
        Address[] messageFrom = message1.getFrom();
        assertThat("fromアドレスの数", messageFrom.length, is(1));
        assertThat("fromアドレス", ((InternetAddress) messageFrom[0]).getAddress(), is(from));

        Address[] messageTO = message1.getRecipients(RecipientType.TO);
        assertThat("TOアドレスの数", messageTO.length, is(3));
        assertThat("TOアドレス1", ((InternetAddress) messageTO[0]).getAddress(), is(to1));
        assertThat("TOアドレス2", ((InternetAddress) messageTO[1]).getAddress(), is(to2));
        assertThat("TOアドレス3", ((InternetAddress) messageTO[2]).getAddress(), is(to3));

        Address[] messageCC = message1.getRecipients(RecipientType.CC);
        assertThat("CCアドレスの数", messageCC.length, is(1));
        assertThat("CCアドレス1", ((InternetAddress) messageCC[0]).getAddress(), is(cc1));

        Address[] messageReplyTo = message1.getReplyTo();
        assertThat("ReplyToの数", messageReplyTo.length, is(1));
        assertThat("ReplyToアドレス", ((InternetAddress) messageReplyTo[0]).getAddress(), is(replyTo));

        String[] messageReturnPath = message1.getHeader("Return-Path");
        assertThat("RetrunPathの数", messageReturnPath.length, is(1));
        assertThat("RetrunPathアドレス", messageReturnPath[0], is("<" + returnPath + ">"));

        String messageSubject = message1.getSubject();
        assertThat("件名", messageSubject, is(subject));

        assertThat("Content-Type", message1.getContentType(), containsString("multipart/mixed"));

        assertThat("添付ファイル付きなので", message1.getContent(), is(instanceOf(Multipart.class)));

        Multipart multiPart = (Multipart) message1.getContent();
        assertThat("添付ファイル2つなので", multiPart.getCount(), is(3));

        for (int i = 0; i < multiPart.getCount(); i++) {
            final Part part = multiPart.getBodyPart(i);
            final String disposition = part.getDisposition();

            if (Part.ATTACHMENT.equals(disposition) || Part.INLINE.equals(disposition)) {
                if (part.getFileName()
                        .equals(file1.getName())) {
                    assertThat("添付ファイルのコンテンツタイプ", part.getContentType(),
                            containsString(aFileContentType));
                    assertAttachedFile(part, file1);
                } else if (part.getFileName()
                               .equals(file2.getName())) {
                    assertThat("添付ファイルのコンテンツタイプ", part.getContentType(),
                            containsString(aFileContentType));
                    assertAttachedFile(part, file2);
                } else {
                    fail("ファイル名が違う");
                }
            } else {
                assertThat("本文のcharset", part.getContentType(), containsString(charset));
                assertThat("本文", part.getContent()
                                     .toString(), is(mailBody));
            }
        }

        // DBの検証（ステータスと送信日時）
        List<MailRequestPattern> mailRequestPatternList = VariousDbTestHelper.findAll(MailRequestPattern.class);
        assertThat("レコード取得数", mailRequestPatternList.size(), is(1));
        assertThat("ステータスが更新されているはず", mailRequestPatternList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestPatternList.get(0).sendDatetime, notNullValue());
    }

    /**
     * {@link nablarch.fw.launcher.Main#execute(nablarch.fw.launcher.CommandLine)}のテスト。
     * <p/>
     * 処理が異常終了する場合のテスト。<br/>
     * <br/>
     * 接続エラーの場合（接続ポート番号を変えて実行する）。
     *
     * @throws java.sql.SQLException
     */
    @Test
    public void testExecuteAbnormalEndConnectionRefused() throws SQLException {

        // データ準備
        String mailRequestId1 = "4-1";
        String subject1 = "異常系1-1(接続エラー)";
        String mailRequestId2 = "4-2";
        String subject2 = "異常系1-2(接続エラー)";

        VariousDbTestHelper.setUpTable(
                new MailRequestPattern(mailRequestId1, subject1, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody, "00"),
                new MailRequestPattern(mailRequestId2, subject2, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody, "00"));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId1, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId2, 2L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderPatternIdSupportTestPortError.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge", "-mailSendPatternId", "00");
        int rc = Main.execute(commandLine);
        assertThat("接続エラー(MessagingException)はリトライ例外で、上限を超えて異常終了し戻り値は180になる。", rc, is(180));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "メール送信要求が 2 件あります。",
                "Failed to send a mail, will be retried to send later. mailRequestId=[4-1], error message=[",
                "Failed to send a mail, will be retried to send later. mailRequestId=[4-2], error message=[",
                "req_id = [SENDMAIL00] usr_id = [hoge] caught a exception to retry. start retry. retryCount[1]",
                "req_id = [SENDMAIL00] usr_id = [hoge] retry process failed. retry limit was exceeded.");

        // DBの検証（ステータスと送信日時）
        List<MailRequestPattern> mailRequestPatternList = VariousDbTestHelper.findAll(MailRequestPattern.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestPatternList.size(), is(2));
        assertThat("ステータスが「送信失敗」になっているはず", mailRequestPatternList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestPatternList.get(0).sendDatetime, is(nullValue()));
        assertThat("ステータスが「送信失敗」になっているはず", mailRequestPatternList.get(1).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestPatternList.get(1).sendDatetime, is(nullValue()));
    }

    /**
     * {@link nablarch.fw.launcher.Main#execute(nablarch.fw.launcher.CommandLine)}のテスト。
     * <p/>
     * 処理が異常終了する場合のテスト<br/>
     * <br/>
     * 接続タイムアウトの場合（接続タイムアウト値とポート番号を変更して実行する）。
     *
     * @throws java.sql.SQLException
     */
    @Test
    public void testExecuteAbnormalEndConnectionTimeout() throws SQLException {

        // データ準備
        String mailRequestId1 = "5-1";
        String subject1 = "異常系2-1（接続タイムアウト）";
        String mailRequestId2 = "5-2";
        String subject2 = "異常系2-2（接続タイムアウト）";

        VariousDbTestHelper.setUpTable(
                new MailRequestPattern(mailRequestId1, subject1, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody, "AB"),
                new MailRequestPattern(mailRequestId2, subject2, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody, "AB"));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId1, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId2, 2L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderPatternIdSupportTestConnectionTimeout.xml",
                "-requestPath", "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge", "-mailSendPatternId",
                "AB");
        int rc = Main.execute(commandLine);
        assertThat("タイムアウト(MessagingException)はリトライ例外で、上限を超えて異常終了し戻り値は180になる。", rc, is(180));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "メール送信要求が 2 件あります。",
                "Failed to send a mail, will be retried to send later. mailRequestId=[5-1], error message=[",
                "Failed to send a mail, will be retried to send later. mailRequestId=[5-2], error message=[",
                "req_id = [SENDMAIL00] usr_id = [hoge] caught a exception to retry. start retry. retryCount[1]",
                "req_id = [SENDMAIL00] usr_id = [hoge] retry process failed. retry limit was exceeded.");

        // DBの検証（ステータスと送信日時）
        List<MailRequestPattern> mailRequestPatternList = VariousDbTestHelper.findAll(MailRequestPattern.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestPatternList.size(), is(2));
        assertThat("ステータスが「送信失敗」になっているはず", mailRequestPatternList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestPatternList.get(0).sendDatetime, is(nullValue()));
        assertThat("ステータスが「送信失敗」になっているはず", mailRequestPatternList.get(1).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestPatternList.get(1).sendDatetime, is(nullValue()));

    }

    /**
     * {@link nablarch.fw.launcher.Main#execute(nablarch.fw.launcher.CommandLine)}のテスト。
     * <p/>
     * 処理が異常終了する場合のテスト<br/>
     * <br/>
     * 送信エラーの場合(charsetに不正な値を入れて実行)。
     *
     * @throws java.sql.SQLException
     */
    @Test
    public void testExecuteAbnormalEndSendError() throws SQLException {
        // データ準備
        String mailRequestId1 = "6-1";
        String subject1 = "異常系3-1(送信エラー）";
        String mailRequestId2 = "6-2";
        String subject2 = "異常系3-2(送信エラー）";

        VariousDbTestHelper.setUpTable(
                new MailRequestPattern(mailRequestId1, subject1, from, replyTo, returnPath, "aaaa",
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody, "QQ"),
                new MailRequestPattern(mailRequestId2, subject2, from, replyTo, returnPath, "aaaa",
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody, "QQ"));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId1, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId2, 2L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderPatternIdSupportTestRetry.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge", "-mailSendPatternId", "QQ");
        int rc = Main.execute(commandLine);
        assertThat("接続エラー(MessagingException)はリトライ例外で、上限を超えて異常終了し戻り値は180になる。", rc, is(180));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "メール送信要求が 2 件あります。",
                "Failed to send a mail, will be retried to send later. mailRequestId=[6-1], error message=[",
                "Failed to send a mail, will be retried to send later. mailRequestId=[6-2], error message=[",
                "req_id = [SENDMAIL00] usr_id = [hoge] caught a exception to retry. start retry. retryCount[1]",
                "req_id = [SENDMAIL00] usr_id = [hoge] retry process failed. retry limit was exceeded.");

        // DBの検証（ステータスと送信日時）
        List<MailRequestPattern> mailRequestPatternList = VariousDbTestHelper.findAll(MailRequestPattern.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestPatternList.size(), is(2));
        assertThat("ステータスが「送信失敗」になっているはず", mailRequestPatternList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestPatternList.get(0).sendDatetime, is(nullValue()));
        assertThat("ステータスが「送信失敗」になっているはず", mailRequestPatternList.get(1).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestPatternList.get(1).sendDatetime, is(nullValue()));

    }

    /**
     * {@link nablarch.fw.launcher.Main#execute(nablarch.fw.launcher.CommandLine)}のテスト。
     * <p/>
     * 処理が異常終了する場合のテスト<br/>
     * <br/>
     * 送信タイムアウトの場合（送信タイムアウト値を変更して実行する）。
     *
     * @throws Exception
     */
    @Test
    public void testExecuteAbnormalEndSendTimeout() throws Exception {

        // データ準備
        String mailRequestId1 = "7-1";
        String subject1 = "異常系4-1(送信タイムアウト）";
        String mailRequestId2 = "7-2";
        String subject2 = "異常系4-2(送信タイムアウト）";

        VariousDbTestHelper.setUpTable(
                new MailRequestPattern(mailRequestId1, subject1, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody, "hg"),
                new MailRequestPattern(mailRequestId2, subject2, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody, "hg"));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId1, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId2, 2L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderPatternIdSupportTestSendTimeout.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge", "-mailSendPatternId", "hg");
        int rc = Main.execute(commandLine);
        assertThat("タイムアウト(MessagingException)はリトライ例外で、上限を超えて異常終了し戻り値は180になる。", rc, is(180));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "メール送信要求が 2 件あります。",
                "Failed to send a mail, will be retried to send later. mailRequestId=[7-1], error message=[",
                "Failed to send a mail, will be retried to send later. mailRequestId=[7-2], error message=[",
                "req_id = [SENDMAIL00] usr_id = [hoge] caught a exception to retry. start retry. retryCount[1]",
                "req_id = [SENDMAIL00] usr_id = [hoge] retry process failed. retry limit was exceeded.");

        // DBの検証（ステータスと送信日時）
        List<MailRequestPattern> mailRequestPatternList = VariousDbTestHelper.findAll(MailRequestPattern.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestPatternList.size(), is(2));
        assertThat("ステータスが「送信失敗」になっているはず", mailRequestPatternList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestPatternList.get(0).sendDatetime, is(nullValue()));
        assertThat("ステータスが「送信失敗」になっているはず", mailRequestPatternList.get(1).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestPatternList.get(1).sendDatetime, is(nullValue()));
    }
}
