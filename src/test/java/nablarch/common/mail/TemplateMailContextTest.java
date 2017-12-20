package nablarch.common.mail;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.collection.IsMapContaining.*;
import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

/**
 * {@link TemplateMailContext}のテストクラス。
 */
public class TemplateMailContextTest {

    private final TemplateMailContext sut = new TemplateMailContext();

    @Before
    public void setUp() {
        sut.setVariable("1", "foo");
        sut.setVariable("2", 123);
        sut.setReplaceKeyValue("3", "bar");
    }

    /**
     * {@link TemplateMailContext#getVariables()}のテスト。
     */
    @Test
    public void testGetVariables() {
        Map<String, Object> variables = sut.getVariables();
        assertThat(variables.size(), is(3));
        assertThat(variables, allOf(
                hasEntry("1", (Object) "foo"),
                hasEntry("2", (Object) 123),
                hasEntry("3", (Object) "bar")));
    }

    /**
     * {@link TemplateMailContext#getReplaceKeyValue()}のテスト。
     */
    @Test
    public void testGetReplaceKeyValue() {
        Map<String, String> replaceKeyValue = sut.getReplaceKeyValue();
        assertThat(replaceKeyValue.size(), is(3));
        assertThat(replaceKeyValue, allOf(
                hasEntry("1", "foo"),
                hasEntry("2", "123"),
                hasEntry("3", "bar")));
    }
}
