package nablarch.common.mail;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.util.Collections;
import java.util.Map;

import org.junit.Test;

/**
 * {@link TinyTemplateEngineMailProcessor}のテストクラス。
 * 
 * <p>
 * {@link TinyTemplateEngineMailProcessor}は元々{@link MailRequester}に実装されていた定型メール処理を
 * 切り出したものなので、{@link MailRequesterTest}にテストがある。
 * </p>
 * 
 * <p>
 * 当テストクラスでは{@link TemplateEngineMailProcessor#process(String, String, Map)}の
 * 動作に関する事柄のみをテストしている。
 * </p>
 * 
 * @see MailRequesterTest#testTemplateMailOK
 * @see MailRequesterTest#testTemplateMailTemplateNotFound
 * @see MailRequesterTest#testNoTemplateIdOrLang
 */
public class TinyTemplateEngineMailProcessorTest {

    @Test
    public void testProcess() {
        TinyTemplateEngineMailProcessor sut = new TinyTemplateEngineMailProcessor();

        MockMailTemplate mailTemplate = new MockMailTemplate("件名テスト{foo}", "本文テスト{foo}", "UTF-8");
        MockMailTemplateTable mailTemplateTable = new MockMailTemplateTable(mailTemplate);
        sut.setMailTemplateTable(mailTemplateTable);

        String templateId = "test-template";
        String lang = "ja";
        Map<String, Object> variables = Collections.singletonMap("foo", (Object) "test");

        TemplateEngineProcessedResult result = sut.process(templateId, lang, variables);

        assertThat("テンプレートIDを指定して検索していることを確認", mailTemplateTable.templateId, is(templateId));
        assertThat("言語を指定して検索していることを確認", mailTemplateTable.lang, is(lang));

        assertThat("件名のテンプレート処理が行われていることを確認", result.getSubject(), is("件名テストtest"));
        assertThat("本文のテンプレート処理が行われていることを確認", result.getMailBody(), is("本文テストtest"));
        assertThat("文字セットが返されることを確認", result.getCharset(), is("UTF-8"));
    }

    private static class MockMailTemplateTable extends MailTemplateTable {

        String templateId;
        String lang;

        private final MockMailTemplate mailTemplate;

        public MockMailTemplateTable(MockMailTemplate mailTemplate) {
            this.mailTemplate = mailTemplate;
        }

        @Override
        public MailTemplate find(String templateId, String lang) {
            this.templateId = templateId;
            this.lang = lang;
            return mailTemplate;
        }
    }

    private static class MockMailTemplate extends MailTemplateTable.MailTemplate {

        private final String subject;
        private final String mailBody;
        private final String charset;

        public MockMailTemplate(String subject, String mailBody, String charset) {
            super(null);
            this.subject = subject;
            this.mailBody = mailBody;
            this.charset = charset;
        }

        @Override
        public String getSubject() {
            return subject;
        }

        @Override
        public String getMailBody() {
            return mailBody;
        }

        @Override
        public String getCharset() {
            return charset;
        }
    }
}
