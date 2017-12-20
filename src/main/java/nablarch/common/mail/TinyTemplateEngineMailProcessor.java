package nablarch.common.mail;

import java.util.Map;
import java.util.Map.Entry;

/**
 * 簡易的なテンプレート機能を提供する{@link TemplateEngineMailProcessor}の実装クラス。
 * 
 * @author Taichi Uragami
 *
 */
public class TinyTemplateEngineMailProcessor implements TemplateEngineMailProcessor {

    /** メールテンプレート管理テーブルのスキーマ情報 */
    private MailTemplateTable mailTemplateTable;

    /**
     * テンプレートIDと言語から取得されたメールテンプレート中のプレースホルダを置換文字列で置換して結果を返す。
     * 
     * <p>
     * プレースホルダは、指定した{@code key}をもとに{@code value}で置換される。<br/>
     * <b>プレースホルダの記述形式は、{キー名} と記載する。</b><br/>
     * プレースホルダがあるにも関わらず置換文字列が渡されない場合は、変換されずメールが送信される。
     * </p>
     * 
     */
    @Override
    public TemplateEngineProcessedResult process(String templateId, String lang,
            Map<String, Object> variables) {
        // テンプレートから件名・本文を組み立てる。
        MailTemplateTable.MailTemplate mailTemplate = mailTemplateTable.find(templateId, lang);

        String subject = replaceTemplateString(variables, mailTemplate.getSubject());

        String mailBody = replaceTemplateString(variables, mailTemplate.getMailBody());

        String charset = mailTemplate.getCharset();

        return new TemplateEngineProcessedResult(subject, mailBody, charset);
    }

    /**
     * テンプレートの文字列を置換する。
     * 
     * @param replaceKeyValue
     *            プレースホルダと置換文字列のマップ
     * @param targetStr
     *            置換対象文字列
     * @return targetStr 置換後の文字列
     */
    private String replaceTemplateString(Map<String, Object> replaceKeyValue,
            String targetStr) {
        for (Entry<String, Object> entry : replaceKeyValue.entrySet()) {
            final String key = entry.getKey();
            if (key == null) {
                throw new IllegalArgumentException("replace key must not be null");
            }
            final Object value = entry.getValue();
            targetStr = targetStr.replace('{' + key + '}', value == null ? "" : value.toString());
        }
        return targetStr;
    }

    /**
     * メールテンプレート管理テーブルのスキーマ情報を設定する。
     * 
     * @param mailTemplateTable
     *            メールテンプレート管理テーブルのスキーマ情報
     */
    public void setMailTemplateTable(MailTemplateTable mailTemplateTable) {
        this.mailTemplateTable = mailTemplateTable;
    }
}
