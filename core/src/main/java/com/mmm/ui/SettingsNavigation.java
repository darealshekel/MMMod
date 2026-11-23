package com.mmm.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure settings navigation and search; rows retain the adapter's config/action identity. */
public final class SettingsNavigation
{
    private SettingsNavigation() {}

    public interface Row
    {
        String label();
        String description();
        String configName();
        boolean heading();
    }

    public interface Section<R extends Row>
    {
        String title();
        String description();
        String category();
        String categoryLabel();
        List<R> rows();
    }

    public static final class Page<R extends Row, S extends Section<R>> {
        private final String title;
        private final S source;
        private final List<R> rows;
        private final List<Page<R, S>> children;
        public Page(String title, S source, List<R> rows, List<Page<R, S>> children) {
            this.title = title;
            this.source = source;
            this.rows = immutable(rows);
            this.children = immutable(children);
        }
        public String title() { return title; }
        public S source() { return source; }
        public List<R> rows() { return rows; }
        public List<Page<R, S>> children() { return children; }

        /** Prefer the direct Everyday settings category, falling back to the first child or this page. */
        public Page<R, S> initialCategory() {
            for (Page<R, S> child : children)
                if ("Everyday settings".equals(child.title)) return child;
            return children.isEmpty() ? this : children.get(0);
        }

        /** Existing pages with rows, in display order: this page first, then each child subtree. */
        public List<Page<R, S>> rowGroups() {
            List<Page<R, S>> groups = new ArrayList<>();
            collectRowGroups(groups);
            return Collections.unmodifiableList(groups);
        }

        private void collectRowGroups(List<Page<R, S>> groups) {
            if (!rows.isEmpty()) groups.add(this);
            for (Page<R, S> child : children) child.collectRowGroups(groups);
        }
    }

    public static final class Match<R extends Row, S extends Section<R>> {
        private final S section;
        private final List<R> rows;
        public Match(S section, List<R> rows) { this.section = section; this.rows = immutable(rows); }
        public S section() { return section; }
        public List<R> rows() { return rows; }
    }

    private static <T> List<T> immutable(List<T> items) {
        return items.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(items));
    }

    public static <R extends Row, S extends Section<R>> Page<R, S> build(List<S> sections)
    {
        List<Page<R, S>> root = new ArrayList<>();
        Map<String, List<Page<R, S>>> categories = new LinkedHashMap<>();
        for (S section : sections)
        {
            if ("Tools".equals(section.title())) {
                List<Page<R, S>> tools = new ArrayList<>();
                for (R row : section.rows()) {
                    if (ToolDirectory.find(row.label()) != null)
                        tools.add(new Page<>(row.label(), section, Collections.singletonList(row), Collections.emptyList()));
                }
                if (!tools.isEmpty()) root.add(new Page<>("Tools", section, Collections.emptyList(), tools));
                continue;
            }
            Page<R, S> page = pageForSection(section);
            String category;
            switch (section.category()) {
                case "GENERAL": category = section.title().equals("Everyday settings") ? "" : "Digging & goals"; break;
                case "NOTIFICATIONS": category = "Digging & goals"; break;
                case "HUD": case "COLORS": category = "HUD"; break;
                case "VISUALS": case "BLOCK_ESP": case "SPEED_GRAPH": category = "Mining visuals"; break;
                default: category = "";
            }
            if (category.isEmpty()) root.add(page);
            else categories.computeIfAbsent(category, key -> new ArrayList<>()).add(page);
        }
        int index = Math.min(1, root.size());
        for (Map.Entry<String,List<Page<R,S>>> category : categories.entrySet())
            root.add(index++, new Page<>(category.getKey(), null, Collections.emptyList(), category.getValue()));
        return new Page<>("Settings", null, Collections.emptyList(), root);
    }

    private static <R extends Row, S extends Section<R>> Page<R, S> pageForSection(S section)
    {
        List<Page<R, S>> topics = new ArrayList<>();
        List<R> rows = new ArrayList<>();
        String title = section.title();
        for (R row : section.rows())
        {
            if (row.heading())
            {
                addTopic(topics, section, title, rows);
                rows = new ArrayList<>();
                title = friendlyHeading(row.label());
            }
            else
            {
                String subtopic;
                switch (row.label()) {
                    case "MMM Header": subtopic = "HUD heading"; break;
                    case "Mining Totals": subtopic = "Mining totals"; break;
                    case "Mining Speed": subtopic = "Speed & time"; break;
                    case "Goal ETA": subtopic = "Goal display"; break;
                    case "Main Background": subtopic = "HUD background"; break;
                    default: subtopic = null;
                }
                if (section.title().equals("HUD content") && subtopic != null)
                {
                    addTopic(topics, section, title, rows);
                    rows = new ArrayList<>();
                    title = subtopic;
                }
                rows.add(row);
            }
        }
        addTopic(topics, section, title, rows);
        if (topics.size() == 1) return new Page<>(section.title(), section, topics.get(0).rows(), Collections.emptyList());
        return new Page<>(section.title(), section, Collections.emptyList(), topics);
    }

    private static <R extends Row, S extends Section<R>> void addTopic(List<Page<R, S>> topics, S section, String title, List<R> rows)
    {
        if (!rows.isEmpty()) topics.add(new Page<>(title, section, rows, Collections.emptyList()));
    }

    public static <R extends Row, S extends Section<R>> List<Match<R, S>> search(List<S> sections, String query)
    {
        query = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Match<R, S>> result = new ArrayList<>();
        for (S section : sections)
        {
            if (query.isEmpty() || matches(query, section.title(), section.description(), section.categoryLabel()))
            {
                result.add(new Match<>(section, section.rows()));
                continue;
            }
            List<R> found = new ArrayList<>();
            List<R> rows = section.rows();
            int index = 0;
            while (index < rows.size())
            {
                R row = rows.get(index++);
                if (!row.heading())
                {
                    if (matches(row, query)) found.add(row);
                    continue;
                }
                boolean headingMatches = matches(row, query);
                List<R> group = new ArrayList<>();
                while (index < rows.size() && !rows.get(index).heading())
                {
                    R child = rows.get(index++);
                    if (headingMatches || matches(child, query)) group.add(child);
                }
                if (headingMatches || !group.isEmpty())
                {
                    found.add(row);
                    found.addAll(group);
                }
            }
            if (!found.isEmpty()) result.add(new Match<>(section, found));
        }
        return result;
    }

    private static boolean matches(Row row, String query)
    {
        return matches(query, row.label(), row.description(), row.configName());
    }

    private static boolean matches(String query, String... values)
    {
        for (String value : values)
            if (value != null && value.toLowerCase(Locale.ROOT).contains(query)) return true;
        return false;
    }

    public static String friendlyHeading(String title)
    {
        if (title.isEmpty()) return title;
        String lower = title.toLowerCase(Locale.ROOT);
        return (Character.toUpperCase(lower.charAt(0)) + lower.substring(1))
                .replaceAll("(?i)\\bhud\\b", "HUD").replace("esp", "highlight");
    }

    public static int visibleHeight(int y, int height, int clipTop, int clipBottom)
    {
        return Math.max(0, Math.min(y + height, clipBottom) - Math.max(y, clipTop));
    }
}
