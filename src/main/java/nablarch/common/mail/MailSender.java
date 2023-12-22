package nablarch.common.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import nablarch.core.date.SystemTimeUtil;
import nablarch.core.db.statement.SqlRow;
import nablarch.core.log.Logger;
import nablarch.core.log.LoggerManager;
import nablarch.core.log.app.FailureLogUtil;
import nablarch.core.repository.SystemRepository;
import nablarch.core.util.annotation.Published;
import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Result;
import nablarch.fw.action.BatchAction;
import nablarch.fw.launcher.ProcessAbnormalEnd;
import nablarch.fw.reader.DatabaseRecordListener;
import nablarch.fw.reader.DatabaseRecordReader;
import nablarch.fw.results.TransactionAbnormalEnd;

/**
 * メール送信要求管理テーブル上の各レコードごとにメール送信を行うバッチアクション。
 *
 * @author Shinsuke Yoshio
 */
public class MailSender extends BatchAction<SqlRow> {

    /** システムリポジトリ用のキー定数 */
    private static final String SYSTEM_REPOSITORY_KEY_MAIL_CONFIG = "mailConfig";

    /** システムリポジトリ用のキー定数 */
    private static final String SYSTEM_REPOSITORY_KEY_MAIL_SESSION_CONFIG = "mailSessionConfig";

    /** システムリポジトリ用のキー定数 */
    private static final String SYSTEM_REPOSITORY_KEY_MAIL_ATTACHED_FILE_TABLE = "mailAttachedFileTable";

    /** システムリポジトリ用のキー定数 */
    private static final String SYSTEM_REPOSITORY_KEY_MAIL_RECIPIENT_TABLE = "mailRecipientTable";

    /** システムリポジトリ用のキー定数 */
    private static final String SYSTEM_REPOSITORY_KEY_MAIL_REQUEST_TABLE = "mailRequestTable";

    /** メール送信バッチを識別するプロセスID */
    private final String processId = UUID.randomUUID().toString();

    /** ロガー */
    private final Logger LOGGER = LoggerManager.get(MailSender.class);

    /**
     * コンストラクタ。
     */
    @Published(tag = "architect")
    public MailSender() {
        // Do nothing
    }

    /**
     * メール送信要求を元にメールを送信する。
     *
     * @param data 入力データ（メール送信要求のレコード）
     * @param context 実行コンテキスト
     * @return 処理結果
     */
    public Result handle(SqlRow data, ExecutionContext context) {

        // テーブルスキーマ情報の取得
        MailRequestTable mailRequestTable = SystemRepository.get(SYSTEM_REPOSITORY_KEY_MAIL_REQUEST_TABLE);
        MailRecipientTable mailRecipientTable = SystemRepository.get(SYSTEM_REPOSITORY_KEY_MAIL_RECIPIENT_TABLE);
        MailAttachedFileTable mailAttachedFileTable = SystemRepository.get(SYSTEM_REPOSITORY_KEY_MAIL_ATTACHED_FILE_TABLE);
        MailSessionConfig mailSenderConfig = SystemRepository.get(SYSTEM_REPOSITORY_KEY_MAIL_SESSION_CONFIG);

        MailRequestTable.MailRequest mailRequest = mailRequestTable.getMailRequest(data);

        String mailRequestId = mailRequest.getMailRequestId();

        // メールセッションの取得
        Session session = createMailSession(mailRequest.getReturnPath(), mailSenderConfig);

        MailConfig mailConfig = SystemRepository.get(SYSTEM_REPOSITORY_KEY_MAIL_CONFIG);
        try {
            // 2重送信防止のため、送信ステータスをはじめに送信済みに更新する。
            updateToSuccess(data, context);

            // 差し戻し先メールアドレスのチェック
            containsInvalidCharacter(mailRequest.getReturnPath(), mailRequestId);

            // メッセージの作成
            MimeMessage mimeMessage = createMimeMessage(data, mailRequestId, mailRequest, session, mailRecipientTable);

            // 添付ファイルの情報を取得
            List<? extends MailAttachedFileTable.MailAttachedFile> attachedFiles = mailAttachedFileTable.find(
                    mailRequestId);

            addBodyContent(mimeMessage, mailRequest, attachedFiles, context);

            // 設定の保存とメール送信
            mimeMessage.saveChanges();
            Transport.send(mimeMessage);
            writeLog(mailConfig.getSendSuccessMessageId(), mailRequestId);
        } catch (CreateMailFailedException e) {
            writeCreateMailFailedLog(data, mailRequest, mailConfig, e);
            updateToFailed(data, context);
            return createTransactionAbnormalEnd(mailRequest, mailConfig, e);
        } catch (SendFailedException e) {
            writeSendMailFailedLog(data, mailRequest, mailConfig, e);
            updateToFailed(data, context);
            return createTransactionAbnormalEnd(mailRequest, mailConfig, e);
        } catch (ProcessAbnormalEnd e) {
            updateToFailed(data, context);
            throw e;
        } catch (Exception e) {
            return handleException(data, context, mailRequest, mailConfig, e);
        }
        return new Result.Success();
    }

