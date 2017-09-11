package com.iota.iri.hash;

/**
 * Created by paul on 7/27/17.
 */
public abstract class SpongeFactory {
    public enum Mode {
        CURL,
        KERL,
        BCURLT
    }
    public static Curl create(Mode mode){
        switch (mode) {
            case CURL: return new Curl();
            case KERL: return new Kerl();
            case BCURLT: return new Curl(true);
            default: return null;
        }
    }
}
