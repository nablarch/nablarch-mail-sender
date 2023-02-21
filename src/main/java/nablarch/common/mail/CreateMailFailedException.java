package nablarch.common.mail;

import jakarta.mail.MessagingException;

/**
 * メール作成時の失敗を表す例外。<br/>
 * MessagingExceptionをラップする際に、入力情報を含めることでより詳細なメッセージを作成できる。
 *
 * @author T.Shimoda
 */
public class CreateMailFailedException extends MessagingException {

    /**
     * コンストラクタ。
     *
     * @param message 例外メッセージ
     * @param cause 起因例外
     */
    public CreateMailFailedException(String message, Exception cause) {
        super(message, cause);
    }

    /**
     * コンストラクタ。
     *
     * @param message 例外メッセージ
     */
    public CreateMailFailedException(String message) {
        super(message);
    }
}
