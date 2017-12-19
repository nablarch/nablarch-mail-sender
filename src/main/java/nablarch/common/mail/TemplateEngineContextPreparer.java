package nablarch.common.mail;

import java.util.Map;

/**
 * テンプレートエンジンを使用して件名と本文の準備をするクラス。
 * 
 * @author Taichi Uragami
 *
 */
public class TemplateEngineContextPreparer {

    /**
     * テンプレートエンジンを使用して件名と本文を処理してメールコンテキストに設定する。
     * 
     * @param ctx メールコンテキスト
     * @param processor テンプレートエンジンの処理を行うクラス
     */
    public void prepareSubjectAndMailBody(
            TemplateMailContext ctx, TemplateEngineMailProcessor processor) {
        String template = ctx.getTemplateId();
        Map<String, Object> variables = ctx.getVariables();
        TemplateEngineProcessedResult result = processor.process(template, variables);
        ctx.setSubject(result.getSubject());
        ctx.setMailBody(result.getMailBody());
    }
}
