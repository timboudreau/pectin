/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.pectin;

import com.google.inject.Singleton;
import com.mastfrog.util.Strings;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Borrowed and modified to handle path parameters, from Acteur.
 *
 * @author Tim Boudreau
 */
@Singleton
class PathPatterns {

    public static final Pattern PARAM_PATTERN = Pattern.compile(":[^\\/]+");
    private final Map<String, Boolean> matchCache = new ConcurrentHashMap<>();
    private final Map<String, String> exactPathForRegex = new ConcurrentHashMap<>();
    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, String>> paramPositionsForGlob = new ConcurrentHashMap<>();
    private final Map<String, String> patternForGlob = new ConcurrentHashMap<>();
    private static final String INVALID = "::////";

    public Pattern patternFor(String glob, Map<Integer, String> nameForPosition) {
        String result = patternFromGlob(glob, nameForPosition);
        return getPattern(result);
    }

    public String patternFromGlob(String pattern, Map<Integer, String> paramPositions) {
        String result = patternForGlob.get(pattern);
        if (result != null) {
            Map<Integer, String> nameForPosition = paramPositionsForGlob.get(pattern);
            if (nameForPosition != null) {
                paramPositions.putAll(nameForPosition);;
            }
            return result;
        }
        String origPattern = pattern;
        if (pattern.length() > 0 && pattern.charAt(0) == '/') {
            pattern = pattern.substring(1);
        }
        if (pattern.indexOf(':') >= 0) {
            Map<Integer, String> m = new LinkedHashMap<>(4);
            StringBuilder sb = new StringBuilder(pattern.length());
            CharSequence[] seqs = Strings.split('/', pattern);
            for (int i = 0; i < seqs.length; i++) {
                CharSequence seq = seqs[i];
                if (seq.length() > 0 && seq.charAt(0) == ':') {
                    sb.append('*');
                    String name = seq.subSequence(1, seq.length()).toString();
                    m.put(i, name);
                } else {
                    sb.append(seq);
                }
                if (i != seqs.length - 1) {
                    sb.append('/');
                }
            }
            pattern = sb.toString();
            paramPositionsForGlob.put(origPattern, m);
            paramPositions.putAll(m);
            System.out.println("POSITIONS IN " + origPattern + ": " + m + ", converted to " + pattern);
        }
        StringBuilder match = new StringBuilder("^\\/?");
        for (char c : pattern.toCharArray()) {
            switch (c) {
                case '$':
                case '.':
                case '{':
                case '}':
                case '[':
                case ']':
                case ')':
                case '(':
                case '^':
                case '/':
                    match.append("\\").append(c);
                    break;
                case '*':
                    match.append("[^\\/]*?");
                    break;
                case '?':
                    match.append("[^\\/]?");
                    break;
                default:
                    match.append(c);
            }
        }
        match.append("$");
        result = match.toString();
        patternForGlob.put(origPattern, result);
        return result;
    }

    String exactPathForRegex(String regex) {
        String result = exactPathForRegex.get(regex);
        if (result != null) {
            if (!INVALID.equals(result)) {
                return result;
            } else {
                return null;
            }
        }
        StringBuilder sb = new StringBuilder();
        char[] chars = regex.toCharArray();
        boolean precedingWasBackslash = false;
        boolean endMarkerFound = false;
        boolean startMarkerFound = false;
        loop:
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (i == 0 && c == '^') {
                continue;
            }
            if (i == chars.length - 1 && c == '$') {
                endMarkerFound = true;
                continue;
            }
            if (i == 0 && c == '^') {
                startMarkerFound = true;
            }
            switch (c) {
                case '\\':
                    if (i != chars.length - 1) {
                        precedingWasBackslash = true;
                    }
                    break;
                case '*':
                    if (precedingWasBackslash) {
                        sb.append(c);
                        continue;
                    }
                case '[':
                case '+':
                case '?':
                case '^':
                case '$':
                case '&':
                    exactPathForRegex.put(regex, INVALID);
                    return null;
                default:
                    sb.append(c);
            }
            precedingWasBackslash = c == '\\';
        }
        if (!endMarkerFound || !startMarkerFound) {
            exactPathForRegex.put(regex, INVALID);
            return null;
        }
        if (sb.length() > 0 && sb.charAt(0) == '/') {
            result = sb.substring(1);
        } else {
            result = sb.toString();
        }
        exactPathForRegex.put(regex, result);
        return result;
    }

    boolean isExactGlob(String s) {
        Boolean match = matchCache.get(s);
        if (match != null) {
            return match;
        }
        boolean result = true;
        for (char c : s.toCharArray()) {
            if ('*' == c || ':' == c) {
                result = false;
                break;
            }
        }
        matchCache.put(s, result);
        return result;
    }

    Pattern getPattern(String regex) {
        Pattern result = patternCache.get(regex);
        if (result == null) {
            result = Pattern.compile(regex);
            patternCache.put(regex, result);
        }
        return result;
    }

}
