package nablarch.common.mail;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;

import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.transaction.SimpleDbTransactionExecutor;
import nablarch.core.db.transaction.SimpleDbTransactionManager;
import nablarch.core.util.FileUtil;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * {@link MailRequester}のテストクラス。
 * <p/>
 * 本クラスでは、メール送信パターンIDをサポートした形式でのテストを実施する。
 * メール送信パターンIDを使用しないケースのテストは、{@link nablarch.common.mail.MailRequesterTest}にて実施する。
 *
 * @author hisaaki sioiri
 */
@RunWith(DatabaseTestRunner.class)
public class MailPatternSupportMailRequesterTest extends MailTestSupport {

    @ClassRule
    public static SystemRepositoryResource repositoryResource = new SystemRepositoryResource(
            "nablarch/common/mail/MailPatternSupportMailRequesterTest.xml");

    /** target codeが使用するトランザクション */
    private static SimpleDbTransactionManager dbTransactionManager;

    @BeforeClass
    public static void setupClass() throws SQLException {
        dbTransactionManager = repositoryResource.getComponent("dbManager-default");
    }

    @Before
    public void setup() {
        // テーブルの初期データセットアップ
        VariousDbTestHelper.setUpTable(new MailTemplate("12345", "ja", "タイトル", "utf-8", "{hoge}あいうえお{fuga}かきくけこ"));
        VariousDbTestHelper.setUpTable(new MailSbnTable("99", 999L));
    }

    /** フリーメール形式でto、cc、bcc全て指定した場合 */
    @Test
    public void testFreeMailNotAttachedFile() {

        final FreeTextMailContext context = new FreeTextMailContext();
        context.setSubject("タイトル");
        context.setCharset("utf-8");
        context.setMailBody("本文1\n"
                + "本文2"
                + "本文3");
        context.addTo("to@to.com");
        context.addCc("cc@cc.com");
        context.addBcc("bcc@bcc.com");
        context.setFrom("from@mail.com");
        context.setReplyTo("reply@mail.com");
        context.setReturnPath("return@mail.com");
        context.setMailSendPatternId("01");
        final MailRequester requester = MailUtil.getMailRequester();

        new SimpleDbTransactionExecutor<Void>(dbTransactionManager) {
            @Override
            public Void execute(AppDbConnection connection) {
                requester.requestToSend(context);
                return null;
            }
        }.doTransaction();

        // 登録結果の確認
        List<MailRequestPattern> mailRequestPatternList = VariousDbTestHelper.findAll(MailRequestPattern.class);
        assertThat(mailRequestPatternList.size(), is(1));
        assertThat(mailRequestPatternList.get(0).mailRequestId, is("1000"));
        assertThat(mailRequestPatternList.get(0).subject, is(context.getSubject()));
        assertThat(mailRequestPatternList.get(0).mailFrom, is(context.getFrom()));
        assertThat(mailRequestPatternList.get(0).replyTo, is(context.getReplyTo()));
        assertThat(mailRequestPatternList.get(0).returnPath, is(context.getReturnPath()));
        assertThat(mailRequestPatternList.get(0).charset, is(context.getCharset()));
        assertThat(mailRequestPatternList.get(0).status, is("0"));
        assertThat(mailRequestPatternList.get(0).requestDatetime, is(notNullValue()));
        assertThat(mailRequestPatternList.get(0).sendDatetime, is(nullValue()));
        assertThat(mailRequestPatternList.get(0).mailBody, is(context.getMailBody()));
        assertThat(mailRequestPatternList.get(0).mailSendPatternId, is(context.getMailSendPatternId()));

        // 送信先の登録確認
        List<MailRecipient> mailRecipientList = VariousDbTestHelper.findAll(MailRecipient.class);
        assertThat(mailRecipientList.size(), is(3));

        assertThat(mailRecipientList.get(0).mailRequestId, is("1000"));
        assertThat(mailRecipientList.get(0).serialNumber, is(1L));
        assertThat(mailRecipientList.get(0).recipientType, is("0"));
        assertThat(mailRecipientList.get(0).mailAddress, is("to@to.com"));

        assertThat(mailRecipientList.get(1).mailRequestId, is("1000"));
        assertThat(mailRecipientList.get(1).serialNumber, is(2L));
        assertThat(mailRecipientList.get(1).recipientType, is("1"));
        assertThat(mailRecipientList.get(1).mailAddress, is("cc@cc.com"));

        assertThat(mailRecipientList.get(2).mailRequestId, is("1000"));
        assertThat(mailRecipientList.get(2).serialNumber, is(3L));
        assertThat(mailRecipientList.get(2).recipientType, is("2"));
        assertThat(mailRecipientList.get(2).mailAddress, is("bcc@bcc.com"));

        // 添付ファイルはなし
        List<MailAttachedFile> mailAttachedFileList = VariousDbTestHelper.findAll(MailAttachedFile.class);
        assertThat(mailAttachedFileList.size(), is(0));
    }

