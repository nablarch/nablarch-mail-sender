package nablarch.common.mail;

import nablarch.core.util.annotation.Published;

/**
 * 非定形メール送信要求を表すクラス。
 * 
 * @author Shinsuke Yoshio
 */
public class FreeTextMailContext extends MailContext {

    /**
     * {@code FreeTextMailContext}オブジェクトを構築する。
     */
    @Published
    public FreeTextMailContext() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Published
    public void setSubject(String subject) {
        super.setSubject(subject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Published
    public void setMailBody(String mailBody) {
        super.setMailBody(mailBody);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Published
    public void setCharset(String charset) {
        super.setCharset(charset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Published(tag = "architect")
    public String getSubject() {
        return super.getSubject();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Published(tag = "architect")
    public String getMailBody() {
        return super.getMailBody();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Published(tag = "architect")
    public String getCharset() {
        return super.getCharset();
    }

}
