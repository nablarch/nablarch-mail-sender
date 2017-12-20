package nablarch.common.mail;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

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

    /** テンプレートとマージする変数 */
    private final Map<String, Object> variables = new HashMap<String, Object>();

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
     * @deprecated 当メソッドは5u13より前から存在する定型メール機能のためにある。
     *               当メソッドの仕様を満たしつつより柔軟な機能をもつ{@link #getVariables()}が追加されたので今後はそちらを使用すること。
     */
    @Published(tag = "architect")
    @Deprecated
    public Map<String, String> getReplaceKeyValue() {
        Map<String, String> replaceKeyValue = new HashMap<String, String>();
        for (Entry<String, Object> entry : variables.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) {
                replaceKeyValue.put(key, (String) value);
            } else if (value == null) {
                replaceKeyValue.put(key, null);
            } else {
                replaceKeyValue.put(key, value.toString());
            }
        }
        return replaceKeyValue;
    }

    /**
     * テンプレートとマージする変数を取得する。
     * 
     * @return テンプレートとマージする変数
     */
    @Published(tag = "architect")
    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    /**
     * メールテンプレート中のプレースホルダのキーと置換文字列を追加する。
     * <p/>
     * プレースホルダは、指定した{@code key}をもとに{@code value}で置換される。<br/>
     * <b>プレースホルダの記述形式は、{キー名} と記載する。</b><br/>
     * プレースホルダがあるにも関わらず置換文字列が渡されない場合は、変換されずメールが送信される。<br/>
     * ただし、テンプレートエンジンを使用したメール送信処理ではプレースホルダがあるにも関わらず
     * 置換文字列が渡されない場合の動作はテンプレートエンジンの仕様に準ずる。
     *
     * @param key プレースホルダのキー
     * @param value 置換文字列(null不可)
     * @deprecated 当メソッドは5u13より前から存在する定型メール機能のためにある。
     *               当メソッドの仕様を満たしつつより柔軟な機能をもつ{@link #setVariable(String, Object)}が追加されたので今後はそちらを使用すること。
     */
    @Published
    @Deprecated
    public void setReplaceKeyValue(String key, String value) {
        this.variables.put(key, value);
    }

    /**
     * テンプレートとマージする変数を追加する。
     * 
     * @param name 変数名
     * @param value 値
     */
    @Published
    public void setVariable(String name, Object value) {
        this.variables.put(name, value);
    }
}
