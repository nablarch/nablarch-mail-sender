package nablarch.common.mail;

import nablarch.core.repository.SystemRepository;

/**
 * {@link TemplateEngineMailProcessor}のファクトリ。
 * 
 * @author Taichi Uragami
 *
 */
public class TemplateEngineMailProcessorFactory {

    /** {@link SystemRepository}に定義されているTemplateEngineMailProcessor名 */
    private static final String PROCESSOR_NAME = "templateEngineMailProcessor";

    /**
     * システムリポジトリから{@link TemplateEngineMailProcessor}のインスタンスを取得する。
     * 
     * システムリポジトリからインスタンスが取得できない場合は{@link IllegalArgumentException}が投げられる。
     * 
     * @return システムリポジトリから取得されたインスタンス
     */
    public static TemplateEngineMailProcessor getProcessor() {
        TemplateEngineMailProcessor processor = SystemRepository.get(PROCESSOR_NAME);
        if (processor == null) {
            throw new IllegalArgumentException(
                    "specified templateEngineMailProcessor is not registered in SystemRepository.");
        }
        return processor;
    }
}
