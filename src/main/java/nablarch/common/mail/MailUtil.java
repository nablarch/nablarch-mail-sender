package nablarch.common.mail;

import nablarch.core.repository.SystemRepository;
import nablarch.core.util.annotation.Published;

/**
 * メール送信ライブラリ関連のユーティリティ。
 * 
 * @author Shinsuke Yoshio
 */
public final class MailUtil {

    /**
     * リポジトリ上のMailRequesterの名称。
     */
    private static final String MAIL_REQUESTER_NAME = "mailRequester";

    /**
     * 隠蔽コンストラクタ
     */
    private MailUtil() {
    }

    /**
     * {@link SystemRepository}から{@link MailRequester}オブジェクトを取得する。
     *
     * @return {@code MailRequester}オブジェクト
     * @throws IllegalArgumentException {@code MailRequester}オブジェクトが取得できなかった場合
     */
    @Published
    public static MailRequester getMailRequester() {
        MailRequester requester = (MailRequester) SystemRepository.get(MAIL_REQUESTER_NAME);
        if(requester == null){
            throw new IllegalArgumentException(
                    "specified " + MAIL_REQUESTER_NAME + " is not registered in SystemRepository.");
        }
        return requester;
    }
}

