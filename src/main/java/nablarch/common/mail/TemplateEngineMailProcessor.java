package nablarch.common.mail;

import java.util.Map;

/**
 * テンプレートエンジンを使用したテンプレートと変数のマージ処理をするためのインターフェース。
 * 
 * @author Taichi Uragami
 *
 */
public interface TemplateEngineMailProcessor {

    /**
     * テンプレートと変数をマージする。
     * 
     * @param template テンプレート
     * @param variables 変数
     * @return 処理結果
     */
    TemplateEngineProcessedResult process(String template, Map<String, Object> variables);
}
