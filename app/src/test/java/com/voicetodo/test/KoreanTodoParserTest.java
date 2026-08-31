package com.voicetodo.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;

public final class KoreanTodoParserTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-19T03:00:00Z"), SEOUL);

    private ParsedCalendarEvent parse(String text) {
        return KoreanTodoParser.parse(text, CLOCK, SEOUL, Locale.KOREAN);
    }

    @Test
    public void importanceCanBeDetectedFromVoiceText() {
        String importantSource = "내일 중요한 월보고 마감";
        TodoItem important = TodoItem.fromParsed(
                "important", importantSource, parse(importantSource), SEOUL, 0L);
        assertTrue(important.important);

        String normalSource = "내일 중요하지 않은 개인 일정";
        TodoItem normal = TodoItem.fromParsed(
                "normal", normalSource, parse(normalSource), SEOUL, 0L);
        assertFalse(normal.important);
    }

    @Test
    public void structuredNoticeIsReducedToAConciseTitle() {
        String source = "가. 입금금액 : 도지회 200만원, 시 단위 지부 20만원, 군 단위 지부 10만원\n"
                + "나. 입금날짜 : 2026.8.24.(월) 10시까지\n"
                + "다. 입금계좌 : 우체국 100-0001-92432 전북지회";

        ParsedCalendarEvent event = parse(source);

        assertEquals(LocalDate.of(2026, 8, 24), event.startDate);
        assertEquals(LocalTime.of(10, 0), event.startTime);
        assertEquals("입금 마감", event.title);
        assertEquals(ParsedCalendarEvent.EventType.DEADLINE, event.eventType);
        assertEquals("업무", event.category);
        assertEquals(source, event.originalText);
    }

    @Test
    public void reportLabelCanProvideTheConciseTitle() {
        String source = "보고: 8월 월간 업무 실적\n"
                + "보고일시: 2026.8.24.(월) 10시까지\n"
                + "담당: 기획팀";

        ParsedCalendarEvent event = parse(source);

        assertEquals("8월 월간 업무 실적", event.title);
        assertEquals(LocalDate.of(2026, 8, 24), event.startDate);
        assertEquals(LocalTime.of(10, 0), event.startTime);
        assertEquals("업무", event.category);
    }

    @Test
    public void explicitStructuredTitleTakesPriority() {
        ParsedCalendarEvent event = parse("제목: 하반기 운영위원회\n일시: 2026.9.2. 14시\n장소: 회의실");
        assertEquals("하반기 운영위원회", event.title);
    }

    @Test
    public void nextWeekWithoutSpaceAnd24HourTime() {
        ParsedCalendarEvent event = parse("다음주 금요일 18시 30분 월보고 마감");
        assertEquals(LocalDate.of(2026, 8, 28), event.startDate);
        assertEquals(LocalTime.of(18, 30), event.startTime);
        assertEquals("월보고 마감", event.title);
        assertEquals("업무", event.category);
        assertFalse(event.requiresConfirmation);
    }

    @Test
    public void koreanHourIsNormalizedButAmbiguousWithoutPeriod() {
        ParsedCalendarEvent event = parse("다음주 금요일 세 시 병원");
        assertEquals(LocalDate.of(2026, 8, 28), event.startDate);
        assertEquals(LocalTime.of(3, 0), event.startTime);
        assertTrue(event.requiresConfirmation);
        assertEquals("AM_PM_AMBIGUITY", event.confirmationReason);
    }

    @Test
    public void absoluteDateRangeIsInclusiveAllDay() {
        ParsedCalendarEvent event = parse("8월 27일~28일 선진지견학");
        assertEquals(LocalDate.of(2026, 8, 27), event.startDate);
        assertEquals(LocalDate.of(2026, 8, 28), event.endDate);
        assertEquals("선진지견학", event.title);
        assertTrue(event.allDay);
        assertTrue(event.multiDay);
        assertFalse(event.requiresConfirmation);
    }

    @Test
    public void crossMonthAndCrossYearRanges() {
        ParsedCalendarEvent month = parse("8월 30일부터 9월 2일까지 선진지견학");
        assertEquals(LocalDate.of(2026, 8, 30), month.startDate);
        assertEquals(LocalDate.of(2026, 9, 2), month.endDate);

        ParsedCalendarEvent year = parse("12월 30일부터 1월 2일까지 여행");
        assertEquals(LocalDate.of(2026, 12, 30), year.startDate);
        assertEquals(LocalDate.of(2027, 1, 2), year.endDate);
    }

    @Test
    public void weekdayRangeAndCompactWeekdays() {
        ParsedCalendarEvent range = parse("다음 주 목요일부터 금요일까지 선진지견학");
        assertEquals(LocalDate.of(2026, 8, 27), range.startDate);
        assertEquals(LocalDate.of(2026, 8, 28), range.endDate);

        ParsedCalendarEvent compact = parse("다음주 금토일 출장");
        assertEquals(LocalDate.of(2026, 8, 28), compact.startDate);
        assertEquals(LocalDate.of(2026, 8, 30), compact.endDate);
        assertEquals("출장", compact.title);
    }

    @Test
    public void dateAndTimeRangeKeepsBothEnds() {
        ParsedCalendarEvent event = parse("8월 27일 오전 9시부터 28일 오후 6시까지 선진지견학");
        assertEquals(LocalDate.of(2026, 8, 27), event.startDate);
        assertEquals(LocalDate.of(2026, 8, 28), event.endDate);
        assertEquals(LocalTime.of(9, 0), event.startTime);
        assertEquals(LocalTime.of(18, 0), event.endTime);
        assertEquals("선진지견학", event.title);
        assertFalse(event.allDay);
        assertFalse(event.requiresConfirmation);
    }

    @Test
    public void calendarDayDurationIncludesStartDay() {
        ParsedCalendarEvent event = parse("8월 27일부터 이틀 동안 선진지견학");
        assertEquals(LocalDate.of(2026, 8, 27), event.startDate);
        assertEquals(LocalDate.of(2026, 8, 28), event.endDate);
        assertTrue(event.allDay);
    }

    @Test
    public void timeRangeAndDuration() {
        ParsedCalendarEvent range = parse("금요일 오후 3시부터 5시까지 회의");
        assertEquals(LocalTime.of(15, 0), range.startTime);
        assertEquals(LocalTime.of(17, 0), range.endTime);
        assertFalse(range.requiresConfirmation);

        ParsedCalendarEvent duration = parse("금요일 오후 3시부터 두 시간 회의");
        assertEquals(Integer.valueOf(120), duration.durationMinutes);
        assertEquals(LocalTime.of(17, 0), duration.endTime);
    }

    @Test
    public void recurrenceAndDeadline() {
        ParsedCalendarEvent weekly = parse("매주 월요일 오전 10시 회의");
        assertNotNull(weekly.repeat);
        assertEquals("WEEKLY", weekly.repeat.frequency);
        assertEquals(1, weekly.repeat.byDays.size());

        ParsedCalendarEvent deadline = parse("금요일까지 보고서 제출");
        assertEquals(ParsedCalendarEvent.EventType.DEADLINE, deadline.eventType);
        assertEquals("보고서 제출", deadline.title);
        assertEquals("업무", deadline.category);
    }

    @Test
    public void correctionKeepsTheLaterValue() {
        ParsedCalendarEvent time = parse("금요일 3시 아니 4시 병원");
        assertEquals(LocalTime.of(4, 0), time.startTime);
        assertEquals("병원", time.title);

        ParsedCalendarEvent day = parse("내일 아니 모레 오후 3시 병원");
        assertEquals(LocalDate.of(2026, 8, 21), day.startDate);

        ParsedCalendarEvent date = parse("8월 27일 아니 28일 선진지견학");
        assertEquals(LocalDate.of(2026, 8, 28), date.startDate);
    }

    @Test
    public void approximateAndMultipleDatesRequireConfirmation() {
        ParsedCalendarEvent approximate = parse("다음 달 말쯤 여행");
        assertEquals(ParsedCalendarEvent.Precision.APPROXIMATE, approximate.precision);
        assertTrue(approximate.requiresConfirmation);

        ParsedCalendarEvent multiple = parse("8월 27일과 28일 회의");
        assertTrue(multiple.requiresConfirmation);
        assertEquals("MULTIPLE_DATE_AMBIGUITY", multiple.confirmationReason);
    }

    @Test
    public void intentAndNoEndTimeArePreserved() {
        ParsedCalendarEvent search = parse("다음 주 금요일 일정 알려줘");
        assertEquals(ParsedCalendarEvent.Intent.SEARCH_EVENT, search.intent);

        ParsedCalendarEvent create = parse("내일 오전 10시 회의");
        assertNull(create.endDate);
        assertNull(create.endTime);
        assertFalse(create.allDay);
    }

    @Test
    public void numericRelativeAndMonthWeekDates() {
        assertEquals(LocalDate.of(2026, 8, 27), parse("8/27 병원").startDate);
        assertEquals(LocalDate.of(2026, 8, 22), parse("3일 뒤 병원").startDate);
        assertEquals(LocalDate.of(2026, 9, 19), parse("한 달 뒤 병원").startDate);
        assertEquals(LocalDate.of(2026, 9, 7), parse("다음 달 첫 번째 월요일 회의").startDate);
    }

    @Test
    public void approximateTimeWeekendAndRepeatUntilNeedCorrectContext() {
        ParsedCalendarEvent around = parse("금요일 오후 3시쯤 병원");
        assertEquals(ParsedCalendarEvent.Precision.APPROXIMATE, around.precision);
        assertTrue(around.requiresConfirmation);

        ParsedCalendarEvent weekend = parse("주말 여행");
        assertEquals(LocalDate.of(2026, 8, 22), weekend.startDate);
        assertEquals(LocalDate.of(2026, 8, 23), weekend.endDate);
        assertEquals("WEEKEND_AMBIGUITY", weekend.confirmationReason);

        ParsedCalendarEvent until = parse("올해 말까지 매주 금요일 오후 3시 회의");
        assertEquals(LocalDate.of(2026, 8, 21), until.startDate);
        assertNotNull(until.repeat);
        assertEquals(LocalDate.of(2026, 12, 31), until.repeat.until);
    }

    @Test
    public void specificationSentencesAlwaysProduceStructuredOutput() {
        String[] samples = {
                "오늘 3시 병원", "내일 오전 10시 회의", "모레 저녁 7시 약속",
                "이번 주 금요일 오후 3시 병원", "오는 금요일 3시 병원", "다가오는 금요일 3시 병원",
                "담주 금요일 오후 3시 병원", "차주 금요일 15시 병원", "다다음 주 금요일 3시 병원",
                "2주 뒤 금요일 3시 병원", "이번 달 27일 병원", "다음 달 5일 회의",
                "2026년 8월 27일부터 28일까지 선진지견학", "8월 27일-28일 선진지견학",
                "다음주 목요일~금요일 선진지견학", "이번주 금토 여행",
                "8월 27일 9시부터 8월 28일 18시까지 선진지견학",
                "내일부터 모레까지 출장", "다음 주 월요일부터 수요일까지 교육",
                "8월 27일 하루 종일 교육", "금요일 3시부터 두 시간 회의",
                "매일 오전 8시 약 먹기", "격주 금요일 오후 3시 회의",
                "매월 첫째 주 금요일 회의", "매월 마지막 금요일 회의",
                "8월 27일까지 신청서 제출", "8월 27일 아니 28일 병원",
                "어 다음 주 금요일 음 오후 3시에 병원",
                "8월 27일부터 28일까지 선진지견학 일정 넣어줘"
        };
        for (String sample : samples) {
            ParsedCalendarEvent event = parse(sample);
            assertEquals(sample, event.originalText);
            assertNotNull("startDate: " + sample, event.startDate);
            assertNotNull("title: " + sample, event.title);
            assertFalse("normalizedText: " + sample, event.normalizedText.isBlank());
        }
    }
}
