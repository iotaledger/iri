package com.trias.resouce.body.request;

public class RegisterOauthRequest extends OauthLoginRequestBody {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private String account;

	private String sex;

	private String email;

	public String getAccount() {
		return account;
	}

	public void setAccount(String account) {
		this.account = account;
	}

	public String getSex() {
		return sex;
	}

	public void setSex(String sex) {
		this.sex = sex;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

}
