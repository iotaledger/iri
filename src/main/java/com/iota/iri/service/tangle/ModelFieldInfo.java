package com.iota.iri.service.tangle;

/**
 * Created by paul on 3/6/17 for iri.
 */
public class ModelFieldInfo {
    public String name;
    public boolean hasMany;
    public Class<?> memberOf;
    public Class<?> belongsTo;
    public Class<?> owns;

    public ModelFieldInfo(String name, Class<?> memberOf, boolean hasMany, Class<?> reference, Class<?> owns) {
        this.memberOf = memberOf;
        this.name = name;
        this.belongsTo = reference;
        this.owns = owns;
        this.hasMany = hasMany;
    }
}
