package nablarch.common.mail;

import nablarch.core.util.annotation.Published;
import nablarch.fw.handler.retry.RetryableException;

/**
 * メール送信時にリトライ可能である状態を示す例外
 *
 * @author T.Shimoda
 */
@Published(tag = "architect")
public class SendMailRetryableException extends RetryableException {

    /**
     * コンストラクタ。
     *
     * @param message 例外メッセージ
     * @param cause 起因例外
     */
    public SendMailRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
