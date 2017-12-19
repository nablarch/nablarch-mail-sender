package nablarch.common.mail;

import java.util.Map;
import java.util.Map.Entry;

import nablarch.common.idgenerator.IdGenerator;
import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.transaction.SimpleDbTransactionExecutor;
import nablarch.core.db.transaction.SimpleDbTransactionManager;
import nablarch.core.util.StringUtil;
import nablarch.core.util.annotation.Published;

/**
 * メール送信要求を行うクラス。
 * <p/>
 * 本クラスのメール送信要求メソッドを呼び出すことで、メール送信要求を管理用テーブル群にINSERTできる。
 * <p/>
 * メール送信要求の種類について<br>
 * メール送信要求は以下の二種類がある。
 * <ul>
 *     <li>定型メール送信({@link TemplateMailContext})。
 *         予めデータベースに登録されたテンプレートを元にメールを作成・送信する。</li>
 *     <li>非定型メール送信({@link FreeTextMailContext})。任意の件名・本文でメールを作成・送信する。</li>
 * </ul>
 * メールの送信単位<br>
 * メール送信要求はメール送信要求APIの呼び出し毎に一つ作成さる。一つのメール送信要求につき一通のメールが送信される。
 *
 * @author Shinsuke Yoshio
 *
 * @see MailUtil#getMailRequester()
 */
@Published(tag = "architect")
public class MailRequester {

    /** メール共通設定を保持するデータオブジェクト */
    private MailRequestConfig mailRequestConfig;

    /** メール関連のコード値を保持するデータオブジェクト */
    private MailConfig mailConfig;

    /** メール送信要求IDジェネレータ */
    private IdGenerator mailRequestIdGenerator;

    /** メール要求管理テーブルのスキーマ情報 */
    private MailRequestTable mailRequestTable;

    /** メール送信先管理テーブルのスキーマ情報 */
    private MailRecipientTable mailRecipientTable;

    /** 添付ファイル管理テーブルのスキーマ情報 */
    private MailAttachedFileTable mailAttachedFileTable;

    /** メールテンプレート管理テーブルのスキーマ情報 */
    private MailTemplateTable mailTemplateTable;

    /** メール送信時のDB登録に利用するトランザクションマネージャ */
    private SimpleDbTransactionManager mailTransactionManager;

    /** {@link TemplateEngineMailProcessor}のファクトリ */
    private final TemplateEngineMailProcessorFactory templateEngineMailProcessorFactory = new TemplateEngineMailProcessorFactory();

    /** テンプレートエンジンを使用して件名と本文の準備をするクラス */
    private final TemplateEngineContextPreparer templateEngineContextPreparer = new TemplateEngineContextPreparer();

    /**
     * 非定型メールの送信要求を行う。
     * 
     * @param ctx 非定型メール送信要求
     * @return メール送信要求ID
     * @throws AttachedFileSizeOverException
     *             添付ファイルのサイズが上限値を超えた場合
     * @throws RecipientCountException
     *             宛先数が上限値を超えた場合
     */
    @Published
    public String requestToSend(FreeTextMailContext ctx)
            throws AttachedFileSizeOverException, RecipientCountException {
        return sendMail(ctx);
    }

    /**
     * 定型メールの送信要求を行う。
     * 
     * @param ctx 定型メール送信要求
     * @return メール送信要求ID
     * @throws AttachedFileSizeOverException
     *             添付ファイルのサイズが上限値を超えた場合
     * @throws RecipientCountException
     *             宛先数が上限値を超えた場合
     */
    @Published
    public String requestToSend(TemplateMailContext ctx)
            throws AttachedFileSizeOverException, RecipientCountException {

        TemplateEngineMailProcessor processor = templateEngineMailProcessorFactory.getProcessor();
        if (processor != null) {
            templateEngineContextPreparer.prepareSubjectAndMailBody(ctx, processor);
            return sendMail(ctx);
        }

        // テンプレートから件名・本文を組み立てる。
        MailTemplateTable.MailTemplate mailTemplate = mailTemplateTable.find(ctx.getTemplateId(), ctx.getLang());

        ctx.setSubject(replaceTemplateString(
                ctx.getReplaceKeyValue(),
                mailTemplate.getSubject()));

        ctx.setMailBody(replaceTemplateString(
                ctx.getReplaceKeyValue(),
                mailTemplate.getMailBody()));

        ctx.setCharset(mailTemplate.getCharset());

        return sendMail(ctx);
    }

    /**
     * メール送信要求処理
     * 
     * @param ctx
     *            メール送信要求
     * @return メール送信要求ID
     */
    private String sendMail(final MailContext ctx) {

        // メール送信要求データをバリデーション
        ctx.validate(mailRequestConfig);

        // 返信先・差し戻し先・文字セットの指定がない場合、デフォルト値を適用。
        if (StringUtil.isNullOrEmpty(ctx.getReturnPath())) {
            ctx.setReturnPath(mailRequestConfig.getDefaultReturnPath());
        }
        if (StringUtil.isNullOrEmpty(ctx.getReplyTo())) {
            ctx.setReplyTo(mailRequestConfig.getDefaultReplyTo());
        }
        if (StringUtil.isNullOrEmpty(ctx.getCharset())) {
            ctx.setCharset(mailRequestConfig.getDefaultCharset());
        }

        if (mailTransactionManager != null) {
            return new SimpleDbTransactionExecutor<String>(mailTransactionManager) {
                @Override
                public String execute(final AppDbConnection connection) {
                    return setupMailWithTransactionName(ctx, mailTransactionManager.getDbTransactionName());
                }
            }.doTransaction();
        } else {
            return setupMail(ctx);
        }
    }

