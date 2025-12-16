package com.university.timetable.util;

/**
 * Levenshtein distance utility for fuzzy string matching.
 * 
 * Based on specs.md Excel Validation Protocol:
 * - Match > 90%: Auto-correct
 * - Match < 60%: Create new
 */
public class LevenshteinMatcher {

    /**
     * Calculate the Levenshtein distance between two strings.
     */
    public static int distance(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return Integer.MAX_VALUE;
        }
        
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                    dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                    );
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * Calculate similarity as a ratio from 0.0 to 1.0.
     * 1.0 means identical, 0.0 means completely different.
     */
    public static double similarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        if (s1.equals(s2)) {
            return 1.0;
        }
        
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) {
            return 1.0;
        }
        
        int distance = distance(s1.toLowerCase(), s2.toLowerCase());
        return 1.0 - ((double) distance / maxLen);
    }

    /**
     * Find the best match from a list of candidates.
     * Returns null if no match meets the minimum threshold.
     */
    public static String findBestMatch(String input, Iterable<String> candidates, double minSimilarity) {
        String bestMatch = null;
        double bestScore = minSimilarity;
        
        for (String candidate : candidates) {
            double score = similarity(input, candidate);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = candidate;
            }
        }
        
        return bestMatch;
    }
}
