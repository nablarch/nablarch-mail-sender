package nablarch.common.mail;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;

import java.util.Collections;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import nablarch.core.repository.ObjectLoader;
import nablarch.core.repository.SystemRepository;

/**
 * {@link TemplateEngineMailProcessorFactory}のテストクラス。
 */
public class TemplateEngineMailProcessorFactoryTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    /**
     * プロセッサーが取得できること。
     */
    @Test
    public void testGetProcessor() {
        SystemRepository.load(new ObjectLoader() {
            @Override
            public Map<String, Object> load() {
                Object processor = new TemplateEngineMailProcessor() {
                    @Override
                    public TemplateEngineProcessedResult process(String template,
                            Map<String, Object> variables) {
                        throw new UnsupportedOperationException();
                    }
                };
                return Collections.singletonMap("templateEngineMailProcessor", processor);
            }
        });
        TemplateEngineMailProcessor processor = TemplateEngineMailProcessorFactory.getProcessor();
        assertThat(processor, is(notNullValue()));
    }

    /**
     * システムリポジトリからプロセッサーが取得できない場合に例外が送出されること。
     */
    @Test
    public void testGetProcessor_undefined() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException
                .expectMessage(is(
                        "specified templateEngineMailProcessor is not registered in SystemRepository."));
        TemplateEngineMailProcessorFactory.getProcessor();
    }

    /**
     * テストの前後でシステムリポジトリをクリアする。
     */
    @Before
    @After
    public void clearSystemRepository() {
        SystemRepository.clear();
    }
}
