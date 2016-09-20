package nablarch.common.mail;

import nablarch.core.util.annotation.Published;

/**
 * 宛先数が不正な場合に発生する例外クラス。
 * 
 * @author Shinsuke Yoshio
 */
@Published
public class RecipientCountException extends RuntimeException {

    /** 宛先数上限値 */
    private int maxRecipientCount;

    /** 実際の宛先数 */
    private int actualRecipientCount;

    /**
     * 宛先数上限値、実際の宛先数を指定し、{@code RecipientCountException}を生成する。
     * 
     * @param maxRecipientCount 宛先数上限値
     * @param actualRecipientCount 実際の宛先数
     */
    public RecipientCountException(int maxRecipientCount,
            int actualRecipientCount) {
        super(String.format(
                "number of recipients was invalid. max = [%s], actual = [%s]",
                maxRecipientCount, actualRecipientCount));

        this.maxRecipientCount = maxRecipientCount;
        this.actualRecipientCount = actualRecipientCount;
    }

    /**
     * 宛先数上限値を取得する。
     * 
     * @return 宛先数上限値
     */
    public int getMaxRecipientCount() {
        return maxRecipientCount;
    }

    /**
     * 実際の宛先数を取得する。
     * 
     * @return 実際の宛先数
     */
    public int getActualRecipientCount() {
        return actualRecipientCount;
    }

}
