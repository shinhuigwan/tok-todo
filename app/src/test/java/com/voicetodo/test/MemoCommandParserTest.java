package com.voicetodo.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class MemoCommandParserTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-21T03:00:00Z"), SEOUL);

    @Test
    public void recognizesCommonMemoCommands() {
        assertTrue(MemoCommandParser.isMemoCommand("우유 사기 메모해줘"));
        assertTrue(MemoCommandParser.isMemoCommand("내일 일정 메모장에 저장해 주세요"));
        assertTrue(MemoCommandParser.isMemoCommand("전화번호 메모로 남겨줘"));
        assertFalse(MemoCommandParser.isMemoCommand("내일 오후 3시 치과 예약"));
    }

    @Test
    public void voiceMemoUsesTheSpokenDateAndRemovesTheCommand() {
        MemoCommandParser.Draft draft = MemoCommandParser.parse(
                "내일 거래처 전화하기 메모해줘", CLOCK, SEOUL);

        assertEquals(LocalDate.of(2026, 8, 22), draft.date);
        assertEquals("거래처 전화하기", draft.title);
        assertEquals("내일 거래처 전화하기", draft.content);
    }

    @Test
    public void memoWithoutDateUsesToday() {
        MemoCommandParser.Draft draft = MemoCommandParser.parse(
                "주차 위치 B3 12번 메모해 주세요", CLOCK, SEOUL);

        assertEquals(LocalDate.of(2026, 8, 21), draft.date);
        assertEquals("주차 위치 B3 12번", draft.title);
    }

    @Test
    public void emptyMemoCommandDoesNotUseTheTodoFallbackTitle() {
        MemoCommandParser.Draft draft = MemoCommandParser.parse("메모해줘", CLOCK, SEOUL);

        assertEquals("새 메모", draft.title);
        assertEquals("", draft.content);
    }
}