    /**
     * メール送信時の例外のハンドル処理を行う。
     * <p/>
     * 本クラスでは、障害ログを出力し、送信ステータスを送信失敗にしてリトライを行う。
     * 本メソッドでは、すべての例外をリトライ対象として{@link SendMailRetryableException}を送出している。
     * 独自の処理を実施したい場合は本メソッドをオーバーライドすることで行うことができる。
     *
     * @param data 入力データ（メール送信要求のレコード）
     * @param context 実行コンテキスト
     * @param mailRequest メール送信要求
     * @param mailConfig メール設定
     * @param e メール送信時の例外
     * @return {@link #handle(SqlRow, ExecutionContext)} が返す処理結果
     */
    @Published(tag = "architect")
    protected Result handleException(final SqlRow data, final ExecutionContext context,
            final MailRequestTable.MailRequest mailRequest, final MailConfig mailConfig, final Exception e) {
        FailureLogUtil.logError(e, data, mailConfig.getSendFailureCode(), mailRequest.getMailRequestId());
        updateToFailed(data, context);
        throw new SendMailRetryableException(
                String.format(
                        "Failed to send a mail, will be retried to send later. mailRequestId=[%s], error message=[%s]",
                        mailRequest.getMailRequestId(), e.getMessage()), e);
    }


    /**
     * メール送信失敗時の{@link SendFailedException}例外の障害検知ログに出力する。
     * <p/>
     * メール送信失敗時に、独自の処理を実施したい場合は本メソッドをオーバーライドすることで行うことができる。
     *
     * @param data 入力データ（メール送信要求のレコード）
     * @param mailRequest メール送信要求
     * @param mailConfig メール設定
     * @param e メール送信失敗時の{@link SendFailedException}例外
     */
    @Published(tag = "architect")
    protected void writeSendMailFailedLog(final SqlRow data, final MailRequestTable.MailRequest mailRequest,
            final MailConfig mailConfig, final SendFailedException e) {
        final String sentAddresses = createStringFromAddresses(e.getValidSentAddresses());
        final String unsentAddresses = createStringFromAddresses(e.getValidUnsentAddresses());
        final String invalidAddresses = createStringFromAddresses(e.getInvalidAddresses());
        final CreateMailFailedException ce = new CreateMailFailedException(
                String.format("Failed to send a mail. Error message:[%s] Mail RequestId:[%s] "
                                + "Sent address:[%s] Unsent address:[%s] Invalid address:[%s]",
                        e.getMessage(), mailRequest.getMailRequestId(),
                        sentAddresses, unsentAddresses, invalidAddresses), e);
        FailureLogUtil.logError(ce, data, mailConfig.getSendFailureCode(), mailRequest.getMailRequestId());
    }

