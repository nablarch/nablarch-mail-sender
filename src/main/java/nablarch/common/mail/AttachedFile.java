package nablarch.common.mail;

import java.io.File;

import nablarch.core.util.annotation.Published;

/**
 * メール添付ファイルの情報を保持するデータオブジェクト。
 * 
 * @author Shinsuke Yoshio
 */
public class AttachedFile {

    /** メール添付ファイルのContent-Type */
    private String contentType;

    /** メール添付ファイル */
    private File file;

    /**
     * メール添付ファイルのContent-Typeを指定し、AttachedFileオブジェクトを生成する。
     * 
     * @param contentType メール添付ファイルのContent-Type
     * @param file メール添付ファイル
     */
    @Published
    public AttachedFile(String contentType, File file) {
        super();
        this.contentType = contentType;
        this.file = file;
    }

    /**
     * AttachedFileオブジェクトを生成する。
     */
    @Published
    public AttachedFile() {
        super();
    }

    /**
     * メール添付ファイル名を取得する。
     * 
     * @return メール添付ファイル名
     */
    public String getName() {
        return file.getName();
    }

    /**
     * メール添付ファイルのContent-Typeを取得する。
     * 
     * @return Content-Type
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * メール添付ファイルのContent-Typeを設定する。
     * 
     * @param contentType Content-Type
     */
    @Published
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * メール添付ファイルを取得する。
     * 
     * @return メール添付ファイル
     */
    public File getFile() {
        return file;
    }

    /**
     * メール添付ファイルを設定する。
     * 
     * @param file メール添付ファイル
     */
    @Published
    public void setFile(File file) {
        this.file = file;
    }

}
