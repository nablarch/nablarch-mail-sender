package nablarch.common.mail;

import nablarch.core.repository.ObjectLoader;
import nablarch.core.repository.SystemRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

/**
 * {@link MailUtil}のテスト。
 */
public class MailUtilTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp(){
        // リポジトリにMailRequesterを定義
        SystemRepository.load(new ObjectLoader() {
            @SuppressWarnings("serial")
            @Override
            public Map<String, Object> load() {
                return new HashMap<String, Object>() {{
                    put("mailRequester", new MailRequester());
                }};
            }
        });
    }

    @After
    public void tearDown() throws Throwable {
        SystemRepository.clear();
    }

    /**
     * {@link MailUtil#getMailRequester()}のテスト。
     */
    @Test
    public void testGetMailRequester(){
        assertTrue(MailUtil.getMailRequester() instanceof MailRequester);
    }

    /**
     * {@link MailUtil#getMailRequester()}のテスト。
     * <p/>
     * リポジトリに値が無い場合、例外を送出するか。
     */
    @Test
    public void testGetMailRequesterErr(){
        SystemRepository.clear();
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("specified mailRequester is not registered in SystemRepository.");

        MailUtil.getMailRequester();
    }
}
