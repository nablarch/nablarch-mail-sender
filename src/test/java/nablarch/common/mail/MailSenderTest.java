package nablarch.common.mail;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.core.Is.is;
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
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.PasswordAuthentication;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage.RecipientType;

import nablarch.core.date.SystemTimeUtil;
import nablarch.core.db.DbAccessException;
import nablarch.core.util.FileUtil;
import nablarch.fw.launcher.CommandLine;
import nablarch.fw.launcher.Main;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.TargetDb;
import nablarch.test.support.db.helper.VariousDbTestHelper;
import nablarch.test.support.log.app.OnMemoryLogWriter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import mockit.Mock;
import mockit.MockUp;

/**
 * {@link MailSender}のテストクラス。
 */
@RunWith(DatabaseTestRunner.class)
public class MailSenderTest extends MailTestSupport {

    @Rule
    public SystemRepositoryResource repositoryResource = new SystemRepositoryResource(
            "nablarch/common/mail/MailSenderTest.xml");

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
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
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
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeCC(), cc1),
                new MailRecipient(mailRequestId, 2L, mailConfig.getRecipientTypeCC(), cc2),
                new MailRecipient(mailRequestId, 3L, mailConfig.getRecipientTypeCC(), cc3),
                new MailRecipient(mailRequestId, 4L, mailConfig.getRecipientTypeBCC(), bcc1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
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
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが更新されているはず", mailRequestList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(0).sendDatetime, notNullValue());
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
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
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

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
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
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
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが更新されているはず", mailRequestList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(0).sendDatetime, is(notNullValue()));
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
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
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

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
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
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
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが更新されているはず", mailRequestList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(0).sendDatetime, is(notNullValue()));
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 処理が異常終了する場合のテスト。<br/>
     * <br/>
     * 接続エラーの場合（接続ポート番号を変えて実行する）。
     *
     * @throws SQLException
     */
    @Test
    public void testExecuteAbnormalEndConnectionRefused() throws SQLException {

        // データ準備
        String mailRequestId = "4";
        String subject = "異常系1(接続エラー)";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTestOverrideSmtpPort.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("接続エラー(MessagingException)はリトライ例外を送出するため異常終了", rc, is(20));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "nablarch.fw.handler.retry.RetryableException: Failed to send a mail, will be retried to send later. Mail RequestId:[4], Error message:[");

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスはリトライなので「未送信」", mailRequestList.get(0).status, is(mailConfig.getStatusUnsent()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, is(nullValue()));
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 処理が異常終了する場合のテスト<br/>
     * <br/>
     * 接続タイムアウトの場合（接続タイムアウト値とポート番号を変更して実行する）。
     *
     * @throws SQLException
     */
    @Test
    public void testExecuteAbnormalEndConnectionTimeout() throws SQLException {

        // データ準備
        String mailRequestId = "5";
        String subject = "異常系2（接続タイムアウト）";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTestOverrideSmtpConnetcionTimeout.xml",
                "-requestPath", "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("接続エラー(MessagingException)はリトライ例外を送出するため異常終了", execute, is(20));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "nablarch.fw.handler.retry.RetryableException: Failed to send a mail, will be retried to send later. Mail RequestId:[5], Error message:[");


        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスがリトライなので「未送信」", mailRequestList.get(0).status, is(mailConfig.getStatusUnsent()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, is(nullValue()));
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 処理が異常終了する場合のテスト<br/>
     * <br/>
     * 送信エラーの場合(charsetに不正な値を入れて実行)。
     *
     * @throws SQLException
     */
    @Test
    public void testExecuteAbnormalEndSendError() throws SQLException {
        // データ準備
        String mailRequestId = "6";
        String subject = "異常系4(送信エラー）";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, "aaaa", mailConfig.getStatusUnsent(),
                        SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("MessagingExceptionはリトライ例外を送出するため異常終了", rc, is(20));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "nablarch.fw.handler.retry.RetryableException: Failed to send a mail, will be retried to send later. Mail RequestId:[6], Error message:[");

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスがリトライなので「未送信」", mailRequestList.get(0).status, is(mailConfig.getStatusUnsent()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, is(nullValue()));
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
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
        String mailRequestId = "7";
        String subject = "異常系4(送信タイムアウト）";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTestOverrideSmtpTimeout.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("タイムアウト(MessagingException)はリトライ例外を送出するため異常終了", rc, is(20));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "nablarch.fw.handler.retry.RetryableException: Failed to send a mail, will be retried to send later. Mail RequestId:[7], Error message:[");

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスがリトライなので「未送信」", mailRequestList.get(0).status, is(mailConfig.getStatusUnsent()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, is(nullValue()));
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 処理が異常終了する場合のテスト<br/>
     * <br/>
     * メールアドレス(To)でExceptionが発生するパターン。
     *
     * @throws Exception
     */
    @Test
    public void testExecuteAbnormalEndAddressExceptionTo() throws Exception {

        // データ準備
        String mailRequestId = "1";
        String subject = "正常系";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1 + "\rhoge"));

        //LogVerifier.setExpectedLogMessages(new ArrayList<Map<String,String>>() {
        //    {
        //        add(new HashMap<String, String>() {
        //            {
        //                put("logLevel", "FATAL");
        //                put("message1", "Domain contains control or whitespace");
        //            }
        //        });
        //    }
        //});

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("送信に失敗しても処理は成功なので0", execute, is(0));

        // ----- assert log -----
        //LogVerifier.verify("assert log");

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, is(nullValue()));
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 処理が異常終了する場合のテスト<br/>
     * <br/>
     * メールアドレス（CC）でExceptionが発生するパターン。
     *
     * @throws Exception
     */
    @Test
    public void testExecuteAbnormalEndAddressExceptionCC() throws Exception {

        // データ準備
        String mailRequestId = "1";
        String subject = "正常系";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 2L, mailConfig.getRecipientTypeCC(), cc1 + "\rhoge"));

        //LogVerifier.setExpectedLogMessages(new ArrayList<Map<String,String>>() {
        //    {
        //        add(new HashMap<String, String>() {
        //            {
        //                put("logLevel", "FATAL");
        //                put("message1", "Domain contains control or whitespace");
        //            }
        //        });
        //    }
        //});

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("送信に失敗しても処理は成功なので0", execute, is(0));

        // ----- assert log -----
        //LogVerifier.verify("assert log");

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, is(nullValue()));
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 処理が異常終了する場合のテスト<br/>
     * <br/>
     * メールアドレス（BCC）でExceptionが発生するパターン。
     *
     * @throws Exception
     */
    @Test
    public void testExecuteAbnormalEndAddressExceptionBCC() throws Exception {

        // データ準備
        String mailRequestId = "1";
        String subject = "正常系";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 2L, mailConfig.getRecipientTypeTO(), "hoge\n" + bcc1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("送信に失敗しても処理は成功なので0", execute, is(0));

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, is(nullValue()));
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 処理が異常終了する場合のテスト<br/>
     * <br/>
     * 送信者メールアドレスでExceptionが発生するパターン。
     *
     * @throws Exception
     */
    @Test
    public void testExecuteAbnormalEndAddressExceptionFrom() throws Exception {

        // データ準備
        String mailRequestId = "1";
        String subject = "送信者メールアドレスが不正";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from + "\rhoge", replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("送信に失敗しても処理は成功なので0", execute, is(0));

        // ----- assert log -----
        OnMemoryLogWriter.assertLogContains("writer.memory", "-ERROR- メール送信に失敗しました。 mailRequestId=[1]");

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, is(nullValue()));
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 返信先メールアドレスが不正でも送信成功するパターン。
     *
     * @throws Exception
     */
    @Test
    public void testExecuteAddressExceptionReplyTo() throws Exception {

        // データ準備
        String mailRequestId = "1";
        String subject = "返信先メールアドレスが不正";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, "hoge\n" + replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("ReplyToの変換に失敗しても、送信の処理は成功なので0", rc, is(0));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "Failed to instantiate mail address. Error message:[",
                "] Mail address:[hoge\nreply@localhost]",
                "-INFO- メールを送信しました。 mailRequestId=[1]");

        // メールの検証
        // replyToが不正になるため、replyToはfromになる。
        assertRecivingPlainMail("to1", from, from, subject, new String[] {to1}, new String[] {});

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが「送信成功」", mailRequestList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録される", mailRequestList.get(0).sendDatetime, notNullValue());

    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 処理が異常終了する場合のテスト<br/>
     * <br/>
     * 文字セットでExceptionが発生するパターン。
     *
     * @throws Exception
     */
    @Test
    public void testExecuteAbnormalEndAddressExceptionCharset() throws Exception {

        // データ準備
        String mailRequestId = "1";
        String subject = "文字セットが不正";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset + "\nhoge",
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");

        int rc = Main.execute(commandLine);
        assertThat("MessagingExceptionはリトライ例外を送出するため異常終了", rc, is(20));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "nablarch.fw.handler.retry.RetryableException: Failed to send a mail, will be retried to send later. Mail RequestId:[1], Error message:[");

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスがリトライなので「未送信」", mailRequestList.get(0).status, is(mailConfig.getStatusUnsent()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, is(nullValue()));
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 処理が異常終了する場合のテスト<br/>
     * <br/>
     * （subjectに改行を設定して実行する）。
     *
     * @throws Exception
     */
    @Test
    public void testExecuteAbnormalEndHeaderInjectionSubject() throws Exception {

        // データ準備
        String mailRequestId = "1";
        String subject = "正常系";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        mailRequestId = "2";
        subject = "異常系(subjectに改行\r\n)";

        VariousDbTestHelper.insert(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.insert(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        mailRequestId = "3";
        subject = "正常系";

        VariousDbTestHelper.insert(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.insert(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("メール送信失敗なので処理自体は成功", rc, is(0));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "-INFO- メールを送信しました。 mailRequestId=[1]",
                "-ERROR- メール送信に失敗しました。 mailRequestId=[2]",
                "-INFO- メールを送信しました。 mailRequestId=[3]");

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestList.size(), is(3));

        // id:1(success)
        assertThat("メールリクエストIDが1", mailRequestList.get(0).mailRequestId, is("1"));
        assertThat("ステータスが「送信成功」になっているはず", mailRequestList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(0).sendDatetime, is(notNullValue()));

        // id:2(fail)
        assertThat("メールリクエストIDが2", mailRequestList.get(1).mailRequestId, is("2"));
        assertThat("ステータスが「送信失敗」になっているはず", mailRequestList.get(1).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(1).sendDatetime, is(nullValue()));

        // id:3(success)
        assertThat("メールリクエストIDが3", mailRequestList.get(2).mailRequestId, is("3"));
        assertThat("ステータスが「送信成功」になっているはず", mailRequestList.get(2).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(2).sendDatetime, is(notNullValue()));

    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 処理が異常終了する場合のテスト<br/>
     * <br/>
     * （ReturnPathに改行を設定して実行する）。
     *
     * @throws Exception
     */
    @Test
    @TargetDb(exclude = TargetDb.Db.DB2) // DB2では本ケース実行後にDB2が停止してしまうため暫定対処。
    public void testExecuteAbnormalEndHeaderInjectionReturnPath() throws Exception {

        // データ準備
        String mailRequestId = "1";
        String subject = "正常系";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        mailRequestId = "2";
        subject = "異常系(returnPathに改行)";

        VariousDbTestHelper.insert(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath + ">\nRCPT TO: <to2@localhost>",
                        charset, mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.insert(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        mailRequestId = "3";
        subject = "正常系";

        VariousDbTestHelper.insert(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.insert(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("送信に失敗しても処理は成功なので0", rc, is(0));

        // ----- assert log -----
        OnMemoryLogWriter.assertLogContains("writer.memory",
                "-INFO- メールを送信しました。 mailRequestId=[1]",
                "-ERROR- メール送信に失敗しました。 mailRequestId=[2]",
                "-INFO- メールを送信しました。 mailRequestId=[3]");

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestList.size(), is(3));

        // id:1(success)
        assertThat("メールリクエストIDが1", mailRequestList.get(0).mailRequestId, is("1"));
        assertThat("ステータスが「送信成功」になっているはず", mailRequestList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(0).sendDatetime, is(notNullValue()));

        // id:2(fail)
        assertThat("メールリクエストIDが2", mailRequestList.get(1).mailRequestId, is("2"));
        assertThat("ステータスが「送信失敗」になっているはず", mailRequestList.get(1).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(1).sendDatetime, is(nullValue()));

        // id:3(success)
        assertThat("メールリクエストIDが3", mailRequestList.get(2).mailRequestId, is("3"));
        assertThat("ステータスが「送信成功」になっているはず", mailRequestList.get(2).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(2).sendDatetime, is(notNullValue()));

        // バッチ実行
        OnMemoryLogWriter.clear();
        commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        rc = Main.execute(commandLine);
        assertThat("正常終了なので戻り値は0となる。", rc, is(0));

        OnMemoryLogWriter.assertLogContains("writer.memory", "-INFO- メール送信要求が 0 件あります。");

    }


    /**
     * 処理が異常終了する場合のテスト<br/>
     * <br/>
     * メール送信失敗→ステータス更新に失敗する場合は処理が異常終了すること。
     */
    @Test
    public void testSendFailAndStatusUpdateFail() throws Exception {

        new MockUp<MailRequestTable>() {
            @Mock
            void updateFailureStatus(final String mailRequestId, final String status) {
                throw new DbAccessException("db error!!!!", new SQLException());
            }
        };

        // データ準備
        String mailRequestId = "1";
        String subject = "正常系";
        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));
        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        mailRequestId = "2";
        subject = "異常系(returnPathに改行)";

        VariousDbTestHelper.insert(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath + ">\nRCPT TO: <to2@localhost>",
                        charset, mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));
        VariousDbTestHelper.insert(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        mailRequestId = "3";
        subject = "正常系";

        VariousDbTestHelper.insert(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.insert(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("送信失敗ステータスへの更新失敗なので異常終了する", rc, is(20));

        // ----- assert log -----
        OnMemoryLogWriter.assertLogContains("writer.memory",
                "-INFO- メールを送信しました。 mailRequestId=[1]",
                "-ERROR- メール送信に失敗しました。 mailRequestId=[2]",
                "failed to update unsent status. need to apply a patch to change the status to unsent. target data=[mailRequestId = 2]");

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestList.size(), is(3));

        // id:1(success)
        assertThat("メールリクエストIDが1", mailRequestList.get(0).mailRequestId, is("1"));
        assertThat("ステータスが「送信成功」になっているはず", mailRequestList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(0).sendDatetime, is(notNullValue()));

        // id:2(fail)
        assertThat("メールリクエストIDが2", mailRequestList.get(1).mailRequestId, is("2"));
        assertThat("送信失敗への変更に失敗するので送信済みステータスのまま", mailRequestList.get(1).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(1).sendDatetime, is(notNullValue()));

        // id:3(unsent)
        assertThat("メールリクエストIDが3", mailRequestList.get(2).mailRequestId, is("3"));
        assertThat("ステータスが「送信成功」になっているはず", mailRequestList.get(2).status, is(mailConfig.getStatusUnsent()));
        assertThat("送信日時は未更新(null)", mailRequestList.get(2).sendDatetime, is(nullValue()));

        // バッチ実行
        OnMemoryLogWriter.clear();
        commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        rc = Main.execute(commandLine);
        assertThat("正常終了なので戻り値は0となる。", rc, is(0));

        // 送信されなかった1件が対象となる
        OnMemoryLogWriter.assertLogContains("writer.memory", "-INFO- メール送信要求が 1 件あります。");

    }

    /**
     * メールアドレスの文字列(From)が不正で、送信に失敗するテスト
     */
    @Test
    public void testSendFailAddressExceptionFrom() {
        // データ準備
        String mailRequestId = "1";
        String subject = "From アドレスのインスタンス生成失敗";

        final String invalidAddress = from + "@" + from;// returnPathも同時に無効な値にすること。
        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, invalidAddress, replyTo, invalidAddress, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));
        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("送信に失敗しても処理は成功なので0", rc, is(0));

        OnMemoryLogWriter.assertLogContains("writer.memory", "Failed to instantiate mail address. Error message:[", "] Mail address:[" + invalidAddress +"]");

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時がnullのまま", mailRequestList.get(0).sendDatetime, nullValue());
    }

    /**
     * メールアドレスの文字列(ReplyTo)が不正でも、送信は成功するテスト
     * @throws Exception メールの検証に失敗したとき
     */
    @Test
    public void testSendSuccessAddressExceptionReplyTo() throws Exception {
        // データ準備
        String mailRequestId = "1";
        String subject = "ReplyTo アドレスのインスタンス生成失敗";
        final String invalidAddress = replyTo + "@" + replyTo;
        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, invalidAddress, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));
        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("ReplyToが不正でも、送信処理は成功なので0", rc, is(0));

        OnMemoryLogWriter.assertLogContains("writer.memory", "Failed to instantiate mail address. Error message:[", "] Mail address:[" + invalidAddress +"]");

        // メールの検証
        assertRecivingPlainMail("to1", from, from, subject, new String[]{to1}, new String[]{});

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが「送信成功」", mailRequestList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(0).sendDatetime, notNullValue());
    }

    /**
     * メールアドレスの文字列(TO)だけが不正でも、送信は成功するテスト
     * @throws Exception メールの検証に失敗したとき
     */
    @Test
    public void testSendSuccessAddressExceptionTO() throws Exception {
        // データ準備
        String mailRequestId = "1";
        String subject = "送信アドレスの不正 TO";
        final String invalidAddress = to1 + "@" + to1;
        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));
        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), invalidAddress),
                new MailRecipient(mailRequestId, 2L, mailConfig.getRecipientTypeCC(), cc1),
                new MailRecipient(mailRequestId, 3L, mailConfig.getRecipientTypeBCC(), bcc1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("一部のアドレスが不正でも送信処理は成功なので0", rc, is(0));

        OnMemoryLogWriter.assertLogContains("writer.memory", "Failed to instantiate mail address. Error message:[", "] Mail address:[" + invalidAddress +"]");

        // メールの検証
        assertRecivingPlainMail("cc1", from, replyTo, subject, new String[]{}, new String[]{cc1});// TOなし
        assertRecivingPlainMail("bcc1", from, replyTo, subject, new String[]{}, new String[]{cc1});// TOなし

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが「送信成功」", mailRequestList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録される", mailRequestList.get(0).sendDatetime, notNullValue());
    }

    /**
     * メールアドレスの文字列(CC)だけが不正でも、送信は成功するテスト
     * @throws Exception メールの検証に失敗したとき
     */
    @Test
    public void testSendSuccessAddressExceptionCC() throws Exception {
        // データ準備
        String mailRequestId = "1";
        String subject = "送信アドレスの不正 CC";
        final String invalidAddress = cc1 + "@" + cc1;
        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));
        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId, 2L, mailConfig.getRecipientTypeCC(), invalidAddress),
                new MailRecipient(mailRequestId, 3L, mailConfig.getRecipientTypeBCC(), bcc1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("一部のアドレスが不正でも送信処理は成功なので0", rc, is(0));

        OnMemoryLogWriter.assertLogContains("writer.memory", "Failed to instantiate mail address. Error message:[", "] Mail address:[" + invalidAddress +"]");

        // メールの検証
        assertRecivingPlainMail("to1", from, replyTo, subject, new String[]{to1}, new String[]{});// CCなし
        assertRecivingPlainMail("bcc1", from, replyTo, subject, new String[]{to1}, new String[]{});// CCなし

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが「送信成功」", mailRequestList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録される", mailRequestList.get(0).sendDatetime, notNullValue());
    }

    /**
     * メールアドレスの文字列(BCC)だけが不正でも、送信は成功するテスト
     * @throws Exception メールの検証に失敗したとき
     */
    @Test
    public void testSendSuccessAddressExceptionBCC() throws Exception {
        // データ準備
        String mailRequestId = "1";
        String subject = "送信アドレスの不正 BCC";
        final String invalidAddress = bcc1 + "@" + bcc1;
        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));
        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId, 2L, mailConfig.getRecipientTypeCC(), cc1),
                new MailRecipient(mailRequestId, 3L, mailConfig.getRecipientTypeBCC(), invalidAddress));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("一部のアドレスが不正でも送信処理は成功なので0", rc, is(0));

        OnMemoryLogWriter.assertLogContains("writer.memory", "Failed to instantiate mail address. Error message:[", "] Mail address:[" + invalidAddress +"]");

        // メールの検証
        assertRecivingPlainMail("to1", from, replyTo, subject, new String[]{to1}, new String[]{cc1});
        assertRecivingPlainMail("cc1", from, replyTo, subject, new String[]{to1}, new String[]{cc1});

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが「送信成功」", mailRequestList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録される", mailRequestList.get(0).sendDatetime, notNullValue());
    }

    /**
     * 送信先すべてのメールアドレスの文字列が不正で、送信に失敗するテスト
     */
    @Test
    public void testSendFailAddressExceptionSendAddressNoValid() {
        // データ準備
        String mailRequestId = "1";
        String subject = "送信アドレスの不正 全て";
        final String invalidAddress1 = to1 + "@" + to1;
        final String invalidAddress2 = cc1 + "@" + cc1;
        final String invalidAddress3 = bcc1 + "@" + bcc1;
        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));
        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), invalidAddress1),
                new MailRecipient(mailRequestId, 2L, mailConfig.getRecipientTypeCC(), invalidAddress2),
                new MailRecipient(mailRequestId, 3L, mailConfig.getRecipientTypeBCC(), invalidAddress3));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("すべてアドレスが不正のため、送信は失敗しても処理は成功なので0", rc, is(0));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "Failed to instantiate mail address. Error message:[",
                "] Mail address:[" + invalidAddress1 +"]",
                "] Mail address:[" + invalidAddress2 +"]",
                "] Mail address:[" + invalidAddress3 +"]");

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時がnullのまま", mailRequestList.get(0).sendDatetime, nullValue());
    }

    private void assertRecivingPlainMail(final String account, final String fromAddress, final String replyToAddress, final String mailSubject, final String tos[],
            final String ccs[]) throws Exception {
        // accountでメールを受信
        Session session = Session.getInstance(sessionProperties, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(account, "default");
            }
        });
        Store store = session.getStore("pop3");
        store.connect();
        Folder folder = openFolder(store);
        Message[] messages = folder.getMessages();
        //メールの削除と取得のタイムラグの間に他のテストケースで送信したメールが複数届く場合があるため、
        //Subjectが一致するものを処理対象とする。
        Message message = null;
        for (Message mail: messages ) {
            if( mailSubject.equals(mail.getSubject()) ) {
                message = mail;
                break;
            }
        }
        assertThat("件名[" + mailSubject + "]が一致するメールが届いていない", message, notNullValue());

        // メールの検証
        // FROM
        Address[] messageFrom = message.getFrom();
        assertThat("fromアドレスの数", messageFrom.length, is(1));
        assertThat("fromアドレス", ((InternetAddress) messageFrom[0]).getAddress(), is(fromAddress));
        // TO
        Address[] messageTO = message.getRecipients(RecipientType.TO);
        if (messageTO == null) {
            assertThat( "TOがnullなのでtos[]は空のはず", tos.length, is(0));
        } else {
            assertThat("TOアドレスの数", messageTO.length, is(tos.length));
        }
        for (int i=0; i<tos.length; i++) {
            final String to = tos[i];
            assertThat("TOアドレス" + (i+1), ((InternetAddress) messageTO[i]).getAddress(), is(to));
        }
        // CC
        Address[] messageCC = message.getRecipients(RecipientType.CC);
        if (messageCC == null) {
            assertThat( "CCがnullなのでccs[]は空のはず", ccs.length, is(0));
        } else {
            assertThat("CCアドレスの数", messageCC.length, is(ccs.length));
        }
        for (int i=0; i<ccs.length; i++) {
            final String cc = ccs[i];
            assertThat("CCアドレス" + (i+1), ((InternetAddress) messageCC[i]).getAddress(), is(cc));
        }
        // BCCは検証できない

        // ReplyTo
        Address[] messageReplyTo = message.getReplyTo();
        assertThat("ReplyToの数", messageReplyTo.length, is(1));
        assertThat("ReplyToアドレス", ((InternetAddress) messageReplyTo[0]).getAddress(), is(replyToAddress));
        // Return-Path
        String[] messageReturnPath = message.getHeader("Return-Path");
        assertThat("RetrunPathの数", messageReturnPath.length, is(1));
        assertThat("RetrunPathアドレス", messageReturnPath[0], is("<" + returnPath + ">"));
        // Subject
        String messageSubject = message.getSubject();
        assertThat("件名", messageSubject, is(mailSubject));
        // Content-Type
        assertThat("Content-Type", message.getContentType(), containsString("text/plain"));
        // Body
        assertThat("添付ファイルなしなので", message.getContent(), is(instanceOf(String.class)));
        assertThat("本文", (String) message.getContent(), is(mailBody));
    }

    /**
     * メール送信の実行時に{@link SendFailedException}が発生した場合の詳細ログがERRORに出力されていることのテスト
     */
    @Test
    public void testSendFailBySendFailedException() {

        new MockUp<Transport>() {

            @Mock
            public void send(Message message) throws MessagingException {
                // 複数要素の場合は、間に", "が入ること
                final Address[] validSent = {new InternetAddress(to1), new InternetAddress(to2), new InternetAddress(cc1)};
                // 1要素の場合は、そのまま
                final Address[] validUnsent = {new InternetAddress(to3)};
                // 空の配列
                final Address[] invalid = {};
                throw new SendFailedException("Test SendFailedException message.", null,
                        validSent, validUnsent, invalid);
            }
        };

        // データ準備
        String mailRequestId = "1";
        String subject = "異常系 Transport.Sendで失敗";
        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));
        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId, 2L, mailConfig.getRecipientTypeTO(), to2),
                new MailRecipient(mailRequestId, 3L, mailConfig.getRecipientTypeTO(), to3),
                new MailRecipient(mailRequestId, 4L, mailConfig.getRecipientTypeTO(), cc1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("送信に失敗しても処理は成功なので0", rc, is(0));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "Failed to send a mail. Error message:[Test SendFailedException message.] "
                        + "Mail RequestId:[1] "
                        + "Subject:[異常系 Transport.Sendで失敗] "
                        + "From:[from@localhost] "
                        + "Sent address:[to1@localhost, to2@localhost, cc1@localhost] "
                        + "Unsent address:[to3@localhost] "
                        + "Invalid address:[]");

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時がnullのまま", mailRequestList.get(0).sendDatetime, nullValue());
    }

    /**
     * メール送信の実行時に{@link SendFailedException}が発生した場合の詳細ログがERRORに出力されていることのテスト
     * (例外から取得されるアドレスがnullの場合)
     */
    @Test
    public void testSendFailedBySendFailedExceptionAddressesNull() {

        new MockUp<Transport>() {

            @Mock
            public void send(Message message) throws MessagingException {
                //アドレスはnullにする。
                final Address[] validSent = null;
                final Address[] validUnsent = null;
                final Address[] invalid = null;
                throw new SendFailedException("Test SendFailedException message.", null,
                        validSent, validUnsent, invalid);
            }
        };

        // データ準備
        String mailRequestId = "1";
        String subject = "異常系 Transport.Sendで失敗";
        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));
        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId, 2L, mailConfig.getRecipientTypeTO(), to2),
                new MailRecipient(mailRequestId, 3L, mailConfig.getRecipientTypeTO(), to3),
                new MailRecipient(mailRequestId, 4L, mailConfig.getRecipientTypeCC(), cc1),
                new MailRecipient(mailRequestId, 5L, mailConfig.getRecipientTypeCC(), cc2),
                new MailRecipient(mailRequestId, 6L, mailConfig.getRecipientTypeCC(), cc3));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("送信に失敗しても処理は成功なので0", rc, is(0));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "Failed to send a mail. Error message:[Test SendFailedException message.] "
                        + "Mail RequestId:[1] "
                        + "Subject:[異常系 Transport.Sendで失敗] "
                        + "From:[from@localhost] "
                        + "Sent address:[] "
                        + "Unsent address:[] "
                        + "Invalid address:[]");

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時がnullのまま", mailRequestList.get(0).sendDatetime, nullValue());
    }

    /**
     * メール送信の実行時に{@link SendFailedException}が発生し、リトライ例外を送出しなかった場合で、
     * {@link nablarch.fw.launcher.ProcessAbnormalEnd}を返す前に「未送信」ステータスへの変更に失敗するテスト
     */
    @Test
    public void testRetryableExceptionAndStatusUpdateFail() {

        new MockUp<MailRequestTable>() {
            @Mock
            void updateFailureStatus(final String mailRequestId, final String status) {
                throw new DbAccessException("db error!!!!", new SQLException());
            }
        };

        // データ準備
        String mailRequestId = "1";
        String subject = "異常系 リトライ例外送出後にDB更新失敗";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, "aaaa", mailConfig.getStatusUnsent(),
                        SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("送信失敗ステータスへの更新失敗なので異常終了する", rc, is(20));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "failed to update unsent status. need to apply a patch to change the status to unsent. target data=[mailRequestId = 1]");

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスは更新に失敗するので「送信済み」のまま", mailRequestList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, notNullValue());
    }

    /**
     * メール送信の実行時に{@link SendFailedException}が発生し、リトライ例外を送出しなかった場合に
     * {@link nablarch.fw.launcher.ProcessAbnormalEnd}となるテスト
     */
    @Test
    public void testNoRetryableExceptionAndProcessAbnormalEnd() {

        new MockUp<Transport>() {

            @Mock
            public void send(Message message) throws MessagingException {
                throw new MessagingException("Test MessagingException message.");
            }
        };

        // データ準備
        String mailRequestId = "1";
        String subject = "異常系 Transport.Sendで失敗（リトライしない）";
        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));
        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId, 2L, mailConfig.getRecipientTypeTO(), to2),
                new MailRecipient(mailRequestId, 3L, mailConfig.getRecipientTypeTO(), to3),
                new MailRecipient(mailRequestId, 4L, mailConfig.getRecipientTypeTO(), cc1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.NoRetryMailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("リトライ例外を送出しないため、送信に失敗しても処理は成功なので0", rc, is(0));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "javax.mail.MessagingException: Test MessagingException message.",
                "-ERROR- メール送信に失敗しました。 mailRequestId=[1]",
                "-ERROR- fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[1]"  // 障害検知ログ
        );

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時がnullのまま", mailRequestList.get(0).sendDatetime, nullValue());
    }

    /**
     * メール送信の実行時に{@link SendFailedException}が発生し、リトライ例外を送出しなかった場合で、
     * {@link nablarch.fw.launcher.ProcessAbnormalEnd}を返す前に「未送信」ステータスへの変更に失敗するテスト
     */
    @Test
    public void testNoRetryableExceptionAndStatusUpdateFail() {

        new MockUp<Transport>() {

            @Mock
            public void send(Message message) throws MessagingException {
                throw new MessagingException("Test MessagingException message.");
            }
        };

        new MockUp<MailRequestTable>() {
            @Mock
            void updateFailureStatus(final String mailRequestId, final String status) {
                throw new DbAccessException("db error!!!!", new SQLException());
            }
        };

        // データ準備
        String mailRequestId = "1";
        String subject = "異常系 Transport.Sendで失敗後にDB更新失敗";
        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));
        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.NoRetryMailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("送信失敗ステータスへの更新失敗なので異常終了する", rc, is(20));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "failed to update unsent status. need to apply a patch to change the status to unsent. target data=[mailRequestId = 1]"
        );

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスは更新に失敗するので「送信済み」のまま", mailRequestList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, notNullValue());
    }

}
