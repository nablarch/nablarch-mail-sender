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

    /** テンプレートとマージする変数 */
    //replaceKeyValue は Map<String, String> のため構造を持ったクラスを渡せないので、
    //Map<String, Object> な変数保管場所を新たに追加する。
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
     */
    @Published(tag = "architect")
    public Map<String, String> getReplaceKeyValue() {
        return replaceKeyValue;
    }

    @Published(tag = "architect")
    public Map<String, Object> getVariables() {
        Map<String, Object> vars = new HashMap<String, Object>(
                variables.size() + replaceKeyValue.size());
        vars.putAll(replaceKeyValue);
        vars.putAll(variables);
        return vars;
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
     */
    @Published
    public void setReplaceKeyValue(String key, String value) {
        this.replaceKeyValue.put(key, value);
    }

    /**
     * テンプレートとマージする変数を追加する。
     * <p>
     * {@link #setReplaceKeyValue(String, String)}とは異なり、こちらのメソッドで保存される変数は
     * テンプレートエンジンを利用した定型メール送信処理でのみ利用される。
     * </p>
     * 
     * @param name 変数名
     * @param value 値
     */
    @Published
    public void setVariable(String name, Object value) {
        this.variables.put(name, value);
    }
}
