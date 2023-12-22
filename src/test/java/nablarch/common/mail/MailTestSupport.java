package nablarch.common.mail;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.mail.Authenticator;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Store;

import nablarch.test.support.log.app.OnMemoryLogWriter;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.results.TransactionAbnormalEnd;
import nablarch.test.support.db.helper.VariousDbTestHelper;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;


public class MailTestSupport {

    /** プロパティ */
    protected static Properties sessionProperties = new Properties();

    /** メール送信用データ */
    protected static String subject = "正常系テスト";

    protected static String from = "from@localhost";

    protected static String to1 = "to1@localhost";

    protected static String to2 = "to2@localhost";

    protected static String to3 = "to3@localhost";

    protected static String cc1 = "cc1@localhost";

    protected static String cc2 = "cc2@localhost";

    protected static String cc3 = "cc3@localhost";

    protected static String bcc1 = "bcc1@localhost";

    protected static String bcc2 = "bcc2@localhost";

    protected static String bcc3 = "bcc3@localhost";

    protected static String returnPath = "return@localhost";

    protected static String replyTo = "reply@localhost";

    protected static String mailBody = "本文";

    protected static String charset = "UTF-8";

    protected static String aFileContentType = "text/plain";

    /** メール受信待ち時間 */
    private long waitTime = 300;

    @BeforeClass
    public static void beforeClass() {
        VariousDbTestHelper.createTable(MailRequest.class);
        VariousDbTestHelper.createTable(MailRequestPattern.class);
        VariousDbTestHelper.createTable(MailRecipient.class);
        VariousDbTestHelper.createTable(MailAttachedFile.class);
        VariousDbTestHelper.createTable(MailTemplate.class);
        VariousDbTestHelper.createTable(MailSbnTable.class);
        VariousDbTestHelper.createTable(MailTestMessage.class);
        VariousDbTestHelper.createTable(MailBatchRequest.class);
        VariousDbTestHelper.createTable(MailRequestMultiProcess.class);
        VariousDbTestHelper.createTable(MailRequestPatternMultiProcess.class);

        sessionProperties.setProperty("mail.smtp.host", "localhost");
        sessionProperties.setProperty("mail.host", "localhost");
        sessionProperties.setProperty("mail.pop3.host", "localhost");
        sessionProperties.setProperty("mail.pop3.port", "10110");
    }

    @Before
    public void before() throws Exception {
        // テーブルを空にする
        VariousDbTestHelper.delete(MailRequest.class);
        VariousDbTestHelper.delete(MailRequestPattern.class);
        VariousDbTestHelper.delete(MailRecipient.class);
        VariousDbTestHelper.delete(MailAttachedFile.class);
        VariousDbTestHelper.delete(MailTemplate.class);
        VariousDbTestHelper.delete(MailSbnTable.class);
        VariousDbTestHelper.delete(MailRequestMultiProcess.class);
        VariousDbTestHelper.delete(MailRequestPatternMultiProcess.class);

        VariousDbTestHelper.setUpTable(new MailSbnTable("99", 0L));

        // 各アカウントのメールボックスを空にする
        String[] accounts = {"from", "to1", "to2", "to3", "cc1", "cc2", "cc3", "bcc1", "bcc2",
                "bcc3", "return", "reply"};
        for (int i = 0; i < accounts.length; i++) {
            cleanupMail(accounts[i]);
        }

        // ログのクリア
        OnMemoryLogWriter.clear();
    }

    public void assertError(Exception catched, String mailReqestId, int abnormalEndExitCode) {
        assertThat("エラーの型", catched, instanceOf(TransactionAbnormalEnd.class));
        assertThat("終了コード", ((TransactionAbnormalEnd) catched).getStatusCode(), is(abnormalEndExitCode));
        assertThat("メッセージ", catched.getMessage(), is("メール送信に失敗しました。 mailRequestId=[" + mailReqestId + "]"));
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

    public void assertAttachedFile(Part part, File file) throws IOException, MessagingException {
        InputStream pis = part.getInputStream();
        byte[] pBytes = convertToByteArray(pis);
        byte[] fBytes = convertToByteArray(file);

        Assert.assertArrayEquals("ファイルのバイト配列が同一であること", pBytes, fBytes);
    }

    public void assertLog(String message) {
        List<String> log = OnMemoryLogWriter.getMessages("writer.memory");
        System.out.println("log = " + log);
        boolean writeLog = false;
        for (String logMessage : log) {
            String str = logMessage.replaceAll("\\r|\\n", "");
            if (str.indexOf(message) >= 0) {
                writeLog = true;
            }
        }
        assertThat("ログが出力されていること", writeLog, is(true));
    }

    public void assertLogWithCount(String categoryName, String messagePattern, int expectedCount)
    {
        List<String> log = OnMemoryLogWriter.getMessages(categoryName);
        System.out.println("log = " + log);
        String msg = messagePattern.replaceAll("\\r|\\n", "");
        Pattern pattern = Pattern.compile(msg);
        int logCount = 0;
        for (String logMessage : log) {
            String str = logMessage.replaceAll("\\r|\\n", "");
            Matcher matcher = pattern.matcher(str);
            if (matcher.matches()) {
                logCount++;
            }
        }
        assertThat( String.format("障害通知ログが指定回数だけ出力されていること messagePattern=[%s]",messagePattern), logCount, is(expectedCount));
    }

    public String createMessagePattern(final String... args) {
        StringBuilder sb = new StringBuilder();
        sb.append(".*");
        for (String arg: args) {
            sb.append(Pattern.quote(arg));
            sb.append(".*");
        }
        return sb.toString();
    }

    public byte[] convertToByteArray(InputStream is) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(is);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int ch;
        while ((ch = bis.read()) != -1) {
            baos.write(ch);
        }
        bis.close();
        baos.close();
        return baos.toByteArray();
    }

    public byte[] convertToByteArray(File file) throws IOException {
        return convertToByteArray(new FileInputStream(file));
    }

    public Folder openFolder(Store store) throws Exception {
        Folder folder = null;
        for (int i = 0; i < 200; i++) {
            // ちょっと待つ...
            Thread.sleep(waitTime);
            folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);
            if (folder.getMessageCount() > 0) {
                break;
            }
            folder.close(true);
        }
        return folder;
    }

    public Folder openFolder(Store store, String expectedSubject) throws Exception {
        Folder folder = null;
        for (int i = 0; i < 200; i++) {
            // ちょっと待つ...
            Thread.sleep(waitTime);
            store.close();
            store.connect();
            folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);
            if (folder.getMessageCount() > 0) {
                Message[] messages = folder.getMessages();
                for (Message message : messages) {
                    if (expectedSubject.equals(message.getSubject())) {
                        return folder;
                    }
                }
            }
            folder.close(true);
        }
        return folder;
    }

    private void cleanupMail(final String account) throws Exception {
        Session session = Session.getInstance(sessionProperties, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(account, "default");
            }
        });

        Store store = session.getStore("pop3");
        store.connect();
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);
        Message[] messages = folder.getMessages();

        System.out.println("account " + account + ": " + messages.length
                + " messages will be deleted.");

        for (int i = 0; i < messages.length; i++) {
            messages[i].setFlag(Flags.Flag.DELETED, true);
        }
        folder.close(true);
        store.close();
    }
}