    /**
     * メールアドレスの配列を文字列にする。
     *
     * @param addresses メールアドレスの配列
     * @return メールアドレスをカンマで連結した文字列
     */
    private static String createStringFromAddresses(final Address[] addresses) {
        if (addresses == null) {
            return "";
        }
        final StringBuilder result = new StringBuilder();
        for (Address address : addresses) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(address);
        }
        return result.toString();
    }

    /**
     * メール作成が失敗した場合に、障害検知ログに出力する。
     * <p/>
     * メール作成が失敗した時に、独自の処理を実施したい場合は本メソッドをオーバーライドすることで行うことができる。
     *
     * @param data 入力データ（メール送信要求のレコード）
     * @param mailRequest メール送信要求
     * @param mailConfig メール設定
     * @param e {@link MessagingException}
     */
    @Published(tag = "architect")
    protected void writeCreateMailFailedLog(final SqlRow data, final MailRequestTable.MailRequest mailRequest,
            final MailConfig mailConfig, final MessagingException e) {
        FailureLogUtil.logError(e, data, mailConfig.getSendFailureCode(), mailRequest.getMailRequestId());
    }

    /**
     * メール送信時に送信失敗を表す{@link MessagingException}が発生した場合のトランザクションの異常終了例外を生成して返す。
     *
     * @param mailRequest メール送信要求
     * @param mailConfig メール設定
     * @param e メール送信時のMessagingException
     * @return 業務トランザクションの異常終了例外
     */
    private TransactionAbnormalEnd createTransactionAbnormalEnd(final MailRequestTable.MailRequest mailRequest,
            final MailConfig mailConfig, final MessagingException e) {
        return new TransactionAbnormalEnd(
                mailConfig.getAbnormalEndExitCode(),
                e,
                mailConfig.getSendFailureCode(),
                mailRequest.getMailRequestId());
    }

    /**
     * メールデータを作成する。
     *
     * @param data 入力データ（メール送信要求のレコード）
     * @param mailRequestId メール送信要求ID
     * @param mailRequest メール送信先情報
     * @param session メールセッション
     * @param mailRecipientTable メール送信先管理テーブルのスキーマ
     * @return メールデータ
     * @throws MessagingException メールメッセージの生成に失敗した場合
     */
    @Published(tag = "architect")
    protected MimeMessage createMimeMessage(final SqlRow data, String mailRequestId,
            MailRequestTable.MailRequest mailRequest,
            Session session, MailRecipientTable mailRecipientTable) throws MessagingException {

        MailConfig mailConfig = SystemRepository.get(SYSTEM_REPOSITORY_KEY_MAIL_CONFIG);

        // エラーの発生したアドレス
        List<String> errorAddresses = new ArrayList<String>();
        // 各送信先の取得
        InternetAddress[] to = getAddresses(mailRequest, mailConfig.getRecipientTypeTO(), mailRecipientTable, errorAddresses);
        InternetAddress[] cc = getAddresses(mailRequest, mailConfig.getRecipientTypeCC(), mailRecipientTable,errorAddresses);
        InternetAddress[] bcc = getAddresses(mailRequest, mailConfig.getRecipientTypeBCC(), mailRecipientTable, errorAddresses);

        MimeMessage mimeMessage = new MimeMessage(session);
        // 宛先の設定
        mimeMessage.setRecipients(MimeMessage.RecipientType.TO, to);
        mimeMessage.setRecipients(MimeMessage.RecipientType.CC, cc);
        mimeMessage.setRecipients(MimeMessage.RecipientType.BCC, bcc);

        // 送信元の設定
        final InternetAddress from = createInternetAddress(mailRequest.getFrom(), mailRequest);
        if (from != null) {
            mimeMessage.setFrom(from);
        } else {
            errorAddresses.add(mailRequest.getFrom());
        }

        // 返信先の設定
        final InternetAddress replyTo = createInternetAddress(mailRequest.getReplyAddress(), mailRequest);
        if (replyTo != null) {
            mimeMessage.setReplyTo(new InternetAddress[] {replyTo});
        } else {
            errorAddresses.add(mailRequest.getReplyAddress());
        }

        // 不正なアドレスがあれば例外を送出
        if (!errorAddresses.isEmpty()) {
            StringBuilder addresses = new StringBuilder();
            for (String address : errorAddresses) {
                if (addresses.length() > 0) {
                    addresses.append(", ");
                }
                addresses.append(address);
            }
            throw new CreateMailFailedException(
                    String.format("Invalid mail addresses found. mailRequestId=[%s] mail addresses=[%s]",
                            mailRequestId, addresses.toString()));
        }

        // 件名のチェック
        containsInvalidCharacter(mailRequest.getSubject(), mailRequestId);

        // 件名の設定
        mimeMessage.setSubject(mailRequest.getSubject(), mailRequest.getCharset());

        // 送信日時の設定
        mimeMessage.setSentDate(SystemTimeUtil.getDate());

        return mimeMessage;
    }

    /**
     * 指定された{@link MimeMessage}にメールメッセージ本文（添付ファイル含む）を追加する。
     * <p/>
     * メッセージ本文を暗号化する場合や、電子署名を付加する場合には本メソッドをオーバライドし処理を本文の追加処理を変更すること。
     *
     * @param mimeMessage {@link MimeMessage}
     * @param mailRequest メール送信要求管理の情報
     * @param attachedFiles 添付ファイルの情報
     * @param context 実行コンテキスト
     * @throws MessagingException メールメッセージの生成に失敗した場合
     */
    @Published(tag = "architect")
    protected void addBodyContent(MimeMessage mimeMessage, MailRequestTable.MailRequest mailRequest,
            List<? extends MailAttachedFileTable.MailAttachedFile> attachedFiles,
            ExecutionContext context) throws MessagingException {

        String mailBody = mailRequest.getMailBody();
        String charset = mailRequest.getCharset();

        if (attachedFiles.isEmpty()) {
            mimeMessage.setText(mailBody, charset);
        } else {
            // 添付ファイルがある場合はマルチパートにする。
            MimeMultipart multipart = new MimeMultipart();

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(mailBody, charset);
            multipart.addBodyPart(textPart);

            for (MailAttachedFileTable.MailAttachedFile attachedFile : attachedFiles) {
                DataSource dataSource = new ByteArrayDataSource(attachedFile.getFile(),
                        attachedFile.getContextType());

                DataHandler dataHandler = new DataHandler(dataSource);

                MimeBodyPart filePart = new MimeBodyPart();
                filePart.setDataHandler(dataHandler);
                filePart.setFileName(attachedFile.getFileName());

                multipart.addBodyPart(filePart);
                mimeMessage.setContent(multipart, attachedFile.getContextType());
            }
        }
    }

    /**
     * java.mail.Sessionオブジェクトを取得する。<br />
     * メールヘッダのReturn-Pathに設定される mail.smtp.from のみ引数として指定する。<br />
     * それ以外は、設定ファイルから読み込む。
     *
     * @param returnPath 差し戻し先メールアドレス。
     * @param mailSenderConfig メール送信用設定値
     * @return メールセッション
     */
    private Session createMailSession(String returnPath,
            MailSessionConfig mailSenderConfig) {
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.host",
                mailSenderConfig.getMailSmtpHost());
        properties.setProperty("mail.host", mailSenderConfig.getMailHost());
        properties.setProperty("mail.smtp.port",
                mailSenderConfig.getMailSmtpPort());
        properties.setProperty("mail.smtp.connectiontimeout",
                mailSenderConfig.getMailSmtpConnectionTimeout());
        properties.setProperty("mail.smtp.timeout",
                mailSenderConfig.getMailSmtpTimeout());
        properties.setProperty("mail.smtp.from", returnPath);

        properties.putAll(mailSenderConfig.getOption());
        return Session.getInstance(properties);

    }

    /**
     * 指定したメール送信要求IDと送信先区分に紐付くメールアドレスの配列を取得する。
     *
     * @param mailRequest メール送信要求
     * @param recipientType 宛先区分
     * @param mailRecipientTable 宛先メールアドレスの配列
     * @param errorAddresses 生成に失敗したアドレスのリスト
     * @return メールアドレスの配列
     */
    private InternetAddress[] getAddresses(MailRequestTable.MailRequest mailRequest,
            String recipientType, MailRecipientTable mailRecipientTable, List<String> errorAddresses) {

        List<? extends MailRecipientTable.MailRecipient> mailRecipients = mailRecipientTable.find(mailRequest.getMailRequestId(),
                recipientType);

        List<InternetAddress> mailAddresses = new ArrayList<InternetAddress>();
        for (MailRecipientTable.MailRecipient mailRecipient : mailRecipients) {
            final String address = mailRecipient.getMailAddress();
            final InternetAddress mailAddress = createInternetAddress(address, mailRequest);
            if (mailAddress != null) {
                mailAddresses.add(mailAddress);
            } else {
                errorAddresses.add(address);
            }
        }

        return mailAddresses.toArray(new InternetAddress[mailAddresses.size()]);
    }

    /**
     * メールアドレスを生成する。{@link AddressException}が発生した場合は、ログを出力しnullを返す。
     *
     * @param address メールアドレスの元となる文字列
     * @param mailRequest メール送信要求
     * @return 生成したメールアドレス
     */
    private InternetAddress createInternetAddress(final String address,
            final MailRequestTable.MailRequest mailRequest) {
        try {
            return new InternetAddress(address);
        } catch (AddressException e) {
            LOGGER.logWarn(String.format(
                    "Failed to instantiate mail address. mailRequestId=[%s] mail address=[%s] error message=[%s]",
                    mailRequest.getMailRequestId(), address, e.getMessage()), e);
            return null;
        }
    }

    /**
     * メールヘッダ・インジェクションチェック<br />
     * チェック対象文字列に\rもしくは\nを含んでいるかのチェック。
     * <p/>
     * チェック内容を変更する場合や、チェック結果の振る舞いを変更する場合には本メソッドをオーバライドし処理をチェック処理を変更すること。
     *
     * @param target チェック対象文字列
     * @param mailRequestId メール送信要求ID
     * @throws InvalidCharacterException チェック対象文字列に\rもしくは\nを含んでいた場合
     */
    @Published(tag = "architect")
    protected void containsInvalidCharacter(String target, String mailRequestId) throws InvalidCharacterException {
        if (target.contains("\r") || target.contains("\n")) {
            throw new InvalidCharacterException(String.format("contains invalid character. mailRequestId = %s, target = %s",
                    mailRequestId, target));
        }
    }

    /** {@inheritDoc} メール送信要求を読み込む{@link DatabaseRecordReader}を生成する。 */
    @Override
    @Published(tag = "architect")
    public DataReader<SqlRow> createReader(ExecutionContext ctx) {

        MailRequestTable mailRequestTable = SystemRepository.get(SYSTEM_REPOSITORY_KEY_MAIL_REQUEST_TABLE);
        MailConfig mailConfig = SystemRepository.get(SYSTEM_REPOSITORY_KEY_MAIL_CONFIG);

        final String mailSendPatternId = ctx.getSessionScopedVar("mailSendPatternId");

        int unsentRecordCount = mailRequestTable.getTargetCount(mailSendPatternId);

        writeLog(mailConfig.getMailRequestCountMessageId(), unsentRecordCount);

        DatabaseRecordReader reader = new DatabaseRecordReader();
        reader.setStatement(mailRequestTable.createReaderStatement(mailSendPatternId, processId));

        reader.setListener(new DatabaseRecordListener() {
            @Override
            public void beforeReadRecords() {
                final MailRequestTable mailRequestTable = SystemRepository.get(SYSTEM_REPOSITORY_KEY_MAIL_REQUEST_TABLE);
                mailRequestTable.updateSendProcessId(mailSendPatternId, processId);
            }
        });
        return reader;
    }

    /**
     * 処理ステータスを異常終了に更新する。
     * <p/>
     * 更新時に例外が発生した場合は、{@link ProcessAbnormalEnd}を送出する。
     *
     * @param data 送信対象データ
     * @param context 実行コンテキスト
     */
    @Published(tag = "architect")
    protected void updateToFailed(final SqlRow data, final ExecutionContext context) {
        final MailRequestTable mailRequestTable = SystemRepository.get(SYSTEM_REPOSITORY_KEY_MAIL_REQUEST_TABLE);
        final MailRequestTable.MailRequest mailRequest = mailRequestTable.getMailRequest(data);
        final MailConfig mailConfig = SystemRepository.get(SYSTEM_REPOSITORY_KEY_MAIL_CONFIG);
        try {
            mailRequestTable.updateFailureStatus(mailRequest.getMailRequestId(), mailConfig.getStatusFailure());
        } catch (RuntimeException re) {
            throw new ProcessAbnormalEnd(
                    mailConfig.getAbnormalEndExitCode(),
                    new SendStatusUpdateFailureException(
                            String.format("Failed to update unsent status. Need to apply a patch to change "
                                            + "the status to failure. mailRequestId=[%s]",
                                    mailRequest.getMailRequestId()), re),
                    mailConfig.getSendFailureCode(),
                    mailRequest.getMailRequestId());
        }
    }

    /**
     * 処理ステータスを正常終了に更新する。
     *
     * @param data 送信対象データ
     * @param context 実行コンテキスト
     */
    @Published(tag = "architect")
    protected void updateToSuccess(final SqlRow data, final ExecutionContext context) {
        final MailRequestTable mailRequestTable = SystemRepository.get(SYSTEM_REPOSITORY_KEY_MAIL_REQUEST_TABLE);
        final MailRequestTable.MailRequest mailRequest = mailRequestTable.getMailRequest(data);
        final MailConfig mailConfig = SystemRepository.get(SYSTEM_REPOSITORY_KEY_MAIL_CONFIG);

        mailRequestTable.updateStatus(mailRequest.getMailRequestId(), mailConfig.getStatusSent());
    }
}
