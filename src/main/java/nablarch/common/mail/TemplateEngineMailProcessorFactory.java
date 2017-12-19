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
     * @return システムリポジトリから取得されたインスタンス
     */
    public TemplateEngineMailProcessor getProcessor() {
        TemplateEngineMailProcessor processor = SystemRepository.get(PROCESSOR_NAME);
        return processor;
    }
}
