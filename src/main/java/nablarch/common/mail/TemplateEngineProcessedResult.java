package nablarch.common.mail;

import java.util.ArrayList;
import java.util.List;

/**
 * テンプレートエンジンで処理した結果を保持するクラス。
 * 
 * @author Taichi Uragami
 *
 */
public class TemplateEngineProcessedResult {

    public static final String DEFAULT_DELIMITER = "---";

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
     * これは{@link #DEFAULT_DELIMITER デフォルトのデリミタ}を指定して{@link #valueOf(String, String)}を呼び出すショートカットである。
     * </p>
     * 
     * @param value 件名と本文を含む文字列
     * @return 件名と本文がセットされたインスタンス
     */
    public static TemplateEngineProcessedResult valueOf(final String value) {
        return valueOf(value, DEFAULT_DELIMITER);
    }

    /**
     * テンプレートエンジンで処理済みの文字列を件名と本文に分割して{@link TemplateEngineProcessedResult}のインスタンスを生成するファクトリーメソッド。
     * 
     * <p>
     * 文字列はデリミタによって件名と本文に分割される。
     * 基本的なルールは次の通り。
     * </p>
     * 
     * <ol>
     * <li>デリミタは引数{@code delimiter}で表される文字列だけからなる行とする（つまり前後に余計な文字を含むものはデリミタとみなさない）</li>
     * <li>デリミタより前にある行は件名とみなす</li>
     * <li>デリミタより後の文字列をすべて本文とみなす</li>
     * </ol>
     * 
     * <p>
     * 例えば、このファクトリーメソッドに次のような文字列が渡されたとする。
     * </p>
     * 
     * <pre>{@code テスト件名
     * ---
     * テスト本文１
     * テスト本文２
     * テスト本文３
     * }</pre>
     * 
     * <p>
     * デリミタが{@literal "---"}である場合、{@code テスト件名}が件名となり、{@code テスト本文１}以降が本文となる。
     * </p>
     * 
     * <p>
     * デリミタよりも前、件名を期待するエリアでは空行は無視される。
     * つまり、次のような文字列は有効である。
     * </p>
     * 
     * <pre>{@code
     * テスト件名
     * 
     * 
     * ---
     * テスト本文１
     * テスト本文２
     * テスト本文３
     * }</pre>
     * 
     * <p>
     * デリミタよりも後、本文を構成するエリアでは空行も無視されずそのまま本文として扱われる。
     * </p>
     * 
     * <p>
     * なお、このファクトリーメソッドでは文字セットは設定されない。
     * </p>
     * 
     * @param value 件名とデリミタと本文を含む文字列
     * @param delimiter 件名と本文を分けるデリミタ
     * @return 件名と本文がセットされたインスタンス
     * @throws IllegalArgumentException 次のいずれかの場合に投げられる。
     *          <ul>
     *          <li>デリミタが含まれない場合</li>
     *          <li>件名が無い場合</li>
     *          <li>件名が改行を含んで複数行に渡っている場合</li>
     *          <li>本文が無い場合</li>
     *          </ul>
     */
    public static TemplateEngineProcessedResult valueOf(final String value,
            final String delimiter) throws IllegalArgumentException {
        class Tokenizer {
            int index = 0;

            public String nextLine() {
                if (index < value.length() == false) {
                    return null;
                }
                StringBuilder sb = new StringBuilder();
                while (index < value.length()) {
                    char c = value.charAt(index++);
                    if (c == '\r') {
                        if (index < value.length() && value.charAt(index) == '\n') {
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
        String line;
        List<String> subjectCandidates = new ArrayList<String>();
        boolean delimiterFound = false;
        while (null != (line = tokenizer.nextLine())) {
            if (line.equals(delimiter)) {
                delimiterFound = true;
                break;
            }
            if (line.isEmpty() == false) {
                subjectCandidates.add(line);
            }
        }
        if (delimiterFound == false) {
            throw new IllegalArgumentException("Delimiter(" + delimiter + ") is not found");
        } else if (subjectCandidates.isEmpty()) {
            throw new IllegalArgumentException("Subject is not found");
        } else if (subjectCandidates.size() > 1) {
            throw new IllegalArgumentException("Subject must not have new line");
        }
        String subject = subjectCandidates.get(0);
        String mailBody = tokenizer.remaining();
        if (mailBody.isEmpty()) {
            throw new IllegalArgumentException("Mail body is not found");
        }
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
