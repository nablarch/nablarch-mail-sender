package nablarch.common.mail;

/**
 * 不正な文字が含まれていた場合に発生する例外。
 * 
 * @author Kohei Sawaki
 */
public class InvalidCharacterException extends CreateMailFailedException {

    /**
     * InvalidCharacterExceptionを生成する。
     *
     * @param message エラーメッセージ
     */
    public InvalidCharacterException(final String message) {
        super(message);
    }

}
