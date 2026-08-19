package com.mmm.social;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MmmChatCensor
{
    private static final String TERMS_RESOURCE = "/assets/mmm/chat-censor-terms.txt";
    private static final List<BlockedTerm> BLOCKED_TERMS = loadTerms();

    private MmmChatCensor()
    {
    }

    public static String censor(String message)
    {
        if (message == null || message.isBlank() || BLOCKED_TERMS.isEmpty())
        {
            return message;
        }

        IndexedText indexed = canonicalize(message);
        if (indexed.codePoints().length == 0)
        {
            return message;
        }

        boolean[] censored = new boolean[message.length()];
        for (BlockedTerm term : BLOCKED_TERMS)
        {
            markMatches(message, indexed, term, censored);
        }

        StringBuilder result = new StringBuilder(message.length());
        for (int index = 0; index < message.length(); )
        {
            int codePoint = message.codePointAt(index);
            int charCount = Character.charCount(codePoint);
            boolean mask = false;
            for (int offset = 0; offset < charCount; offset++)
            {
                mask |= censored[index + offset];
            }
            result.append(mask && Character.isWhitespace(codePoint) == false ? '*' : new String(Character.toChars(codePoint)));
            index += charCount;
        }
        return result.toString();
    }

    static int loadedTermCount()
    {
        return BLOCKED_TERMS.size();
    }

    private static void markMatches(String message, IndexedText indexed, BlockedTerm term, boolean[] censored)
    {
        int[] input = indexed.codePoints();
        int[] needle = term.codePoints();
        for (int start = 0; start <= input.length - needle.length; start++)
        {
            if (matchesAt(input, needle, start) == false)
            {
                continue;
            }

            int originalStart = indexed.starts()[start];
            int originalEnd = indexed.ends()[start + needle.length - 1];
            if (term.requiresWordBoundary() && hasWordBoundaries(message, originalStart, originalEnd) == false)
            {
                continue;
            }
            if (hasReasonableGaps(message, indexed, start, needle.length) == false)
            {
                continue;
            }
            for (int index = originalStart; index < originalEnd; index++)
            {
                censored[index] = true;
            }
        }
    }

    private static boolean matchesAt(int[] input, int[] needle, int start)
    {
        for (int index = 0; index < needle.length; index++)
        {
            if (input[start + index] != needle[index])
            {
                return false;
            }
        }
        return true;
    }

    private static boolean hasWordBoundaries(String message, int start, int end)
    {
        boolean startsAtBoundary = start == 0
                || Character.isLetterOrDigit(message.codePointBefore(start)) == false;
        boolean endsAtBoundary = end >= message.length()
                || Character.isLetterOrDigit(message.codePointAt(end)) == false;
        return startsAtBoundary && endsAtBoundary;
    }

    private static boolean hasReasonableGaps(String message, IndexedText indexed, int start, int length)
    {
        for (int index = start; index < start + length - 1; index++)
        {
            int gapStart = indexed.ends()[index];
            int gapEnd = indexed.starts()[index + 1];
            if (gapEnd - gapStart > 3 || containsLetterOrDigit(message, gapStart, gapEnd))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean containsLetterOrDigit(String value, int start, int end)
    {
        for (int index = start; index < end; )
        {
            int codePoint = value.codePointAt(index);
            if (Character.isLetterOrDigit(codePoint))
            {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static IndexedText canonicalize(String value)
    {
        List<Integer> codePoints = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        for (int originalIndex = 0; originalIndex < value.length(); )
        {
            int originalCodePoint = value.codePointAt(originalIndex);
            int originalEnd = originalIndex + Character.charCount(originalCodePoint);
            String normalized = Normalizer.normalize(
                    new String(Character.toChars(originalCodePoint)).toLowerCase(Locale.ROOT),
                    Normalizer.Form.NFKD);
            for (int normalizedIndex = 0; normalizedIndex < normalized.length(); )
            {
                int normalizedCodePoint = normalized.codePointAt(normalizedIndex);
                normalizedIndex += Character.charCount(normalizedCodePoint);
                if (isCombiningMark(normalizedCodePoint))
                {
                    continue;
                }
                int canonical = canonicalCodePoint(normalizedCodePoint);
                if (Character.isLetterOrDigit(canonical))
                {
                    codePoints.add(canonical);
                    starts.add(originalIndex);
                    ends.add(originalEnd);
                }
            }
            originalIndex = originalEnd;
        }
        return new IndexedText(toArray(codePoints), toArray(starts), toArray(ends));
    }

    private static boolean isCombiningMark(int codePoint)
    {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    private static int canonicalCodePoint(int codePoint)
    {
        return switch (codePoint)
        {
            case '0' -> 'o';
            case '1', '!' -> 'i';
            case '3' -> 'e';
            case '4', '@' -> 'a';
            case '5', '$' -> 's';
            case '7' -> 't';
            case '8' -> 'b';
            case '9' -> 'g';
            default -> codePoint;
        };
    }

    private static List<BlockedTerm> loadTerms()
    {
        Set<String> terms = new LinkedHashSet<>();
        try (InputStream stream = MmmChatCensor.class.getResourceAsStream(TERMS_RESOURCE))
        {
            if (stream != null)
            {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
                {
                    reader.lines()
                            .map(String::trim)
                            .filter(line -> line.isBlank() == false && line.startsWith("#") == false)
                            .forEach(terms::add);
                }
            }
        }
        catch (Exception ignored)
        {
        }

        List<BlockedTerm> blockedTerms = new ArrayList<>();
        for (String term : terms)
        {
            IndexedText canonical = canonicalize(term);
            if (canonical.codePoints().length >= 2)
            {
                blockedTerms.add(new BlockedTerm(
                        canonical.codePoints(),
                        containsCjkCodePoint(canonical.codePoints()) == false));
            }
        }
        blockedTerms.sort(Comparator.comparingInt((BlockedTerm term) -> term.codePoints().length).reversed());
        return List.copyOf(blockedTerms);
    }

    private static boolean containsCjkCodePoint(int[] codePoints)
    {
        for (int codePoint : codePoints)
        {
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL)
            {
                return true;
            }
        }
        return false;
    }

    private static int[] toArray(List<Integer> values)
    {
        int[] result = new int[values.size()];
        for (int index = 0; index < values.size(); index++)
        {
            result[index] = values.get(index);
        }
        return result;
    }

    private record IndexedText(int[] codePoints, int[] starts, int[] ends)
    {
    }

    private record BlockedTerm(int[] codePoints, boolean requiresWordBoundary)
    {
    }
}
