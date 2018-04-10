package com.denizenscript.denizen2core.scripts;

import com.denizenscript.denizen2core.utilities.CoreUtilities;

import java.util.List;

public class ScriptHelper {

    public static String clearComments(String input) {
        StringBuilder result = new StringBuilder(input.length());
        List<String> lines = CoreUtilities.split(input.replace("\t", "    ").replace("\r", ""), '\n');
        for (String l : lines) {
            String line = l.trim();
            if (!line.startsWith("#") && !line.equals("}")) {
                String liner = l.replace('\0', ' ').replaceAll("\\s+$", "");
                if (!line.endsWith(":") && line.startsWith("-")) {
                    liner = liner.replace(": ", "<unescape[&co]> ");
                    liner = liner.replace("#", "<unescape[&ns]>");
                    if (liner.endsWith(" {")) {
                        liner = liner.substring(0, liner.length() - 2) + ":";
                    }
                }
                else if (line.endsWith(":") && !line.startsWith("-")) {
                    liner = liner.replace("&", "&amp");
                    liner = liner.replace(".", "&dot");
                }
                result.append(liner).append("\n");
            }
            else {
                result.append("\n");
            }
        }
        result.append("\n");
        return result.toString();
    }

    /**
     * Compares two version strings.
     *
     * Use this instead of String.compareTo() for a non-lexicographical
     * comparison that works for version strings. e.g. "1.10".compareTo("1.6").
     *
     * @note It does not work if "1.10" is supposed to be equal to "1.10.0".
     *
     * @param str1 a string of ordinal numbers separated by decimal points.
     * @param str2 a string of ordinal numbers separated by decimal points.
     * @return The result is a negative integer if str1 is _numerically_ less than str2.
     *         The result is a positive integer if str1 is _numerically_ greater than str2.
     *         The result is zero if the strings are _numerically_ equal.
     */
    public static int versionCompare(String str1, String str2) {
        String[] vals1 = str1.split("\\.");
        String[] vals2 = str2.split("\\.");
        int i = 0;
        // set index to first non-equal ordinal or length of shortest version string
        while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
            i++;
        }
        // compare first non-equal ordinal number
        if (i < vals1.length && i < vals2.length) {
            int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
            return Integer.signum(diff);
        }
        // the strings are equal or one string is a substring of the other
        // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
        return Integer.signum(vals1.length - vals2.length);
    }
}
