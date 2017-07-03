package nablarch.common.mail;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import nablarch.core.db.transaction.SimpleDbTransactionManager;
import nablarch.core.repository.SystemRepository;
import nablarch.core.repository.di.DiContainer;
import nablarch.core.repository.di.config.xml.XmlComponentDefinitionLoader;
import nablarch.core.util.FileUtil;
import nablarch.core.util.annotation.Published;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;


/**
 * {@link MailRequester}のテストクラス。
 */
@Published(tag = "architect")
@RunWith(DatabaseTestRunner.class)
public class MailRequesterTest extends MailTestSupport {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public SystemRepositoryResource repositoryResource = new SystemRepositoryResource(
            "nablarch/common/mail/MailRequesterTest.xml");

    /** トランザクションマネージャー */
    private static SimpleDbTransactionManager db;

    /** メールのデフォルト設定を保持するデータオブジェクト */
    private static MailRequestConfig mailRequestConfig;

    /** 出力ライブラリ(メール送信)のコード値を保持するデータオブジェクト */
    private static MailConfig mailConfig;

    public static final String LINE_SEPARATOR = System.getProperty("line.separator");

    // 使いまわすテストデータ
    private static File file1;

    private static File file2;

    private static File file3;

    static {
        try {
            file1 = new File(FileUtil.getClasspathResourceURL("nablarch/common/mail/mailAttachedFile.txt")
                                     .toURI());
            file2 = new File(FileUtil.getClasspathResourceURL("nablarch/common/mail/mailAttachedFile2.txt")
                                     .toURI());
            file3 = new File(FileUtil.getClasspathResourceURL("nablarch/common/mail/mailAttachedFile3.txt")
                                     .toURI());
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static String mailTemplateId = "0000000001";

    private static String lang = "ja";

    private static String subjectTemplate = "{1}について{option}";

    private static String mailBodyTemplate = "{1}は、申請番号{2}で申請されました。" + LINE_SEPARATOR
            + "{3}は速やかに{1}を承認してください。{option}";

    @Override
    @Before
    public void before() throws Exception {
        super.before();
        mailRequestConfig = repositoryResource.getComponent("mailRequestConfig");
        mailConfig = repositoryResource.getComponent("mailConfig");
        db = repositoryResource.getComponent("dbManager-default");
        VariousDbTestHelper.setUpTable(
                new MailTemplate(mailTemplateId, lang, subjectTemplate, charset, mailBodyTemplate));
    }

    /**
     * 非定形メール正常系1<br>
     * <br/>
     * TO:なし,CC:複数、BCC:1件、Return-Path,Reply-TO,charset指定、添付ファイルなし
     *
     * @throws Exception
     */
    @Test
    public void testMailRequestOK1() throws Exception {

        MailRequester requester = MailUtil.getMailRequester();

        db.beginTransaction();
        try {
            FreeTextMailContext ctx = new FreeTextMailContext();
            ctx.setFrom(from);
            ctx.addCc(cc1);
            ctx.addCc(cc2);
            ctx.addCc(cc3);
            ctx.addBcc(bcc1);
            ctx.setReturnPath(returnPath);
            ctx.setReplyTo(replyTo);
            ctx.setSubject(subject);
            ctx.setMailBody(mailBody);
            ctx.setCharset(charset);

            // 送信要求
            String mailRequestId = requester.requestToSend(ctx);
            db.commitTransaction();

            // メール送信要求管理テーブルの確認
            MailRequest mailRequest = VariousDbTestHelper.findById(MailRequest.class, mailRequestId);
            assertThat(mailRequest, is(notNullValue()));
            assertThat(mailRequest.subject, is(subject));
            assertThat(mailRequest.mailFrom, is(from));
            assertThat(mailRequest.replyTo, is(replyTo));
            assertThat(mailRequest.returnPath, is(returnPath));
            assertThat(mailRequest.charset, is(charset));
            assertThat(mailRequest.status, is(mailConfig.getStatusUnsent()));
            assertThat(mailRequest.mailBody, is(mailBody));

            List<MailRecipient> mailRecipientList = VariousDbTestHelper.findAll(MailRecipient.class, "mailRequestId",
                    "serialNumber");
            assertThat(mailRecipientList.size(), is(4));

            assertThat(mailRecipientList.get(0).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(0).serialNumber, is(1L));
            assertThat(mailRecipientList.get(0).recipientType, is(mailConfig.getRecipientTypeCC()));
            assertThat(mailRecipientList.get(0).mailAddress, is(cc1));
            assertThat(mailRecipientList.get(1).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(1).serialNumber, is(2L));
            assertThat(mailRecipientList.get(1).recipientType, is(mailConfig.getRecipientTypeCC()));
            assertThat(mailRecipientList.get(1).mailAddress, is(cc2));
            assertThat(mailRecipientList.get(2).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(2).serialNumber, is(3L));
            assertThat(mailRecipientList.get(2).recipientType, is(mailConfig.getRecipientTypeCC()));
            assertThat(mailRecipientList.get(2).mailAddress, is(cc3));
            assertThat(mailRecipientList.get(3).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(3).serialNumber, is(4L));
            assertThat(mailRecipientList.get(3).recipientType, is(mailConfig.getRecipientTypeBCC()));
            assertThat(mailRecipientList.get(3).mailAddress, is(bcc1));

            List<MailAttachedFile> mailAttachedFileList = VariousDbTestHelper.findAll(MailAttachedFile.class);
            assertThat(mailAttachedFileList.size(), is(0));

        } finally {
            db.endTransaction();
        }

    }

    /**
     * 非定形メール正常系2<br/>
     * <br/>
     * TO:1件,CC:0件、BCC:複数件、Return-Path,Reply-TO,charsetデフォルト、添付ファイル1件
     *
     * @throws Exception
     */
    @Test
    public void testMailRequestOK2() throws Exception {

        AttachedFile afile1 = new AttachedFile(aFileContentType, file1);

        MailRequester requester = MailUtil.getMailRequester();

        db.beginTransaction();

        FileInputStream fis = null;
        BufferedInputStream bis = null;
        try {

            FreeTextMailContext ctx = new FreeTextMailContext();
            ctx.setFrom(from);
            ctx.addTo(to1);
            ctx.addBcc(bcc1);
            ctx.addBcc(bcc2);
            ctx.addBcc(bcc3);
            ctx.setSubject(subject);
            ctx.setMailBody(mailBody);
            ctx.addAttachedFile(afile1);

            // 送信要求
            String mailRequestId = requester.requestToSend(ctx);
            db.commitTransaction();

            // メール送信要求管理テーブルの確認
            MailRequest mailRequest = VariousDbTestHelper.findById(MailRequest.class, mailRequestId);
            assertThat(mailRequest, is(notNullValue()));

            assertThat(mailRequest.subject, is(subject));
            assertThat(mailRequest.mailFrom, is(from));
            assertThat(mailRequest.replyTo, is(mailRequestConfig.getDefaultReplyTo()));
            assertThat(mailRequest.returnPath, is(mailRequestConfig.getDefaultReturnPath()));
            assertThat(mailRequest.charset, is(mailRequestConfig.getDefaultCharset()));
            assertThat(mailRequest.mailBody, is(mailBody));

            // メール送信先テーブルの確認(TO:1件,BCC:3件)
            List<MailRecipient> mailRecipientList = VariousDbTestHelper.findAll(MailRecipient.class, "mailRequestId",
                    "serialNumber");
            assertThat(mailRecipientList.size(), is(4));
            assertThat(mailRecipientList.get(0).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(0).serialNumber, is(1L));
            assertThat(mailRecipientList.get(0).recipientType, is(mailConfig.getRecipientTypeTO()));
            assertThat(mailRecipientList.get(0).mailAddress, is(to1));
            assertThat(mailRecipientList.get(1).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(1).serialNumber, is(2L));
            assertThat(mailRecipientList.get(1).recipientType, is(mailConfig.getRecipientTypeBCC()));
            assertThat(mailRecipientList.get(1).mailAddress, is(bcc1));
            assertThat(mailRecipientList.get(2).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(2).serialNumber, is(3L));
            assertThat(mailRecipientList.get(2).recipientType, is(mailConfig.getRecipientTypeBCC()));
            assertThat(mailRecipientList.get(2).mailAddress, is(bcc2));
            assertThat(mailRecipientList.get(3).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(3).serialNumber, is(4L));
            assertThat(mailRecipientList.get(3).recipientType, is(mailConfig.getRecipientTypeBCC()));
            assertThat(mailRecipientList.get(3).mailAddress, is(bcc3));

            List<MailAttachedFile> mailAttachedFileList = VariousDbTestHelper.findAll(MailAttachedFile.class);
            assertThat(mailAttachedFileList.size(), is(1));

            fis = new FileInputStream(file1);
            bis = new BufferedInputStream(fis);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int ch;
            while ((ch = bis.read()) != -1) {
                baos.write(ch);
            }
            byte[] fileBytes = baos.toByteArray();
            assertThat(mailAttachedFileList.get(0).attachedFile, is(fileBytes));

        } finally {
            db.endTransaction();
            FileUtil.closeQuietly(fis);
            FileUtil.closeQuietly(bis);
        }

    }

    /**
     * 正常系3<br/>
     * <br/>
     * TO:複数件,CC:1件、BCC:0件、Return-Path,Reply-TO,charsetデフォルト、添付ファイル複数
     *
     * @throws Exception
     */
    @Test
    public void testMailRequestOK3() throws Exception {

        AttachedFile afile1 = new AttachedFile();
        afile1.setContentType(aFileContentType);
        afile1.setFile(file1);

        AttachedFile afile2 = new AttachedFile();
        afile2.setContentType(aFileContentType);
        afile2.setFile(file2);

        AttachedFile afile3 = new AttachedFile();
        afile3.setContentType(aFileContentType);
        afile3.setFile(file3);

        MailRequester requester = MailUtil.getMailRequester();

        db.beginTransaction();

        FileInputStream fis1 = null;
        BufferedInputStream bis1 = null;
        FileInputStream fis2 = null;
        BufferedInputStream bis2 = null;
        FileInputStream fis3 = null;
        BufferedInputStream bis3 = null;
        try {

            FreeTextMailContext ctx = new FreeTextMailContext();
            ctx.setFrom(from);
            ctx.addTo(to1);
            ctx.addTo(to2);
            ctx.addTo(to3);
            ctx.addCc(cc1);
            ctx.setSubject(subject);
            ctx.setMailBody(mailBody);
            ctx.addAttachedFile(afile1);
            ctx.addAttachedFile(afile2);
            ctx.addAttachedFile(afile3);

            // 送信要求
            String mailRequestId = requester.requestToSend(ctx);
            db.commitTransaction();

            // メール送信要求管理テーブルの確認
            MailRequest mailRequest = VariousDbTestHelper.findById(MailRequest.class, mailRequestId);
            assertThat(mailRequest, is(notNullValue()));

            assertThat(mailRequest.subject, is(subject));
            assertThat(mailRequest.mailFrom, is(from));
            assertThat(mailRequest.replyTo, is(mailRequestConfig.getDefaultReplyTo()));
            assertThat(mailRequest.returnPath, is(mailRequestConfig.getDefaultReturnPath()));
            assertThat(mailRequest.charset, is(mailRequestConfig.getDefaultCharset()));
            assertThat(mailRequest.status, is(mailConfig.getStatusUnsent()));
            assertThat(mailRequest.mailBody, is(mailBody));

            List<MailRecipient> mailRecipientList = VariousDbTestHelper.findAll(MailRecipient.class, "mailRequestId",
                    "serialNumber");
            assertThat(mailRecipientList.size(), is(4));
            assertThat(mailRecipientList.get(0).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(0).serialNumber, is(1L));
            assertThat(mailRecipientList.get(0).recipientType, is(mailConfig.getRecipientTypeTO()));
            assertThat(mailRecipientList.get(0).mailAddress, is(to1));
            assertThat(mailRecipientList.get(1).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(1).serialNumber, is(2L));
            assertThat(mailRecipientList.get(1).recipientType, is(mailConfig.getRecipientTypeTO()));
            assertThat(mailRecipientList.get(1).mailAddress, is(to2));
            assertThat(mailRecipientList.get(2).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(2).serialNumber, is(3L));
            assertThat(mailRecipientList.get(2).recipientType, is(mailConfig.getRecipientTypeTO()));
            assertThat(mailRecipientList.get(2).mailAddress, is(to3));
            assertThat(mailRecipientList.get(3).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(3).serialNumber, is(4L));
            assertThat(mailRecipientList.get(3).recipientType, is(mailConfig.getRecipientTypeCC()));
            assertThat(mailRecipientList.get(3).mailAddress, is(cc1));

            List<MailAttachedFile> mailAttachedFileList = VariousDbTestHelper.findAll(MailAttachedFile.class);
            assertThat(mailAttachedFileList.size(), is(3));

            fis1 = new FileInputStream(file1);
            bis1 = new BufferedInputStream(fis1);
            ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
            int ch1;
            while ((ch1 = bis1.read()) != -1) {
                baos1.write(ch1);
            }
            byte[] fileBytes1 = baos1.toByteArray();
            assertThat(mailAttachedFileList.get(0).attachedFile, is(fileBytes1));

            fis2 = new FileInputStream(file2);
            bis2 = new BufferedInputStream(fis2);
            ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
            int ch2;
            while ((ch2 = bis2.read()) != -1) {
                baos2.write(ch2);
            }
            byte[] fileBytes2 = baos2.toByteArray();
            assertThat(mailAttachedFileList.get(1).attachedFile, is(fileBytes2));

            fis3 = new FileInputStream(file3);
            bis3 = new BufferedInputStream(fis3);
            ByteArrayOutputStream baos3 = new ByteArrayOutputStream();
            int ch3;
            while ((ch3 = bis3.read()) != -1) {
                baos3.write(ch3);
            }
            byte[] fileBytes3 = baos3.toByteArray();
            assertThat(mailAttachedFileList.get(2).attachedFile, is(fileBytes3));

        } finally {
            db.endTransaction();
            FileUtil.closeQuietly(fis1);
            FileUtil.closeQuietly(bis1);
            FileUtil.closeQuietly(fis2);
            FileUtil.closeQuietly(bis2);
            FileUtil.closeQuietly(fis3);
            FileUtil.closeQuietly(bis3);
        }

    }

    /**
     * 宛先数上限値超え（上限値100）
     *
     * @throws Exception
     */
    @Test
    public void testRecipientOver() throws Exception {

        MailRequester requester = MailUtil.getMailRequester();

        String subjectOK = "宛先数上限値超えテストOK";
        String subjectNG = "宛先数上限値超えテストNG";
        String from = "from@localhost";
        String cc = "cc@localhost";
        String bcc = "bcc@localhost";
        String mailBody = "本文";

        int adresseeToCountOK = mailRequestConfig.getMaxRecipientCount() - 2;
        int adresseeToCountNG = mailRequestConfig.getMaxRecipientCount() - 1;

        // 宛先上限数セーフ
        db.beginTransaction();
        try {
            FreeTextMailContext ctx = new FreeTextMailContext();
            ctx.setFrom(from);
            ctx.addCc(cc);
            ctx.addBcc(bcc);
            ctx.setSubject(subjectOK);
            ctx.setMailBody(mailBody);

            for (int i = 0; i < adresseeToCountOK; i++) {
                String to = "to" + i + "@localhost";
                ctx.addTo(to);
            }

            // 送信要求
            String mailRequestId = requester.requestToSend(ctx);
            db.commitTransaction();

            List<MailRecipient> mailRecipientList = VariousDbTestHelper.findAll(MailRecipient.class);
            assertEquals(mailRequestConfig.getMaxRecipientCount(), mailRecipientList.size());

        } catch (RecipientCountException e) {
            fail("例外発生しないはず");
        } finally {
            db.endTransaction();
        }

        // 宛先数上限値超え
        db.beginTransaction();
        try {
            FreeTextMailContext ctx = new FreeTextMailContext();
            ctx.setFrom(from);
            ctx.addCc(cc);
            ctx.addBcc(bcc);
            ctx.setSubject(subjectNG);
            ctx.setMailBody(mailBody);

            for (int i = 0; i < adresseeToCountNG; i++) {
                String to = "to" + i + "@localhost";
                ctx.addTo(to);
            }

            // 送信要求
            requester.requestToSend(ctx);
            db.commitTransaction();
            fail("例外が発生するはず");

        } catch (RecipientCountException e) {
            // success
            assertEquals(mailRequestConfig.getMaxRecipientCount(), e.getMaxRecipientCount());
            assertEquals(mailRequestConfig.getMaxRecipientCount() + 1, e.getActualRecipientCount());
            assertEquals("number of recipients was invalid. max = [50], actual = [51]", e.getMessage());
        } finally {
            db.endTransaction();
        }
    }

    /**
     * 宛先（TO,CC,BCC）がひとつもなし
     *
     * @throws Exception
     */
    @Test
    public void testRecipientZero() throws Exception {

        MailRequester requester = MailUtil.getMailRequester();

        String subject = "宛先なしテスト";
        String from = "from@localhost";
        String mailBody = "本文";

        db.beginTransaction();
        try {
            FreeTextMailContext ctx = new FreeTextMailContext();
            ctx.setFrom(from);
            ctx.setSubject(subject);
            ctx.setMailBody(mailBody);

            // 送信要求！
            requester.requestToSend(ctx);
            db.commitTransaction();
            fail("例外が発生するはず");

        } catch (RecipientCountException e) {
            // success
            assertEquals(mailRequestConfig.getMaxRecipientCount(), e.getMaxRecipientCount());
            assertEquals(0, e.getActualRecipientCount());
            assertEquals("number of recipients was invalid. max = [50], actual = [0]", e.getMessage());

        } finally {
            db.endTransaction();
        }
    }

    /**
     * 添付ファイルサイズオーバー時のテスト
     *
     * @throws Exception
     */
    @Test
    public void testAttachedFileSizeOver() throws Exception {

        MailRequester requester = MailUtil.getMailRequester();

        // データ
        String subjectOK = "添付ファイルサイズ上限値超えテストOK";
        String subjectNG = "添付ファイルサイズ上限値超えテストNG";
        String from = "from@localhost";
        String to = "to@localhost";
        String mailBody = "本文";
        String aFileContentType = "text/Plain";

        // ファイルの許容サイズをテスト用に修正
        mailRequestConfig.setMaxAttachedFileSize(100);

        // ファイルサイズぎりセーフ
        db.beginTransaction();
        try {
            AttachedFile afile = new AttachedFile();
            afile.setContentType(aFileContentType);
            File file = temporaryFolder.newFile();
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(new byte[mailRequestConfig.getMaxAttachedFileSize()]);
            fos.close();
            afile.setFile(file);

            FreeTextMailContext ctx = new FreeTextMailContext();
            ctx.setFrom(from);

            ctx.addTo(to);
            ctx.setSubject(subjectOK);
            ctx.setMailBody(mailBody);
            ctx.addAttachedFile(afile);

            // 送信要求！
            requester.requestToSend(ctx);
            db.commitTransaction();

        } catch (AttachedFileSizeOverException e) {
            fail("例外発生しないはず");

        } finally {
            db.endTransaction();
        }

        // ファイルサイズオーバー(1ファイルで)
        db.beginTransaction();
        try {
            AttachedFile aFile = new AttachedFile();
            aFile.setContentType(aFileContentType);
            File file = temporaryFolder.newFile();
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(new byte[mailRequestConfig.getMaxAttachedFileSize() + 1]);
            fos.close();
            aFile.setFile(file);

            FreeTextMailContext ctx = new FreeTextMailContext();
            ctx.setFrom(from);
            ctx.addTo(to);
            ctx.setSubject(subjectNG);
            ctx.setMailBody(mailBody);
            ctx.addAttachedFile(aFile);

            // 送信要求！
            requester.requestToSend(ctx);
            db.commitTransaction();
            fail("例外が発生するはず");

        } catch (AttachedFileSizeOverException e) {
            // success
            assertEquals(mailRequestConfig.getMaxAttachedFileSize(), e.getMaxFileSize());
            assertEquals(mailRequestConfig.getMaxAttachedFileSize() + 1, e.getActualFileSize());
            assertEquals("exceeded max attached file size. max = [100], actual = [101]", e.getMessage());

        } finally {
            db.endTransaction();
        }
        // ファイルサイズオーバー(複数ファイルで)
        db.beginTransaction();
        try {
            AttachedFile aFile1 = new AttachedFile();
            aFile1.setContentType(aFileContentType);
            File file1 = temporaryFolder.newFile();
            FileOutputStream fos1 = new FileOutputStream(file1);
            fos1.write(new byte[mailRequestConfig.getMaxAttachedFileSize() / 2]);
            fos1.close();
            aFile1.setFile(file1);

            AttachedFile aFile2 = new AttachedFile();
            aFile2.setContentType(aFileContentType);
            File file2 = temporaryFolder.newFile();
            FileOutputStream fos2 = new FileOutputStream(file2);
            fos2.write(new byte[mailRequestConfig.getMaxAttachedFileSize() / 2]);
            fos2.close();
            aFile2.setFile(file2);

            AttachedFile aFile3 = new AttachedFile();
            aFile3.setContentType(aFileContentType);
            File file3 = temporaryFolder.newFile();
            FileOutputStream fos3 = new FileOutputStream(file3);
            fos3.write(new byte[1]);
            fos3.close();
            aFile3.setFile(file3);

            FreeTextMailContext ctx = new FreeTextMailContext();
            ctx.setFrom(from);
            ctx.addTo(to);
            ctx.setSubject(subjectNG);
            ctx.setMailBody(mailBody);
            ctx.addAttachedFile(aFile1);
            ctx.addAttachedFile(aFile2);
            ctx.addAttachedFile(aFile3);

            // 送信要求！
            requester.requestToSend(ctx);
            db.commitTransaction();
            fail("例外が発生するはず");

        } catch (AttachedFileSizeOverException e) {
            // success
            assertEquals(mailRequestConfig.getMaxAttachedFileSize(), e.getMaxFileSize());
            assertEquals(mailRequestConfig.getMaxAttachedFileSize() + 1, e.getActualFileSize());
            assertEquals("exceeded max attached file size. max = [100], actual = [101]", e.getMessage());

        } finally {
            db.endTransaction();
        }
    }

    /**
     * 定型メール送信正常系
     *
     * @throws SQLException
     * @throws IOException
     */
    @Test
    public void testTemplateMailOK() throws SQLException, IOException {

        MailRequester requester = MailUtil.getMailRequester();

        db.beginTransaction();
        try {
            //データ準備
            TemplateMailContext tctx = new TemplateMailContext();
            tctx.setFrom(from);
            tctx.addTo(to1);
            tctx.addCc(cc1);
            tctx.addBcc(bcc1);
            tctx.setTemplateId(mailTemplateId);
            tctx.setLang(lang);

            tctx.setReplaceKeyValue("1", "申請書");
            tctx.setReplaceKeyValue("2", "00001");
            tctx.setReplaceKeyValue("3", "承認者");
            tctx.setReplaceKeyValue("option", null);

            //送信要求!
            String mailRequestId = requester.requestToSend(tctx);
            db.commitTransaction();

            // メール送信要求管理TBLの確認
            MailRequest mailRequest = VariousDbTestHelper.findById(MailRequest.class, mailRequestId);
            assertThat("要求テーブルのレコード", mailRequest, is(notNullValue()));

            assertThat("送信者", mailRequest.mailFrom, is(from));
            assertThat("返信先はデフォルト", mailRequest.replyTo, is(mailRequestConfig.getDefaultReplyTo()));
            assertThat("差し戻し先もデフォルト", mailRequest.returnPath, is(mailRequestConfig.getDefaultReturnPath()));
            assertThat("文字セット", mailRequest.charset, is(charset));
            assertThat("ステータス", mailRequest.status, is(mailConfig.getStatusUnsent()));

            String expectedSubject = "申請書について";
            String expectedMailBody = "申請書は、申請番号00001で申請されました。" + LINE_SEPARATOR
                    + "承認者は速やかに申請書を承認してください。";

            assertThat("件名", mailRequest.subject, is(expectedSubject));
            assertThat("本文", mailRequest.mailBody, is(expectedMailBody));

        } finally {
            db.endTransaction();
        }
    }

    /**
     * テンプレートIDと言語を指定しない場合
     */
    @Test
    public void testNoTemplateIdOrLang() {
        MailRequester requester = MailUtil.getMailRequester();

        //テンプレートIDを指定しない。
        db.beginTransaction();
        try {
            //データ準備
            TemplateMailContext tctx = new TemplateMailContext();
            tctx.setFrom(from);
            tctx.addTo(to1);
            tctx.addCc(cc1);
            tctx.addBcc(bcc1);
            //tctx.setTemplateId(mailTemplateId);
            tctx.setLang(lang);

            tctx.setReplaceKeyValue("1", "申請書");
            tctx.setReplaceKeyValue("2", "00001");
            tctx.setReplaceKeyValue("3", "承認者");

            //送信要求!
            requester.requestToSend(tctx);
            db.commitTransaction();
            fail();
        } catch (IllegalArgumentException e) {
            // success
            assertThat(e.getMessage(),
                    is("mail template was not found. mailTemplateId = [null], lang = [" + lang + "]"));

        } finally {
            db.endTransaction();
        }

        //言語を指定しない。
        db.beginTransaction();
        try {
            //データ準備
            TemplateMailContext tctx = new TemplateMailContext();
            tctx.setFrom(from);
            tctx.addTo(to1);
            tctx.addCc(cc1);
            tctx.addBcc(bcc1);

            tctx.setTemplateId(mailTemplateId);
            //tctx.setLang(lang);

            tctx.setReplaceKeyValue("1", "申請書");
            tctx.setReplaceKeyValue("2", "00001");
            tctx.setReplaceKeyValue("3", "承認者");

            //送信要求!
            requester.requestToSend(tctx);
            db.commitTransaction();
            fail();
        } catch (IllegalArgumentException e) {
            // success
            assertThat(e.getMessage(),
                    is("mail template was not found. mailTemplateId = [" + mailTemplateId + "], lang = [null]"));

        } finally {
            db.endTransaction();
        }

        //両方指定しない。
        db.beginTransaction();
        try {
            //データ準備
            TemplateMailContext tctx = new TemplateMailContext();
            tctx.setFrom(from);
            tctx.addTo(to1);
            tctx.addCc(cc1);
            tctx.addBcc(bcc1);

            //tctx.setTemplateId(mailTemplateId);
            //tctx.setLang(lang);

            tctx.setReplaceKeyValue("1", "申請書");
            tctx.setReplaceKeyValue("2", "00001");
            tctx.setReplaceKeyValue("3", "承認者");

            //送信要求!
            requester.requestToSend(tctx);
            db.commitTransaction();
            fail();
        } catch (IllegalArgumentException e) {
            // success
            assertThat(e.getMessage(), is("mail template was not found. mailTemplateId = [null], lang = [null]"));

        } finally {
            db.endTransaction();
        }

    }

    /**
     * 指定したIDと言語に紐付くテンプレートが存在しない場合
     */
    @Test
    public void testTemplateMailTemplateNotFound() {
        MailRequester requester = MailUtil.getMailRequester();

        String lang_en = "en";

        db.beginTransaction();
        try {
            // データ準備
            TemplateMailContext tctx = new TemplateMailContext();
            tctx.setFrom(from);
            tctx.addTo(to1);
            tctx.addCc(cc1);
            tctx.addBcc(bcc1);
            tctx.setTemplateId(mailTemplateId);
            tctx.setLang(lang_en);

            tctx.setReplaceKeyValue("1", "申請書");
            tctx.setReplaceKeyValue("2", "00001");
            tctx.setReplaceKeyValue("3", "承認者");

            // 送信要求!
            requester.requestToSend(tctx);
            db.commitTransaction();
            fail("例外が発生するはず");

        } catch (IllegalArgumentException e) {
            // success
            assertThat(e.getMessage(), is("mail template was not found. mailTemplateId = ["
                    + mailTemplateId + "], lang = [" + lang_en + "]"));

        } finally {
            db.endTransaction();
        }
    }

    @Test
    public void testSetNullToTemplateName_shouldThrowException() throws Exception {
        MailRequester requester = MailUtil.getMailRequester();

        db.beginTransaction();
        try {
            // データ準備
            TemplateMailContext tctx = new TemplateMailContext();
            tctx.setFrom(from);
            tctx.addTo(to1);
            tctx.setTemplateId(null);

            requester.requestToSend(tctx);
            fail();
        } catch (IllegalArgumentException e) {
            // success
            assertThat(e.getMessage(), is("mail template was not found. mailTemplateId = [null], lang = [null]"));

        } finally {
            db.endTransaction();
        }
    }

    @Test
    public void testSetNullToReplaceKye_shouldThrowException() throws Exception {
        MailRequester requester = MailUtil.getMailRequester();

        db.beginTransaction();
        try {
            // データ準備
            TemplateMailContext tctx = new TemplateMailContext();
            tctx.setFrom(from);
            tctx.addTo(to1);
            tctx.setTemplateId(mailTemplateId);
            tctx.setLang("ja");
            tctx.setReplaceKeyValue(null, "dummy");

            requester.requestToSend(tctx);
            fail();

        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("replace key must not be null"));
        } finally {
            db.endTransaction();
        }
    }

    /**
     * 設定ファイルで、{@link MailRequester}と{@link nablarch.common.idgenerator.TableIdGenerator}に
     * トランザクションマネージャを設定した時のテスト。
     * データ登録が正常に行なわれることを確認する。
     *
     */
    @Test
    public void testSpecifiedOtherTxOnConfigurationFile() throws Exception {
        SystemRepository.clear();
        SystemRepository.load(
                new DiContainer(
                        new XmlComponentDefinitionLoader(
                                "nablarch/common/mail/MailRequesterTestOtherTransaction.xml")));
        final SimpleDbTransactionManager db = SystemRepository.get("dbManager-default");
        MailRequester requester = MailUtil.getMailRequester();

        try {
            db.beginTransaction();
            FreeTextMailContext ctx = new FreeTextMailContext();
            ctx.setFrom(from);
            ctx.addCc(cc1);
            ctx.addCc(cc2);
            ctx.addCc(cc3);
            ctx.addBcc(bcc1);
            ctx.setReturnPath(returnPath);
            ctx.setReplyTo(replyTo);
            ctx.setSubject(subject);
            ctx.setMailBody(mailBody);
            ctx.setCharset(charset);

            // 送信要求
            String mailRequestId = requester.requestToSend(ctx);

            // メール送信要求管理テーブルの確認
            MailRequest mailRequest = VariousDbTestHelper.findById(MailRequest.class, mailRequestId);
            assertThat(mailRequest, is(notNullValue()));
            assertThat(mailRequest.subject, is(subject));
            assertThat(mailRequest.mailFrom, is(from));
            assertThat(mailRequest.replyTo, is(replyTo));
            assertThat(mailRequest.returnPath, is(returnPath));
            assertThat(mailRequest.charset, is(charset));
            assertThat(mailRequest.status, is(mailConfig.getStatusUnsent()));
            assertThat(mailRequest.mailBody, is(mailBody));

            List<MailRecipient> mailRecipientList = VariousDbTestHelper.findAll(MailRecipient.class, "mailRequestId",
                    "serialNumber");
            assertThat(mailRecipientList.size(), is(4));

            assertThat(mailRecipientList.get(0).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(0).serialNumber, is(1L));
            assertThat(mailRecipientList.get(0).recipientType, is(mailConfig.getRecipientTypeCC()));
            assertThat(mailRecipientList.get(0).mailAddress, is(cc1));
            assertThat(mailRecipientList.get(1).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(1).serialNumber, is(2L));
            assertThat(mailRecipientList.get(1).recipientType, is(mailConfig.getRecipientTypeCC()));
            assertThat(mailRecipientList.get(1).mailAddress, is(cc2));
            assertThat(mailRecipientList.get(2).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(2).serialNumber, is(3L));
            assertThat(mailRecipientList.get(2).recipientType, is(mailConfig.getRecipientTypeCC()));
            assertThat(mailRecipientList.get(2).mailAddress, is(cc3));
            assertThat(mailRecipientList.get(3).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(3).serialNumber, is(4L));
            assertThat(mailRecipientList.get(3).recipientType, is(mailConfig.getRecipientTypeBCC()));
            assertThat(mailRecipientList.get(3).mailAddress, is(bcc1));

            List<MailAttachedFile> mailAttachedFileList = VariousDbTestHelper.findAll(MailAttachedFile.class);
            assertThat(mailAttachedFileList.size(), is(0));
            db.commitTransaction();
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            db.endTransaction();
        }
    }
    /**
     * 設定ファイルで、{@link MailRequester}と{@link nablarch.common.idgenerator.TableIdGenerator}に
     * トランザクションマネージャを設定した時のテスト。
     * 業務トランザクション内で例外が発生しても、メール送信要求のトランザクションに影響を与えないことを確認する。
     *
     */
    @Test
    public void testSpecifiedOtherTxOnConfigurationFileError() throws Exception {
        SystemRepository.clear();
        SystemRepository.load(
                new DiContainer(
                        new XmlComponentDefinitionLoader(
                                "nablarch/common/mail/MailRequesterTestOtherTransactionError.xml")));
        final SimpleDbTransactionManager db = SystemRepository.get("dbManager-default");
        MailRequester requester = MailUtil.getMailRequester();
        String mailRequestId = null;
        try {
            db.beginTransaction();
            FreeTextMailContext ctx = new FreeTextMailContext();
            ctx.setFrom(from);
            ctx.addCc(cc1);
            ctx.addCc(cc2);
            ctx.addCc(cc3);
            ctx.addBcc(bcc1);
            ctx.setReturnPath(returnPath);
            ctx.setReplyTo(replyTo);
            ctx.setSubject(subject);
            ctx.setMailBody(mailBody);
            ctx.setCharset(charset);

            // 送信要求
            mailRequestId = requester.requestToSend(ctx);
            throw new RuntimeException("業務トランザクション内で例外発生");

        } catch (Exception ignore) {
            // 業務トランザクションで例外が発生してもメール送信要求のトランザクションに
            // 影響与えないことを確認する。
            // キャッチした例外は無視し、finallyの中でテーブル確認を行う。
        } finally {
            db.endTransaction();
            // メール送信要求管理テーブルの確認
            MailRequest mailRequest = VariousDbTestHelper.findById(MailRequest.class, mailRequestId);
            assertThat(mailRequest, is(notNullValue()));
            assertThat(mailRequest.subject, is(subject));
            assertThat(mailRequest.mailFrom, is(from));
            assertThat(mailRequest.replyTo, is(replyTo));
            assertThat(mailRequest.returnPath, is(returnPath));
            assertThat(mailRequest.charset, is(charset));
            assertThat(mailRequest.status, is(mailConfig.getStatusUnsent()));
            assertThat(mailRequest.mailBody, is(mailBody));

            List<MailRecipient> mailRecipientList = VariousDbTestHelper.findAll(MailRecipient.class, "mailRequestId",
                    "serialNumber");
            assertThat(mailRecipientList.size(), is(4));

            assertThat(mailRecipientList.get(0).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(0).serialNumber, is(1L));
            assertThat(mailRecipientList.get(0).recipientType, is(mailConfig.getRecipientTypeCC()));
            assertThat(mailRecipientList.get(0).mailAddress, is(cc1));
            assertThat(mailRecipientList.get(1).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(1).serialNumber, is(2L));
            assertThat(mailRecipientList.get(1).recipientType, is(mailConfig.getRecipientTypeCC()));
            assertThat(mailRecipientList.get(1).mailAddress, is(cc2));
            assertThat(mailRecipientList.get(2).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(2).serialNumber, is(3L));
            assertThat(mailRecipientList.get(2).recipientType, is(mailConfig.getRecipientTypeCC()));
            assertThat(mailRecipientList.get(2).mailAddress, is(cc3));
            assertThat(mailRecipientList.get(3).mailRequestId, is(mailRequestId));
            assertThat(mailRecipientList.get(3).serialNumber, is(4L));
            assertThat(mailRecipientList.get(3).recipientType, is(mailConfig.getRecipientTypeBCC()));
            assertThat(mailRecipientList.get(3).mailAddress, is(bcc1));

            List<MailAttachedFile> mailAttachedFileList = VariousDbTestHelper.findAll(MailAttachedFile.class);
            assertThat(mailAttachedFileList.size(), is(0));
        }
    }
}
