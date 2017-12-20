package nablarch.common.mail;

/**
 * テンプレートエンジンの処理で発生した例外をラップする例外クラス。
 * 
 * @author Taichi Uragami
 *
 */
public class TemplateEngineProcessingException extends RuntimeException {

    /**
     * インスタンスを生成する。
     * 
     * @param cause 原因
     */
    public TemplateEngineProcessingException(Throwable cause) {
        super(cause);
    }
}
