package com.voicetodo.test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class KoreanTodoParser {
    private static final String WEEKDAY = "(월요일|화요일|수요일|목요일|금요일|토요일|일요일)";
    private static final String WEEK_QUALIFIER = "(이번 주|다음 주|다다음 주|지난주|지지난주|오는|다가오는|이번에 오는|이번)";
    private static final String PERIOD = "(오전|오후|아침|점심|저녁|밤|새벽|낮)";
    private static final String CLOCK_TIME = "(?:(?:오전|오후|아침|점심|저녁|밤|새벽|낮)\\s*)?\\d{1,2}시(?:\\s*\\d{1,2}분|\\s*반|\\s*정각)?";

    private static final Pattern ABSOLUTE_RANGE = Pattern.compile(
            "(?:(\\d{2,4})년\\s*)?(\\d{1,2})월\\s*(\\d{1,2})일\\s*(?:" + CLOCK_TIME + "\\s*)?" +
                    "(?:부터|에서|~|-)\\s*(?:(\\d{2,4})년\\s*)?(?:(\\d{1,2})월\\s*)?(\\d{1,2})일(?!\\s*(?:동안|간|뒤|후))");
    private static final Pattern DAY_RANGE = Pattern.compile(
            "(?<!월\\s)(\\d{1,2})일\\s*(?:부터|에서|~|-)\\s*(\\d{1,2})일(?!\\s*(?:동안|간|뒤|후))");
    private static final Pattern WEEKDAY_RANGE = Pattern.compile(
            "(?:" + WEEK_QUALIFIER + "\\s*)?" + WEEKDAY + "\\s*(?:부터|에서|~|-)\\s*" +
                    "(?:" + WEEK_QUALIFIER + "\\s*)?" + WEEKDAY + "(?:까지)?");
    private static final Pattern RELATIVE_RANGE = Pattern.compile(
            "(오늘|내일|모레|글피|그글피)\\s*(?:부터|에서|~|-)\\s*(오늘|내일|모레|글피|그글피)(?:까지)?");
    private static final Pattern SHORT_WEEKDAY_RANGE = Pattern.compile(
            "(?:" + WEEK_QUALIFIER + "\\s*)([월화수목금토일]{2,3})(?=\\s|$)");
    private static final Pattern AMBIGUOUS_DATE_LIST = Pattern.compile(
            "(?:(\\d{1,2})월\\s*)?(\\d{1,2})일?(?:\\s*(?:,|하고|과|와)\\s*)(\\d{1,2})일");
    private static final Pattern COMPACT_DATE_LIST = Pattern.compile(
            "(\\d{1,2})월\\s*(\\d{1,2})\\s*,\\s*(\\d{1,2})일");

    private static final Pattern FULL_DATE = Pattern.compile("(?:(\\d{2,4})년\\s*)?(\\d{1,2})월\\s*(\\d{1,2})일");
    private static final Pattern NUMERIC_DATE = Pattern.compile("(?:(\\d{4})[./-])?(\\d{1,2})[./-](\\d{1,2})(?!\\d)");
    private static final Pattern RELATIVE_MONTH_DAY = Pattern.compile("(이번|다음|다다음) 달\\s*(\\d{1,2})일");
    private static final Pattern MONTH_WEEKDAY = Pattern.compile(
            "(이번|다음|다다음) 달\\s*(?:(첫째|둘째|셋째|넷째|마지막)(?: 주)?|([1-4])주차|([1-4])번째)\\s*" + WEEKDAY);
    private static final Pattern RELATIVE_MONTHS = Pattern.compile("(\\d+)개월\\s*(?:뒤|후)(?:\\s*(\\d{1,2})일)?");
    private static final Pattern RELATIVE_WEEKS_WEEKDAY = Pattern.compile("(\\d+)주\\s*(?:뒤|후)\\s*" + WEEKDAY);
    private static final Pattern RELATIVE_OFFSET = Pattern.compile("(\\d+)\\s*(일|주|개월)\\s*(?:뒤|후)");
    private static final Pattern WEEKDAY_DATE = Pattern.compile("(?:" + WEEK_QUALIFIER + "\\s*)?" + WEEKDAY);
    private static final Pattern APPROXIMATE_DATE = Pattern.compile("(이번|다음) 달\\s*(초|중순|말)(?:쯤)?|(올해 말|내년 초)");

    private static final Pattern TIME = Pattern.compile(
            "(?:" + PERIOD + "\\s*)?(\\d{1,2})시(?:\\s*(\\d{1,2})분)?(?:\\s*(반|정각))?(?!간)");
    private static final Pattern ABSTRACT_TIME = Pattern.compile("(아침 일찍|늦은 오후|늦은 밤|아침|오전|점심때|점심|오후|저녁때|저녁|밤|새벽)");
    private static final Pattern DURATION = Pattern.compile("(\\d+)시간(?:\\s*(반))?(?:\\s*(\\d+)분)?");
    private static final Pattern DAY_DURATION = Pattern.compile("(\\d+)일\\s*(?:동안|간)");
    private static final Pattern REMINDER = Pattern.compile("(\\d+)\\s*(분|시간|일)\\s*전");

    static ParsedCalendarEvent parse(String source) {
        ZoneId zoneId = ZoneId.systemDefault();
        return parse(source, Clock.system(zoneId), zoneId, Locale.KOREAN);
    }

    static ParsedCalendarEvent parse(String source, Clock clock, ZoneId zoneId, Locale locale) {
        String original = source == null ? "" : source.trim();
        String normalized = applyCorrections(normalizeKoreanText(original));
        LocalDate today = LocalDate.now(clock);
        LocalTime nowTime = LocalTime.now(clock).withSecond(0).withNano(0);

        ParsedCalendarEvent event = new ParsedCalendarEvent();
        event.originalText = original;
        event.normalizedText = normalized;
        event.intent = parseIntent(normalized);

        DateParse date = parseDate(normalized, today, event.intent);
        TimeParse time = parseTime(normalized, nowTime, date.explicit);
        event.startDate = date.start == null ? today : date.start;
        event.endDate = date.end;
        event.startTime = time.start;
        event.endTime = time.end;
        event.durationMinutes = time.durationMinutes;
        event.allDay = time.allDay;
        event.multiDay = event.endDate != null && !event.endDate.equals(event.startDate);
        event.dateExpression = date.expression;
        event.timeExpression = time.expression;
        event.repeat = parseRepeat(normalized, event.startDate);
        event.reminderMinutes = parseReminder(normalized);
        event.eventType = isDeadline(normalized, date, time)
                ? ParsedCalendarEvent.EventType.DEADLINE : ParsedCalendarEvent.EventType.EVENT;

        if (date.approximate || time.approximate) event.precision = ParsedCalendarEvent.Precision.APPROXIMATE;
        event.requiresConfirmation = date.requiresConfirmation || time.requiresConfirmation;
        event.confirmationReason = date.reason != null ? date.reason : time.reason;
        event.title = extractTitle(normalized, date, time);
        if (event.title.isBlank()) {
            event.title = "새 할 일";
            event.requiresConfirmation = true;
            event.confirmationReason = "MISSING_TITLE";
        }
        event.category = guessCategory(event.title + " " + normalized);

        event.confidence = 0.98;
        if (!date.explicit && !time.explicit) event.confidence = 0.72;
        if (event.precision == ParsedCalendarEvent.Precision.APPROXIMATE) event.confidence = Math.min(event.confidence, 0.76);
        if (event.requiresConfirmation) event.confidence = Math.min(event.confidence, 0.68);
        if ("MISSING_TITLE".equals(event.confirmationReason)) event.confidence = 0.35;

        if (event.endDate != null && event.endDate.isBefore(event.startDate)) {
            event.requiresConfirmation = true;
            event.confirmationReason = "END_BEFORE_START";
            event.confidence = 0.3;
        }
        if (!event.allDay && event.endDate == null && event.endTime != null && event.startTime != null
                && event.endTime.isBefore(event.startTime)) {
            event.requiresConfirmation = true;
            event.confirmationReason = "END_BEFORE_START";
            event.confidence = 0.3;
        }

        if (date.bareWeekday && event.startDate.equals(today) && event.startTime != null
                && !event.startTime.isAfter(nowTime)) {
            event.startDate = event.startDate.plusWeeks(1);
            if (event.endDate != null) event.endDate = event.endDate.plusWeeks(1);
        }
        return event;
    }

    static String normalizeKoreanText(String source) {
        String text = source == null ? "" : source.trim();
        text = text.replace('～', '~').replace('〜', '~').replace('–', '-').replace('—', '-');
        text = text.replaceAll("다음다음\\s*주|다다음주", "다다음 주");
        text = text.replaceAll("다음주|담주|차주", "다음 주");
        text = text.replaceAll("이번주", "이번 주");
        text = text.replaceAll("저번\\s*주|지난\\s+주", "지난주");
        text = text.replaceAll("지지난\\s*주", "지지난주");
        text = text.replaceAll("이번달", "이번 달");
        text = text.replaceAll("다다음달", "다다음 달");
        text = text.replaceAll("다음달", "다음 달");
        text = text.replaceAll("한\\s*달", "1개월");
        text = text.replaceAll("두\\s*달", "2개월");
        text = text.replaceAll("세\\s*달", "3개월");
        text = text.replaceAll("일주일", "1주");
        text = text.replaceAll("첫\\s*번째", "1번째");
        text = text.replaceAll("두\\s*번째", "2번째");
        text = text.replaceAll("세\\s*번째", "3번째");
        text = text.replaceAll("네\\s*번째", "4번째");
        text = text.replaceAll("첫\\s+주", "첫째 주");

        String[][] hours = {
                {"열두", "12"}, {"열한", "11"}, {"열", "10"}, {"아홉", "9"}, {"여덟", "8"},
                {"일곱", "7"}, {"여섯", "6"}, {"다섯", "5"}, {"네", "4"}, {"세", "3"},
                {"두", "2"}, {"한", "1"}
        };
        for (String[] pair : hours) {
            text = text.replaceAll(pair[0] + "\\s*시", pair[1] + "시");
            text = text.replaceAll(pair[0] + "\\s*시간", pair[1] + "시간");
        }
        String[][] minutes = {
                {"사십오", "45"}, {"삼십", "30"}, {"이십", "20"}, {"십오", "15"},
                {"사십", "40"}, {"십", "10"}, {"오", "5"}
        };
        for (String[] pair : minutes) text = text.replaceAll(pair[0] + "\\s*분", pair[1] + "분");

        text = text.replaceAll("하루(?=\\s*(?:뒤|후|동안|간))", "1일");
        text = text.replaceAll("이틀(?=\\s*(?:뒤|후|동안|간))", "2일");
        text = text.replaceAll("사흘(?=\\s*(?:뒤|후|동안|간))", "3일");
        text = text.replaceAll("나흘(?=\\s*(?:뒤|후|동안|간))", "4일");
        text = text.replaceAll("닷새(?=\\s*(?:뒤|후|동안|간))", "5일");
        text = text.replaceAll("엿새(?=\\s*(?:뒤|후|동안|간))", "6일");

        text = text.replaceAll("(\\d{2,4})\\s*년\\s*", "$1년 ");
        text = text.replaceAll("(\\d{1,2})\\s*월\\s*", "$1월 ");
        text = text.replaceAll("(\\d{1,2})\\s*일", "$1일");
        text = text.replaceAll("(\\d{1,2})\\s*시", "$1시");
        text = text.replaceAll("(\\d{1,2})\\s*분", "$1분");
        text = text.replaceAll("\\s*~\\s*", "~");
        text = text.replaceAll("(^|\\s)(어|음|아|저기|그니까|그러니까|잠깐|뭐였지)(?=\\s|$)", " ");
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String applyCorrections(String input) {
        String text = input;
        Matcher inheritedMonth = Pattern.compile(
                "(\\d{1,2})월\\s*\\d{1,2}일\\s*(?:아니|아니다|말고|정정|아니고)\\s*(\\d{1,2})일").matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (inheritedMonth.find()) {
            inheritedMonth.appendReplacement(buffer,
                    Matcher.quoteReplacement(inheritedMonth.group(1) + "월 " + inheritedMonth.group(2) + "일"));
        }
        inheritedMonth.appendTail(buffer);
        text = buffer.toString();

        String correction = "(?:아니|아니다|말고|정정|아니고)";
        String timeValue = "(?:(?:오전|오후|아침|점심|저녁|밤|새벽)\\s*)?\\d{1,2}시(?:\\s*\\d{1,2}분|\\s*반)?";
        text = text.replaceAll(timeValue + "\\s*" + correction + "\\s*(?=" + timeValue + ")", " ");
        text = text.replaceAll("(?:오늘|내일|모레|글피|그글피)\\s*" + correction +
                "\\s*(?=오늘|내일|모레|글피|그글피)", " ");
        text = text.replaceAll("(?:(?:이번 주|다음 주|다다음 주)\\s*)?" + WEEKDAY + "\\s*" + correction +
                "\\s*(?=(?:(?:이번 주|다음 주|다다음 주)\\s*)?" + WEEKDAY + ")", " ");
        return text.replaceAll("\\s+", " ").trim();
    }

    private static ParsedCalendarEvent.Intent parseIntent(String text) {
        if (containsAny(text, "삭제해줘", "지워줘", "취소해줘", "일정 빼줘"))
            return ParsedCalendarEvent.Intent.DELETE_EVENT;
        if (containsAny(text, "바꿔줘", "변경해줘", "수정해줘", "시간 옮겨줘", "날짜 옮겨줘"))
            return ParsedCalendarEvent.Intent.UPDATE_EVENT;
        if (containsAny(text, "언제야", "일정 알려줘", "일정 찾아줘", "무슨 일정 있어"))
            return ParsedCalendarEvent.Intent.SEARCH_EVENT;
        return ParsedCalendarEvent.Intent.CREATE_EVENT;
    }

    private static DateParse parseDate(String text, LocalDate today, ParsedCalendarEvent.Intent intent) {
        DateParse result = new DateParse();
        Matcher absoluteRange = ABSOLUTE_RANGE.matcher(text);
        if (absoluteRange.find()) {
            Integer startYear = parseYear(absoluteRange.group(1));
            int startMonth = intValue(absoluteRange.group(2));
            int startDay = intValue(absoluteRange.group(3));
            Integer endYear = parseYear(absoluteRange.group(4));
            Integer endMonth = absoluteRange.group(5) == null ? null : intValue(absoluteRange.group(5));
            LocalDate start = safeDate(startYear == null ? today.getYear() : startYear, startMonth, startDay);
            if (start != null && startYear == null && intent == ParsedCalendarEvent.Intent.CREATE_EVENT && start.isBefore(today))
                start = start.plusYears(1);
            int resolvedEndMonth = endMonth == null ? startMonth : endMonth;
            int resolvedEndYear = endYear == null && start != null ? start.getYear() : (endYear == null ? today.getYear() : endYear);
            LocalDate end = safeDate(resolvedEndYear, resolvedEndMonth, intValue(absoluteRange.group(6)));
            if (start != null && end != null && end.isBefore(start) && endYear == null && endMonth != null && resolvedEndMonth < startMonth)
                end = end.plusYears(1);
            result.set(start, end, absoluteRange.group(), true);
            validateDates(result);
            return result;
        }

        Matcher compactList = COMPACT_DATE_LIST.matcher(text);
        if (compactList.find()) {
            LocalDate start = futureDate(today, null, intValue(compactList.group(1)), intValue(compactList.group(2)), intent);
            LocalDate end = start == null ? null : safeDate(start.getYear(), start.getMonthValue(), intValue(compactList.group(3)));
            result.set(start, end, compactList.group(), true);
            validateDates(result);
            return result;
        }

        Matcher dayRange = DAY_RANGE.matcher(text);
        if (dayRange.find()) {
            LocalDate start = currentOrNextMonth(today, intValue(dayRange.group(1)), intent);
            LocalDate end = start == null ? null : safeDate(start.getYear(), start.getMonthValue(), intValue(dayRange.group(2)));
            if (start != null && end != null && end.isBefore(start)) {
                YearMonth next = YearMonth.from(start).plusMonths(1);
                end = safeDate(next.getYear(), next.getMonthValue(), intValue(dayRange.group(2)));
            }
            result.set(start, end, dayRange.group(), true);
            validateDates(result);
            return result;
        }

        Matcher weekdayRange = WEEKDAY_RANGE.matcher(text);
        if (weekdayRange.find()) {
            String firstQualifier = weekdayRange.group(1);
            String firstName = weekdayRange.group(2);
            String secondQualifier = weekdayRange.group(3) == null ? firstQualifier : weekdayRange.group(3);
            String secondName = weekdayRange.group(4);
            LocalDate start = resolveWeekday(today, firstQualifier, weekday(firstName));
            LocalDate end = resolveWeekday(today, secondQualifier, weekday(secondName));
            if (end.isBefore(start)) end = end.plusWeeks(1);
            result.set(start, end, weekdayRange.group(), true);
            return result;
        }

        Matcher relativeRange = RELATIVE_RANGE.matcher(text);
        if (relativeRange.find()) {
            result.set(relativeDay(today, relativeRange.group(1)), relativeDay(today, relativeRange.group(2)), relativeRange.group(), true);
            validateDates(result);
            return result;
        }

        Matcher shorthand = SHORT_WEEKDAY_RANGE.matcher(text);
        if (shorthand.find()) {
            String qualifier = shorthand.group(1);
            String compact = shorthand.group(2);
            LocalDate start = resolveWeekday(today, qualifier, compactWeekday(compact.charAt(0)));
            LocalDate end = resolveWeekday(today, qualifier, compactWeekday(compact.charAt(compact.length() - 1)));
            if (end.isBefore(start)) end = end.plusWeeks(1);
            result.set(start, end, shorthand.group(), true);
            return result;
        }

        Matcher ambiguous = AMBIGUOUS_DATE_LIST.matcher(text);
        if (ambiguous.find()) {
            int month = ambiguous.group(1) == null ? today.getMonthValue() : intValue(ambiguous.group(1));
            LocalDate start = futureDate(today, null, month, intValue(ambiguous.group(2)), intent);
            LocalDate end = start == null ? null : safeDate(start.getYear(), month, intValue(ambiguous.group(3)));
            result.set(start, end, ambiguous.group(), true);
            result.requiresConfirmation = true;
            result.reason = "MULTIPLE_DATE_AMBIGUITY";
            return result;
        }

        parseSingleDate(text, today, intent, result);
        Matcher days = DAY_DURATION.matcher(text);
        if (days.find() && result.start != null && text.contains("부터")) {
            int amount = Math.max(1, intValue(days.group(1)));
            result.end = result.start.plusDays(amount - 1L);
            result.expression = joinExpressions(result.expression, days.group());
        }
        return result;
    }

    private static void parseSingleDate(String text, LocalDate today, ParsedCalendarEvent.Intent intent, DateParse result) {
        Matcher full = FULL_DATE.matcher(text);
        if (full.find()) {
            result.start = futureDate(today, parseYear(full.group(1)), intValue(full.group(2)), intValue(full.group(3)), intent);
            result.expression = full.group();
            result.explicit = true;
            if (result.start == null) invalidDate(result);
            return;
        }
        Matcher numeric = NUMERIC_DATE.matcher(text);
        if (numeric.find()) {
            result.start = futureDate(today, parseYear(numeric.group(1)), intValue(numeric.group(2)), intValue(numeric.group(3)), intent);
            result.expression = numeric.group();
            result.explicit = true;
            if (result.start == null) invalidDate(result);
            return;
        }
        Matcher monthWeek = MONTH_WEEKDAY.matcher(text);
        if (monthWeek.find()) {
            YearMonth month = YearMonth.from(today).plusMonths(monthOffset(monthWeek.group(1)));
            int position = ordinal(monthWeek.group(2), monthWeek.group(3), monthWeek.group(4));
            DayOfWeek day = weekday(monthWeek.group(5));
            result.start = position == -1
                    ? month.atEndOfMonth().with(TemporalAdjusters.previousOrSame(day))
                    : month.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(position, day));
            result.expression = monthWeek.group();
            result.explicit = true;
            return;
        }
        Matcher relativeMonthDay = RELATIVE_MONTH_DAY.matcher(text);
        if (relativeMonthDay.find()) {
            YearMonth month = YearMonth.from(today).plusMonths(monthOffset(relativeMonthDay.group(1)));
            result.start = safeDate(month.getYear(), month.getMonthValue(), intValue(relativeMonthDay.group(2)));
            result.expression = relativeMonthDay.group();
            result.explicit = true;
            if (result.start == null) invalidDate(result);
            return;
        }
        Matcher months = RELATIVE_MONTHS.matcher(text);
        if (months.find()) {
            LocalDate base = today.plusMonths(intValue(months.group(1)));
            result.start = months.group(2) == null ? base
                    : safeDate(base.getYear(), base.getMonthValue(), intValue(months.group(2)));
            result.expression = months.group();
            result.explicit = true;
            if (result.start == null) invalidDate(result);
            return;
        }
        Matcher weeksWeekday = RELATIVE_WEEKS_WEEKDAY.matcher(text);
        if (weeksWeekday.find()) {
            LocalDate base = today.plusWeeks(intValue(weeksWeekday.group(1)));
            result.start = base.with(TemporalAdjusters.nextOrSame(weekday(weeksWeekday.group(2))));
            result.expression = weeksWeekday.group();
            result.explicit = true;
            return;
        }
        Matcher approximate = APPROXIMATE_DATE.matcher(text);
        if (approximate.find() && !(text.contains("매") &&
                text.substring(approximate.end()).trim().startsWith("까지"))) {
            String phrase = approximate.group();
            if ("올해 말".equals(phrase)) result.start = LocalDate.of(today.getYear(), 12, 31);
            else if ("내년 초".equals(phrase)) result.start = LocalDate.of(today.getYear() + 1, 1, 1);
            else {
                YearMonth month = YearMonth.from(today).plusMonths("다음".equals(approximate.group(1)) ? 1 : 0);
                result.start = switch (approximate.group(2)) {
                    case "초" -> month.atDay(1);
                    case "중순" -> month.atDay(15);
                    default -> month.atEndOfMonth();
                };
            }
            result.expression = phrase;
            result.explicit = true;
            result.approximate = true;
            result.requiresConfirmation = true;
            result.reason = "APPROXIMATE_DATE";
            return;
        }
        Matcher offset = RELATIVE_OFFSET.matcher(text);
        if (offset.find()) {
            int amount = intValue(offset.group(1));
            result.start = switch (offset.group(2)) {
                case "주" -> today.plusWeeks(amount);
                case "개월" -> today.plusMonths(amount);
                default -> today.plusDays(amount);
            };
            result.expression = offset.group();
            result.explicit = true;
            return;
        }
        for (String word : new String[]{"그글피", "글피", "모레", "내일", "오늘"}) {
            if (text.contains(word)) {
                result.start = relativeDay(today, word);
                result.expression = word;
                result.explicit = true;
                return;
            }
        }
        if (text.contains("주말")) {
            result.start = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
            result.end = result.start.plusDays(1);
            result.expression = "주말";
            result.explicit = true;
            result.requiresConfirmation = true;
            result.reason = "WEEKEND_AMBIGUITY";
            return;
        }
        Matcher weekday = WEEKDAY_DATE.matcher(text);
        if (weekday.find()) {
            String qualifier = weekday.group(1);
            result.start = resolveWeekday(today, qualifier, weekday(weekday.group(2)));
            result.expression = weekday.group();
            result.explicit = true;
            result.bareWeekday = qualifier == null || containsAny(qualifier, "오는", "다가오는", "이번");
            return;
        }
        result.start = today;
        result.explicit = false;
    }

    private static TimeParse parseTime(String text, LocalTime now, boolean hasDate) {
        TimeParse result = new TimeParse();
        if (containsAny(text, "하루 종일", "종일")) {
            result.allDay = true;
            result.explicit = true;
            result.expression = text.contains("하루 종일") ? "하루 종일" : "종일";
            return result;
        }
        List<TimeToken> tokens = new ArrayList<>();
        Matcher matcher = TIME.matcher(text);
        while (matcher.find()) {
            tokens.add(timeToken(matcher.group(), matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4), matcher.start(), matcher.end()));
        }
        addSpecialTime(tokens, text, "정오", LocalTime.NOON);
        addSpecialTime(tokens, text, "자정", LocalTime.MIDNIGHT);
        tokens.sort(Comparator.comparingInt(token -> token.startIndex));

        if (!tokens.isEmpty()) {
            TimeToken first = tokens.get(0);
            result.start = first.time;
            result.explicit = true;
            result.expression = first.raw;
            if (first.ambiguous) ambiguity(result, first.reason == null ? "AM_PM_AMBIGUITY" : first.reason);
            String suffix = text.substring(first.endIndex).trim();
            if (suffix.startsWith("쯤") || suffix.startsWith("정도") || suffix.startsWith("경")) {
                result.approximate = true;
                ambiguity(result, "APPROXIMATE_TIME");
            }
            if (tokens.size() >= 2) {
                TimeToken second = tokens.get(1);
                String between = text.substring(first.endIndex, second.startIndex);
                if (containsAny(between, "부터", "에서", "~", "-") || text.substring(second.endIndex).trim().startsWith("까지")) {
                    if (second.period == null && first.period != null && second.originalHour <= 12)
                        second = timeToken(second.raw, first.period, String.valueOf(second.originalHour),
                                String.valueOf(second.time.getMinute()), null, second.startIndex, second.endIndex);
                    result.end = second.time;
                    result.expression = text.substring(first.startIndex, second.endIndex);
                    if (second.ambiguous) ambiguity(result, second.reason == null ? "AM_PM_AMBIGUITY" : second.reason);
                }
            }
            Matcher duration = DURATION.matcher(text);
            if (duration.find(first.endIndex)) {
                int minutes = intValue(duration.group(1)) * 60;
                if (duration.group(2) != null) minutes += 30;
                if (duration.group(3) != null) minutes += intValue(duration.group(3));
                result.durationMinutes = minutes;
                if (result.end == null) result.end = result.start.plusMinutes(minutes);
                result.expression = joinExpressions(result.expression, duration.group());
            }
            return result;
        }
        Matcher abstractTime = ABSTRACT_TIME.matcher(text);
        if (abstractTime.find()) {
            String word = abstractTime.group(1);
            result.start = switch (word) {
                case "아침", "아침 일찍" -> LocalTime.of(8, 0);
                case "오전" -> LocalTime.of(10, 0);
                case "점심", "점심때" -> LocalTime.NOON;
                case "오후", "늦은 오후" -> LocalTime.of(15, 0);
                case "저녁", "저녁때" -> LocalTime.of(19, 0);
                case "밤", "늦은 밤" -> LocalTime.of(21, 0);
                default -> LocalTime.of(6, 0);
            };
            result.expression = word;
            result.explicit = true;
            result.approximate = true;
            ambiguity(result, "APPROXIMATE_TIME");
            return result;
        }
        if (hasDate) result.allDay = true;
        else result.start = now;
        return result;
    }

    private static TimeToken timeToken(String raw, String period, String hourText, String minuteText,
                                       String modifier, int startIndex, int endIndex) {
        int originalHour = intValue(hourText);
        int hour = Math.max(0, Math.min(23, originalHour));
        int minute = minuteText == null || minuteText.isBlank() ? 0 : intValue(minuteText);
        if ("반".equals(modifier)) minute = 30;
        minute = Math.max(0, Math.min(59, minute));
        boolean ambiguous = false;
        String reason = null;
        if (period != null) {
            boolean pm = containsAny(period, "오후", "저녁", "밤");
            if (pm && hour < 12) hour += 12;
            if (containsAny(period, "오전", "아침", "새벽") && hour == 12) hour = 0;
            if (containsAny(period, "점심", "낮") && hour < 11) hour += 12;
            if ("밤".equals(period) && originalHour == 12) {
                ambiguous = true;
                reason = "MIDNIGHT_AMBIGUITY";
            }
        } else if (originalHour >= 1 && originalHour <= 12) {
            ambiguous = true;
            reason = "AM_PM_AMBIGUITY";
        }
        return new TimeToken(raw, period, originalHour, LocalTime.of(hour, minute), ambiguous, reason, startIndex, endIndex);
    }

    private static void addSpecialTime(List<TimeToken> tokens, String text, String word, LocalTime time) {
        int index = text.indexOf(word);
        if (index >= 0) tokens.add(new TimeToken(word, word, time.getHour(), time, false, null, index, index + word.length()));
    }

    private static ParsedCalendarEvent.RepeatRule parseRepeat(String text, LocalDate startDate) {
        ParsedCalendarEvent.RepeatRule repeat = null;
        if (text.contains("평일")) {
            repeat = new ParsedCalendarEvent.RepeatRule();
            repeat.frequency = "WEEKLY";
            repeat.byDays.addAll(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        } else if (text.contains("매일")) {
            repeat = new ParsedCalendarEvent.RepeatRule();
            repeat.frequency = "DAILY";
        } else if (text.contains("격주")) {
            repeat = new ParsedCalendarEvent.RepeatRule();
            repeat.frequency = "WEEKLY";
            repeat.interval = 2;
        } else {
            Matcher everyWeeks = Pattern.compile("(\\d+)주마다").matcher(text);
            if (everyWeeks.find()) {
                repeat = new ParsedCalendarEvent.RepeatRule();
                repeat.frequency = "WEEKLY";
                repeat.interval = intValue(everyWeeks.group(1));
            } else if (text.contains("매주")) {
                repeat = new ParsedCalendarEvent.RepeatRule();
                repeat.frequency = "WEEKLY";
            } else if (containsAny(text, "매월", "매달")) {
                repeat = new ParsedCalendarEvent.RepeatRule();
                repeat.frequency = "MONTHLY";
            } else if (text.contains("매년")) {
                repeat = new ParsedCalendarEvent.RepeatRule();
                repeat.frequency = "YEARLY";
            }
        }
        if (repeat == null) return null;
        Matcher weekday = Pattern.compile(WEEKDAY).matcher(text);
        while (weekday.find()) {
            DayOfWeek value = weekday(weekday.group(1));
            if (!repeat.byDays.contains(value)) repeat.byDays.add(value);
        }
        Matcher monthDay = Pattern.compile("매(?:월|달)\\s*(\\d{1,2})일").matcher(text);
        if (monthDay.find()) repeat.byMonthDays.add(intValue(monthDay.group(1)));
        if (text.matches(".*(?:첫째|첫 번째|1주차).*")) repeat.bySetPosition = 1;
        else if (text.matches(".*(?:둘째|두 번째|2주차).*")) repeat.bySetPosition = 2;
        else if (text.matches(".*(?:셋째|세 번째|3주차).*")) repeat.bySetPosition = 3;
        else if (text.contains("마지막")) repeat.bySetPosition = -1;
        Matcher count = Pattern.compile("(\\d+)\\s*(?:번|회) 반복").matcher(text);
        if (count.find()) repeat.count = intValue(count.group(1));
        if (text.contains("올해 말까지")) repeat.until = LocalDate.of(startDate.getYear(), 12, 31);
        return repeat;
    }

    private static String extractTitle(String normalized, DateParse date, TimeParse time) {
        String title = normalized;
        if (date.expression != null) title = title.replace(date.expression, " ");
        if (time.expression != null) title = title.replace(time.expression, " ");
        title = TIME.matcher(title).replaceAll(" ");
        title = DURATION.matcher(title).replaceAll(" ");
        title = FULL_DATE.matcher(title).replaceAll(" ");
        title = WEEKDAY_DATE.matcher(title).replaceAll(" ");
        title = title.replaceAll("오늘|내일|모레|글피|그글피|정오|자정", " ");
        title = REMINDER.matcher(title).replaceAll(" ");
        title = DAY_DURATION.matcher(title).replaceAll(" ");
        title = title.replaceAll("매일|매주|격주|평일|매월|매달|매년|\\d+주마다", " ");
        title = title.replaceAll("올해 말까지|다음 달까지|\\d{1,2}월까지|\\d+\\s*(?:번|회) 반복", " ");
        title = title.replaceAll("하루 종일|종일", " ");
        title = title.replaceAll("캘린더에|달력에|일정에|일정으로", " ");
        title = title.replaceAll("등록해줘|추가해줘|넣어줘|저장해줘|기억해줘|잡아줘|만들어줘|일정 잡아줘|일정 만들어줘", " ");
        title = title.replaceAll("바꿔줘|변경해줘|수정해줘|옮겨줘|삭제해줘|지워줘|취소해줘|찾아줘|알려줘", " ");
        title = title.replaceAll("가야 돼|가야 해|있어|있음|이야", " ");
        title = title.replaceAll("(^|\\s)(일정|스케줄|좀|하나)(?=\\s|$)", " ");
        title = title.replaceAll("(^|\\s)(부터|까지|에서|에)(?=\\s|$)", " ");
        title = title.replace("~", " ");
        title = title.replaceAll("\\s+", " ").trim();
        return title.replaceAll("^[,·\\-]|[,·\\-]$", "").trim();
    }

    private static int parseReminder(String text) {
        Matcher matcher = REMINDER.matcher(text);
        if (!matcher.find()) return 0;
        int amount = intValue(matcher.group(1));
        return switch (matcher.group(2)) {
            case "시간" -> amount * 60;
            case "일" -> amount * 24 * 60;
            default -> amount;
        };
    }

    private static boolean isDeadline(String text, DateParse date, TimeParse time) {
        if (!text.contains("까지") || date.end != null || time.end != null) return false;
        return containsAny(text, "마감", "제출", "신청", "보내기", "결제", "완료");
    }

    private static String guessCategory(String text) {
        String lower = text.toLowerCase(Locale.KOREAN);
        if (containsAny(lower, "회의", "보고서", "보고", "월보고", "업무", "회사", "프로젝트", "마감",
                "제출", "신청서", "출장", "교육", "견학")) return "업무";
        if (containsAny(lower, "병원", "치과", "운동", "약", "검진")) return "건강";
        if (containsAny(lower, "구매", "장보기", "사기", "마트")) return "쇼핑";
        if (containsAny(lower, "만나", "약속", "예약", "미팅")) return "약속";
        return "개인";
    }

    private static LocalDate resolveWeekday(LocalDate today, String qualifier, DayOfWeek day) {
        if (qualifier == null || containsAny(qualifier, "오는", "다가오는") || "이번".equals(qualifier))
            return today.with(TemporalAdjusters.nextOrSame(day));
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        int offset = switch (qualifier) {
            case "다음 주" -> 1;
            case "다다음 주" -> 2;
            case "지난주" -> -1;
            case "지지난주" -> -2;
            default -> 0;
        };
        return weekStart.plusWeeks(offset).plusDays(day.getValue() - 1L);
    }

    private static LocalDate futureDate(LocalDate today, Integer year, int month, int day,
                                        ParsedCalendarEvent.Intent intent) {
        LocalDate date = safeDate(year == null ? today.getYear() : year, month, day);
        if (date != null && year == null && intent == ParsedCalendarEvent.Intent.CREATE_EVENT && date.isBefore(today))
            date = date.plusYears(1);
        return date;
    }

    private static LocalDate currentOrNextMonth(LocalDate today, int day, ParsedCalendarEvent.Intent intent) {
        LocalDate date = safeDate(today.getYear(), today.getMonthValue(), day);
        if (date != null && intent == ParsedCalendarEvent.Intent.CREATE_EVENT && date.isBefore(today)) {
            YearMonth next = YearMonth.from(today).plusMonths(1);
            date = safeDate(next.getYear(), next.getMonthValue(), day);
        }
        return date;
    }

    private static LocalDate safeDate(int year, int month, int day) {
        try { return LocalDate.of(year, month, day); }
        catch (RuntimeException ignored) { return null; }
    }

    private static Integer parseYear(String value) {
        if (value == null) return null;
        int year = intValue(value);
        return year < 100 ? 2000 + year : year;
    }

    private static LocalDate relativeDay(LocalDate today, String word) {
        return switch (word) {
            case "내일" -> today.plusDays(1);
            case "모레" -> today.plusDays(2);
            case "글피" -> today.plusDays(3);
            case "그글피" -> today.plusDays(4);
            default -> today;
        };
    }

    private static int monthOffset(String qualifier) {
        return switch (qualifier) { case "다음" -> 1; case "다다음" -> 2; default -> 0; };
    }

    private static int ordinal(String word, String weekNumber, String positionNumber) {
        if (weekNumber != null) return intValue(weekNumber);
        if (positionNumber != null) return intValue(positionNumber);
        if (word == null) return 1;
        return switch (word) { case "둘째" -> 2; case "셋째" -> 3; case "넷째" -> 4; case "마지막" -> -1; default -> 1; };
    }

    private static DayOfWeek weekday(String name) {
        return switch (name) {
            case "월요일" -> DayOfWeek.MONDAY;
            case "화요일" -> DayOfWeek.TUESDAY;
            case "수요일" -> DayOfWeek.WEDNESDAY;
            case "목요일" -> DayOfWeek.THURSDAY;
            case "금요일" -> DayOfWeek.FRIDAY;
            case "토요일" -> DayOfWeek.SATURDAY;
            default -> DayOfWeek.SUNDAY;
        };
    }

    private static DayOfWeek compactWeekday(char value) {
        return switch (value) {
            case '월' -> DayOfWeek.MONDAY; case '화' -> DayOfWeek.TUESDAY; case '수' -> DayOfWeek.WEDNESDAY;
            case '목' -> DayOfWeek.THURSDAY; case '금' -> DayOfWeek.FRIDAY; case '토' -> DayOfWeek.SATURDAY;
            default -> DayOfWeek.SUNDAY;
        };
    }

    private static void validateDates(DateParse result) {
        if (result.start == null || result.end == null) invalidDate(result);
        else if (result.end.isBefore(result.start)) {
            result.requiresConfirmation = true;
            result.reason = "END_BEFORE_START";
        }
    }

    private static void invalidDate(DateParse result) {
        result.requiresConfirmation = true;
        result.reason = "INVALID_DATE";
    }

    private static void ambiguity(TimeParse result, String reason) {
        result.requiresConfirmation = true;
        result.reason = reason;
    }

    private static String joinExpressions(String first, String second) {
        if (first == null || first.isBlank()) return second;
        if (second == null || second.isBlank()) return first;
        return first + " " + second;
    }

    private static int intValue(String value) { return Integer.parseInt(value.trim()); }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }

    private static final class DateParse {
        LocalDate start;
        LocalDate end;
        String expression;
        boolean explicit;
        boolean approximate;
        boolean requiresConfirmation;
        String reason;
        boolean bareWeekday;
        void set(LocalDate start, LocalDate end, String expression, boolean explicit) {
            this.start = start; this.end = end; this.expression = expression; this.explicit = explicit;
        }
    }

    private static final class TimeParse {
        LocalTime start;
        LocalTime end;
        Integer durationMinutes;
        String expression;
        boolean explicit;
        boolean allDay;
        boolean approximate;
        boolean requiresConfirmation;
        String reason;
    }

    private static final class TimeToken {
        final String raw;
        final String period;
        final int originalHour;
        final LocalTime time;
        final boolean ambiguous;
        final String reason;
        final int startIndex;
        final int endIndex;
        TimeToken(String raw, String period, int originalHour, LocalTime time, boolean ambiguous,
                  String reason, int startIndex, int endIndex) {
            this.raw = raw; this.period = period; this.originalHour = originalHour; this.time = time;
            this.ambiguous = ambiguous; this.reason = reason; this.startIndex = startIndex; this.endIndex = endIndex;
        }
    }

    private KoreanTodoParser() {}
}
