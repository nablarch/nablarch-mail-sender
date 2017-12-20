package nablarch.common.mail;

/**
 * テンプレートエンジンで処理した結果を保持するクラス。
 * 
 * @author Taichi Uragami
 *
 */
public class TemplateEngineProcessedResult {

    /** 件名 */
    private final String subject;
    /** 本文 */
    private final String mailBody;
    /** 文字セット */
    private final String charset;

    /**
     * {@link TemplateEngineProcessedResult}のインスタンスを生成する。
     * 
     * @param subject 件名
     * @param mailBody 本文
     * @param charset 文字セット
     */
    public TemplateEngineProcessedResult(final String subject, final String mailBody,
            final String charset) {
        this.subject = subject;
        this.mailBody = mailBody;
        this.charset = charset;
    }

    /**
     * ルールに則って文字列を件名と本文に分割して{@link TemplateEngineProcessedResult}のインスタンスを生成するファクトリーメソッド。
     * 
     * <p>
     * 文字列に適用される分割のルールは次の通り。
     * </p>
     * 
     * <ol>
     * <li>1行目は件名とみなす</li>
     * <li>2行目は無視</li>
     * <li>3行目以降を本文とみなす</li>
     * </ol>
     * 
     * <p>
     * 例えば、このファクトリーメソッドに次のような文字列が渡されたとする。
     * </p>
     * 
     * <pre>{@code テスト件名
     * 
     * テスト本文１
     * テスト本文２
     * テスト本文３
     * }</pre>
     * 
     * <p>
     * この場合、{@code テスト件名}が件名となり、{@code テスト本文１}以降が本文となる。
     * </p>
     * 
     * <p>
     * なお、このファクトリーメソッドでは文字セットは設定されない。
     * </p>
     * 
     * @param value 件名と本文を含む文字列
     * @return 件名と本文がセットされたインスタンス
     */
    public static TemplateEngineProcessedResult valueOf(final String value) {
        class Tokenizer {
            int index = 0;

            public String nextLine() {
                StringBuilder sb = new StringBuilder();
                while (index < value.length()) {
                    char c = value.charAt(index++);
                    if (c == '\r') {
                        if (value.charAt(index) == '\n') {
                            index++;
                        }
                        break;
                    } else if (c == '\n') {
                        break;
                    }
                    sb.append(c);
                }
                return sb.toString();
            }

            public String remaining() {
                return value.substring(index);
            }
        }
        Tokenizer tokenizer = new Tokenizer();
        String subject = tokenizer.nextLine();
        tokenizer.nextLine(); //2行目は読み捨てる
        String mailBody = tokenizer.remaining();
        return new TemplateEngineProcessedResult(subject, mailBody, null);
    }

    /**
    * 件名を取得する。
    * 
    * @return 件名
    */
    public String getSubject() {
        return subject;
    }

    /**
     * 本文を取得する。
     * 
     * @return 本文
     */
    public String getMailBody() {
        return mailBody;
    }

    /**
     * 文字セットを取得する。
     * 
     * @return 文字セット
     */
    public String getCharset() {
        return charset;
    }
}
