package it.doqui.index.ecmengine.mtom.dto;

public class FileReport extends MtomEngineDto {
    private static final long serialVersionUID = 5719651966051480138L;

    private FileFormatInfo[] formats;
    private boolean damaged;
    private boolean passwordProtected;
    private int signatures;

    public boolean isDamaged() {
        return damaged;
    }

    public void setDamaged(boolean damaged) {
        this.damaged = damaged;
    }

    public boolean isPasswordProtected() {
        return passwordProtected;
    }

    public void setPasswordProtected(boolean passwordProtected) {
        this.passwordProtected = passwordProtected;
    }

    public FileFormatInfo[] getFormats() {
        return formats;
    }

    public void setFormats(FileFormatInfo[] formats) {
        this.formats = formats;
    }

    public int getSignatures() {
        return signatures;
    }

    public void setSignatures(int signatures) {
        this.signatures = signatures;
    }

    public String toString() {
        return "FileReport [formats=" + (formats!= null && formats.length > 0) + "]";
    }
}