    /** フリーメール形式で添付ファイルを指定した場合 */
    @Test
    public void testFreeMailAttachedFile() throws Exception {

        final File attachedFile = new File(FileUtil.getClasspathResourceURL("nablarch/common/mail/mailAttachedFile.txt")
                                                   .toURI());

        final FreeTextMailContext context = new FreeTextMailContext();
        context.setSubject("タイトル");
        context.setCharset("utf-8");
        context.setMailBody("本文1\n"
                + "本文2"
                + "本文3");
        context.addTo("to@to.com");
        context.addCc("cc@cc.com");
        context.addBcc("bcc@bcc.com");
        context.setFrom("from@mail.com");
        context.setReplyTo("reply@mail.com");
        context.setReturnPath("return@mail.com");
        context.setMailSendPatternId("01");
        context.addAttachedFile(new AttachedFile("text/plain", attachedFile));
        final MailRequester requester = MailUtil.getMailRequester();

        new SimpleDbTransactionExecutor<Void>(dbTransactionManager) {
            @Override
            public Void execute(AppDbConnection connection) {
                requester.requestToSend(context);
                return null;
            }
        }.doTransaction();

        // 登録結果の確認
        List<MailRequestPattern> mailRequestPatternList = VariousDbTestHelper.findAll(MailRequestPattern.class);
        assertThat(mailRequestPatternList.size(), is(1));
        assertThat(mailRequestPatternList.get(0).mailRequestId, is("1000"));
        assertThat(mailRequestPatternList.get(0).subject, is(context.getSubject()));
        assertThat(mailRequestPatternList.get(0).mailFrom, is(context.getFrom()));
        assertThat(mailRequestPatternList.get(0).replyTo, is(context.getReplyTo()));
        assertThat(mailRequestPatternList.get(0).returnPath, is(context.getReturnPath()));
        assertThat(mailRequestPatternList.get(0).charset, is(context.getCharset()));
        assertThat(mailRequestPatternList.get(0).status, is("0"));
        assertThat(mailRequestPatternList.get(0).requestDatetime, is(notNullValue()));
        assertThat(mailRequestPatternList.get(0).sendDatetime, is(nullValue()));
        assertThat(mailRequestPatternList.get(0).mailBody, is(context.getMailBody()));
        assertThat(mailRequestPatternList.get(0).mailSendPatternId, is(context.getMailSendPatternId()));

        // 送信先の登録確認
        List<MailRecipient> mailRecipientList = VariousDbTestHelper.findAll(MailRecipient.class);
        assertThat(mailRecipientList.size(), is(3));

        assertThat(mailRecipientList.get(0).mailRequestId, is("1000"));
        assertThat(mailRecipientList.get(0).serialNumber, is(1L));
        assertThat(mailRecipientList.get(0).recipientType, is("0"));
        assertThat(mailRecipientList.get(0).mailAddress, is("to@to.com"));

        assertThat(mailRecipientList.get(1).mailRequestId, is("1000"));
        assertThat(mailRecipientList.get(1).serialNumber, is(2L));
        assertThat(mailRecipientList.get(1).recipientType, is("1"));
        assertThat(mailRecipientList.get(1).mailAddress, is("cc@cc.com"));

        assertThat(mailRecipientList.get(2).mailRequestId, is("1000"));
        assertThat(mailRecipientList.get(2).serialNumber, is(3L));
        assertThat(mailRecipientList.get(2).recipientType, is("2"));
        assertThat(mailRecipientList.get(2).mailAddress, is("bcc@bcc.com"));

        // 添付ファイルの確認
        List<MailAttachedFile> mailAttachedFileList = VariousDbTestHelper.findAll(MailAttachedFile.class);
        assertThat(mailAttachedFileList.size(), is(1));
        assertThat(mailAttachedFileList.get(0).mailRequestId, is("1000"));
        assertThat(mailAttachedFileList.get(0).serialNumber, is(1L));
        assertThat(mailAttachedFileList.get(0).fileName, is("mailAttachedFile.txt"));
        assertThat(mailAttachedFileList.get(0).contentType, is("text/plain"));

        InputStream stream = new ByteArrayInputStream((mailAttachedFileList.get(0).attachedFile));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        int b;
        while ((b = stream.read()) != -1) {
            outputStream.write(b);
        }
        assertThat((long) outputStream.size(), is(attachedFile.length()));
    }

