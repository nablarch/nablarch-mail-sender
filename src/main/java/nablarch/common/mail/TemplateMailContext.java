package nablarch.common.mail;

import java.util.HashMap;
import java.util.Map;

import nablarch.core.util.annotation.Published;

/**
 * 定型メール送信要求を表すクラス。
 * 
 * @author Shinsuke Yoshio
 */
public class TemplateMailContext extends MailContext {

    /** テンプレートID */
    private String templateId;

    /** 言語 */
    private String lang;

    /** プレースホルダと置換文字列のマップ */
    private final Map<String, String> replaceKeyValue = new HashMap<String, String>();

    /**
     * {@link TemplateMailContext}のインスタンスを生成する。
     */
    @Published
    public TemplateMailContext() {
        super();
    }

    /**
     * テンプレートIDを取得する。
     * 
     * @return テンプレートID
     */
    @Published(tag = "architect")
    public String getTemplateId() {
        return templateId;
    }

    /**
     * テンプレートIDを設定する。
     * 
     * @param templateId テンプレートID
     */
    @Published
    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    /**
     * 言語を取得する。
     * 
     * @return 言語
     */
    @Published(tag = "architect")
    public String getLang() {
        return lang;
    }

    /**
     * 言語を設定する。
     * 
     * @param lang 言語
     */
    @Published
    public void setLang(String lang) {
        this.lang = lang;
    }

    /**
     * プレースホルダのキーと置換文字列のマップを取得する。
     * 
     * @return プレースホルダと置換文字列のマップ
     */
    @Published(tag = "architect")
    public Map<String, String> getReplaceKeyValue() {
        return replaceKeyValue;
    }

    /**
     * メールテンプレート中のプレースホルダのキーと置換文字列を追加する。
     * <p/>
     * プレースホルダは、指定した{@code key}をもとに{@code value}で置換される。<br/>
     * <b>プレースホルダの記述形式は、{キー名} と記載する。</b><br/>
     * プレースホルダがあるにも関わらず置換文字列が渡されない場合は、変換されずメールが送信される。<br/>
     *
     * @param key プレースホルダのキー
     * @param value 置換文字列(null不可)
     */
    @Published
    public void setReplaceKeyValue(String key, String value) {
        this.replaceKeyValue.put(key, value);
    }

}
