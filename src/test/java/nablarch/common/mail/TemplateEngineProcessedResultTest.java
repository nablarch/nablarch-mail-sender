package nablarch.common.mail;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import org.junit.Test;

/**
 * {@link TemplateEngineProcessedResult}のテストクラス。
 */
public class TemplateEngineProcessedResultTest {

    /**
     * 改行コードがLFの場合にファクトリーメソッドが機能すること。
     */
    @Test
    public void testValueOf_LF() {
        TemplateEngineProcessedResult sut = TemplateEngineProcessedResult
                .valueOf("aaa\n\nbbb\nccc\nddd");
        assertThat(sut.getSubject(), is("aaa"));
        assertThat(sut.getMailBody(), is("bbb\nccc\nddd"));
    }

    /**
     * 改行コードがCRLFの場合にファクトリーメソッドが機能すること。
     */
    @Test
    public void testValueOf_CRLF() {
        TemplateEngineProcessedResult sut = TemplateEngineProcessedResult
                .valueOf("aaa\r\n\r\nbbb\r\nccc\r\nddd");
        assertThat(sut.getSubject(), is("aaa"));
        assertThat(sut.getMailBody(), is("bbb\r\nccc\r\nddd"));
    }

    /**
     * 件名だけの場合にファクトリーメソッドが機能すること。
     */
    @Test
    public void testValueOf_subject_only() {
        TemplateEngineProcessedResult sut = TemplateEngineProcessedResult
                .valueOf("aaa");
        assertThat(sut.getSubject(), is("aaa"));
        assertThat(sut.getMailBody(), is(""));
    }

    /**
     * 本文だけの場合にファクトリーメソッドが機能すること。
     */
    @Test
    public void testValueOf_body_only() {
        TemplateEngineProcessedResult sut = TemplateEngineProcessedResult
                .valueOf("\r\n\r\nbbb\r\nccc\r\nddd");
        assertThat(sut.getSubject(), is(""));
        assertThat(sut.getMailBody(), is("bbb\r\nccc\r\nddd"));
    }

    /**
     * サロゲートペアを含む場合にファクトリーメソッドが機能すること。
     */
    @Test
    public void testValueOf_surrogate_pair() {
        TemplateEngineProcessedResult sut = TemplateEngineProcessedResult
                .valueOf("aaa\uD867\uDE3D\r\n\r\nbbb\uD867\uDE3D");
        assertThat(sut.getSubject(), is("aaa\uD867\uDE3D"));
        assertThat(sut.getMailBody(), is("bbb\uD867\uDE3D"));
    }
}
