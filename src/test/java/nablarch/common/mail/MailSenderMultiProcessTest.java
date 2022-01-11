package nablarch.common.mail;

import nablarch.core.date.SystemTimeUtil;
import nablarch.fw.launcher.CommandLine;
import nablarch.fw.launcher.Main;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage.RecipientType;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * {@link MailSender}のテストクラス。
 * <p/>
 * 本クラスでは、マルチプロセス用の設定がされているときに送信されることを確認するテストを行う。
 */
@RunWith(DatabaseTestRunner.class)
public class MailSenderMultiProcessTest extends MailTestSupport {

    @Rule
    public SystemRepositoryResource repositoryResource = new SystemRepositoryResource(
            "nablarch/common/mail/MailSenderTestMultiProcess.xml");

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

    @AfterClass
    public static void tearDown() throws Exception {
        System.out.println("MailSenderPatternIdSupportTest.tearDown");
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * マルチプロセス用の処理が正常終了する場合のテスト<br/>
     * 未送信のメールを送信できる。
     * <br/>
     * TO:0件,CC:複数,BCC1件,添付ファイルなし
     *
     * @throws Exception
     */
    @Test
    public void testExecuteNormalEnd() throws Exception {

        // データ準備
        String mailRequestId = "1";
        String subject = "正常系1";

        VariousDbTestHelper.setUpTable(
                new MailRequestMultiProcess(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody, null));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeCC(), cc1),
                new MailRecipient(mailRequestId, 2L, mailConfig.getRecipientTypeCC(), cc2),
                new MailRecipient(mailRequestId, 3L, mailConfig.getRecipientTypeCC(), cc3),
                new MailRecipient(mailRequestId, 4L, mailConfig.getRecipientTypeBCC(), bcc1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTestMultiProcess.xml", "-requestPath",
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
        List<MailRequestMultiProcess> mailRequestList = VariousDbTestHelper.findAll(MailRequestMultiProcess.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが更新されているはず", mailRequestList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestList.get(0).sendDatetime, notNullValue());
        assertThat("プロセスIDが登録されているはず", mailRequestList.get(0).processId, notNullValue());
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * マルチプロセス用の処理が正常終了する場合のテスト<br/>
     * 送信済みのものを送信しない。
     *
     * @throws Exception
     */
    @Test
    public void testExecuteDoNotSendAlreadySent() throws Exception {

        // データ準備
        String mailRequestId = "1";
        String subject = "正常系1";

        VariousDbTestHelper.setUpTable(
                new MailRequestMultiProcess(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusSent(), SystemTimeUtil.getTimestamp(), null, mailBody, null));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeCC(), cc1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTestMultiProcess.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("正常終了なので戻り値は0となる。", execute, is(0));

        // ログ出力確認
        assertLog("メール送信要求が 0 件あります。");

        // DBの検証（ステータスと送信日時）
        List<MailRequestMultiProcess> mailRequestList = VariousDbTestHelper.findAll(MailRequestMultiProcess.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが更新されてないはず", mailRequestList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されてないはず", mailRequestList.get(0).sendDatetime, nullValue());
        assertThat("プロセスIDが登録されていないはず", mailRequestList.get(0).processId, nullValue());
    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * マルチプロセス用の処理が正常終了する場合のテスト<br/>
     * 別プロセスが処理中のものを送信しない。
     *
     * @throws Exception
     */
    @Test
    public void testExecuteDoNotSendOtherProcessSending() throws Exception {

        // データ準備
        String mailRequestId = "1";
        String subject = "正常系1";
        String otherProcessId = UUID.randomUUID().toString();

        VariousDbTestHelper.setUpTable(
                new MailRequestMultiProcess(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody, otherProcessId));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeCC(), cc1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTestMultiProcess.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge");
        int execute = Main.execute(commandLine);
        assertThat("正常終了なので戻り値は0となる。", execute, is(0));

        // ログ出力確認
        assertLog("メール送信要求が 0 件あります。");

        // DBの検証（ステータスと送信日時）
        List<MailRequestMultiProcess> mailRequestList = VariousDbTestHelper.findAll(MailRequestMultiProcess.class);
        assertThat("レコード取得数", mailRequestList.size(), is(1));
        assertThat("ステータスが更新されないはず", mailRequestList.get(0).status, is(mailConfig.getStatusUnsent()));
        assertThat("送信日時が登録されないはず", mailRequestList.get(0).sendDatetime, nullValue());
        assertThat("プロセスIDが登録されているはず", mailRequestList.get(0).processId, is(otherProcessId));
    }

    /**
     * {@link nablarch.fw.launcher.Main#execute(nablarch.fw.launcher.CommandLine)}のテスト。
     * <p/>
     * 処理が正常終了する場合のテスト<br/>
     * パターンIDの設定とマルチプロセスの設定どちらもされていても正常に送信できる。
     * <br/>
     * メールリクエストは対象となるパターンIDのほか、対象外ものも１件づつ、ダミーのプロセスIDが付与されたものを１件登録する。<br>
     * メールバッチがパターンIDを認識して自身が処理すべきリクエストを処理できているかを検証する。<br>
     * ダミーのプロセスIDが付与されたものは処理されないことも検証する。<br>
     * TO:0件,CC:複数,BCC1件,添付ファイルなし
     *
     * @throws Exception
     */
    @Test
    public void testExecuteNormalEndWithPatternId() throws Exception {

        // データ準備
        String mailRequestId1 = "1";
        String mailRequestId2 = "2";
        String mailRequestId3 = "3";

        String subject = "正常系1";


        String dummyProcessID3 = "dummyProcessID3";


        VariousDbTestHelper.setUpTable(
                new MailRequestPatternMultiProcess(mailRequestId1, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody, "02", null),
                new MailRequestPatternMultiProcess(mailRequestId2, subject, from, replyTo, returnPath, charset, mailConfig.getStatusUnsent(),
                        SystemTimeUtil.getTimestamp(), null, mailBody, "01", null),
                new MailRequestPatternMultiProcess(mailRequestId3, subject, from, replyTo, returnPath, charset, mailConfig.getStatusUnsent(),
                        SystemTimeUtil.getTimestamp(), null, mailBody, "02", dummyProcessID3));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId1, 1L, mailConfig.getRecipientTypeCC(), cc1),
                new MailRecipient(mailRequestId1, 2L, mailConfig.getRecipientTypeCC(), cc2),
                new MailRecipient(mailRequestId1, 3L, mailConfig.getRecipientTypeCC(), cc3),
                new MailRecipient(mailRequestId1, 4L, mailConfig.getRecipientTypeBCC(), bcc1));

        // メール送信処理：パターンID=02だけ処理
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTestPatternIdAndMultiProcess.xml", "-requestPath",
                "nablarch.common.mail.MailSender/SENDMAIL00", "-userId", "hoge", "-mailSendPatternId", "02");
        int execute = Main.execute(commandLine);
        assertThat("正常終了なので戻り値は0となる。", execute, CoreMatchers.is(0));

        // ログ出力確認
        assertLog("メール送信要求が 1 件あります。");
        assertLog("メールを送信しました。 mailRequestId=[" + mailRequestId1 + "]");

        // 処理対象となるmailRequestId1はプロセスIDが更新され、それ以外は変わっていないことを確認
        assertThat("プロセスIDが更新されているはず", VariousDbTestHelper.findById(MailRequestPatternMultiProcess.class, mailRequestId1).processId, is(notNullValue()));
        assertThat("プロセスIDが更新されていないはず", VariousDbTestHelper.findById(MailRequestPatternMultiProcess.class, mailRequestId2).processId,is(nullValue()));
        assertThat("プロセスIDが更新されていないはず", VariousDbTestHelper.findById(MailRequestPatternMultiProcess.class, mailRequestId3).processId,is(dummyProcessID3));


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
        List<MailRequestPatternMultiProcess> mailRequestPatternList = VariousDbTestHelper.findAll(MailRequestPatternMultiProcess.class,
                "mailRequestId");
        assertThat("レコード取得数", mailRequestPatternList.size(), is(3));

        //----------------------------------------------------------------------
        // 送信対象のパターンID
        //----------------------------------------------------------------------
        assertThat("ステータスが更新されているはず", mailRequestPatternList.get(0).status, is(mailConfig.getStatusSent()));
        assertThat("送信日時が登録されているはず", mailRequestPatternList.get(0).sendDatetime, is(notNullValue()));

        //----------------------------------------------------------------------
        // 送信対象外のパターンID / 別プロセス処理中
        //----------------------------------------------------------------------
        assertThat("ステータスが更新されてないはず", mailRequestPatternList.get(1).status, is(mailConfig.getStatusUnsent()));
        assertThat("送信日時が登録されてないはず", mailRequestPatternList.get(1).sendDatetime, is(nullValue()));

        assertThat("ステータスが更新されてないはず", mailRequestPatternList.get(2).status, is(mailConfig.getStatusUnsent()));
        assertThat("送信日時が登録されてないはず", mailRequestPatternList.get(2).sendDatetime, is(nullValue()));


    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 処理が正常終了する場合のテスト<br/>
     * マルチプロセスの設定をしないケース。
     * {@link MailRequestTable#createReaderStatement(String)}のテスト。
     * <br/>
     * TO:0件,CC:複数,BCC1件,添付ファイルなし
     *
     * @throws Exception
     */
    @Test
    public void testExecuteNormalEndNotMultiProcess() throws Exception {

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
                "nablarch.common.mail.ExMailSender/SENDMAIL00", "-userId", "hoge");
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
     * 処理が異常終了する場合のテスト<br/>
     * マルチプロセスの設定がされているがプロセスIDを指定しないケース。
     *
     * @throws Exception
     */
    @Test
    public void testExecuteAbnormalEnd() throws Exception {

        // データ準備
        String mailRequestId = "1";
        String subject = "正常系1";

        VariousDbTestHelper.setUpTable(
                new MailRequestMultiProcess(mailRequestId, subject, from, replyTo, returnPath, charset,
                        mailConfig.getStatusUnsent(), SystemTimeUtil.getTimestamp(), null, mailBody, null));

        VariousDbTestHelper.setUpTable(
                new MailRecipient(mailRequestId, 1L, mailConfig.getRecipientTypeCC(), cc1),
                new MailRecipient(mailRequestId, 2L, mailConfig.getRecipientTypeCC(), cc2),
                new MailRecipient(mailRequestId, 3L, mailConfig.getRecipientTypeCC(), cc3),
                new MailRecipient(mailRequestId, 4L, mailConfig.getRecipientTypeBCC(), bcc1));

        // バッチ実行
        CommandLine commandLine = new CommandLine("-diConfig",
                "nablarch/common/mail/MailSenderTestMultiProcess.xml", "-requestPath",
                "nablarch.common.mail.ExMailSender/SENDMAIL00", "-userId", "hoge");
        Main.execute(commandLine);

        // ログ出力確認
        assertLog("sendProcessId must not be null if you use in multi process.");
    }
}
