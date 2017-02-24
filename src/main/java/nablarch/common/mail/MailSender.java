package nablarch.common.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import nablarch.core.db.statement.SqlRow;
import nablarch.core.log.Logger;
import nablarch.core.log.LoggerManager;
import nablarch.core.repository.SystemRepository;
import nablarch.core.util.annotation.Published;
import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Result;
import nablarch.fw.action.BatchAction;
import nablarch.fw.handler.retry.RetryableException;
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

    /** メール送信バッチを識別するプロセスID */
    private final String processId = UUID.randomUUID().toString();

    /** ロガー */
    private static final Logger LOGGER = LoggerManager.get(MailSender.class);

    /**
     * コンストラクタ。
     */
    @Published(tag = "architect")
    public MailSender() {
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
        MailRequestTable mailRequestTable = SystemRepository.get("mailRequestTable");
        MailRecipientTable mailRecipientTable = SystemRepository.get("mailRecipientTable");
        MailAttachedFileTable mailAttachedFileTable = SystemRepository.get("mailAttachedFileTable");
        MailSessionConfig mailSenderConfig = SystemRepository.get("mailSessionConfig");

        MailRequestTable.MailRequest mailRequest = mailRequestTable.getMailRequest(data);

        String mailRequestId = mailRequest.getMailRequestId();

        // メールセッションの取得
        Session session = createMailSession(mailRequest.getReturnPath(), mailSenderConfig);

        final MailConfig mailConfig = SystemRepository.get("mailConfig");
        try {
            // 事前にステータスを送信済みに更新する。
            updateToSuccess(data, context);

            // 差し戻し先メールアドレスのチェック
            containsInvalidCharacter(mailRequest.getReturnPath(), mailRequestId);

            // メッセージの作成
            MimeMessage mimeMessage = createMimeMessage(mailRequestId, mailRequest, session, mailRecipientTable);

            // 添付ファイルの情報を取得
            List<? extends MailAttachedFileTable.MailAttachedFile> attachedFiles = mailAttachedFileTable.find(
                    mailRequestId);

            addBodyContent(mimeMessage, mailRequest, attachedFiles, context);

            // 設定の保存とメール送信
            mimeMessage.saveChanges();
            Transport.send(mimeMessage);
            writeLog(mailConfig.getSendSuccessMessageId(), mailRequestId);
        } catch(AddressException e) {
            // 必須アドレスの変換例外による送信失敗なので、トランザクションの異常終了とする。
            return createTransactionAbnormalEnd(data, context, mailRequest, mailConfig, e);
        } catch(InvalidCharacterException e) {
            // フィールドの文字列不正例外による送信失敗なので、トランザクションの異常終了とする。
            return createTransactionAbnormalEnd(data, context, mailRequest, mailConfig, e);
        } catch(SendFailedException e) {
            // 送信エラーの場合は、アドレス等の詳細をERRORレベルのログに出力する。
            writeSendFailedDetailsLog(mailRequest, e);
            // 送信エラーによる送信失敗なので、トランザクションの異常終了とする。
            return createTransactionAbnormalEnd(data, context, mailRequest, mailConfig, e);
        }
        catch (MessagingException e) {
            try {
                // リトライ可能例外かチェック
                checkAndThrowRetryableException(data, context, mailRequest, e);
            } catch (RetryableException re) {
                try {
                    // 送信ステータスを未送信に戻す。
                    updateToUnsent(data, context);
                } catch (RuntimeException updateException) {
                    throw new StatusUpdateFailedException(
                            "failed to update unsent status. "
                                    + "need to apply a patch to change the status to unsent. "
                                    + "target data=[mailRequestId = " + mailRequest.getMailRequestId() + ']',
                            updateException);
                }
                throw re;
            }
            // 送信失敗以外のMessagingExceptionで、リトライ不可のものは、プロセスの異常終了とする。
            return createProcessAbnormalEnd(data, context, mailRequest, mailConfig, e);
        }
        return new Result.Success();
    }

    /**
     * メール送信失敗時の{@link SendFailedException}例外を補足した場合に、詳細メッセージをログに出力する。
     * <p/>
     * メール送信失敗時に、出力するログの記述やデータベースへの書き込み等、独自の処理を実施したい場合は
     * 本メソッドをオーバーライドすることで行うことができる。
     * @param mailRequest メール要求
     * @param e メール送信失敗時の{@link SendFailedException}例外
     */
    @Published(tag = "architect")
    protected void writeSendFailedDetailsLog(final MailRequestTable.MailRequest mailRequest,
            final SendFailedException e) {
        final String sentAddresses = addressesToString(e.getValidSentAddresses());
        final String unsentAddresses = addressesToString(e.getValidUnsentAddresses());
        final String invalidAddresses = addressesToString(e.getInvalidAddresses());
        LOGGER.logError(
                String.format("Failed to send a mail. Error message:[%s] Mail RequestId:[%s] "
                                + "Subject:[%s] From:[%s] "
                                + "Sent address:[%s] Unsent address:[%s] Invalid address:[%s]",
                        e.getMessage(), mailRequest.getMailRequestId(),
                        mailRequest.getSubject(), mailRequest.getFrom(),
                        sentAddresses, unsentAddresses, invalidAddresses));
    }

    /**
     * メール送信時に送信失敗を表す{@link MessagingException}が発生した場合のトランザクションの異常終了状態を生成して返す。
     * @param data 入力データ（メール送信要求のレコード）
     * @param context 実行コンテキスト
     * @param mailRequest メール送信要求
     * @param mailConfig メール設定
     * @param e メール送信時のMessagingException
     * @return 業務トランザクションの異常終了例外
     */
    private Result createTransactionAbnormalEnd(final SqlRow data, final ExecutionContext context,
            final MailRequestTable.MailRequest mailRequest, final MailConfig mailConfig, final MessagingException e) {
        final TransactionAbnormalEnd transactionAbnormalEnd = new TransactionAbnormalEnd(
                mailConfig.getAbnormalEndExitCode(),
                e,
                mailConfig.getSendFailureCode(),
                mailRequest.getMailRequestId());
        LOGGER.logError(transactionAbnormalEnd.getMessage(), e);
        writeErrorLog(data, mailConfig.getSendFailureCode(), mailRequest.getMailRequestId());

        // 送信ステータスを送信失敗にする。
        try {
            updateToFailed(data, context);
        } catch (RuntimeException updateException) {
            throw new StatusUpdateFailedException(
                    "failed to update unsent status. "
                            + "need to apply a patch to change the status to unsent. "
                            + "target data=[mailRequestId = " + mailRequest.getMailRequestId() + ']', updateException);
        }
        return transactionAbnormalEnd;
    }

    /**
     * メール送信時に{@link MessagingException}が発生した場合のプロセスの異常終了状態を生成して返す。
     * @param data 入力データ（メール送信要求のレコード）
     * @param context 実行コンテキスト
     * @param mailRequest メール送信要求
     * @param mailConfig メール設定
     * @param e メール送信時のMessagingException
     * @return アプリケーションの異常終了例外
     */
    private Result createProcessAbnormalEnd(final SqlRow data, final ExecutionContext context,
            final MailRequestTable.MailRequest mailRequest, final MailConfig mailConfig, final MessagingException e) {
        final ProcessAbnormalEnd processAbnormalEnd = new ProcessAbnormalEnd(
                mailConfig.getAbnormalEndExitCode(),
                e,
                mailConfig.getSendFailureCode(),
                mailRequest.getMailRequestId());
        LOGGER.logError(processAbnormalEnd.getMessage(), e);
        writeErrorLog(data, mailConfig.getSendFailureCode(), mailRequest.getMailRequestId());

        // 送信ステータスを送信失敗にする。
        try {
            updateToFailed(data, context);
        } catch (RuntimeException updateException) {
            throw new StatusUpdateFailedException(
                    "failed to update unsent status. "
                            + "need to apply a patch to change the status to unsent. "
                            + "target data=[mailRequestId = " + mailRequest.getMailRequestId() + ']', updateException);
        }
        return processAbnormalEnd;
    }

    /**
     * メール送信時の{@link MessagingException}例外から、リトライ可能かどうかを判断し、{@link RetryableException}を送出する。
     * 本メソッドでRetryableExceptionを送出した場合は、メール送信要求は、「未送信」の状態に設定される。
     * 本メソッドでRetryableExceptionを送出しなかった場合は、このハンドラの{@link #handle(SqlRow, ExecutionContext)}は{@link ProcessAbnormalEnd}を返す。
     *
     * <p/>
     * 本クラスでは、すべてのMessagingExceptionをRetryableExceptionとして返すが、派生クラスでオーバーライドすることにより
     * 例外の種別や元例外等の情報を元にリトライするかどうかのハンドルを行うことができる。
     * @param data 入力データ（メール送信要求のレコード）
     * @param context 実行コンテキスト
     * @param mailRequest メール送信要求
     * @param e メール送信時のMessagingException
     * @throws RetryableException リトライを行う場合、送出する。
     */
    @Published(tag = "architect")
    protected void checkAndThrowRetryableException(final SqlRow data, final ExecutionContext context,
            final MailRequestTable.MailRequest mailRequest, final MessagingException e) throws RetryableException{
        //原因が接続エラーの場合は、リトライ可能と判断し、RetryableExceptionを送出する。
        throw new RetryableException(
                String.format(
                        "Failed to send a mail, will be retried to send later. Mail RequestId:[%s], Error message:[%s]",
                        mailRequest.getMailRequestId(), e.getMessage()), e);
    }

    /**
     * メールアドレスの配列を文字列にする。
     * @param addresses メールアドレスの配列
     * @return メールアドレスをカンマで連結した文字列
     */
    private static String addressesToString(final Address[] addresses) {
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
     * メールデータを作成する。
     *
     * @param mailRequestId メール送信要求ID
     * @param mailRequest メール送信先情報
     * @param session メールセッション
     * @param mailRecipientTable メール送信先管理テーブルのスキーマ
     * @return メールデータ
     * @throws MessagingException メールメッセージの生成に失敗した場合
     */
    @Published(tag = "architect")
    protected MimeMessage createMimeMessage(String mailRequestId, MailRequestTable.MailRequest mailRequest,
            Session session, MailRecipientTable mailRecipientTable) throws MessagingException {

        MailConfig mailConfig = SystemRepository.get("mailConfig");

        // 各送信先の取得
        InternetAddress[] to = getAddresses(mailRequestId, mailConfig.getRecipientTypeTO(), mailRecipientTable);
        InternetAddress[] cc = getAddresses(mailRequestId, mailConfig.getRecipientTypeCC(), mailRecipientTable);
        InternetAddress[] bcc = getAddresses(mailRequestId, mailConfig.getRecipientTypeBCC(), mailRecipientTable);

        MimeMessage mimeMessage = new MimeMessage(session);
        // 宛先の設定
        mimeMessage.setRecipients(MimeMessage.RecipientType.TO, to);
        mimeMessage.setRecipients(MimeMessage.RecipientType.CC, cc);
        mimeMessage.setRecipients(MimeMessage.RecipientType.BCC, bcc);

        // 送信元の設定 from は必須であるため例外は無視しない。
        mimeMessage.setFrom(createInternetAddress(mailRequest.getFrom()));

        // 返信先の設定
        try {
            InternetAddress[] replyTo = new InternetAddress[1];
            replyTo[0] = createInternetAddress(mailRequest.getReplyAddress());
            mimeMessage.setReplyTo(replyTo);
        } catch (AddressException ignore) {
            // 返信先のアドレス変換失敗は、ログ出力のみ行い、処理は継続する。
        }

        // 件名のチェック
        containsInvalidCharacter(mailRequest.getSubject(), mailRequestId);

        // 件名の設定
        mimeMessage.setSubject(mailRequest.getSubject(), mailRequest.getCharset());

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
     * @param mailRequestId メール送信要求ID
     * @param recipientType 宛先区分
     * @param mailRecipientTable 宛先メールアドレスの配列
     * @return メールアドレスの配列
     */
    private static InternetAddress[] getAddresses(String mailRequestId,
            String recipientType, MailRecipientTable mailRecipientTable) {

        List<? extends MailRecipientTable.MailRecipient> mailRecipients = mailRecipientTable.find(mailRequestId,
                recipientType);

        List<InternetAddress> mailAddresses = new ArrayList<InternetAddress>();
        for (MailRecipientTable.MailRecipient mailRecipient : mailRecipients) {
            final String address = mailRecipient.getMailAddress();
            try {
                final InternetAddress mailAddress = createInternetAddress(address);
                mailAddresses.add(mailAddress);
            } catch (AddressException ignore) {
                // 送信先のアドレス変換失敗は、ログ出力のみ行い、処理は継続する。
            }
        }

        return mailAddresses.toArray(new InternetAddress[mailAddresses.size()]);
    }

    /**
     * メールアドレスを生成する。{@link AddressException}が発生した場合は、ERRORログに出力し、AddressExceptionを送出する。
     * @param address メールアドレスの元となる文字列
     * @return 生成したメールアドレス
     * @throws AddressException メールアドレスの生成に失敗した場合
     */
    private static InternetAddress createInternetAddress(final String address) throws AddressException {
        try {
            return new InternetAddress(address);
        } catch (AddressException e) {
            LOGGER.logError(String.format("Failed to instantiate mail address. Error message:[%s] Mail address:[%s]",
                            e.getMessage(), address), e);
            throw e;
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

        MailRequestTable mailRequestTable = SystemRepository.get("mailRequestTable");
        MailConfig mailConfig = SystemRepository.get("mailConfig");

        String mailSendPatternId = ctx.getSessionScopedVar("mailSendPatternId");

        int unsentRecordCount = mailRequestTable.getTargetCount(mailSendPatternId);

        writeLog(mailConfig.getMailRequestCountMessageId(), unsentRecordCount);

        DatabaseRecordReader reader = new DatabaseRecordReader();
        reader.setStatement(mailRequestTable.createReaderStatement(mailSendPatternId, processId));

        reader.setListener(new DatabaseRecordListener() {
            @Override
            public void beforeReadRecords() {
                final MailRequestTable mailRequestTable = SystemRepository.get("mailRequestTable");
                mailRequestTable.updateSendProcessId(processId);
            }
        });
        return reader;
    }

    /**
     * 処理ステータスを異常終了に更新する。
     *
     * @param data 送信対象データ
     * @param context 実行コンテキスト
     */
    @Published(tag = "architect")
    protected void updateToFailed(final SqlRow data, final ExecutionContext context) {
        MailRequestTable mailRequestTable = SystemRepository.get("mailRequestTable");
        MailRequestTable.MailRequest mailRequest = mailRequestTable.getMailRequest(data);
        MailConfig mailConfig = SystemRepository.get("mailConfig");

        mailRequestTable.updateFailureStatus(mailRequest.getMailRequestId(), mailConfig.getStatusFailure());
    }

    /**
     * 処理ステータスを未送信に更新する。
     *
     * @param data 送信対象データ
     * @param context 実行コンテキスト
     */
    @Published(tag = "architect")
    protected void updateToUnsent(final SqlRow data, final ExecutionContext context) {
        MailRequestTable mailRequestTable = SystemRepository.get("mailRequestTable");
        MailRequestTable.MailRequest mailRequest = mailRequestTable.getMailRequest(data);
        MailConfig mailConfig = SystemRepository.get("mailConfig");

        mailRequestTable.updateFailureStatus(mailRequest.getMailRequestId(), mailConfig.getStatusUnsent());
    }

    /**
     * 処理ステータスを正常終了に更新する。
     *
     * @param data 送信対象データ
     * @param context 実行コンテキスト
     */
    @Published(tag = "architect")
    protected void updateToSuccess(final SqlRow data, final ExecutionContext context) {
        MailRequestTable mailRequestTable = SystemRepository.get("mailRequestTable");
        MailRequestTable.MailRequest mailRequest = mailRequestTable.getMailRequest(data);
        MailConfig mailConfig = SystemRepository.get("mailConfig");

        String mailRequestId = mailRequest.getMailRequestId();
        mailRequestTable.updateStatus(mailRequestId, mailConfig.getStatusSent());
    }

    /**
     * ステータスの更新に失敗したことを表す例外クラス。
     */
    public static class StatusUpdateFailedException extends RuntimeException {

        /**
         * メッセージと起因例外を持つ例外を構築する。
         *
         * @param message メッセージ
         * @param cause 起因例外
         */
        public StatusUpdateFailedException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
