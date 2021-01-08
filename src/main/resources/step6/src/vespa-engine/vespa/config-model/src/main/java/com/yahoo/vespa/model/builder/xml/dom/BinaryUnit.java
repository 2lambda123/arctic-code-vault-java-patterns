// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. (rank 298) copied from https://github.com/vespa-engine/vespa/blob/f8dfd639931905a9d6dae95f5b4e0ae812042117/config-model/src/main/java/com/yahoo/vespa/model/builder/xml/dom/BinaryUnit.java
package com.yahoo.vespa.model.builder.xml.dom;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.yahoo.text.Lowercase.toLowerCase;

/**
 * @author Tony Vaagenes
 */
public class BinaryUnit {
    //The pattern must match the one given in the schema
    private static Pattern pattern = Pattern.compile("(\\d+(\\.\\d*)?)\\s*([kmgKMG])?");

    public static double valueOf(String valueString) {
        Matcher matcher = pattern.matcher(valueString);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Value '" + valueString + "' does not match the pattern for binary unit");
        }
        double value = Double.valueOf(matcher.group(1));
        String unit = matcher.group(3);
        if (unit != null) {
            value *= unitToValue(toLowerCase(unit).charAt(0));
        }
        return value;
    }

    private static double unitToValue(char unit) {
           final char units[] = {'k', 'm', 'g'};
           for (int i=0; i<units.length; ++i) {
               if (units[i] == unit) {
                   return Math.pow(2, 10*(i+1));
               }
           }

           throw new RuntimeException("No such unit: '" + unit + "'");
    }
}