    /** テンプレートメール形式でto、cc、bcc全て指定した場合 */
    @Test
    public void testTemplateMailNotAttachedFile() {

        final TemplateMailContext context = new TemplateMailContext();
        context.addTo("to@to.com");
        context.addCc("cc@cc.com");
        context.addBcc("bcc@bcc.com");
        context.setFrom("from@mail.com");
        context.setReplyTo("reply@mail.com");
        context.setReturnPath("return@mail.com");
        context.setMailSendPatternId("99");
        context.setReplaceKeyValue("hoge", "ほげ");
        context.setReplaceKeyValue("fuga", "ふが");
        context.setTemplateId("12345");
        context.setLang("ja");
        final MailRequester requester = MailUtil.getMailRequester();

        new SimpleDbTransactionExecutor<Void>(dbTransactionManager) {
            @Override
            public Void execute(AppDbConnection connection) {
                requester.requestToSend(context);
                return null;
            }
        }.doTransaction();

        // 登録結果の確認
        List<MailRequestPattern> mailRequestPatternList = VariousDbTestHelper.findAll(MailRequestPattern.class);
        assertThat(mailRequestPatternList.size(), is(1));
        assertThat(mailRequestPatternList.get(0).mailRequestId, is("1000"));
        assertThat("テンプレートのタイトルが設定されること", mailRequestPatternList.get(0).subject, is("タイトル"));
        assertThat(mailRequestPatternList.get(0).mailFrom, is(context.getFrom()));
        assertThat(mailRequestPatternList.get(0).replyTo, is(context.getReplyTo()));
        assertThat(mailRequestPatternList.get(0).returnPath, is(context.getReturnPath()));
        assertThat("テンプレートの文字セットが設定されること", mailRequestPatternList.get(0).charset, is("utf-8"));
        assertThat(mailRequestPatternList.get(0).status, is("0"));
        assertThat(mailRequestPatternList.get(0).requestDatetime, is(notNullValue()));
        assertThat(mailRequestPatternList.get(0).sendDatetime, is(nullValue()));
        assertThat("テンプレートメッセージが設定されること", mailRequestPatternList.get(0).mailBody, is("ほげあいうえおふがかきくけこ"));
        assertThat(mailRequestPatternList.get(0).mailSendPatternId, is(context.getMailSendPatternId()));

        // 送信先の登録確認
        List<MailRecipient> mailRecipientList = VariousDbTestHelper.findAll(MailRecipient.class);
        assertThat(mailRecipientList.size(), is(3));

        assertThat(mailRecipientList.get(0).mailRequestId, is("1000"));
        assertThat(mailRecipientList.get(0).serialNumber, is(1L));
        assertThat(mailRecipientList.get(0).recipientType, is("0"));
        assertThat(mailRecipientList.get(0).mailAddress, is("to@to.com"));

        assertThat(mailRecipientList.get(1).mailRequestId, is("1000"));
        assertThat(mailRecipientList.get(1).serialNumber, is(2L));
        assertThat(mailRecipientList.get(1).recipientType, is("1"));
        assertThat(mailRecipientList.get(1).mailAddress, is("cc@cc.com"));

        assertThat(mailRecipientList.get(2).mailRequestId, is("1000"));
        assertThat(mailRecipientList.get(2).serialNumber, is(3L));
        assertThat(mailRecipientList.get(2).recipientType, is("2"));
        assertThat(mailRecipientList.get(2).mailAddress, is("bcc@bcc.com"));

        // 添付ファイルはなし
        List<MailAttachedFile> mailAttachedFileList = VariousDbTestHelper.findAll(MailAttachedFile.class);
        assertThat(mailAttachedFileList.size(), is(0));
    }

