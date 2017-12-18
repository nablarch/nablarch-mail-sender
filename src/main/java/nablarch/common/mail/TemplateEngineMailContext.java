package nablarch.common.mail;

import java.util.HashMap;
import java.util.Map;

import nablarch.core.util.annotation.Published;

/**
 * テンプレートエンジンを使用した定型メール送信要求を表すクラス。
 * 
 * @author Taichi Uragami
 *
 */
public class TemplateEngineMailContext extends MailContext {

    /** テンプレート */
    private String template;

    /** テンプレートにマージする変数 */
    private Map<String, Object> variables = new HashMap<String, Object>();

    /**
     * {@link TemplateEngineMailContext}のインスタンスを生成する。
     */
    @Published
    public TemplateEngineMailContext() {
    }

    /**
     * テンプレートと変数をマージして件名と本文を組み立ててメール送信の準備をする。
     * 
     * @param processor
     */
    public void prepareSubjectAndMailBody(final TemplateEngineMailProcessor processor) {
        TemplateEngineProcessedResult result = processor.process(template, variables);
        setSubject(result.getSubject());
        setMailBody(result.getMailBody());
    }

    /**
     * テンプレートを設定する。
     * 
     * @param template テンプレート
     */
    @Published
    public void setTemplate(String template) {
        this.template = template;
    }

    /**
     * テンプレートにマージする変数を設定する。
     * 
     * @param name 変数名
     * @param value 値
     */
    @Published
    public void setVariable(String name, Object value) {
        variables.put(name, value);
    }
}