    /**
     * メール送信要求IDの採番、送信DBへの登録処理
     *
     * @param ctx メール送信要求
     * @param transactionName トランザクション名
     * @return メール送信要求ID
     */
    private String setupMailWithTransactionName(final MailContext ctx, final String transactionName) {
        // メール送信要求ID採番
        final String mailRequestId = mailRequestIdGenerator.generateId(mailConfig.getMailRequestSbnId());
        // 各DBに登録
        mailRequestTable.insert(mailRequestId, ctx, transactionName);
        mailRecipientTable.insert(mailRequestId, ctx, mailConfig, transactionName);
        mailAttachedFileTable.insert(mailRequestId, ctx, transactionName);
        return mailRequestId;
    }

    /**
     * メール送信要求IDの採番、送信DBへの登録処理
     *
     * @param ctx メール送信要求
     * @return メール送信要求ID
     */
    private String setupMail(final MailContext ctx) {
        // メール送信要求ID採番
        final String mailRequestId = mailRequestIdGenerator.generateId(mailConfig.getMailRequestSbnId());

        mailRequestTable.insert(mailRequestId, ctx);
        mailRecipientTable.insert(mailRequestId, ctx, mailConfig);
        mailAttachedFileTable.insert(mailRequestId, ctx);
        return mailRequestId;
    }

    /**
     * テンプレートの文字列を置換する。
     * 
     * @param replaceKeyValue
     *            プレースホルダと置換文字列のマップ
     * @param targetStr
     *            置換対象文字列
     * @return targetStr 置換後の文字列
     */
    private String replaceTemplateString(Map<String, String> replaceKeyValue,
            String targetStr) {
        for (Entry<String, String> entry : replaceKeyValue.entrySet()) {
            final String key = entry.getKey();
            if (key == null) {
                throw new IllegalArgumentException("replace key must not be null");
            }
            final String value = entry.getValue();
            targetStr = targetStr.replace('{' + key + '}', value == null ? "" : value);
        }
        return targetStr;
    }

    /**
     * メール送信要求共通設定を保持するデータオブジェクトを設定する。
     * 
     * @param mailRequestConfig
     *            メール送信要求共通設定を保持するデータオブジェクト
     */
    public void setMailRequestConfig(MailRequestConfig mailRequestConfig) {
        this.mailRequestConfig = mailRequestConfig;
    }

    /**
     * メール関連のコード値を保持するデータオブジェクトを設定する。
     * 
     * @param mailConfig
     *            メール関連のコード値を保持するデータオブジェクト
     */
    public void setMailConfig(MailConfig mailConfig) {
        this.mailConfig = mailConfig;
    }

    /**
     * メール送信要求IDジェネレータを設定する。
     * 
     * @param mailRequestIdGenerator
     *            メール送信要求IDジェネレータ
     */
    public void setMailRequestIdGenerator(IdGenerator mailRequestIdGenerator) {
        this.mailRequestIdGenerator = mailRequestIdGenerator;
    }

    /**
     * mailSendRequestTable メール送信要求管理テーブルのスキーマ情報を設定する。
     * 
     * @param mailRequestTable
     *            メール送信要求管理テーブルのスキーマ。
     */
    public void setMailRequestTable(MailRequestTable mailRequestTable) {
        this.mailRequestTable = mailRequestTable;
    }

    /**
     * メール送信先管理テーブルのスキーマ情報を設定する。
     * 
     * @param mailRecipientTable
     *            メール送信先管理テーブルのスキーマ情報
     */
    public void setMailRecipientTable(MailRecipientTable mailRecipientTable) {
        this.mailRecipientTable = mailRecipientTable;
    }

    /**
     * 添付ファイル管理テーブルのスキーマ情報を設定する。
     * 
     * @param mailAttachedFileTable
     *            添付ファイル管理テーブルのスキーマ情報
     */
    public void setMailAttachedFileTable(
            MailAttachedFileTable mailAttachedFileTable) {
        this.mailAttachedFileTable = mailAttachedFileTable;
    }

    /**
     * メールテンプレート管理テーブルのスキーマ情報を設定する。
     * 
     * @param mailTemplateTable
     *            メールテンプレート管理テーブルのスキーマ情報
     */
    public void setMailTemplateTable(MailTemplateTable mailTemplateTable) {
        this.mailTemplateTable = mailTemplateTable;
    }

    /**
     * メール送信時に利用するトランザクションマネージャを設定する。
     *
     * @param mailTransactionManager
     *             トランザクションマネージャ
     */
    public void setMailTransactionManager(SimpleDbTransactionManager mailTransactionManager) {
        this.mailTransactionManager = mailTransactionManager;
    }
}
