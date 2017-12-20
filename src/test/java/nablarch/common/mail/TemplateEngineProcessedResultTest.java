package nablarch.common.mail;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * {@link TemplateEngineProcessedResult}のテストクラス。
 */
public class TemplateEngineProcessedResultTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    /**
     * 改行コードがLFの場合にファクトリーメソッドが機能すること。
     */
    @Test
    public void testValueOf_LF() {
        TemplateEngineProcessedResult sut = TemplateEngineProcessedResult
                .valueOf("aaa\n---\nbbb\nccc\nddd");
        assertThat(sut.getSubject(), is("aaa"));
        assertThat(sut.getMailBody(), is("bbb\nccc\nddd"));
    }

    /**
     * 改行コードがCRLFの場合にファクトリーメソッドが機能すること。
     */
    @Test
    public void testValueOf_CRLF() {
        TemplateEngineProcessedResult sut = TemplateEngineProcessedResult
                .valueOf("aaa\r\n---\r\nbbb\r\nccc\r\nddd");
        assertThat(sut.getSubject(), is("aaa"));
        assertThat(sut.getMailBody(), is("bbb\r\nccc\r\nddd"));
    }

    /**
     * 改行コードがCRの場合にファクトリーメソッドが機能すること。
     */
    @Test
    public void testValueOf_CR() {
        TemplateEngineProcessedResult sut = TemplateEngineProcessedResult
                .valueOf("aaa\r---\rbbb\rccc\rddd");
        assertThat(sut.getSubject(), is("aaa"));
        assertThat(sut.getMailBody(), is("bbb\rccc\rddd"));
    }

    /**
     * 本文が無い場合にファクトリーメソッドが例外を投げること。
     */
    @Test
    public void testValueOf_thrown_exception_mailBody_not_found() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Mail body is not found");

        TemplateEngineProcessedResult.valueOf("subject only\r\n---\r\n");
    }

    /**
     * 件名が無い場合にファクトリーメソッドが例外を投げること。
     */
    @Test
    public void testValueOf_thrown_exception_subject_not_found() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Subject is not found");

        TemplateEngineProcessedResult.valueOf("\r\n---\r\nbbb\r\nccc\r\nddd");
    }

    /**
     * デリミタが無い場合にファクトリーメソッドが例外を投げること。
     */
    @Test
    public void testValueOf_thrown_exception_delimiter_not_found() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Delimiter(---) is not found");

        TemplateEngineProcessedResult.valueOf("aaa\r\n\r\nbbb\r\nccc\r\nddd");
    }

    /**
     * 件名が改行を含む場合にファクトリーメソッドが例外を投げること。
     */
    @Test
    public void testValueOf_thrown_exception_subject_break_line() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Subject must not have new line");

        TemplateEngineProcessedResult.valueOf("aaa\r\naaa\r\n---\r\nbbb\r\nccc\r\nddd");
    }

    /**
     * サロゲートペアを含む場合にファクトリーメソッドが機能すること。
     */
    @Test
    public void testValueOf_surrogate_pair() {
        TemplateEngineProcessedResult sut = TemplateEngineProcessedResult
                .valueOf("aaa\uD867\uDE3D\r\n---\r\nbbb\uD867\uDE3D");
        assertThat(sut.getSubject(), is("aaa\uD867\uDE3D"));
        assertThat(sut.getMailBody(), is("bbb\uD867\uDE3D"));
    }

    /**
     * 本文にデリミタと同じ文字列の行があってもファクトリーメソッドが機能すること。
     */
    @Test
    public void testValueOf_same_line_as_delimiter() {
        TemplateEngineProcessedResult sut = TemplateEngineProcessedResult
                .valueOf("aaa\r\n---\r\nbbb\r\n---\r\nddd");
        assertThat(sut.getSubject(), is("aaa"));
        assertThat(sut.getMailBody(), is("bbb\r\n---\r\nddd"));
    }

    /**
     * デリミタより前（件名を期待するエリア）では空行は無視されること。
     */
    @Test
    public void testValueOf_ignore_empty_line_in_before_delemiter() {
        TemplateEngineProcessedResult sut = TemplateEngineProcessedResult
                .valueOf("\r\naaa\r\n\r\n---\r\nbbb\r\nccc\r\nddd");
        assertThat(sut.getSubject(), is("aaa"));
        assertThat(sut.getMailBody(), is("bbb\r\nccc\r\nddd"));
    }

    /**
     * デリミタより後（本文のエリア）では空行はそのまま本文に含まれること。
     */
    @Test
    public void testValueOf_contain_empty_line_in_after_delemiter() {
        TemplateEngineProcessedResult sut = TemplateEngineProcessedResult
                .valueOf("aaa\r\n---\r\n\r\nbbb\r\n\r\nccc\r\n\r\nddd\r\n");
        assertThat(sut.getSubject(), is("aaa"));
        assertThat(sut.getMailBody(), is("\r\nbbb\r\n\r\nccc\r\n\r\nddd\r\n"));
    }

    /**
     * ファクトリーメソッドにデリミタを指定できること
     */
    @Test
    public void testValueOf_with_delimiter() {
        TemplateEngineProcessedResult sut = TemplateEngineProcessedResult
                .valueOf("aaa\r\n***\r\nbbb\r\nccc\r\nddd", "***");
        assertThat(sut.getSubject(), is("aaa"));
        assertThat(sut.getMailBody(), is("bbb\r\nccc\r\nddd"));
    }
}
