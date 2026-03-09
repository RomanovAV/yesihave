package org.avromanov.yesihave.persistence;

import java.util.StringJoiner;

public final class VectorSql {
    private VectorSql() {
    }

    public static String toVectorLiteral(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }
}
