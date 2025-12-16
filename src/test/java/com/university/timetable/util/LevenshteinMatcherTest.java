package com.university.timetable.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for LevenshteinMatcher fuzzy matching.
 * Based on specs.md fuzzy matching thresholds:
 * - Match > 90%: Auto-correct
 * - Match < 60%: Create new
 */
class LevenshteinMatcherTest {

    @Test
    void similarity_identicalStrings_returns1() {
        assertThat(LevenshteinMatcher.similarity("Computer Science", "Computer Science"))
            .isEqualTo(1.0);
    }

    @Test
    void similarity_completelyDifferent_returnsLow() {
        double similarity = LevenshteinMatcher.similarity("ABC", "XYZ");
        assertThat(similarity).isLessThan(0.5);
    }

    @Test
    void similarity_singleTypo_returnsHigh() {
        // "Computar Science" vs "Computer Science" - one character difference
        double similarity = LevenshteinMatcher.similarity("Computar Science", "Computer Science");
        assertThat(similarity).isGreaterThan(0.9);
    }

    @Test
    void similarity_caseInsensitive() {
        double similarity = LevenshteinMatcher.similarity("ENGINEERING", "engineering");
        assertThat(similarity).isEqualTo(1.0);
    }

    @Test
    void distance_identicalStrings_returns0() {
        assertThat(LevenshteinMatcher.distance("test", "test")).isEqualTo(0);
    }

    @Test
    void distance_oneCharacterDifference_returns1() {
        assertThat(LevenshteinMatcher.distance("cat", "bat")).isEqualTo(1);
    }

    @Test
    void distance_insertion_returnsCorrectCount() {
        assertThat(LevenshteinMatcher.distance("test", "tests")).isEqualTo(1);
    }

    @Test
    void distance_deletion_returnsCorrectCount() {
        assertThat(LevenshteinMatcher.distance("tests", "test")).isEqualTo(1);
    }

    @Test
    void findBestMatch_returnsMatchAboveThreshold() {
        List<String> candidates = List.of("Computer Science", "Electrical Engineering", "Mathematics");
        
        String match = LevenshteinMatcher.findBestMatch("Computar Science", candidates, 0.9);
        
        assertThat(match).isEqualTo("Computer Science");
    }

    @Test
    void findBestMatch_returnsNullBelowThreshold() {
        List<String> candidates = List.of("Physics", "Chemistry", "Biology");
        
        String match = LevenshteinMatcher.findBestMatch("Computer Science", candidates, 0.9);
        
        assertThat(match).isNull();
    }

    @Test
    void similarity_nullInput_returns0() {
        assertThat(LevenshteinMatcher.similarity(null, "test")).isEqualTo(0.0);
        assertThat(LevenshteinMatcher.similarity("test", null)).isEqualTo(0.0);
    }

    @Test
    void similarity_emptyStrings_returns1() {
        assertThat(LevenshteinMatcher.similarity("", "")).isEqualTo(1.0);
    }
}
