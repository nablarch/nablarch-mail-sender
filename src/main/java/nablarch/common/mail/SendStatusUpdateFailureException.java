package nablarch.common.mail;

/**
 * メール送信時の送信ステータス更新に失敗した状態を示す例外
 * 本例外が発生した際は、送信ステータスが未送信から送信済みまたは送信失敗へ失敗したことを表す。
 *
 * @author T.Shimoda
 */
public class SendStatusUpdateFailureException extends RuntimeException {

    /**
     * コンストラクタ。
     *
     * @param message 例外メッセージ
     * @param cause 起因例外
     */
    public SendStatusUpdateFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
