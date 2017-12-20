package nablarch.common.mail;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.collection.IsMapContaining.*;
import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Test;

/**
 * {@link TemplateEngineContextPreparer}のテストクラス。
 */
public class TemplateEngineContextPreparerTest {

    @Test
    public void testPrepareSubjectAndMailBody() {
        TemplateMailContext ctx = new TemplateMailContext();
        ctx.setTemplateId("hoge.template");
        ctx.setLang("ja");
        ctx.setReplaceKeyValue("foo", "hello");
        ctx.setVariable("bar", 123);

        assertThat("処理前はコンテキストの件名がnullであることを確認", ctx.getSubject(), is(nullValue()));
        assertThat("処理前はコンテキストの本文がnullであることを確認", ctx.getMailBody(), is(nullValue()));

        MockTemplateEngineMailProcessor processor = new MockTemplateEngineMailProcessor(
                new TemplateEngineProcessedResult("件名テスト", "本文テスト", "UTF-8"));
        new TemplateEngineContextPreparer().prepareSubjectAndMailBody(ctx, processor);

        assertThat("テンプレートIDがprocessorに渡されていることを確認", processor.templateId, is("hoge.template"));
        assertThat("言語がprocessorに渡されていることを確認", processor.lang, is("ja"));
        assertThat("変数がprocessorに渡されていることを確認", processor.variables, allOf(
                hasEntry("foo", (Object) "hello"),
                hasEntry("bar", (Object) 123)));
        assertThat("コンテキストへ件名がセットされていることを確認", ctx.getSubject(), is("件名テスト"));
        assertThat("コンテキストへ本文がセットされていることを確認", ctx.getMailBody(), is("本文テスト"));
        assertThat("コンテキストへ文字セットがセットされていることを確認", ctx.getCharset(), is("UTF-8"));
    }

    private static class MockTemplateEngineMailProcessor implements TemplateEngineMailProcessor {

        TemplateEngineProcessedResult result;
        String templateId;
        String lang;
        Map<String, Object> variables;

        MockTemplateEngineMailProcessor(TemplateEngineProcessedResult result) {
            this.result = result;
        }

        @Override
        public TemplateEngineProcessedResult process(String templateId, String lang,
                Map<String, Object> variables) {
            this.templateId = templateId;
            this.lang = lang;
            this.variables = variables;
            return result;
        }
    }
}
