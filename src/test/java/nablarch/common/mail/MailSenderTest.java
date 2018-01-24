package nablarch.common.mail;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
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

import nablarch.core.date.BasicSystemTimeProvider;
import nablarch.core.date.SystemTimeUtil;
import nablarch.core.db.DbAccessException;
import nablarch.core.util.FileUtil;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.launcher.CommandLine;
import nablarch.fw.launcher.Main;
import nablarch.fw.launcher.ProcessAbnormalEnd;
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
 *
 * メールサーバーソフトのJamesのセットアップと、アカウントの追加については、README.mdファイルを参照すること。
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
        OnMemoryLogWriter.clear();
    }

    public static class TestSystemTimeProvider extends BasicSystemTimeProvider {

        @Override
        public Date getDate() {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                return sdf.parse("2017/01/23 12:34:56");
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 処理が正常終了する場合のテスト<br/>
     * <br/>
     * TO:0件,CC:複数,BCC1件,添付ファイルなし
     * <br/>
     *
     * @throws Exception
     */
    @Test
    @TargetDb(exclude = TargetDb.Db.SQL_SERVER)
    public void testExecuteNormalEnd1() throws Exception {

        // データ準備
        String mailRequestId = "1";
        String subject = "正常系1\uD83C\uDF63";
        String body = mailBody + "\uD83C\uDF63";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, body));

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
        //メールの削除と取得のタイムラグの間に他のテストケースで送信したメールが複数届く場合があるため、
        //Subjectが一致するものを待つ。
        Folder folder = openFolder(store, subject);
        Message[] messages = folder.getMessages();
        Message message = null;
        for (Message mail: messages ) {
            if( subject.equals(mail.getSubject()) ) {
                message = mail;
                break;
            }
        }
        assertThat("件名[" + subject + "]が一致するメールが届いていない", message, notNullValue());

        // メールの検証
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

        assertThat("送信日時", message.getSentDate(), is(new TestSystemTimeProvider().getDate()));

        assertThat("Content-Type", message.getContentType(), containsString("text/plain"));
        assertThat("charset", message.getContentType(), containsString(charset));

        assertThat("添付ファイルなしなので", message.getContent(), is(instanceOf(String.class)));
        assertThat("本文", (String) message.getContent(), is(body));

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
        //メールの削除と取得のタイムラグの間に他のテストケースで送信したメールが複数届く場合があるため、
        //Subjectが一致するものを待つ。
        Folder folder1 = openFolder(store1, subject);
        Message[] messages = folder1.getMessages();
        Message message = null;
        for (Message mail: messages ) {
            if( subject.equals(mail.getSubject()) ) {
                message = mail;
                break;
            }
        }
        assertThat("件名[" + subject + "]が一致するメールが届いていない", message, notNullValue());

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

        assertThat("送信日時", message1.getSentDate(), is(new TestSystemTimeProvider().getDate()));

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
        Folder folder2 = openFolder(store2, subject);
        Message[] messages2 = folder2.getMessages();
        Message message2 = null;
        for (Message mail: messages2 ) {
            if( subject.equals(mail.getSubject()) ) {
                message2 = mail;
                break;
            }
        }
        assertThat("件名[" + subject + "]が一致するメールが届いていない", message2, notNullValue());

        Session session3 = Session.getInstance(sessionProperties, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("bcc3", "default");
            }
        });
        Store store3 = session3.getStore("pop3");
        store3.connect();
        Folder folder3 = openFolder(store3, subject);
        Message[] messages3 = folder3.getMessages();
        Message message3 = null;
        for (Message mail: messages3 ) {
            if( subject.equals(mail.getSubject()) ) {
                message3 = mail;
                break;
            }
        }
        assertThat("件名[" + subject + "]が一致するメールが届いていない", message3, notNullValue());

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
        Folder folder1 = openFolder(store1, subject);
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

        assertThat("送信日時", message1.getSentDate(), is(new TestSystemTimeProvider().getDate()));

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
        String mailRequestId1 = "4-1";
        String subject1 = "異常系1-1(接続エラー)";
        String mailRequestId2 = "4-2";
        String subject2 = "異常系1-2(接続エラー)";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId1, subject1, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody),
                new MailRequest(mailRequestId2, subject2, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId1, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId2, 2L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTestOverrideSmtpPort.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("接続エラー(MessagingException)はリトライで上限に達して異常終了して戻り値は180となる。", execute, is(180));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "nablarch.common.mail.SendMailRetryableException: Failed to send a mail, will be retried to send later. mailRequestId=[4-1], error message=[",
                "nablarch.common.mail.SendMailRetryableException: Failed to send a mail, will be retried to send later. mailRequestId=[4-2], error message=[",
                "req_id = [SENDMAIL00] usr_id = [hoge] application was abnormal end.");
        //障害通知ログに[4-1]と[4-2]が１回ずつ出力されていること
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[4-1]"
                ),1);
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[4-2]"
                ),1);

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestList.size(), is(2));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, is(nullValue()));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(1).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(1).sendDatetime, is(nullValue()));
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
        String mailRequestId1 = "5-1";
        String subject1 = "異常系2-1（接続タイムアウト）";
        String mailRequestId2 = "5-2";
        String subject2 = "異常系2-2（接続タイムアウト）";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId1, subject1, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody),
                new MailRequest(mailRequestId2, subject2, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId1, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId2, 2L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTestOverrideSmtpConnetcionTimeout.xml",
                "-requestPath", "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("タイムアウト(MessagingException)はリトライで上限に達して異常終了し戻り値は180となる。", execute, is(180));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "nablarch.common.mail.SendMailRetryableException: Failed to send a mail, will be retried to send later. mailRequestId=[5-1], error message=[",
                "nablarch.common.mail.SendMailRetryableException: Failed to send a mail, will be retried to send later. mailRequestId=[5-2], error message=[",
                "req_id = [SENDMAIL00] usr_id = [hoge] application was abnormal end.");
        //障害通知ログに[5-1]と[5-2]が１回ずつ出力されていること
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[5-1]"
                ), 1);
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[5-2]"
                ), 1);

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class,"mailRequestId");
        assertThat("レコード取得数", mailRequestList.size(), is(2));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, is(nullValue()));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(1).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(1).sendDatetime, is(nullValue()));
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
        String mailRequestId1 = "6-1";
        String subject1 = "異常系3-1(送信エラー）";
        String mailRequestId2 = "6-2";
        String subject2 = "異常系3-2(送信エラー）";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId1, subject1, from, replyTo, returnPath, "aaaa", mailConfig.getStatusUnsent(),
                        SystemTimeUtil.getTimestamp(), null, mailBody),
                new MailRequest(mailRequestId2, subject2, from, replyTo, returnPath, "aaaa", mailConfig.getStatusUnsent(),
                        SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId1, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId2, 2L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTestRetry.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("タイムアウト(MessagingException)はリトライで上限に達して異常終了し戻り値は180となる。", execute, is(180));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "nablarch.common.mail.SendMailRetryableException: Failed to send a mail, will be retried to send later. mailRequestId=[6-1], error message=[",
                "nablarch.common.mail.SendMailRetryableException: Failed to send a mail, will be retried to send later. mailRequestId=[6-2], error message=[",
                "req_id = [SENDMAIL00] usr_id = [hoge] application was abnormal end.");
        //障害通知ログに[6-1]と[6-2]が１回ずつ出力されていること
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[6-1]"
                ), 1);
        assertLogWithCount("writer.failure-memory", createMessagePattern(
                "-ERROR- MONITOR [",
                "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[6-2]"
        ), 1);

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestList.size(), is(2));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, is(nullValue()));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(1).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(1).sendDatetime, is(nullValue()));
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
        String mailRequestId1 = "7-1";
        String subject1 = "異常系4-1(送信タイムアウト）";
        String mailRequestId2 = "7-2";
        String subject2 = "異常系4-2(送信タイムアウト）";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId1, subject1, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody),
                new MailRequest(mailRequestId2, subject2, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId1, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId2, 2L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTestOverrideSmtpTimeout.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("タイムアウト(MessagingException)はリトライで上限に達して異常終了し戻り値は180となる。", execute, is(180));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "nablarch.common.mail.SendMailRetryableException: Failed to send a mail, will be retried to send later. mailRequestId=[7-1], error message=[",
                "nablarch.common.mail.SendMailRetryableException: Failed to send a mail, will be retried to send later. mailRequestId=[7-2], error message=[",
                "req_id = [SENDMAIL00] usr_id = [hoge] application was abnormal end.");
        //障害通知ログに[7-1]と[7-2]が１回ずつ出力されていること
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[7-1]"
                ), 1);
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[7-2]"
                ), 1);

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class,"mailRequestId");
        assertThat("レコード取得数", mailRequestList.size(), is(2));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, is(nullValue()));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(1).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(1).sendDatetime, is(nullValue()));
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 一部メールの送信に失敗する場合のテスト<br/>
     * <br/>
     * メールアドレス(To)でExceptionが発生するパターン。
     *
     * @throws Exception
     */
    @Test
    public void testSendFailAddressExceptionTo() throws Exception {

        // データ準備
        final String invalidAddress = to1 + "\rhoge";
        String mailRequestId1 = "1";
        String subject1 = "TOアドレスが不正";
        String mailRequestId2 = "2";
        String subject2 = "正常系TO 前のメールが送信失敗でも中断せず送信される";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId1, subject1, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody),
                new MailRequest(mailRequestId2, subject2, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId1, 1L, mailConfig.getRecipientTypeTO(), invalidAddress),
                new MailRecipient(mailRequestId1, 2L, mailConfig.getRecipientTypeCC(), cc1),
                new MailRecipient(mailRequestId1, 3L, mailConfig.getRecipientTypeBCC(), bcc1),
                new MailRecipient(mailRequestId2, 4L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId2, 5L, mailConfig.getRecipientTypeCC(), cc1),
                new MailRecipient(mailRequestId2, 6L, mailConfig.getRecipientTypeBCC(), bcc1));

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
        assertThat("送信失敗は異常終了ではなく戻り値は0となる。", execute, is(0));

        // ----- assert log -----
        //LogVerifier.verify("assert log");

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "Invalid mail addresses found. mailRequestId=[1] mail addresses=[" + invalidAddress + "]",
                "Failed to instantiate mail address. mailRequestId=[1] mail address=[" + invalidAddress + "]",
                "メール送信に失敗しました。 mailRequestId=[1]",
                "メールを送信しました。 mailRequestId=[2]");
        //障害通知ログに[1]と、Invalid mail addresses のstackTraceが１回出力されていること
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[1]"
                ), 1);
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "nablarch.common.mail.CreateMailFailedException: Invalid mail addresses found. mailRequestId=[1] mail addresses=["
                                + invalidAddress + "]"
                ), 1);

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestList.size(), is(2));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, is(nullValue()));
        assertThat("ステータスが「送信成功」", mailRequestList.get(1).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(1).sendDatetime, notNullValue());

        // メールの検証
        assertRecivingPlainMail("to1", from, replyTo, subject2, new String[]{to1}, new String[]{cc1});
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 一部メールの送信に失敗する場合のテスト<br/>
     * <br/>
     * メールアドレス（CC）でExceptionが発生するパターン。
     *
     * @throws Exception
     */
    @Test
    public void testSendFailAddressExceptionCC() throws Exception {

        // データ準備
        final String invalidAddress = cc1 + "\rhoge";
        String mailRequestId1 = "1";
        String subject1 = "CCアドレスが不正";
        String mailRequestId2 = "2";
        String subject2 = "正常系CC 前のメールが送信失敗でも中断せず送信される";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId1, subject1, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody),
                new MailRequest(mailRequestId2, subject2, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId1, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId1, 2L, mailConfig.getRecipientTypeCC(), invalidAddress),
                new MailRecipient(mailRequestId1, 3L, mailConfig.getRecipientTypeBCC(), bcc1),
                new MailRecipient(mailRequestId2, 4L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId2, 5L, mailConfig.getRecipientTypeCC(), cc1),
                new MailRecipient(mailRequestId2, 6L, mailConfig.getRecipientTypeBCC(), bcc1));

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
        assertThat("送信失敗は異常終了ではなく戻り値は0となる。", execute, is(0));

        // ----- assert log -----
        //LogVerifier.verify("assert log");

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "Invalid mail addresses found. mailRequestId=[1] mail addresses=[" + invalidAddress + "]",
                "Failed to instantiate mail address. mailRequestId=[1] mail address=[" + invalidAddress + "]",
                "メール送信に失敗しました。 mailRequestId=[1]",
                "メールを送信しました。 mailRequestId=[2]");
        //障害通知ログに[1]と、Invalid mail addresses のstackTraceが１回出力されていること
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[1]"
                ), 1);
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "nablarch.common.mail.CreateMailFailedException: Invalid mail addresses found. mailRequestId=[1] mail addresses=["
                                + invalidAddress + "]"
                ), 1);

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestList.size(), is(2));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, is(nullValue()));
        assertThat("ステータスが「送信成功」", mailRequestList.get(1).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(1).sendDatetime, notNullValue());

        // メールの検証
        assertRecivingPlainMail("to1", from, replyTo, subject2, new String[]{to1}, new String[]{cc1});
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 一部メールの送信に失敗する場合のテスト<br/>
     * <br/>
     * メールアドレス（BCC）でExceptionが発生するパターン。
     *
     * @throws Exception
     */
    @Test
    public void testSendFailAddressExceptionBCC() throws Exception {

        // データ準備
        final String invalidAddress = "hoge\n" + bcc1;
        String mailRequestId1 = "1";
        String subject1 = "BCCアドレスが不正";
        String mailRequestId2 = "2";
        String subject2 = "正常系BCC 前のメールが送信失敗でも中断せず送信される";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId1, subject1, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody),
                new MailRequest(mailRequestId2, subject2, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId1, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId1, 2L, mailConfig.getRecipientTypeCC(), cc1),
                new MailRecipient(mailRequestId1, 3L, mailConfig.getRecipientTypeBCC(), invalidAddress),
                new MailRecipient(mailRequestId2, 4L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId2, 5L, mailConfig.getRecipientTypeCC(), cc1),
                new MailRecipient(mailRequestId2, 6L, mailConfig.getRecipientTypeBCC(), bcc1));

        //LogVerifier.setExpectedLogMessages(new ArrayList<Map<String,String>>() {
        //    {
        //        add(new HashMap<String, String>() {
        //            {
        //                put("logLevel", "FATAL");
        //                put("message1", "Local address contains control or whitespace");
        //            }
        //        });
        //    }
        //});

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("送信失敗は異常終了ではなく戻り値は0となる。", execute, is(0));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "Invalid mail addresses found. mailRequestId=[1] mail addresses=[" + invalidAddress + "]",
                "Failed to instantiate mail address. mailRequestId=[1] mail address=[" + invalidAddress + "]",
                "メール送信に失敗しました。 mailRequestId=[1]",
                "メールを送信しました。 mailRequestId=[2]");
        //障害通知ログに[1]と、Invalid mail addresses のstackTraceが１回出力されていること
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[1]"
                ), 1);
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "nablarch.common.mail.CreateMailFailedException: Invalid mail addresses found. mailRequestId=[1] mail addresses=["
                                + invalidAddress + "]"
                ), 1);

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestList.size(), is(2));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, is(nullValue()));
        assertThat("ステータスが「送信成功」", mailRequestList.get(1).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(1).sendDatetime, notNullValue());

        // メールの検証
        assertRecivingPlainMail("to1", from, replyTo, subject2, new String[]{to1}, new String[]{cc1});
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 一部メールの送信に失敗する場合のテスト<br/>
     * <br/>
     * 送信者メールアドレスでExceptionが発生するパターン。
     *
     * @throws Exception
     */
    @Test
    public void testSendFailAddressExceptionFrom() throws Exception {
        // データ準備
        final String invalidAddress = from + "\rhoge";
        String mailRequestId1 = "1";
        String subject1 = "送信者メールアドレスが不正";
        String mailRequestId2 = "2";
        String subject2 = "正常系FROM 前のメールが送信失敗でも中断せず送信される";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId1, subject1, invalidAddress, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody),
                new MailRequest(mailRequestId2, subject2, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));
        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId1, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId2, 2L, mailConfig.getRecipientTypeTO(), to1)
        );

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
        assertThat("送信失敗は異常終了ではなく戻り値は0となる。", execute, is(0));

        // ----- assert log -----
        //LogVerifier.verify("assert log");

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "Invalid mail addresses found. mailRequestId=[1] mail addresses=[" + invalidAddress + "]",
                "Failed to instantiate mail address. mailRequestId=[1] mail address=[" + invalidAddress + "]",
                "メール送信に失敗しました。 mailRequestId=[1]",
                "メールを送信しました。 mailRequestId=[2]");
        //障害通知ログに[1]と、Invalid mail addresses のstackTraceが１回出力されていること
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[1]"
                ), 1);
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "nablarch.common.mail.CreateMailFailedException: Invalid mail addresses found. mailRequestId=[1] mail addresses=["
                                + invalidAddress + "]"
                ), 1);

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestList.size(), is(2));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されない", mailRequestList.get(0).sendDatetime, nullValue());
        assertThat("ステータスが「送信成功」", mailRequestList.get(1).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(1).sendDatetime, notNullValue());

        // メールの検証
        assertRecivingPlainMail("to1", from, replyTo, subject2, new String[]{to1}, new String[]{});
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 一部メールの送信に失敗する場合のテスト<br/>
     * <br/>
     * 返信先メールアドレスでExceptionが発生するパターン。
     *
     * @throws Exception
     */
    @Test
    public void testSendFailAddressExceptionReplyTo() throws Exception {
        // データ準備
        final String invalidAddress = "hoge\n" + replyTo;
        String mailRequestId1 = "1";
        String subject1 = "返信先メールアドレスが不正";
        String mailRequestId2 = "2";
        String subject2 = "正常系ReplyTo 前のメールが送信失敗でも中断せず送信される";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId1, subject1, from, invalidAddress, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody),
                new MailRequest(mailRequestId2, subject2, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));
        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId1, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId2, 2L, mailConfig.getRecipientTypeTO(), to1)
        );

        //LogVerifier.setExpectedLogMessages(new ArrayList<Map<String,String>>() {
        //    {
        //        add(new HashMap<String, String>() {
        //            {
        //                put("logLevel", "FATAL");
        //                put("message1", "Local address contains control or whitespace");
        //            }
        //        });
        //    }
        //});

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("送信失敗は異常終了ではなく戻り値は0となる。", execute, is(0));

        // ----- assert log -----
        //LogVerifier.verify("assert log");

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "Invalid mail addresses found. mailRequestId=[1] mail addresses=[" + invalidAddress + "]",
                "Failed to instantiate mail address. mailRequestId=[1] mail address=[" + invalidAddress + "]",
                "メール送信に失敗しました。 mailRequestId=[1]",
                "メールを送信しました。 mailRequestId=[2]");
        //障害通知ログに[1]と、Invalid mail addresses のstackTraceが１回出力されていること
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[1]"
                ), 1);
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "nablarch.common.mail.CreateMailFailedException: Invalid mail addresses found. mailRequestId=[1] mail addresses=["
                                + invalidAddress + "]"
                ), 1);

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestList.size(), is(2));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されない", mailRequestList.get(0).sendDatetime, nullValue());
        assertThat("ステータスが「送信成功」", mailRequestList.get(1).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(1).sendDatetime, notNullValue());

        // メールの検証
        assertRecivingPlainMail("to1", from, replyTo, subject2, new String[]{to1}, new String[]{});
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 複数のアドレスが不正でメールの送信に失敗する場合のテスト<br/>
     *
     * @throws Exception
     */
    @Test
    public void testSendFailMultipleAddressExceptions() throws Exception {
        // データ準備
        final String invalidFrom = from + "@";
        final String invalidReplyTo = replyTo + "@";
        final String invalidTo1 = to1 + "@";
        final String invalidCc1 = cc1 + "@";
        final String invalidBcc1 = bcc1 + "@";

        String mailRequestId1 = "1";
        String subject1 = "メールアドレスが複数不正";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId1, subject1, invalidFrom, invalidReplyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody)
        );
        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId1, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId1, 2L, mailConfig.getRecipientTypeTO(), invalidTo1),
                new MailRecipient(mailRequestId1, 3L, mailConfig.getRecipientTypeCC(), cc1),
                new MailRecipient(mailRequestId1, 4L, mailConfig.getRecipientTypeCC(), invalidCc1),
                new MailRecipient(mailRequestId1, 5L, mailConfig.getRecipientTypeBCC(), bcc1),
                new MailRecipient(mailRequestId1, 6L, mailConfig.getRecipientTypeBCC(), invalidBcc1)
        );

        //LogVerifier.setExpectedLogMessages(new ArrayList<Map<String,String>>() {
        //    {
        //        add(new HashMap<String, String>() {
        //            {
        //                put("logLevel", "FATAL");
        //                put("message1", "Local address contains control or whitespace");
        //            }
        //        });
        //    }
        //});

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("送信失敗は異常終了ではなく戻り値は0となる。", execute, is(0));

        // ----- assert log -----
        //LogVerifier.verify("assert log");

        String invalidAddresses = invalidTo1 + ", " + invalidCc1 + ", " + invalidBcc1 + ", " + invalidFrom + ", " + invalidReplyTo;
        OnMemoryLogWriter.assertLogContains("writer.memory",
                "Invalid mail addresses found. mailRequestId=[1] mail addresses=[" + invalidAddresses + "]",
                "メール送信に失敗しました。 mailRequestId=[1]");
        //不正なアドレスがすべて警告として出力されていること
        assertLogWithCount("writer.memory",
                createMessagePattern(
                        "-WARN- ROO [",
                        "] req_id = [SENDMAIL00] usr_id = [hoge] Failed to instantiate mail address. mailRequestId=[1] mail address=[" + invalidTo1
                ), 1);
        assertLogWithCount("writer.memory",
                createMessagePattern(
                        "-WARN- ROO [",
                        "] req_id = [SENDMAIL00] usr_id = [hoge] Failed to instantiate mail address. mailRequestId=[1] mail address=[" + invalidCc1
                ), 1);
        assertLogWithCount("writer.memory",
                createMessagePattern(
                        "-WARN- ROO [",
                        "] req_id = [SENDMAIL00] usr_id = [hoge] Failed to instantiate mail address. mailRequestId=[1] mail address=[" + invalidBcc1
                ), 1);
        assertLogWithCount("writer.memory",
                createMessagePattern(
                        "-WARN- ROO [",
                        "] req_id = [SENDMAIL00] usr_id = [hoge] Failed to instantiate mail address. mailRequestId=[1] mail address=[" + invalidFrom
                ), 1);
        assertLogWithCount("writer.memory",
                createMessagePattern(
                        "-WARN- ROO [",
                        "] req_id = [SENDMAIL00] usr_id = [hoge] Failed to instantiate mail address. mailRequestId=[1] mail address=[" + invalidReplyTo
                ), 1);

        //障害通知ログに[1]と、Invalid mail addresses のstackTraceが１回出力されていること
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[1]"
                ), 1);
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "nablarch.common.mail.CreateMailFailedException: Invalid mail addresses found. mailRequestId=[1] mail addresses=["
                                + invalidAddresses + "]"
                ), 1);

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されない", mailRequestList.get(0).sendDatetime, nullValue());
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
        String mailRequestId1 = "1";
        String subject1 = "文字セットが不正1";
        String mailRequestId2 = "2";
        String subject2 = "文字セットが不正2";

        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId1, subject1, from, replyTo, returnPath, charset + "\nhoge",
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody),
                new MailRequest(mailRequestId2, subject2, from, replyTo, returnPath, charset + "\nhoge",
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId1, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId2, 1L, mailConfig.getRecipientTypeTO(), to1));

        //LogVerifier.setExpectedLogMessages(new ArrayList<Map<String,String>>() {
        //    {
        //        add(new HashMap<String, String>() {
        //            {
        //                put("logLevel", "FATAL");
        //                put("message1", "Encoding error");
        //            }
        //        });
        //    }
        //});

        // バッチ実行 (リトライするハンドラ構成でのテスト)
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTestRetry.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("送信失敗でリトライ上限に達して戻り値は180になる。", execute, is(180));
        //障害通知ログに[1]と[2]が１回ずつ出力されていること
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[1]"
                ), 1);
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[2]"
                ), 1);

        // ----- assert log -----
        //LogVerifier.verify("assert log");

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "nablarch.common.mail.SendMailRetryableException: Failed to send a mail, will be retried to send later. mailRequestId=[1], error message=[");

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestList.size(), is(2));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, is(nullValue()));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(1).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(1).sendDatetime, is(nullValue()));
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 一部メールの送信に失敗する場合のテスト<br/>
     * <br/>
     * （subjectに改行を設定して実行する）。
     *
     * @throws Exception
     */
    @Test
    public void testSendFailHeaderInjectionSubject() throws Exception {

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

        //LogVerifier.setExpectedLogMessages(new ArrayList<Map<String,String>>() {
        //    {
        //        add(new HashMap<String, String>() {
        //            {
        //                put("logLevel", "FATAL");
        //                put("message1", "contains invalid character");
        //            }
        //        });
        //    }
        //});

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("2つ目が送信失敗になるが中断せず戻り値は0となる。", execute, is(0));

        // ----- assert log -----
        //LogVerifier.verify("assert log");

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "メールを送信しました。 mailRequestId=[1]",
                "メール送信に失敗しました。 mailRequestId=[2]",
                "メールを送信しました。 mailRequestId=[3]");
        //障害通知ログに[2]が１回出力されていること
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[2]"
                ), 1);

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestList.size(), is(3));
        assertThat("メールリクエストIDが1", mailRequestList.get(0).mailRequestId, is("1"));
        assertThat("ステータスが「送信成功」になっているはず", mailRequestList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(0).sendDatetime, is(notNullValue()));
        assertThat("メールリクエストIDが2", mailRequestList.get(1).mailRequestId, is("2"));
        assertThat("ステータスが「送信失敗」になっているはず", mailRequestList.get(1).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(1).sendDatetime, is(nullValue()));
        assertThat("メールリクエストIDが3", mailRequestList.get(2).mailRequestId, is("3"));
        assertThat("ステータスが「送信成功」のままのはず", mailRequestList.get(2).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(2).sendDatetime, is(notNullValue()));
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 一部メールの送信に失敗する場合のテスト<br/>
     * <br/>
     * （ReturnPathに改行を設定して実行する）。
     *
     * @throws Exception
     */
    @Test
    @TargetDb(exclude = TargetDb.Db.DB2) // DB2では本ケース実行後にDB2が停止してしまうため暫定対処。
    public void testSendFailHeaderInjectionReturnPath() throws Exception {

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

        //LogVerifier.setExpectedLogMessages(new ArrayList<Map<String,String>>() {
        //    {
        //        add(new HashMap<String, String>() {
        //            {
        //                put("logLevel", "FATAL");
        //                put("message1", "contains invalid character");
        //            }
        //        });
        //    }
        //});

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("2つ目が送信失敗となるが中断せず戻り値は0となる。", execute, is(0));

        // ----- assert log -----
        //LogVerifier.verify("assert log");

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "メールを送信しました。 mailRequestId=[1]",
                "メール送信に失敗しました。 mailRequestId=[2]",
                "メールを送信しました。 mailRequestId=[3]");
        //障害通知ログに[2]が１回出力されていること
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[2]"
                ), 1);

        // DBの検証（ステータスと送信日時）
        final List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestList.size(), is(3));
        assertThat("メールリクエストIDが1", mailRequestList.get(0).mailRequestId, is("1"));
        assertThat("ステータスが「送信成功」になっているはず", mailRequestList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(0).sendDatetime, is(notNullValue()));
        assertThat("メールリクエストIDが2", mailRequestList.get(1).mailRequestId, is("2"));
        assertThat("ステータスが「送信失敗」になっているはず", mailRequestList.get(1).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(1).sendDatetime, is(nullValue()));
        assertThat("メールリクエストIDが3", mailRequestList.get(2).mailRequestId, is("3"));
        assertThat("ステータスが「送信成功」になっているはず", mailRequestList.get(2).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(2).sendDatetime, is(notNullValue()));

    }

    /**
     * ステータス更新処理が異常終了する場合のテスト<br/>
     * <br/>
     * メール送信失敗→未送信へのステータス更新に失敗する場合は処理がリトライ構成でも異常終了すること。
     */
    @Test
    public void testSendFailAndUnsentStatusUpdateFail() throws Exception {
        new MockUp<Transport>() {
            @Mock
            void send(Message message) throws MessagingException {
                throw new MessagingException("Test MessagingException.");
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
        String subject = "異常系 送信失敗へのDB更新失敗";
        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));
        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        OnMemoryLogWriter.clear();

        // バッチ実行 (リトライするハンドラ構成でのテスト)
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTestRetry.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("送信失敗ステータスへの更新失敗なので異常終了する", rc, is(mailConfig.getAbnormalEndExitCode()));

        // ----- assert log -----
        OnMemoryLogWriter.assertLogContains("writer.memory",
                "Failed to update unsent status. Need to apply a patch to change the status to failure. mailRequestId=[1]",
                "[199 ProcessAbnormalEnd] メール送信に失敗しました。 mailRequestId=[1]"
        );
        //障害通知ログに[1]と、パッチ適用メッセージが１回出力されていること
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[1]"
                ), 1);
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "nablarch.common.mail.SendStatusUpdateFailureException: Failed to update unsent status. Need to apply a patch to change the status to failure. mailRequestId=[1]"
                ), 1);

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("メールリクエストIDが1", mailRequestList.get(0).mailRequestId, is("1"));
        assertThat("ステータス更新が失敗し、「送信済み」になっているはず", mailRequestList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(0).sendDatetime, notNullValue());
    }

    /**
     * ステータス更新処理が異常終了する場合のテスト<br/>
     * <br/>
     * メール送信失敗(リトライ例外)→ステータス更新に失敗する場合は処理が異常終了すること。
     */
    @Test
    public void testSendFailAndFailureStatusUpdateFail() throws Exception {

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

        OnMemoryLogWriter.clear();

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTestRetry.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int rc = Main.execute(commandLine);
        assertThat("送信失敗ステータスへの更新失敗なので異常終了する", rc, is(mailConfig.getAbnormalEndExitCode()));

        // ----- assert log -----
        OnMemoryLogWriter.assertLogContains("writer.memory",
                "メールを送信しました。 mailRequestId=[1]",
                "メール送信に失敗しました。 mailRequestId=[2]",
                "Failed to update unsent status. Need to apply a patch to change the status to failure. mailRequestId=[2]");
        //障害通知ログに[2]と、パッチ適用メッセージが１回出力されていること
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[2]"
                ), 1);
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "nablarch.common.mail.SendStatusUpdateFailureException: Failed to update unsent status. Need to apply a patch to change the status to failure. mailRequestId=[2]"
                ), 1);

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestList.size(), is(3));

        // id:1(success)
        assertThat("メールリクエストIDが1", mailRequestList.get(0).mailRequestId, is("1"));
        assertThat("ステータスが「送信成功」になっているはず", mailRequestList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(0).sendDatetime, is(notNullValue()));

        // id:2(fail)
        assertThat("メールリクエストIDが2", mailRequestList.get(1).mailRequestId, is("2"));
        assertThat("送信失敗への変更に失敗するので「送信済み」のまま", mailRequestList.get(1).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(1).sendDatetime, is(notNullValue()));

        // id:3(unsent)
        assertThat("メールリクエストIDが3", mailRequestList.get(2).mailRequestId, is("3"));
        assertThat("ステータスが「未送信」になっているはず", mailRequestList.get(2).status, is(mailConfig.getStatusUnsent()));
        assertThat("送信日時が未更新(null)", mailRequestList.get(2).sendDatetime, is(nullValue()));
    }

    /**
     * メール送信の実行時に{@link SendFailedException}が発生した場合、詳細のログがERRORに出力され、異常終了していることのテスト
     */
    @Test
    public void testSendFailBySendFailedException() {
        new MockUp<Transport>() {

            @Mock
            public void send(Message message) throws MessagingException {
                // 複数アドレスの場合、間に ", " が入る
                final Address[] validSent = {new InternetAddress(to1), new InternetAddress(to2), new InternetAddress(cc1)};
                // １アドレスの場合
                final Address[] validUnsent = {new InternetAddress(to3)};
                // 空の場合
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
        int execute = Main.execute(commandLine);
        assertThat("送信失敗は異常終了ではなく戻り値は0となる。", execute, is(0));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "Failed to send a mail. Error message:[Test SendFailedException message.] Mail RequestId:[1] "
                        + "Sent address:[to1@localhost, to2@localhost, cc1@localhost] Unsent address:[to3@localhost] Invalid address:[]",
                         "メール送信に失敗しました。 mailRequestId=[1]");
        //障害通知ログに[1]と、メール送信失敗のアドレスの詳細が１回出力されていること
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[1]"
                ), 1);
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "Failed to send a mail. Error message:[Test SendFailedException message.] Mail RequestId:[1] ",
                        "Sent address:[to1@localhost, to2@localhost, cc1@localhost] Unsent address:[to3@localhost] Invalid address:[]"
                ), 1);

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時がnullのまま", mailRequestList.get(0).sendDatetime, nullValue());
    }

    /**
     * メール送信の実行時に{@link SendFailedException}が発生した場合、詳細のログがERRORに出力され、異常終了していることのテスト
     */
    @Test
    public void testSendFailBySendFailedExceptionNullAddresses() {
        new MockUp<Transport>() {

            @Mock
            public void send(Message message) throws MessagingException {
                // nullの場合
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
                new MailRecipient(mailRequestId, 4L, mailConfig.getRecipientTypeTO(), cc1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTest.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("送信失敗は異常終了ではなく戻り値は0となる。", execute, is(0));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "Failed to send a mail. Error message:[Test SendFailedException message.] Mail RequestId:[1] "
                        + "Sent address:[] Unsent address:[] Invalid address:[]",
                "メール送信に失敗しました。 mailRequestId=[1]");
        //障害通知ログに[1]と、メール送信失敗のアドレスの詳細が１回出力されていること
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[1]"
                ), 1);
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "Failed to send a mail. Error message:[Test SendFailedException message.] Mail RequestId:[1] ",
                        "Sent address:[] Unsent address:[] Invalid address:[]"
                ), 1);

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時がnullのまま", mailRequestList.get(0).sendDatetime, nullValue());
    }

    /**
     * メール送信の実行時に{@link SendFailedException}が発生し、リトライ例外を送出せずに
     * {@link nablarch.fw.launcher.ProcessAbnormalEnd}を送出してプロセス異常終了するテスト
     */
    @Test
    public void testNoRetryableExceptionAndProcessAbnormalEnd() {

        new MockUp<Transport>() {

            @Mock
            public void send(Message message) throws MessagingException {
                throw new RuntimeException("Test RuntimeException message in Transport.send.");
            }
        };

        // データ準備
        String mailRequestId = "1";
        String subject = "異常系 Transport.Sendで失敗（リトライしない）";
        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));
        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行 (リトライするハンドラ構成でのテスト)
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTestRetry.xml", "-requestPath",
                "nablarch.common.mail.NoRetryMailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("プロセスの異常終了なので戻り値は199となる。", execute, is(mailConfig.getAbnormalEndExitCode()));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "Test RuntimeException message in Transport.send.",
                "[199 ProcessAbnormalEnd] メール送信に失敗しました。 mailRequestId=[1]" // プロセス異常終了ログ
        );
        //障害通知ログに[1]が１回出力されていること
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        " -ERROR- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[1]"
                ), 1);

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時がnullのまま", mailRequestList.get(0).sendDatetime, nullValue());
    }

    /**
     * メール送信の実行時に{@link SendFailedException}が発生し、リトライ例外を送出しなかった場合に
     * {@link nablarch.fw.launcher.ProcessAbnormalEnd}となるテスト
     */
    @Test
    public void testRethrowProcessAbnormalEnd() {

        new MockUp<Transport>() {

            @Mock
            public void send(Message message) throws MessagingException {
                throw new ProcessAbnormalEnd(198,"SEND_FAIL0", "1");
            }
        };

        // データ準備
        String mailRequestId = "1";
        String subject = "異常系 Transport.Sendで失敗";
        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));
        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行 (リトライするハンドラ構成でのテスト)
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTestRetry.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("プロセスの異常終了を再送出するので戻り値はその例外の値の198となる。", execute, is(198));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "[198 ProcessAbnormalEnd] メール送信に失敗しました。 mailRequestId=[1]" // 再送出のプロセス異常終了ログ
        );
        //障害通知ログに再送出したメッセージが１回出力されていること
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "-FATAL- MONITOR [",
                        "] boot_proc = [] proc_sys = [] req_id = [SENDMAIL00] usr_id = [hoge] fail_code = [SEND_FAIL0] メール送信に失敗しました。 mailRequestId=[1]"
                ), 1);
        assertLogWithCount("writer.failure-memory",
                createMessagePattern(
                        "[198 ProcessAbnormalEnd] メール送信に失敗しました。 mailRequestId=[1]"
                ), 1);

        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時がnullのまま", mailRequestList.get(0).sendDatetime, nullValue());
    }

    /**
     * メール送信の実行時に{@link MessagingException}が発生し、リトライ上限に達するテスト
     */
    @Test
    public void testRetryableExceptionAndExceedRetryCount() throws Exception {

        new MockUp<Transport>() {

            @Mock
            public void send(Message message) throws MessagingException {
                throw new MessagingException("Test MessagingException message.");
            }
        };

        // データ準備
        String mailRequestId1 = "1";
        String subject1 = "リトライ1回目";
        String mailRequestId2 = "2";
        String subject2 = "リトライ上限で失敗";
        VariousDbTestHelper.setUpTable(
                new MailRequest(mailRequestId1, subject1, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody),
                new MailRequest(mailRequestId2, subject2, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody));
        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId1, 1L, mailConfig.getRecipientTypeTO(), to1),
                new MailRecipient(mailRequestId2, 2L, mailConfig.getRecipientTypeTO(), to1));

        // バッチ実行 (リトライするハンドラ構成でのテスト)
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTestRetry.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("リトライ上限超過による異常終了なので戻り値は180となる。", execute, is(180));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "caught a exception to retry. start retry. retryCount[1]",
                "retry process failed. retry limit was exceeded." // 2通目でリトライ上限を超える。
        );
        // DBの検証（ステータスと送信日時）
        List<MailRequest> mailRequestList = VariousDbTestHelper.findAll(MailRequest.class, "mailRequestId");
        assertThat("レコード取得数", mailRequestList.size(), is(2));
        assertThat("ステータスが「送信失敗」", mailRequestList.get(0).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(0).sendDatetime, nullValue());
        assertThat("ステータスが「送信失敗」", mailRequestList.get(1).status, is(mailConfig.getStatusFailure()));
        assertThat("送信日時が登録されていないはず", mailRequestList.get(1).sendDatetime, nullValue());
    }

    private void assertRecivingPlainMail(final String account, final String fromAddress, final String replyToAddress, final String mailSubject, final String to[],
            final String cc[]) throws Exception {
        // accountでメールを受信
        Session session = Session.getInstance(sessionProperties, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(account, "default");
            }
        });
        Store store = session.getStore("pop3");
        store.connect();
        //メールの削除と取得のタイムラグの間に他のテストケースで送信したメールが複数届く場合があるため、
        //Subjectが一致するものを待つ。
        Folder folder = openFolder(store, mailSubject);
        Message[] messages = folder.getMessages();
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
            assertThat( "TOがnullなのでtos[]は空のはず", to.length, is(0));
        } else {
            assertThat("TOアドレスの数", messageTO.length, is(to.length));
        }
        for (int i=0; i<to.length; i++) {
            assertThat("TOアドレス " + (i+1), ((InternetAddress) messageTO[i]).getAddress(), is(to[i]));
        }
        // CC
        Address[] messageCC = message.getRecipients(RecipientType.CC);
        if (messageCC == null) {
            assertThat( "CCがnullなのでccs[]は空のはず", cc.length, is(0));
        } else {
            assertThat("CCアドレスの数", messageCC.length, is(cc.length));
        }
        for (int i=0; i<cc.length; i++) {
            assertThat("CCアドレス " + (i+1), ((InternetAddress) messageCC[i]).getAddress(), is(cc[i]));
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

    public static class MailTestErrorHandler implements Handler<Object, Object> {

        protected static Exception catched;

        /** {@inheritDoc} */
        public Object handle(Object req, ExecutionContext ctx) {

            try {
                return ctx.handleNext(req);
            } catch (RuntimeException e) {
                catched = e;
                throw e;
            }
        }
    }
}
