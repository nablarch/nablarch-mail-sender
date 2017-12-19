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
        ctx.setReplaceKeyValue("foo", "hello");
        ctx.setVariable("bar", 123);

        assertThat("処理前はコンテキストの件名がnullであることを確認", ctx.getSubject(), is(nullValue()));
        assertThat("処理前はコンテキストの本文がnullであることを確認", ctx.getMailBody(), is(nullValue()));

        MockTemplateEngineMailProcessor processor = new MockTemplateEngineMailProcessor(
                new TemplateEngineProcessedResult("件名テスト", "本文テスト"));
        new TemplateEngineContextPreparer().prepareSubjectAndMailBody(ctx, processor);

        assertThat("テンプレートがprocessorに渡されていることを確認", processor.template, is("hoge.template"));
        assertThat("変数がprocessorに渡されていることを確認", processor.variables, allOf(
                hasEntry("foo", (Object) "hello"),
                hasEntry("bar", (Object) 123)));
        assertThat("コンテキストへ件名がセットされていることを確認", ctx.getSubject(), is("件名テスト"));
        assertThat("コンテキストへ本文がセットされていることを確認", ctx.getMailBody(), is("本文テスト"));
    }

    private static class MockTemplateEngineMailProcessor implements TemplateEngineMailProcessor {

        TemplateEngineProcessedResult result;
        String template;
        Map<String, Object> variables;

        MockTemplateEngineMailProcessor(TemplateEngineProcessedResult result) {
            this.result = result;
        }

        @Override
        public TemplateEngineProcessedResult process(String template,
                Map<String, Object> variables) {
            this.template = template;
            this.variables = variables;
            return result;
        }
    }
}
