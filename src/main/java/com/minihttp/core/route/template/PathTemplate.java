package com.minihttp.core.route.template;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class PathTemplate {
    private enum SegmentType {
        VARIABLE,
        STATIC
    }

    private record Segment(SegmentType segmentType, String segment) {
        private static Segment create(String segment) {
            return new Segment(PathTemplate.getSegmentType(segment), segment);
        }
    }

    private static final String DEFAULT_SEPARATOR = "/";
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^/{}]+)}");

    private final String path;
    private final List<Segment> segments;

    private PathTemplate(String path, String separator) {
        this.path = path;
        this.segments = Stream.of(path.split(separator))
                              .map(Segment::create)
                              .toList();
    }

    public static PathTemplate create(String path) {
        return new PathTemplate(path, DEFAULT_SEPARATOR);
    }

    public static List<String> extractPathVariableNames(String path) {
        return PATH_VARIABLE.matcher(path)
                            .results()
                            .map(match -> match.group(1))
                            .toList();
    }

    public static boolean isSamePattern(PathTemplate pathTemplate1, PathTemplate pathTemplate2) {
        if (pathTemplate1.segments.size() != pathTemplate2.segments.size()) {
            return false;
        }

        int segmentAmount = pathTemplate1.segments.size();

        for (int segmentIndex = 0; segmentIndex < segmentAmount; segmentIndex++) {
            SegmentType segmentType1 = pathTemplate1.segments.get(segmentIndex).segmentType();
            SegmentType segmentType2 = pathTemplate2.segments.get(segmentIndex).segmentType();

            if (segmentType1 != segmentType2) {
                return false;
            }
        }

        return true;
    }

    public static boolean isSamePath(String path1, String path2) {
        PathTemplate pathTemplate1 = PathTemplate.create(path1);
        PathTemplate pathTemplate2 = PathTemplate.create(path2);

        if (!PathTemplate.isSamePattern(pathTemplate1, pathTemplate2)) {
            return false;
        }

        int segmentAmount = pathTemplate1.segments.size();

        for (int segmentIndex = 0; segmentIndex < segmentAmount; segmentIndex++) {
            Segment segment1 = pathTemplate1.segments.get(segmentIndex);
            Segment segment2 = pathTemplate2.segments.get(segmentIndex);

            if (segment1.segmentType() == SegmentType.VARIABLE) {
                continue;
            }

            if (!Objects.equals(segment1.segment(), segment2.segment())) {
                return false;
            }
        }

        return true;
    }

    private static boolean isStaticSegment(String segment) {
        if (PATH_VARIABLE.matcher(segment)
                         .matches()) {
            return false;
        }

        if (segment.indexOf('{') != -1 || segment.indexOf('}') != -1) {
            throw new IllegalArgumentException("Invalid path segment: " + segment);
        }

        return true;
    }

    private static SegmentType getSegmentType(String segment) {
        return PathTemplate.isStaticSegment(segment) ? SegmentType.STATIC : SegmentType.VARIABLE;
    }
}
