package org.influxdb.querybuilder;

public class Utility {

    static StringBuilder addSemicolonIfMissing(final StringBuilder stringBuilder) {
        int length = trimLast(stringBuilder);
        if (length == 0 || stringBuilder.charAt(length - 1) != ';') {
            stringBuilder.append(';');
        }
        return stringBuilder;
    }

    static int trimLast(final StringBuilder stringBuilder) {
        int length = stringBuilder.length();
        while (length > 0 && stringBuilder.charAt(length - 1) <= ' ') {
            length -= 1;
        }
        if (length != stringBuilder.length()) {
            stringBuilder.setLength(length);
        }
        return length;
    }
}
