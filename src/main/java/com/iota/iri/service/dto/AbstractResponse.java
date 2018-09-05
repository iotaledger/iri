package com.iota.iri.service.dto;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
  * 
  * Abstract response.
  *
  **/
public abstract class AbstractResponse {

	private static class Emptyness extends AbstractResponse {}

    private Integer duration;

    /**
     * Returns a String that represents this object.
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
     * The duration it took to process this command in milliseconds
     *
     * @return The duration.
     */
    public Integer getDuration() {
        return duration;
    }

    /**
     * Sets the duration.
     *
     * @param duration The duration
     */
    public void setDuration(Integer duration) {
		this.duration = duration;
	}

    /**
     * Returns an empty AbstractResponse
     *
     * @return Returns an empty AbstractResponse
     */
    public static AbstractResponse createEmptyResponse() {
    	return new Emptyness();
    }

}
