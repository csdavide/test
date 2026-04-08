package it.doqui.index.ecmengine.mtom.dto;

public class SigilloSigner extends MtomEngineDto {
    private static final long serialVersionUID = 424125479895422806L;

    // delegato
    private String type = null;
    private String delegatedDomain = null;
    private String delegatedPassword = null;
    private String delegatedUser = null;

    // delegante
    private String user = null;
    private String otpPwd = null;
    private String typeHSM = null;
    private String typeOtpAuth = null;
    private String idenv = null;

    public String getType() {
	return type;
    }

    public void setType(String type) {
	this.type = type;
    }

    public String getDelegatedDomain() {
	return delegatedDomain;
    }

    public void setDelegatedDomain(String delegatedDomain) {
	this.delegatedDomain = delegatedDomain;
    }

    public String getDelegatedPassword() {
	return delegatedPassword;
    }

    public void setDelegatedPassword(String delegatedPassword) {
	this.delegatedPassword = delegatedPassword;
    }

    public String getDelegatedUser() {
	return delegatedUser;
    }

    public void setDelegatedUser(String delegatedUser) {
	this.delegatedUser = delegatedUser;
    }

    public String getUser() {
	return user;
    }

    public void setUser(String user) {
	this.user = user;
    }

    public String getOtpPwd() {
	return otpPwd;
    }

    public void setOtpPwd(String otpPwd) {
	this.otpPwd = otpPwd;
    }

    public String getTypeHSM() {
	return typeHSM;
    }

    public void setTypeHSM(String typeHSM) {
	this.typeHSM = typeHSM;
    }

    public String getTypeOtpAuth() {
	return typeOtpAuth;
    }

    public void setTypeOtpAuth(String typeOtpAuth) {
	this.typeOtpAuth = typeOtpAuth;
    }

    public String getIdenv() {return idenv;}

    public void setIdenv(String idenv) {this.idenv = idenv;}
}
