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
     * テンプレートIDと言語から取得されたテンプレートと変数をマージして、その結果を返す。
     * 
     * <p>
     * 当メソッドが実行されたとき、まず渡されたテンプレートIDと言語をもとにテンプレートを検索する。
     * 具体的な検索処理は当インターフェースの実装クラスによって異なるが、
     * 例えばテンプレートIDをファイル名とみなしてファイルシステムを検索する実装が考えられる。
     * また、言語はオプションなので実装クラスによっては検索に使用しなくてもよい。
     * </p>
     * 
     * <p>
     * テンプレートを取得できたら、テンプレートと変数をマージする。
     * このとき、実装クラスによっては場合分けや繰り返しなどの処理をするものもある。
     * </p>
     * 
     * <p>
     * マージした結果を{@link TemplateEngineProcessedResult}にセットして、当メソッドの呼び出し元へ返す。
     * </p>
     * 
     * @param templateId テンプレートID
     * @param lang 言語（{@code null}でもよい）
     * @param variables 変数
     * @return 処理結果
     * @throws TemplateEngineProcessingException テンプレートが見つからなかった場合やテンプレートと変数のマージに失敗した場合に投げられる
     */
    TemplateEngineProcessedResult process(String templateId, String lang,
            Map<String, Object> variables) throws TemplateEngineProcessingException;
}