    /** テンプレートメール形式で添付ファイルを指定した場合 */
    @Test
    public void testTemplateMailAttachedFile() throws Exception {

        final URL url = FileUtil.getClasspathResourceURL("nablarch/common/mail/mailAttachedFile.txt");
        final File attachedFile = new File(url.toURI());

        final TemplateMailContext context = new TemplateMailContext();
        context.setTemplateId("12345");
        context.setLang("ja");
        context.addTo("to@to.com");
        context.addCc("cc@cc.com");
        context.addBcc("bcc@bcc.com");
        context.setFrom("from@mail.com");
        context.setReplyTo("reply@mail.com");
        context.setReturnPath("return@mail.com");
        context.setMailSendPatternId("77");
        context.addAttachedFile(new AttachedFile("text/plain", attachedFile));
        final MailRequester requester = MailUtil.getMailRequester();

        new SimpleDbTransactionExecutor<Void>(dbTransactionManager) {
            @Override
            public Void execute(AppDbConnection connection) {
                requester.requestToSend(context);
                return null;
            }
        }.doTransaction();

        // 登録結果の確認
        List<MailRequestPattern> mailRequestPatternList = VariousDbTestHelper.findAll(MailRequestPattern.class);
        assertThat(mailRequestPatternList.size(), is(1));
        assertThat(mailRequestPatternList.get(0).mailRequestId, is("1000"));
        assertThat("テンプレートのタイトルが設定される", mailRequestPatternList.get(0).subject, is("タイトル"));
        assertThat(mailRequestPatternList.get(0).mailFrom, is(context.getFrom()));
        assertThat(mailRequestPatternList.get(0).replyTo, is(context.getReplyTo()));
        assertThat(mailRequestPatternList.get(0).returnPath, is(context.getReturnPath()));
        assertThat("テンプレートの文字セットが設定される", mailRequestPatternList.get(0).charset, is("utf-8"));
        assertThat(mailRequestPatternList.get(0).status, is("0"));
        assertThat(mailRequestPatternList.get(0).requestDatetime, is(notNullValue()));
        assertThat(mailRequestPatternList.get(0).sendDatetime, is(nullValue()));
        assertThat("テンプレートの本文が設定される", mailRequestPatternList.get(0).mailBody, is(context.getMailBody()));
        assertThat(mailRequestPatternList.get(0).mailSendPatternId, is(context.getMailSendPatternId()));

        // 送信先の登録確認
        List<MailRecipient> mailRecipientList = VariousDbTestHelper.findAll(MailRecipient.class);
        assertThat(mailRecipientList.size(), is(3));

        assertThat(mailRecipientList.get(0).mailRequestId, is("1000"));
        assertThat(mailRecipientList.get(0).serialNumber, is(1L));
        assertThat(mailRecipientList.get(0).recipientType, is("0"));
        assertThat(mailRecipientList.get(0).mailAddress, is("to@to.com"));

        assertThat(mailRecipientList.get(1).mailRequestId, is("1000"));
        assertThat(mailRecipientList.get(1).serialNumber, is(2L));
        assertThat(mailRecipientList.get(1).recipientType, is("1"));
        assertThat(mailRecipientList.get(1).mailAddress, is("cc@cc.com"));

        assertThat(mailRecipientList.get(2).mailRequestId, is("1000"));
        assertThat(mailRecipientList.get(2).serialNumber, is(3L));
        assertThat(mailRecipientList.get(2).recipientType, is("2"));
        assertThat(mailRecipientList.get(2).mailAddress, is("bcc@bcc.com"));

        // 添付ファイルの確認
        List<MailAttachedFile> mailAttachedFileList = VariousDbTestHelper.findAll(MailAttachedFile.class);
        assertThat(mailAttachedFileList.size(), is(1));
        assertThat(mailAttachedFileList.get(0).mailRequestId, is("1000"));
        assertThat(mailAttachedFileList.get(0).serialNumber, is(1L));
        assertThat(mailAttachedFileList.get(0).fileName, is("mailAttachedFile.txt"));
        assertThat(mailAttachedFileList.get(0).contentType, is("text/plain"));

        InputStream stream = new ByteArrayInputStream(mailAttachedFileList.get(0).attachedFile);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        int b;
        while ((b = stream.read()) != -1) {
            outputStream.write(b);
        }
        assertThat((long) outputStream.size(), is(attachedFile.length()));
    }
}

