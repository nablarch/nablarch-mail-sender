package nablarch.common.mail;

import nablarch.core.util.annotation.Published;

/**
 * 添付ファイルサイズ上限値オーバー時に発生する例外クラス。
 * 
 * @author Shinsuke Yoshio
 */
@Published
public class AttachedFileSizeOverException extends RuntimeException {

    /** 実際のファイルサイズ */
    private long actualFileSize;

    /** ファイルサイズ上限値 */
    private long maxFileSize;

    /**
     * 実際のファイルサイズ、ファイルサイズ上限値を指定し、{@code AttachedFileSizeOverException}を生成する。
     *
     * @param maxFileSize ファイルサイズ上限値
     * @param actualFileSize 実際のファイルサイズ
     */
    public AttachedFileSizeOverException(long maxFileSize, long actualFileSize) {
        super(String.format(
                "exceeded max attached file size. max = [%s], actual = [%s]",
                maxFileSize, actualFileSize));
        this.maxFileSize = maxFileSize;
        this.actualFileSize = actualFileSize;
    }

    /**
     * 実際のファイルサイズを取得する。
     * 
     * @return 実際のファイルサイズ
     */
    public long getActualFileSize() {
        return actualFileSize;
    }

    /**
     * ファイルサイズ上限値を取得する。
     * 
     * @return ファイルサイズ上限値
     */
    public long getMaxFileSize() {
        return maxFileSize;
    }

}
