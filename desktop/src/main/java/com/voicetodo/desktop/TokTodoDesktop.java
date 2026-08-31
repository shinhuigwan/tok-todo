package com.voicetodo.desktop;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class TokTodoDesktop {
    private static final Color PURPLE = new Color(103, 87, 255);
    private static final DateTimeFormatter DATE_TEXT = DateTimeFormatter.ofPattern("yyyy년 M월 d일");
    private final LocalStore store = new LocalStore();
    private final GoogleCalendarSync calendarSync = new GoogleCalendarSync();
    private AppState state;
    private LocalDate selectedDate = LocalDate.now();
    private JFrame frame;
    private JLabel dateLabel;
    private JLabel summaryLabel;
    private JLabel syncLabel;
    private JTextField titleInput;
    private JSpinner timeInput;
    private JComboBox<String> categoryInput;
    private JTextArea journalInput;
    private JButton syncButton;
    private final TodoTableModel tableModel = new TodoTableModel();

    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                new TokTodoDesktop().start();
            } catch (Exception error) {
                JOptionPane.showMessageDialog(null, error.getMessage(), "톡todo 시작 실패", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void start() throws Exception {
        state = store.load();
        frame = new JFrame("톡todo · 일정과 일일업무일지");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setMinimumSize(new Dimension(920, 650));
        frame.setSize(1120, 760);
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                saveJournal(false);
                saveState();
                frame.dispose();
            }
        });

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildContent(), BorderLayout.CENTER);
        root.add(buildStatus(), BorderLayout.SOUTH);
        frame.setContentPane(root);
        refreshAll();
        frame.setVisible(true);

        if (Files.exists(AppPaths.credentials()) && Files.exists(AppPaths.token())) sync(false);
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout(12, 8));
        JPanel brand = new JPanel(new GridLayout(2, 1));
        JLabel title = new JLabel("톡todo");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
        title.setForeground(PURPLE);
        summaryLabel = new JLabel();
        brand.add(title);
        brand.add(summaryLabel);
        panel.add(brand, BorderLayout.WEST);

        JPanel date = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 2));
        JButton previous = new JButton("‹");
        JButton today = new JButton("오늘");
        JButton next = new JButton("›");
        dateLabel = new JLabel();
        dateLabel.setFont(dateLabel.getFont().deriveFont(Font.BOLD, 17f));
        previous.addActionListener(event -> changeDate(selectedDate.minusDays(1)));
        today.addActionListener(event -> changeDate(LocalDate.now()));
        next.addActionListener(event -> changeDate(selectedDate.plusDays(1)));
        date.add(previous);
        date.add(dateLabel);
        date.add(today);
        date.add(next);
        panel.add(date, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 2));
        JButton connect = new JButton("Google 연결");
        connect.addActionListener(event -> importCredentials());
        syncButton = new JButton("지금 동기화");
        syncButton.setForeground(PURPLE);
        syncButton.addActionListener(event -> sync(true));
        actions.add(connect);
        actions.add(syncButton);
        panel.add(actions, BorderLayout.EAST);
        return panel;
    }

    private JSplitPane buildContent() {
        JPanel todoPanel = new JPanel(new BorderLayout(8, 8));
        todoPanel.setBorder(BorderFactory.createTitledBorder("오늘의 업무"));
        todoPanel.add(buildAddRow(), BorderLayout.NORTH);
        JTable table = new JTable(tableModel);
        table.setRowHeight(28);
        table.getColumnModel().getColumn(0).setPreferredWidth(55);
        table.getColumnModel().getColumn(0).setMaxWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setMaxWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setMaxWidth(110);
        todoPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        JButton delete = new JButton("선택 업무 삭제");
        delete.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            Todo todo = tableModel.rows.get(row);
            if (JOptionPane.showConfirmDialog(frame, "‘" + todo.title + "’을 삭제할까요?", "업무 삭제",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                todo.deleted = true;
                todo.updatedAt = System.currentTimeMillis();
                saveState();
                refreshAll();
            }
        });
        JPanel deleteRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        deleteRow.add(delete);
        todoPanel.add(deleteRow, BorderLayout.SOUTH);

        JPanel journalPanel = new JPanel(new BorderLayout(8, 8));
        journalPanel.setBorder(BorderFactory.createTitledBorder("일일업무일지"));
        journalInput = new JTextArea();
        journalInput.setLineWrap(true);
        journalInput.setWrapStyleWord(true);
        journalInput.setFont(journalInput.getFont().deriveFont(15f));
        journalInput.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        journalPanel.add(new JScrollPane(journalInput), BorderLayout.CENTER);
        JPanel journalActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton fillCompleted = new JButton("완료 업무 추가");
        fillCompleted.addActionListener(event -> appendCompletedTodos());
        JButton copy = new JButton("업무일지 복사");
        copy.addActionListener(event -> copyJournal());
        JButton save = new JButton("업무일지 저장");
        save.setForeground(PURPLE);
        save.addActionListener(event -> saveJournal(true));
        journalActions.add(fillCompleted);
        journalActions.add(copy);
        journalActions.add(save);
        journalPanel.add(journalActions, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, todoPanel, journalPanel);
        split.setResizeWeight(0.52);
        split.setDividerLocation(540);
        return split;
    }

    private JPanel buildAddRow() {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        titleInput = new JTextField();
        titleInput.setToolTipText("업무 내용을 입력하세요");
        row.add(titleInput, BorderLayout.CENTER);
        JPanel options = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        timeInput = new JSpinner(new SpinnerDateModel());
        timeInput.setEditor(new JSpinner.DateEditor(timeInput, "HH:mm"));
        categoryInput = new JComboBox<>(new String[]{"업무", "약속", "개인", "건강", "쇼핑"});
        JButton add = new JButton("추가");
        add.setForeground(PURPLE);
        add.addActionListener(event -> addTodo());
        titleInput.addActionListener(event -> addTodo());
        options.add(new JLabel("시간"));
        options.add(timeInput);
        options.add(categoryInput);
        options.add(add);
        row.add(options, BorderLayout.EAST);
        return row;
    }

    private JPanel buildStatus() {
        JPanel panel = new JPanel(new BorderLayout());
        syncLabel = new JLabel("로컬에 저장됨");
        syncLabel.setForeground(Color.DARK_GRAY);
        panel.add(syncLabel, BorderLayout.WEST);
        JLabel path = new JLabel("데이터: " + AppPaths.directory());
        path.setForeground(Color.GRAY);
        panel.add(path, BorderLayout.EAST);
        return panel;
    }

    private void addTodo() {
        String title = titleInput.getText().trim();
        if (title.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            titleInput.requestFocusInWindow();
            return;
        }
        LocalTime time = Instant.ofEpochMilli(((Date) timeInput.getValue()).getTime())
                .atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0);
        Todo todo = new Todo();
        todo.title = title;
        todo.category = String.valueOf(categoryInput.getSelectedItem());
        todo.scheduledAt = LocalDateTime.of(selectedDate, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        state.todos.add(todo);
        titleInput.setText("");
        saveState();
        refreshAll();
        titleInput.requestFocusInWindow();
    }

    private void changeDate(LocalDate date) {
        saveJournal(false);
        selectedDate = date;
        refreshAll();
    }

    private void refreshAll() {
        String weekday = selectedDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN);
        dateLabel.setText("  " + selectedDate.format(DATE_TEXT) + " " + weekday + "  ");
        long open = todosForDate().stream().filter(todo -> !todo.completed).count();
        long done = todosForDate().stream().filter(todo -> todo.completed).count();
        summaryLabel.setText("미완료 " + open + "개 · 완료 " + done + "개");
        tableModel.reload();
        Journal journal = journalForDate(false);
        journalInput.setText(journal == null ? "" : journal.content);
        journalInput.setCaretPosition(0);
    }

    private List<Todo> todosForDate() {
        List<Todo> result = new ArrayList<>();
        for (Todo todo : state.todos) {
            LocalDate date = Instant.ofEpochMilli(todo.scheduledAt).atZone(ZoneId.systemDefault()).toLocalDate();
            if (!todo.deleted && date.equals(selectedDate)) result.add(todo);
        }
        result.sort(java.util.Comparator.comparingLong(todo -> todo.scheduledAt));
        return result;
    }

    private Journal journalForDate(boolean create) {
        for (Journal journal : state.journals) {
            if (!journal.deleted && selectedDate.toString().equals(journal.date)) return journal;
        }
        if (!create) return null;
        Journal journal = new Journal();
        journal.date = selectedDate.toString();
        journal.id = "journal-" + journal.date;
        state.journals.add(journal);
        return journal;
    }

    private void saveJournal(boolean showMessage) {
        if (journalInput == null) return;
        String content = journalInput.getText().stripTrailing();
        Journal existing = journalForDate(false);
        if (content.isBlank() && existing == null) return;
        Journal journal = existing == null ? journalForDate(true) : existing;
        if (journal.content.equals(content)) return;
        journal.content = content;
        journal.updatedAt = System.currentTimeMillis();
        if (content.isBlank()) journal.deleted = true;
        saveState();
        if (showMessage) syncLabel.setText("업무일지를 저장했습니다.");
    }

    private void appendCompletedTodos() {
        StringBuilder text = new StringBuilder(journalInput.getText().stripTrailing());
        if (!text.isEmpty()) text.append("\n\n");
        text.append("[완료 업무]\n");
        int count = 0;
        for (Todo todo : todosForDate()) {
            if (!todo.completed) continue;
            text.append("- ").append(todo.title).append('\n');
            count++;
        }
        if (count == 0) text.append("- 완료된 업무 없음\n");
        journalInput.setText(text.toString());
    }

    private void copyJournal() {
        saveJournal(false);
        String text = selectedDate.format(DATE_TEXT) + " 업무일지\n\n" + journalInput.getText().strip();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        syncLabel.setText("업무일지를 클립보드에 복사했습니다.");
    }

    private void importCredentials() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Google Cloud에서 받은 credentials.json 선택");
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        File selected = chooser.getSelectedFile();
        try {
            Files.createDirectories(AppPaths.directory());
            Files.copy(selected.toPath(), AppPaths.credentials(), StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(AppPaths.token());
            sync(true);
        } catch (Exception error) {
            showError("Google 설정 파일을 저장하지 못했습니다.", error);
        }
    }

    private void sync(boolean interactive) {
        saveJournal(false);
        saveState();
        syncButton.setEnabled(false);
        syncLabel.setText(interactive ? "Google 계정 연결 및 동기화 중…" : "자동 동기화 중…");
        new SwingWorker<GoogleCalendarSync.SyncResult, Void>() {
            @Override protected GoogleCalendarSync.SyncResult doInBackground() throws Exception {
                return calendarSync.sync(state, interactive);
            }
            @Override protected void done() {
                syncButton.setEnabled(true);
                try {
                    GoogleCalendarSync.SyncResult result = get();
                    store.save(state);
                    refreshAll();
                    syncLabel.setText("동기화 완료 · 가져옴 " + result.pulled() + " · 보냄 " + result.pushed());
                } catch (Exception error) {
                    syncLabel.setText("동기화하지 못했습니다.");
                    if (interactive) showError("Google Calendar 동기화에 실패했습니다.", rootCause(error));
                }
            }
        }.execute();
    }

    private void saveState() {
        try { store.save(state); }
        catch (Exception error) { showError("로컬 데이터를 저장하지 못했습니다.", error); }
    }

    private void showError(String title, Throwable error) {
        JOptionPane.showMessageDialog(frame, title + "\n\n" + error.getMessage(), "톡todo", JOptionPane.ERROR_MESSAGE);
    }

    private static Throwable rootCause(Throwable error) {
        Throwable value = error;
        while (value.getCause() != null) value = value.getCause();
        return value;
    }

    private final class TodoTableModel extends AbstractTableModel {
        private final String[] columns = {"완료", "업무", "시간", "분류"};
        private List<Todo> rows = new ArrayList<>();

        void reload() {
            rows = todosForDate();
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Class<?> getColumnClass(int column) { return column == 0 ? Boolean.class : String.class; }
        @Override public boolean isCellEditable(int row, int column) { return column == 0 || column == 1 || column == 3; }

        @Override public Object getValueAt(int row, int column) {
            Todo todo = rows.get(row);
            return switch (column) {
                case 0 -> todo.completed;
                case 1 -> todo.title;
                case 2 -> Instant.ofEpochMilli(todo.scheduledAt).atZone(ZoneId.systemDefault()).toLocalTime()
                        .format(DateTimeFormatter.ofPattern("HH:mm"));
                default -> todo.category;
            };
        }

        @Override public void setValueAt(Object value, int row, int column) {
            Todo todo = rows.get(row);
            if (column == 0) todo.completed = Boolean.TRUE.equals(value);
            else if (column == 1 && value != null && !value.toString().isBlank()) todo.title = value.toString().trim();
            else if (column == 3 && value != null) todo.category = value.toString().trim();
            todo.updatedAt = System.currentTimeMillis();
            saveState();
            refreshAll();
        }
    }
}
