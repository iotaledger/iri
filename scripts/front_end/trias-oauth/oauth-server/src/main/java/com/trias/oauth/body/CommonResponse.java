package com.trias.oauth.body;

import java.io.Serializable;

public class CommonResponse implements Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private int code;

    private String message;

    private Object data;

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public static CommonResponse CreateSuccessResponse(String message, Object data){
        CommonResponse response = new CommonResponse();
        response.setCode(1);
        response.setMessage(message);
        response.setData(data);
        return response;
    }

    public static CommonResponse CreateErrorResponse(String message, Object data){
        CommonResponse response = new CommonResponse();
        response.setCode(0);
        response.setMessage(message);
        response.setData(data);
        return response;
    }
}