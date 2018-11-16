package com.iota.iri.service.dto;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
  * 
  * Every response that the IRI API gives is a child of this class.<br/>
  * Duration for every response is recorded automatically during the processing of a request.
  *
  **/
public abstract class AbstractResponse {

    /**
     * An 'empty' Response class.
     * Will only contain values which are included in {@link AbstractResponse} itself.
     * This is used when an API command does not need to return data.
     */
	private static class Emptyness extends AbstractResponse {}

	/**
	 * The duration it took to process this command in milliseconds
	 */
    private Integer duration;

    /**
     * Builds a string representation of this object using multiple lines
     *
     * @return Returns a string representation of this object.
     */
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }

    
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this, false);
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj, false);
    }

    /**
     *
     * @return {@link #duration}
     */
    public Integer getDuration() {
        return duration;
    }

    /**
     * 
     * @param duration {@link #duration}
     */
    public void setDuration(Integer duration) {
		this.duration = duration;
	}

    /**
     * If a response doesn't need to send data back, an {@link Emptyness} is used.
     * This has all the fields and functions of an AbstractResponse, and nothing more.
     * 
     * @return Returns a plain AbstractResponse without extra fields
     */
    public static AbstractResponse createEmptyResponse() {
    	return new Emptyness();
    }

}
