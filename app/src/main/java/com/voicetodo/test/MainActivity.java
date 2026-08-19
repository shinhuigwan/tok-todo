package com.voicetodo.test;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final int REQUEST_SPEECH = 1001;
    private static final int REQUEST_NOTIFICATION = 1002;
    private static final int YEAR_PICKER_START = 2000;
    private static final int YEAR_PICKER_END = 2100;
    private static final int[] REMINDER_OPTIONS = {
            0,
            5, 10, 15, 30,
            60, 120, 180, 360, 720,
            1_440, 2_880, 4_320, 7_200,
            10_080, 20_160, 40_320
    };
    private static final String[] REMINDER_LABELS = {
            "일정 시간",
            "5분 전", "10분 전", "15분 전", "30분 전",
            "1시간 전", "2시간 전", "3시간 전", "6시간 전", "12시간 전",
            "1일 전", "2일 전", "3일 전", "5일 전",
            "1주 전", "2주 전", "4주 전"
    };

    private static final int INK = Color.rgb(24, 29, 43);
    private static final int MUTED = Color.rgb(112, 119, 139);
    private static final int BACKGROUND = Color.rgb(245, 246, 250);
    private static final int SURFACE = Color.WHITE;
    private static final int PURPLE = Color.rgb(103, 87, 255);
    private static final int PURPLE_SOFT = Color.rgb(239, 236, 255);
    private static final int RED = Color.rgb(225, 73, 86);
    private static final int RED_SOFT = Color.rgb(255, 236, 239);
    private static final int RED_RANGE = Color.rgb(249, 184, 192);
    private static final int BLUE = Color.rgb(54, 112, 226);
    private static final int BLUE_SOFT = Color.rgb(232, 240, 255);
    private static final int BLUE_RANGE = Color.rgb(177, 202, 249);
    private static final int AMBER = Color.rgb(202, 132, 19);
    private static final int AMBER_SOFT = Color.rgb(255, 247, 222);
    private static final int BORDER = Color.rgb(227, 229, 238);

    private final List<TodoItem> items = new ArrayList<>();
    private TodoStore store;
    private GridLayout calendarGrid;
    private TextView yearTitle;
    private TextView monthTitle;
    private TextView selectedDateTitle;
    private TextView selectedDateSummary;
    private TextView headerSummary;
    private LinearLayout detailList;
    private EditText searchInput;
    private LinearLayout searchSection;
    private TextView searchResultTitle;
    private LinearLayout searchResultList;
    private String activeSearchQuery = "";
    private YearMonth visibleMonth = YearMonth.now();
    private LocalDate selectedDate = LocalDate.now();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotificationHelper.createChannel(this);
        NotificationHelper.scheduleNextDailySummary(this);
        store = new TodoStore(this);
        items.addAll(store.load());
        sortItems();
        setContentView(buildScreen());
        renderAll();

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 사용자가 앱을 열었으면 오래된 묶음 알림을 알림창에 남겨 두지 않는다.
        NotificationHelper.clearDailySummary(this);
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);

        LinearLayout page = vertical();
        page.setPadding(dp(18), dp(20), dp(18), dp(32));
        scroll.addView(page, matchWrap());

        LinearLayout top = horizontal();
        LinearLayout heading = vertical();
        TextView eyebrow = text("톡todo", 13, PURPLE, true);
        heading.addView(eyebrow);
        TextView title = text("오늘을 말로 정리하세요", 27, INK, true);
        title.setPadding(0, dp(4), 0, 0);
        heading.addView(title);
        headerSummary = text("", 14, MUTED, false);
        headerSummary.setPadding(0, dp(6), 0, 0);
        heading.addView(headerSummary);
        top.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView todayBadge = text("오늘", 13, PURPLE, true);
        todayBadge.setGravity(Gravity.CENTER);
        todayBadge.setBackground(rounded(PURPLE_SOFT, 18, Color.TRANSPARENT));
        todayBadge.setOnClickListener(v -> {
            selectedDate = LocalDate.now();
            visibleMonth = YearMonth.from(selectedDate);
            renderAll();
        });
        top.addView(todayBadge, new LinearLayout.LayoutParams(dp(58), dp(38)));
        page.addView(top, matchWrap());

        LinearLayout voiceCard = vertical();
        voiceCard.setGravity(Gravity.CENTER);
        voiceCard.setPadding(dp(14), dp(7), dp(14), dp(7));
        voiceCard.setBackground(rounded(PURPLE, 18, Color.TRANSPARENT));
        voiceCard.setOnClickListener(v -> startSpeech());
        TextView voiceTitle = text("🎙  말하면 바로 저장돼요", 16, Color.WHITE, true);
        voiceTitle.setGravity(Gravity.CENTER);
        voiceCard.addView(voiceTitle);
        TextView voiceHint = text("예: 내일 오후 3시 치과 예약, 30분 전에 알려줘", 11,
                Color.rgb(225, 222, 255), false);
        voiceHint.setGravity(Gravity.CENTER);
        voiceHint.setPadding(0, dp(3), 0, 0);
        voiceCard.addView(voiceHint);
        LinearLayout.LayoutParams voiceParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(66));
        voiceParams.setMargins(0, dp(14), 0, 0);
        page.addView(voiceCard, voiceParams);

        LinearLayout manualRow = horizontal();
        manualRow.setPadding(0, dp(11), 0, 0);
        EditText manualInput = new EditText(this);
        manualInput.setHint("직접 입력해도 바로 저장됩니다");
        manualInput.setTextSize(14);
        manualInput.setTextColor(INK);
        manualInput.setHintTextColor(Color.rgb(157, 162, 179));
        manualInput.setSingleLine(true);
        manualInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        manualInput.setPadding(dp(15), 0, dp(12), 0);
        manualInput.setBackground(rounded(SURFACE, 15, BORDER));
        manualRow.addView(manualInput, new LinearLayout.LayoutParams(0, dp(52), 1f));
        Button addButton = button("저장", PURPLE_SOFT, PURPLE);
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(76), dp(52));
        addParams.setMarginStart(dp(8));
        manualRow.addView(addButton, addParams);
        addButton.setOnClickListener(v -> {
            String text = manualInput.getText().toString().trim();
            if (text.isEmpty()) {
                manualInput.setError("할 일을 입력해 주세요.");
                return;
            }
            addTodoFromText(text);
            manualInput.setText("");
        });
        page.addView(manualRow, matchWrap());

        LinearLayout searchRow = horizontal();
        searchRow.setPadding(0, dp(10), 0, 0);
        searchInput = new EditText(this);
        searchInput.setHint("🔎  저장된 할 일 키워드 검색");
        searchInput.setTextSize(14);
        searchInput.setTextColor(INK);
        searchInput.setHintTextColor(Color.rgb(145, 151, 169));
        searchInput.setSingleLine(true);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setPadding(dp(15), 0, dp(12), 0);
        searchInput.setBackground(rounded(SURFACE, 15, BORDER));
        searchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button searchButton = button("검색", Color.rgb(238, 240, 246), INK);
        LinearLayout.LayoutParams searchButtonParams = new LinearLayout.LayoutParams(dp(70), dp(48));
        searchButtonParams.setMarginStart(dp(8));
        searchRow.addView(searchButton, searchButtonParams);
        searchButton.setOnClickListener(v -> runSearch());
        page.addView(searchRow, matchWrap());

        searchSection = vertical();
        searchSection.setPadding(dp(14), dp(13), dp(14), dp(13));
        searchSection.setBackground(rounded(SURFACE, 18, BORDER));
        searchSection.setVisibility(View.GONE);
        LinearLayout.LayoutParams searchSectionParams = matchWrap();
        searchSectionParams.setMargins(0, dp(10), 0, 0);
        page.addView(searchSection, searchSectionParams);

        LinearLayout searchHeader = horizontal();
        searchResultTitle = text("", 16, INK, true);
        searchHeader.addView(searchResultTitle, new LinearLayout.LayoutParams(0, dp(36), 1f));
        Button clearSearch = button("닫기", Color.rgb(246, 246, 249), MUTED);
        clearSearch.setOnClickListener(v -> clearSearch());
        searchHeader.addView(clearSearch, new LinearLayout.LayoutParams(dp(62), dp(36)));
        searchSection.addView(searchHeader, matchWrap());
        searchResultList = vertical();
        searchSection.addView(searchResultList, matchWrap());

        LinearLayout calendarPanel = vertical();
        calendarPanel.setPadding(dp(14), dp(14), dp(14), dp(12));
        calendarPanel.setBackground(rounded(SURFACE, 22, BORDER));
        LinearLayout.LayoutParams panelParams = matchWrap();
        panelParams.setMargins(0, dp(20), 0, 0);
        page.addView(calendarPanel, panelParams);

        LinearLayout calendarHeader = horizontal();
        Button previous = compactButton("‹");
        previous.setOnClickListener(v -> moveVisibleMonth(-1));
        calendarHeader.addView(previous, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout dateSelectors = horizontal();
        dateSelectors.setGravity(Gravity.CENTER);
        yearTitle = text("", 18, INK, true);
        yearTitle.setGravity(Gravity.CENTER);
        yearTitle.setPadding(dp(12), 0, dp(12), 0);
        yearTitle.setBackground(rounded(Color.rgb(247, 247, 251), 14, Color.TRANSPARENT));
        yearTitle.setContentDescription("연도 선택");
        yearTitle.setOnClickListener(v -> showYearPicker());
        dateSelectors.addView(yearTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));

        monthTitle = text("", 18, PURPLE, true);
        monthTitle.setGravity(Gravity.CENTER);
        monthTitle.setPadding(dp(12), 0, dp(12), 0);
        monthTitle.setBackground(rounded(PURPLE_SOFT, 14, Color.TRANSPARENT));
        monthTitle.setContentDescription("월 선택");
        monthTitle.setOnClickListener(v -> showMonthPicker());
        LinearLayout.LayoutParams monthSelectorParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        monthSelectorParams.setMarginStart(dp(7));
        dateSelectors.addView(monthTitle, monthSelectorParams);
        calendarHeader.addView(dateSelectors, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button next = compactButton("›");
        next.setOnClickListener(v -> moveVisibleMonth(1));
        calendarHeader.addView(next, new LinearLayout.LayoutParams(dp(42), dp(42)));
        calendarPanel.addView(calendarHeader, matchWrap());

        calendarGrid = new SwipeCalendarGrid();
        calendarGrid.setColumnCount(7);
        calendarGrid.setRowCount(7);
        calendarGrid.setPadding(0, dp(8), 0, 0);
        calendarPanel.addView(calendarGrid, matchWrap());

        LinearLayout legend = horizontal();
        legend.setGravity(Gravity.CENTER);
        legend.setPadding(0, dp(9), 0, 0);
        legend.addView(legendItem("미완료", RED_RANGE));
        TextView spacer = new TextView(this);
        legend.addView(spacer, new LinearLayout.LayoutParams(dp(18), 1));
        legend.addView(legendItem("완료", BLUE_RANGE));
        calendarPanel.addView(legend, matchWrap());
        TextView importanceLegend = text("진한 숫자·선 = 중요 일정 포함", 11, AMBER, true);
        importanceLegend.setGravity(Gravity.CENTER);
        importanceLegend.setPadding(0, dp(5), 0, 0);
        calendarPanel.addView(importanceLegend, matchWrap());

        LinearLayout detailHeader = horizontal();
        detailHeader.setPadding(dp(2), dp(22), dp(2), dp(10));
        LinearLayout detailHeading = vertical();
        selectedDateTitle = text("", 21, INK, true);
        detailHeading.addView(selectedDateTitle);
        selectedDateSummary = text("", 13, MUTED, false);
        selectedDateSummary.setPadding(0, dp(4), 0, 0);
        detailHeading.addView(selectedDateSummary);
        detailHeader.addView(detailHeading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView changeHint = text("카드에서 상태 변경", 12, PURPLE, true);
        changeHint.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        detailHeader.addView(changeHint);
        page.addView(detailHeader, matchWrap());

        detailList = vertical();
        page.addView(detailList, matchWrap());
        return scroll;
    }

    private void startSpeech() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "날짜, 시간과 할 일을 말해 주세요");
        try {
            startActivityForResult(intent, REQUEST_SPEECH);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "음성 인식 서비스가 없습니다. 직접 입력을 이용해 주세요.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SPEECH || resultCode != RESULT_OK || data == null) return;
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) {
            Toast.makeText(this, "음성을 인식하지 못했습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        // 음성 인식이 끝나는 즉시 분석하고 저장한다.
        addTodoFromText(results.get(0));
    }

    private void addTodoFromText(String source) {
        ParsedCalendarEvent parsed = KoreanTodoParser.parse(source);
        if (parsed.intent == ParsedCalendarEvent.Intent.SEARCH_EVENT) {
            selectedDate = parsed.startDate;
            visibleMonth = YearMonth.from(selectedDate);
            renderAll();
            Toast.makeText(this, "해당 날짜의 일정을 열었습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (parsed.intent == ParsedCalendarEvent.Intent.DELETE_EVENT ||
                parsed.intent == ParsedCalendarEvent.Intent.UPDATE_EVENT) {
            selectedDate = parsed.startDate;
            visibleMonth = YearMonth.from(selectedDate);
            renderAll();
            Toast.makeText(this, "해당 날짜의 카드를 열었습니다. 카드에서 변경하거나 삭제해 주세요.", Toast.LENGTH_LONG).show();
            return;
        }
        if (parsed.requiresConfirmation) {
            showParseConfirmation(source, parsed);
            return;
        }
        saveParsedTodo(source, parsed);
    }

    private void showParseConfirmation(String source, ParsedCalendarEvent parsed) {
        new AlertDialog.Builder(this)
                .setTitle("일정을 확인해 주세요")
                .setMessage(formatParsedSummary(parsed) + "\n\n확인 이유: " + confirmationLabel(parsed.confirmationReason))
                .setNegativeButton("취소", null)
                .setPositiveButton("이대로 저장", (dialog, which) -> saveParsedTodo(source, parsed))
                .show();
    }

    private void saveParsedTodo(String source, ParsedCalendarEvent parsed) {
        TodoItem item = TodoItem.fromParsed(
                UUID.randomUUID().toString(), source, parsed, ZoneId.systemDefault(), System.currentTimeMillis());
        items.add(item);
        sortItems();
        store.save(items);
        NotificationHelper.schedule(this, item);

        selectedDate = dateOf(item);
        visibleMonth = YearMonth.from(selectedDate);
        renderAll();
        Toast.makeText(this, "‘" + item.title + "’을 바로 저장했습니다.", Toast.LENGTH_SHORT).show();
    }

    private String formatParsedSummary(ParsedCalendarEvent parsed) {
        StringBuilder summary = new StringBuilder();
        summary.append("제목: ").append(parsed.title).append("\n");
        summary.append("날짜: ").append(parsed.startDate);
        if (parsed.endDate != null) summary.append(" ~ ").append(parsed.endDate);
        summary.append("\n시간: ");
        if (parsed.allDay) summary.append("하루 종일");
        else {
            summary.append(parsed.startTime == null ? "미정" : parsed.startTime);
            if (parsed.endTime != null) summary.append(" ~ ").append(parsed.endTime);
        }
        summary.append("\n분류: ").append(parsed.category);
        if (parsed.repeat != null) summary.append(" · ").append(parsed.repeat.summary());
        if (parsed.eventType == ParsedCalendarEvent.EventType.DEADLINE) summary.append(" · 마감");
        return summary.toString();
    }

    private String confirmationLabel(String reason) {
        if (reason == null) return "해석 확인 필요";
        return switch (reason) {
            case "AM_PM_AMBIGUITY" -> "오전인지 오후인지 불분명합니다";
            case "MIDNIGHT_AMBIGUITY" -> "밤 12시의 날짜가 불분명합니다";
            case "APPROXIMATE_DATE" -> "정확한 날짜가 아닌 표현입니다";
            case "APPROXIMATE_TIME" -> "정확한 시간이 아닌 표현입니다";
            case "MULTIPLE_DATE_AMBIGUITY" -> "하나의 기간인지 두 일정인지 불분명합니다";
            case "WEEKEND_AMBIGUITY" -> "토요일·일요일 전체 일정인지 확인이 필요합니다";
            case "INVALID_DATE" -> "유효하지 않은 날짜입니다";
            case "END_BEFORE_START" -> "종료가 시작보다 빠릅니다";
            case "MISSING_TITLE" -> "일정 제목을 찾지 못했습니다";
            default -> reason;
        };
    }

    private void renderAll() {
        renderHeader();
        renderCalendar();
        renderSelectedDate();
        if (!activeSearchQuery.isEmpty()) renderSearchResults();
    }

    private void runSearch() {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            searchInput.setError("검색할 키워드를 입력해 주세요.");
            return;
        }
        activeSearchQuery = query;
        searchSection.setVisibility(View.VISIBLE);
        renderSearchResults();
    }

    private void clearSearch() {
        activeSearchQuery = "";
        searchInput.setText("");
        searchSection.setVisibility(View.GONE);
        searchResultList.removeAllViews();
    }

    private void renderSearchResults() {
        if (activeSearchQuery.isEmpty()) return;
        String normalizedQuery = activeSearchQuery.toLowerCase(Locale.KOREAN);
        searchResultList.removeAllViews();
        List<TodoItem> results = new ArrayList<>();
        for (TodoItem item : items) {
            String searchable = (item.title + " " + item.originalVoiceText + " " + item.category + " "
                    + dateOf(item).getMonthValue() + "월 " + dateOf(item).getDayOfMonth() + "일 "
                    + endDateOf(item).getMonthValue() + "월 " + endDateOf(item).getDayOfMonth() + "일 "
                    + item.repeatFrequency + " " + item.eventType + (item.important ? " 중요 긴급 최우선" : " 일반"))
                    .toLowerCase(Locale.KOREAN);
            if (!searchable.contains(normalizedQuery)) continue;
            results.add(item);
        }
        results.sort(Comparator.comparing((TodoItem item) -> !item.important)
                .thenComparing(item -> item.completed)
                .thenComparingLong(item -> item.scheduledAt));
        for (TodoItem item : results) {
            searchResultList.addView(buildSearchResult(item), searchResultParams());
        }
        int matches = results.size();
        searchResultTitle.setText("‘" + activeSearchQuery + "’ 검색 결과 " + matches + "개");
        if (matches == 0) {
            TextView empty = text("일치하는 할 일이 없습니다.", 14, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(22), 0, dp(15));
            searchResultList.addView(empty, matchWrap());
        }
    }

    private View buildSearchResult(TodoItem item) {
        LinearLayout card = horizontal();
        card.setPadding(dp(12), dp(11), dp(11), dp(11));
        card.setBackground(rounded(Color.rgb(248, 248, 251), 13, Color.TRANSPARENT));
        card.setOnClickListener(v -> {
            selectedDate = dateOf(item);
            visibleMonth = YearMonth.from(selectedDate);
            clearSearch();
            renderAll();
            Toast.makeText(this, "해당 날짜의 상세 일정을 열었습니다.", Toast.LENGTH_SHORT).show();
        });

        TextView statusDot = text(item.completed ? "✓" : "!", 12, Color.WHITE, true);
        statusDot.setGravity(Gravity.CENTER);
        statusDot.setBackground(rounded(item.completed ? BLUE : RED, 13, Color.TRANSPARENT));
        card.addView(statusDot, new LinearLayout.LayoutParams(dp(26), dp(26)));

        LinearLayout copy = vertical();
        TextView title = text((item.important ? "★ " : "") + item.title,
                15, item.completed ? MUTED : INK, true);
        copy.addView(title);
        TextView meta = text(formatSchedule(item, true) + " · " + item.category, 12, MUTED, false);
        meta.setPadding(0, dp(3), 0, 0);
        copy.addView(meta);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMarginStart(dp(10));
        card.addView(copy, copyParams);

        TextView arrow = text("›", 24, MUTED, false);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT));
        return card;
    }

    private void renderHeader() {
        int todayOpen = 0;
        int allOpen = 0;
        int importantOpen = 0;
        LocalDate today = LocalDate.now();
        for (TodoItem item : items) {
            if (!item.completed) {
                allOpen++;
                if (item.important) importantOpen++;
                if (occursOnDate(item, today)) todayOpen++;
            }
        }
        headerSummary.setText("오늘 " + todayOpen + "개 · 중요 " + importantOpen
                + "개 · 전체 미완료 " + allOpen + "개");
    }

    private void moveVisibleMonth(int amount) {
        jumpToMonth(visibleMonth.plusMonths(amount));
    }

    private void jumpToMonth(YearMonth target) {
        int day = Math.min(selectedDate.getDayOfMonth(), target.lengthOfMonth());
        visibleMonth = target;
        selectedDate = target.atDay(day);
        renderAll();
    }

    private void showYearPicker() {
        int count = YEAR_PICKER_END - YEAR_PICKER_START + 1;
        String[] years = new String[count];
        for (int i = 0; i < count; i++) years[i] = (YEAR_PICKER_START + i) + "년";
        int selected = Math.max(0, Math.min(count - 1, visibleMonth.getYear() - YEAR_PICKER_START));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("연도 선택")
                .setSingleChoiceItems(years, selected, (picker, which) -> {
                    jumpToMonth(YearMonth.of(YEAR_PICKER_START + which, visibleMonth.getMonthValue()));
                    picker.dismiss();
                })
                .setNegativeButton("취소", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getListView().setSelection(Math.max(0, selected - 2)));
        dialog.show();
    }

    private void showMonthPicker() {
        String[] months = new String[12];
        for (int i = 0; i < months.length; i++) months[i] = (i + 1) + "월";

        new AlertDialog.Builder(this)
                .setTitle(visibleMonth.getYear() + "년 월 선택")
                .setSingleChoiceItems(months, visibleMonth.getMonthValue() - 1, (dialog, which) -> {
                    jumpToMonth(YearMonth.of(visibleMonth.getYear(), which + 1));
                    dialog.dismiss();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void renderCalendar() {
        yearTitle.setText(visibleMonth.getYear() + "년 ▾");
        monthTitle.setText(visibleMonth.getMonthValue() + "월 ▾");
        calendarGrid.removeAllViews();

        String[] weekdays = {"일", "월", "화", "수", "목", "금", "토"};
        for (int column = 0; column < 7; column++) {
            int color = column == 0 ? RED : (column == 6 ? BLUE : MUTED);
            TextView dayName = text(weekdays[column], 12, color, true);
            dayName.setGravity(Gravity.CENTER);
            calendarGrid.addView(dayName, gridParams(0, column, dp(30)));
        }

        LocalDate first = visibleMonth.atDay(1);
        int firstColumn = first.getDayOfWeek().getValue() % 7;
        int length = visibleMonth.lengthOfMonth();
        for (int slot = 0; slot < 42; slot++) {
            int row = slot / 7 + 1;
            int column = slot % 7;
            int day = slot - firstColumn + 1;
            if (day < 1 || day > length) {
                calendarGrid.addView(new TextView(this), gridParams(row, column, dp(66)));
                continue;
            }
            LocalDate date = visibleMonth.atDay(day);
            calendarGrid.addView(buildDayCell(date, column), gridParams(row, column, dp(66)));
        }
    }

    private View buildDayCell(LocalDate date, int column) {
        boolean selected = date.equals(selectedDate);
        boolean today = date.equals(LocalDate.now());
        int stroke = selected || today ? PURPLE : Color.TRANSPARENT;
        int background = selected ? PURPLE_SOFT : SURFACE;

        LinearLayout cell = vertical();
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(2), dp(4), dp(2), dp(4));
        cell.setBackground(rounded(background, 13, stroke));
        cell.setOnClickListener(v -> {
            selectedDate = date;
            renderCalendar();
            renderSelectedDate();
        });

        int dayColor = column == 0 ? RED : (column == 6 ? BLUE : INK);
        TextView number = text(String.valueOf(date.getDayOfMonth()), 14, dayColor, selected || today);
        number.setGravity(Gravity.CENTER);
        cell.addView(number, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

        int open = countSingleDayForDate(date, false);
        int completed = countSingleDayForDate(date, true);
        boolean importantOpen = hasImportantSingleDay(date, false);
        boolean importantCompleted = hasImportantSingleDay(date, true);
        LinearLayout counts = horizontal();
        counts.setGravity(Gravity.CENTER);
        if (open > 0) counts.addView(countBadge(open, RED, RED_SOFT, importantOpen));
        if (open > 0 && completed > 0) {
            TextView gap = new TextView(this);
            counts.addView(gap, new LinearLayout.LayoutParams(dp(3), 1));
        }
        if (completed > 0) counts.addView(countBadge(completed, BLUE, BLUE_SOFT, importantCompleted));
        cell.addView(counts, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));

        LinearLayout rangeLines = vertical();
        rangeLines.setGravity(Gravity.CENTER_VERTICAL);
        addRangeLine(rangeLines, date, false, RED_RANGE);
        addRangeLine(rangeLines, date, true, BLUE_RANGE);
        cell.addView(rangeLines, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14)));
        return cell;
    }

    private void renderSelectedDate() {
        String weekday = selectedDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN);
        selectedDateTitle.setText(selectedDate.getMonthValue() + "월 " + selectedDate.getDayOfMonth() + "일 " + weekday);
        int open = countForDate(selectedDate, false);
        int completed = countForDate(selectedDate, true);
        int important = countImportantForDate(selectedDate);
        selectedDateSummary.setText("미완료 " + open + "개 · 완료 " + completed
                + "개" + (important > 0 ? " · 중요 " + important + "개" : ""));

        detailList.removeAllViews();
        List<TodoItem> dayItems = new ArrayList<>();
        for (TodoItem item : items) {
            if (!occursOnDate(item, selectedDate)) continue;
            dayItems.add(item);
        }
        dayItems.sort(Comparator.comparing((TodoItem item) -> !item.important)
                .thenComparingLong(item -> item.scheduledAt));
        int shown = 0;
        for (TodoItem item : dayItems) {
            detailList.addView(buildDetailCard(item), detailCardParams());
            shown++;
        }
        if (shown == 0) {
            LinearLayout empty = vertical();
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(32), dp(20), dp(32));
            empty.setBackground(rounded(SURFACE, 18, BORDER));
            TextView emptyTitle = text("이 날짜에는 할 일이 없어요", 16, INK, true);
            emptyTitle.setGravity(Gravity.CENTER);
            empty.addView(emptyTitle);
            TextView emptyHint = text("마이크를 누르고 일정을 말해 보세요.", 13, MUTED, false);
            emptyHint.setGravity(Gravity.CENTER);
            emptyHint.setPadding(0, dp(6), 0, 0);
            empty.addView(emptyHint);
            detailList.addView(empty, matchWrap());
        }
    }

    private View buildDetailCard(TodoItem item) {
        LinearLayout card = horizontal();
        card.setPadding(dp(14), dp(14), dp(12), dp(14));
        card.setBackground(rounded(SURFACE, 18, BORDER));

        TextView statusBar = new TextView(this);
        statusBar.setBackground(rounded(item.completed ? BLUE : RED, 4, Color.TRANSPARENT));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(5), ViewGroup.LayoutParams.MATCH_PARENT);
        barParams.setMarginEnd(dp(12));
        card.addView(statusBar, barParams);

        LinearLayout content = vertical();
        LinearLayout meta = horizontal();
        TextView category = text(item.category, 12, categoryColor(item.category), true);
        category.setPadding(dp(9), dp(4), dp(9), dp(4));
        category.setBackground(rounded(categoryBackground(item.category), 14, Color.TRANSPARENT));
        meta.addView(category);
        TextView status = text(item.completed ? "완료" : "미완료", 12, item.completed ? BLUE : RED, true);
        status.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        meta.addView(status, new LinearLayout.LayoutParams(0, dp(27), 1f));
        content.addView(meta, matchWrap());

        LinearLayout titleRow = horizontal();
        titleRow.setPadding(0, dp(7), 0, dp(2));
        TextView priority = text(item.important ? "★" : "☆", 23, item.important ? AMBER : MUTED, true);
        priority.setGravity(Gravity.CENTER);
        priority.setBackground(rounded(item.important ? AMBER_SOFT : Color.rgb(247, 247, 250),
                17, Color.TRANSPARENT));
        priority.setOnClickListener(v -> toggleImportant(item));
        titleRow.addView(priority, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView title = text(item.title, 18, item.completed ? MUTED : INK, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        if (item.completed) {
            SpannableString strike = new SpannableString(item.title);
            strike.setSpan(new StrikethroughSpan(), 0, strike.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            title.setText(strike);
        }
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, dp(40), 1f);
        titleParams.setMarginStart(dp(9));
        titleRow.addView(title, titleParams);
        content.addView(titleRow, matchWrap());

        String reminder = formatReminderSummary(item);
        String extra = "";
        if (!item.repeatFrequency.isBlank()) extra += " · " + repeatLabel(item);
        if ("DEADLINE".equals(item.eventType)) extra += " · 마감";
        TextView time = text(formatSchedule(item, false) + " · " + reminder + extra, 13, MUTED, false);
        content.addView(time);

        if (!item.originalVoiceText.isBlank()) {
            TextView original = text("“" + item.originalVoiceText + "”", 12, Color.rgb(151, 156, 172), false);
            original.setPadding(0, dp(6), 0, 0);
            content.addView(original);
        }

        LinearLayout actions = horizontal();
        actions.setPadding(0, dp(12), 0, 0);
        Button toggle = button(item.completed ? "미완료" : "완료",
                item.completed ? RED_SOFT : BLUE_SOFT,
                item.completed ? RED : BLUE);
        toggle.setOnClickListener(v -> toggleStatus(item));
        actions.addView(toggle, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button reminderButton = button("알림", PURPLE_SOFT, PURPLE);
        reminderButton.setOnClickListener(v -> showReminderSettings(item));
        LinearLayout.LayoutParams reminderParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        reminderParams.setMarginStart(dp(8));
        actions.addView(reminderButton, reminderParams);

        Button delete = button("삭제", Color.rgb(246, 246, 249), MUTED);
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        deleteParams.setMarginStart(dp(8));
        actions.addView(delete, deleteParams);
        delete.setOnClickListener(v -> confirmDelete(item));
        content.addView(actions, matchWrap());

        card.addView(content, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return card;
    }

    private void toggleStatus(TodoItem item) {
        item.completed = !item.completed;
        if (item.completed) NotificationHelper.cancel(this, item.id);
        else NotificationHelper.schedule(this, item);
        store.save(items);
        renderAll();
        Toast.makeText(this, item.completed ? "완료로 변경했습니다." : "미완료로 되돌렸습니다.", Toast.LENGTH_SHORT).show();
    }

    private void toggleImportant(TodoItem item) {
        item.important = !item.important;
        if (!item.completed) NotificationHelper.schedule(this, item);
        store.save(items);
        renderAll();
        Toast.makeText(this, item.important ? "중요 일정으로 표시했습니다." : "일반 일정으로 변경했습니다.",
                Toast.LENGTH_SHORT).show();
    }

    private void showReminderSettings(TodoItem item) {
        List<Integer> current = item.reminderOffsetList();
        boolean[] checked = new boolean[REMINDER_OPTIONS.length];
        for (int i = 0; i < REMINDER_OPTIONS.length; i++) {
            checked[i] = current.contains(REMINDER_OPTIONS[i]);
        }

        new AlertDialog.Builder(this)
                .setTitle("알림 시점 선택 · 복수 선택 가능")
                .setMultiChoiceItems(REMINDER_LABELS, checked, (dialog, which, isChecked) ->
                        checked[which] = isChecked)
                .setNegativeButton("취소", null)
                .setNeutralButton("모두 해제", (dialog, which) ->
                        saveReminderSettings(item, new ArrayList<>()))
                .setPositiveButton("저장", (dialog, which) -> {
                    List<Integer> selected = new ArrayList<>();
                    for (int i = 0; i < REMINDER_OPTIONS.length; i++) {
                        if (checked[i]) selected.add(REMINDER_OPTIONS[i]);
                    }
                    saveReminderSettings(item, selected);
                })
                .show();
    }

    private void saveReminderSettings(TodoItem item, List<Integer> offsets) {
        item.setReminderOffsets(offsets);
        int scheduled = NotificationHelper.schedule(this, item);
        store.save(items);
        renderAll();

        String message;
        if (offsets.isEmpty()) message = "이 일정의 개별 알림을 껐습니다.";
        else if (item.completed) message = "설정을 저장했습니다. 미완료로 되돌리면 알림이 예약됩니다.";
        else if (scheduled == 0) message = "설정한 알림 시점이 이미 지나 저장만 했습니다.";
        else message = "개별 알림 " + scheduled + "개를 예약했습니다.";
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String formatReminderSummary(TodoItem item) {
        List<Integer> offsets = item.reminderOffsetList();
        if (offsets.isEmpty()) return "알림 없음";
        StringBuilder value = new StringBuilder("알림 ");
        for (int i = 0; i < offsets.size(); i++) {
            if (i > 0) value.append(" · ");
            value.append(reminderLabel(offsets.get(i)));
        }
        return value.toString();
    }

    private String reminderLabel(int minutes) {
        if (minutes == 0) return "일정 시간";
        if (minutes % 10_080 == 0) return (minutes / 10_080) + "주 전";
        if (minutes % 1_440 == 0) return (minutes / 1_440) + "일 전";
        if (minutes % 60 == 0) return (minutes / 60) + "시간 전";
        return minutes + "분 전";
    }

    private void confirmDelete(TodoItem item) {
        new AlertDialog.Builder(this)
                .setMessage("‘" + item.title + "’을 삭제할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> {
                    NotificationHelper.cancel(this, item.id);
                    items.remove(item);
                    store.save(items);
                    renderAll();
                })
                .show();
    }

    private int countForDate(LocalDate date, boolean completed) {
        int count = 0;
        for (TodoItem item : items) {
            if (item.completed == completed && occursOnDate(item, date)) count++;
        }
        return count;
    }

    private int countSingleDayForDate(LocalDate date, boolean completed) {
        int count = 0;
        for (TodoItem item : items) {
            if (item.completed == completed && !isMultiDay(item) && occursOnDate(item, date)) count++;
        }
        return count;
    }

    private int countImportantForDate(LocalDate date) {
        int count = 0;
        for (TodoItem item : items) {
            if (item.important && occursOnDate(item, date)) count++;
        }
        return count;
    }

    private boolean hasImportantSingleDay(LocalDate date, boolean completed) {
        for (TodoItem item : items) {
            if (item.completed == completed && item.important && !isMultiDay(item)
                    && occursOnDate(item, date)) return true;
        }
        return false;
    }

    private boolean isMultiDay(TodoItem item) {
        return item.multiDay || endDateOf(item).isAfter(dateOf(item));
    }

    private void addRangeLine(LinearLayout container, LocalDate date, boolean completed, int color) {
        boolean present = false;
        boolean important = false;
        boolean continuesBefore = false;
        boolean continuesAfter = false;
        for (TodoItem item : items) {
            if (item.completed != completed || !isMultiDay(item) || !occursOnDate(item, date)) continue;
            present = true;
            important |= item.important;
            continuesBefore |= occursOnDate(item, date.minusDays(1));
            continuesAfter |= occursOnDate(item, date.plusDays(1));
        }
        if (!present) return;

        View line = new View(this);
        int displayColor = important ? (completed ? BLUE : RED) : color;
        line.setBackground(rangeDrawable(displayColor, continuesBefore, continuesAfter));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
        params.setMargins(continuesBefore ? 0 : dp(4), dp(1), continuesAfter ? 0 : dp(4), 0);
        container.addView(line, params);
    }

    private LocalDate dateOf(TodoItem item) {
        return Instant.ofEpochMilli(item.scheduledAt).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private LocalDate endDateOf(TodoItem item) {
        long value = item.endAt > 0 ? item.endAt : item.scheduledAt;
        return Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private boolean occursOnDate(TodoItem item, LocalDate date) {
        LocalDate start = dateOf(item);
        LocalDate end = endDateOf(item);
        if (item.repeatFrequency == null || item.repeatFrequency.isBlank())
            return !date.isBefore(start) && !date.isAfter(end);
        if (date.isBefore(start)) return false;
        if (item.repeatUntil > 0 && date.isAfter(Instant.ofEpochMilli(item.repeatUntil)
                .atZone(ZoneId.systemDefault()).toLocalDate())) return false;

        long span = Math.max(0, ChronoUnit.DAYS.between(start, end));
        for (long offset = 0; offset <= span; offset++) {
            LocalDate candidateStart = date.minusDays(offset);
            if (isRepeatStart(item, start, candidateStart)) return true;
        }
        return false;
    }

    private boolean isRepeatStart(TodoItem item, LocalDate base, LocalDate candidate) {
        if (candidate.isBefore(base)) return false;
        int interval = Math.max(1, item.repeatInterval);
        return switch (item.repeatFrequency) {
            case "DAILY" -> ChronoUnit.DAYS.between(base, candidate) % interval == 0;
            case "WEEKLY" -> {
                LocalDate baseWeek = base.minusDays(base.getDayOfWeek().getValue() - 1L);
                LocalDate candidateWeek = candidate.minusDays(candidate.getDayOfWeek().getValue() - 1L);
                long weeks = ChronoUnit.WEEKS.between(baseWeek, candidateWeek);
                boolean selectedDay = item.repeatDays == null || item.repeatDays.isBlank()
                        ? candidate.getDayOfWeek() == base.getDayOfWeek()
                        : item.repeatDays.contains(candidate.getDayOfWeek().name());
                yield weeks >= 0 && weeks % interval == 0 && selectedDay;
            }
            case "MONTHLY" -> {
                long months = ChronoUnit.MONTHS.between(YearMonth.from(base), YearMonth.from(candidate));
                if (months < 0 || months % interval != 0) yield false;
                if (item.repeatMonthDays != null && !item.repeatMonthDays.isBlank())
                    yield containsCsvNumber(item.repeatMonthDays, candidate.getDayOfMonth());
                if (item.repeatSetPosition != 0 && item.repeatDays != null && !item.repeatDays.isBlank()) {
                    DayOfWeek day = candidate.getDayOfWeek();
                    if (!item.repeatDays.contains(day.name())) yield false;
                    LocalDate expected = item.repeatSetPosition > 0
                            ? candidate.withDayOfMonth(1).with(TemporalAdjusters.dayOfWeekInMonth(item.repeatSetPosition, day))
                            : candidate.with(TemporalAdjusters.lastInMonth(day));
                    yield candidate.equals(expected);
                }
                yield candidate.getDayOfMonth() == Math.min(base.getDayOfMonth(), YearMonth.from(candidate).lengthOfMonth());
            }
            case "YEARLY" -> candidate.getMonth() == base.getMonth() &&
                    candidate.getDayOfMonth() == base.getDayOfMonth() &&
                    (candidate.getYear() - base.getYear()) % interval == 0;
            default -> candidate.equals(base);
        };
    }

    private boolean containsCsvNumber(String csv, int number) {
        for (String value : csv.split(",")) {
            try {
                if (Integer.parseInt(value.trim()) == number) return true;
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    private void sortItems() {
        items.sort(Comparator.comparingLong(item -> item.scheduledAt));
    }

    private String formatTime(long timestamp) {
        return new SimpleDateFormat("a h:mm", Locale.KOREAN).format(new Date(timestamp));
    }

    private String formatSchedule(TodoItem item, boolean includeDate) {
        LocalDate start = dateOf(item);
        LocalDate end = endDateOf(item);
        StringBuilder value = new StringBuilder();
        if (includeDate) value.append(start.getMonthValue()).append("월 ").append(start.getDayOfMonth()).append("일");
        if (item.multiDay && !end.equals(start)) {
            if (!includeDate) value.append(start.getMonthValue()).append("월 ").append(start.getDayOfMonth()).append("일");
            value.append(" ~ ");
            value.append(end.getMonthValue()).append("월 ").append(end.getDayOfMonth()).append("일");
            if (!includeDate) value.append(" · ");
        }
        if (includeDate) value.append(" · ");
        if (item.allDay) value.append("하루 종일");
        else {
            value.append(formatTime(item.scheduledAt));
            if (item.endAt > item.scheduledAt) value.append(" ~ ").append(formatTime(item.endAt));
        }
        return value.toString();
    }

    private String repeatLabel(TodoItem item) {
        return switch (item.repeatFrequency) {
            case "DAILY" -> item.repeatInterval == 1 ? "매일" : item.repeatInterval + "일마다";
            case "WEEKLY" -> item.repeatInterval == 1 ? "매주" : item.repeatInterval + "주마다";
            case "MONTHLY" -> "매월";
            case "YEARLY" -> "매년";
            default -> item.repeatFrequency;
        };
    }

    private View legendItem(String label, int color) {
        LinearLayout item = horizontal();
        View line = new View(this);
        line.setBackground(rounded(color, 3, Color.TRANSPARENT));
        item.addView(line, new LinearLayout.LayoutParams(dp(28), dp(6)));
        TextView text = text(label, 12, MUTED, false);
        text.setPadding(dp(6), 0, 0, 0);
        item.addView(text);
        return item;
    }

    private View countBadge(int count, int color, int background, boolean important) {
        TextView badge = text(String.valueOf(count), 10, important ? Color.WHITE : color, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(important ? color : background, 9,
                important ? AMBER : Color.TRANSPARENT));
        badge.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(20)));
        return badge;
    }

    private GradientDrawable rangeDrawable(int color, boolean continuesBefore, boolean continuesAfter) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        float radius = dp(3);
        float left = continuesBefore ? 0 : radius;
        float right = continuesAfter ? 0 : radius;
        drawable.setCornerRadii(new float[]{left, left, right, right, right, right, left, left});
        return drawable;
    }

    private GridLayout.LayoutParams gridParams(int row, int column, int height) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(row), GridLayout.spec(column, 1f));
        params.width = 0;
        params.height = height;
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        return params;
    }

    private LinearLayout.LayoutParams detailCardParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(10));
        return params;
    }

    private LinearLayout.LayoutParams searchResultParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(5), 0, 0);
        return params;
    }

    private Button compactButton(String label) {
        Button button = button(label, Color.rgb(247, 247, 251), INK);
        button.setTextSize(24);
        button.setPadding(0, 0, 0, dp(3));
        return button;
    }

    private Button button(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(foreground);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(rounded(background, 14, Color.TRANSPARENT));
        return button;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != Color.TRANSPARENT) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int categoryColor(String category) {
        return switch (category) {
            case "업무" -> Color.rgb(70, 88, 210);
            case "약속" -> Color.rgb(190, 87, 52);
            case "건강" -> Color.rgb(39, 139, 95);
            case "쇼핑" -> Color.rgb(156, 87, 178);
            default -> PURPLE;
        };
    }

    private int categoryBackground(String category) {
        return switch (category) {
            case "업무" -> Color.rgb(235, 238, 255);
            case "약속" -> Color.rgb(255, 239, 231);
            case "건강" -> Color.rgb(229, 249, 239);
            case "쇼핑" -> Color.rgb(249, 235, 252);
            default -> PURPLE_SOFT;
        };
    }

    private final class SwipeCalendarGrid extends GridLayout {
        private final int swipeDistance = dp(42);
        private float downX;
        private float downY;
        private boolean swiping;

        SwipeCalendarGrid() {
            super(MainActivity.this);
            setClickable(true);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
                swiping = false;
                return false;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (Math.abs(dx) >= swipeDistance && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    swiping = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - downX;
                if (swiping && Math.abs(dx) >= swipeDistance) {
                    int direction = dx < 0 ? 1 : -1;
                    post(() -> moveVisibleMonth(direction));
                }
                swiping = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                swiping = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
            return swiping || super.onTouchEvent(event);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
