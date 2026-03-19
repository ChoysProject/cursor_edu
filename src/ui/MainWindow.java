package ui;

import util.CsvReader;
import validator.InterfaceValidator;
import validator.ValidationResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 인터페이스 검증 시스템 메인 윈도우.
 */
public class MainWindow extends JFrame {

    // ------------------------------------------------------------------
    //  색상 상수
    // ------------------------------------------------------------------
    private static final Color COL_TOOLBAR  = new Color(232, 237, 245);
    private static final Color COL_SUMMARY  = new Color(240, 244, 251);
    private static final Color COL_ERROR    = new Color(255, 224, 224);
    private static final Color COL_WARNING  = new Color(255, 243, 205);
    private static final Color COL_OK       = new Color(216, 240, 220);
    private static final Color COL_BTN_OPEN = new Color(70,  130, 180);
    private static final Color COL_BTN_RUN  = new Color(40,  167,  69);
    private static final Color COL_BTN_EXP  = new Color(108, 117, 125);
    private static final Color COL_BTN_RST  = new Color(220,  53,  69);

    // ------------------------------------------------------------------
    //  색상 — 그룹 헤더
    // ------------------------------------------------------------------
    private static final Color COL_GROUP_HEADER = new Color(208, 222, 248);
    private static final Color COL_ROW_EVEN     = Color.WHITE;
    private static final Color COL_ROW_ODD      = new Color(248, 249, 253);

    // ------------------------------------------------------------------
    //  상태
    // ------------------------------------------------------------------
    private final InterfaceValidator        validator      = new InterfaceValidator();
    private       List<Map<String, String>> csvData        = new ArrayList<>();
    private       List<String>              headers        = new ArrayList<>();
    private       List<ValidationResult>    resultData     = new ArrayList<>();

    /**
     * 프리뷰 테이블의 각 행이 그룹 헤더인지 데이터 행인지를 추적합니다.
     * "HEADER:<인터페이스ID>"  → 그룹 헤더 행
     * "DATA"                   → 데이터(필드) 행
     */
    private final List<String> groupRowTypes   = new ArrayList<>();
    /** 현재 접혀 있는 인터페이스ID 집합 */
    private final Set<String>  collapsedGroups = new HashSet<>();

    // ------------------------------------------------------------------
    //  UI 컴포넌트
    // ------------------------------------------------------------------
    private JLabel           lblFile;
    private JTabbedPane      tabs;
    private DefaultTableModel previewModel;
    private JTable            previewTable;
    private DefaultTableModel resultModel;
    private JTable            resultTable;
    private JLabel            lblTotal, lblError, lblWarning, lblOk;
    private JLabel            statusBar;

    // ==================================================================
    //  생성자
    // ==================================================================
    public MainWindow() {
        super("인터페이스 검증 시스템  v0.1");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(960, 640));
        setLocationRelativeTo(null);
        buildUI();
    }

    // ==================================================================
    //  UI 구성
    // ==================================================================
    private void buildUI() {
        setLayout(new BorderLayout());
        add(buildToolbar(),   BorderLayout.NORTH);
        add(buildTabs(),      BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    // ── 상단 툴바 ──────────────────────────────────────────────────────
    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 10));
        bar.setBackground(COL_TOOLBAR);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        bar.add(makeBtn("📂  CSV 열기",     COL_BTN_OPEN, e -> openFile()));
        bar.add(makeBtn("✅  검증 실행",     COL_BTN_RUN,  e -> runValidation()));
        bar.add(makeBtn("💾  결과 내보내기", COL_BTN_EXP,  e -> exportResults()));
        bar.add(makeBtn("🔄  초기화",        COL_BTN_RST,  e -> reset()));

        lblFile = new JLabel("파일을 선택하세요");
        lblFile.setForeground(Color.GRAY);
        lblFile.setFont(font(11, false));
        lblFile.setBorder(new EmptyBorder(0, 16, 0, 0));
        bar.add(lblFile);
        return bar;
    }

    private JButton makeBtn(String text, Color bg, ActionListener al) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setFont(font(12, false));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(al);
        return b;
    }

    // ── 탭 뷰 ─────────────────────────────────────────────────────────
    private JTabbedPane buildTabs() {
        tabs = new JTabbedPane();
        tabs.setFont(font(12, false));
        tabs.addTab("📋  CSV 미리보기", buildPreviewTab());
        tabs.addTab("⚠️  검증 결과",    buildResultTab());
        tabs.addTab("⚙️  설정",          buildSettingsTab());
        return tabs;
    }

    // ── [탭1] CSV 미리보기 ────────────────────────────────────────────
    private JPanel buildPreviewTab() {
        JPanel panel = new JPanel(new BorderLayout());

        // ── 상단 컨트롤 바 ──────────────────────────────────────────
        JPanel ctrlBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        ctrlBar.setBackground(new Color(245, 247, 252));
        ctrlBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        JButton btnCollapseAll = new JButton("▶  모두 접기");
        btnCollapseAll.setFont(font(11, false));
        btnCollapseAll.addActionListener(e -> {
            for (Map<String, String> row : csvData) {
                String id = row.getOrDefault("인터페이스ID", "").trim();
                collapsedGroups.add(id.isEmpty() ? "(ID없음)" : id);
            }
            refreshPreview();
        });

        JButton btnExpandAll = new JButton("▼  모두 펼치기");
        btnExpandAll.setFont(font(11, false));
        btnExpandAll.addActionListener(e -> { collapsedGroups.clear(); refreshPreview(); });

        JLabel hint = new JLabel("  ※ 첫 번째 열 클릭으로 개별 접기/펼치기");
        hint.setFont(font(10, false));
        hint.setForeground(Color.GRAY);

        ctrlBar.add(new JLabel("그룹:"));
        ctrlBar.add(btnCollapseAll);
        ctrlBar.add(btnExpandAll);
        ctrlBar.add(hint);

        // ── 테이블 ──────────────────────────────────────────────────
        previewModel = new DefaultTableModel() {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        previewTable = buildTable(previewModel);

        // 그룹 헤더/데이터 행 색상 구분 렌더러
        previewTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, col);
                if (!isSelected && row < groupRowTypes.size()) {
                    if (groupRowTypes.get(row).startsWith("HEADER:")) {
                        c.setBackground(COL_GROUP_HEADER);
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    } else {
                        c.setBackground(row % 2 == 0 ? COL_ROW_EVEN : COL_ROW_ODD);
                        c.setFont(c.getFont().deriveFont(Font.PLAIN));
                    }
                }
                return c;
            }
        });

        // 첫 번째 열(토글 열) 클릭 → 접기/펼치기
        previewTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = previewTable.rowAtPoint(e.getPoint());
                int col = previewTable.columnAtPoint(e.getPoint());
                if (col == 0 && row >= 0 && row < groupRowTypes.size()) {
                    String type = groupRowTypes.get(row);
                    if (type.startsWith("HEADER:")) {
                        String id = type.substring(7);
                        if (collapsedGroups.contains(id)) collapsedGroups.remove(id);
                        else                              collapsedGroups.add(id);
                        refreshPreview();
                    }
                }
            }
        });

        panel.add(ctrlBar,                          BorderLayout.NORTH);
        panel.add(new JScrollPane(previewTable),    BorderLayout.CENTER);
        return panel;
    }

    // ── [탭2] 검증 결과 ───────────────────────────────────────────────
    private JPanel buildResultTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));

        // 요약 카드
        JPanel summary = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 10));
        summary.setBackground(COL_SUMMARY);
        summary.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 210, 230)));

        lblTotal   = summaryLabel("전체: 0",   Color.DARK_GRAY);
        lblError   = summaryLabel("오류: 0",   new Color(200, 40, 40));
        lblWarning = summaryLabel("경고: 0",   new Color(200, 100,  0));
        lblOk      = summaryLabel("정상: 0",   new Color( 30, 140, 50));
        for (JLabel l : new JLabel[]{lblTotal, lblError, lblWarning, lblOk}) {
            summary.add(l);
        }

        // 필터 버튼
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        filterBar.setBackground(Color.WHITE);
        filterBar.setBorder(new EmptyBorder(2, 4, 2, 4));
        filterBar.add(new JLabel("필터:"));

        for (String[] f : new String[][]{
                {"전체", "ALL"}, {"오류만", "ERROR"},
                {"경고만", "WARNING"}, {"정상만", "OK"}}) {
            JButton btn = new JButton(f[0]);
            btn.setFont(font(11, false));
            String key = f[1];
            btn.addActionListener(e -> filterResults(key));
            filterBar.add(btn);
        }

        // 결과 테이블
        String[] cols = {"행번호", "인터페이스ID", "심각도", "검증항목", "내용"};
        resultModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        resultTable = buildTable(resultModel);

        int[] widths = {55, 110, 70, 150, 600};
        for (int i = 0; i < widths.length; i++) {
            resultTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // 행 색상 렌더러 — 심각도 컬럼(인덱스 2) 값으로 색상 결정
        resultTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    Object sev = resultModel.getValueAt(row, 2);
                    if ("ERROR".equals(sev))        c.setBackground(COL_ERROR);
                    else if ("WARNING".equals(sev)) c.setBackground(COL_WARNING);
                    else if ("OK".equals(sev))      c.setBackground(COL_OK);
                    else                            c.setBackground(Color.WHITE);
                }
                return c;
            }
        });

        JPanel center = new JPanel(new BorderLayout());
        center.add(filterBar, BorderLayout.NORTH);
        center.add(new JScrollPane(resultTable), BorderLayout.CENTER);

        panel.add(summary, BorderLayout.NORTH);
        panel.add(center,  BorderLayout.CENTER);
        return panel;
    }

    private JLabel summaryLabel(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(13, true));
        l.setForeground(color);
        return l;
    }

    // ── [탭3] 설정 ────────────────────────────────────────────────────
    private JScrollPane buildSettingsTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));
        panel.setBackground(Color.WHITE);

        // 네이밍룰
        addSectionTitle(panel, "인터페이스 네이밍룰");
        addSectionHint(panel,
                "추후 내부 규칙 확정 후 패턴을 입력하세요. "
                + "InterfaceValidator.java 의 checkNamingRule() 에도 반영됩니다.");
        JPanel nameForm = sectionPanel();
        addFormRow(nameForm, "인터페이스ID 패턴 (정규식):", "예: ^IF_[A-Z]{2}\\d{4}$");
        addFormRow(nameForm, "인터페이스명 패턴 (정규식):", "예: ^[가-힣A-Za-z0-9_\\s\\-]+$");
        panel.add(nameForm);
        panel.add(Box.createVerticalStrut(16));

        // 허용 필드타입
        addSectionTitle(panel, "허용 필드타입 목록");
        addSectionHint(panel, "InterfaceValidator.java 의 VALID_FIELD_TYPES 상수를 수정하세요.");
        JPanel typePanel = sectionPanel();
        JLabel typeInfo = new JLabel(
                "현재 허용값:  VARCHAR, NUMBER, DATE, CHAR, TIMESTAMP, CLOB, BLOB");
        typeInfo.setFont(font(11, false));
        typeInfo.setBorder(new EmptyBorder(8, 10, 8, 10));
        typePanel.add(typeInfo);
        panel.add(typePanel);
        panel.add(Box.createVerticalStrut(16));

        // 검증 항목 ON/OFF
        addSectionTitle(panel, "검증 항목 활성화");
        addSectionHint(panel, "(추후 체크박스로 개별 항목 ON/OFF 연동 예정)");
        JPanel checkPanel = sectionPanel();
        JPanel checks = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        checks.setBackground(new Color(240, 244, 251));
        for (String item : new String[]{
                "필수값 검증", "인터페이스타입 검증", "인터페이스쿼리 검증",
                "선후처리 검증", "필드 검증", "매핑모델 검증", "네이밍룰 (비활성)"}) {
            JCheckBox cb = new JCheckBox(item, !item.contains("비활성"));
            cb.setFont(font(11, false));
            cb.setBackground(new Color(240, 244, 251));
            cb.setEnabled(false);  // TODO: 실제 연동 시 활성화
            checks.add(cb);
        }
        checkPanel.add(checks);
        panel.add(checkPanel);

        return new JScrollPane(panel);
    }

    private JPanel sectionPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(new Color(240, 244, 251));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 210, 230)),
                new EmptyBorder(4, 4, 4, 4)));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private void addSectionTitle(JPanel parent, String text) {
        JLabel l = new JLabel(text);
        l.setFont(font(13, true));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(l);
        parent.add(Box.createVerticalStrut(3));
    }

    private void addSectionHint(JPanel parent, String text) {
        JLabel l = new JLabel(text);
        l.setFont(font(11, false));
        l.setForeground(Color.GRAY);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(l);
        parent.add(Box.createVerticalStrut(6));
    }

    private void addFormRow(JPanel parent, String label, String placeholder) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setBackground(new Color(240, 244, 251));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = new JLabel(label);
        lbl.setFont(font(11, false));
        lbl.setPreferredSize(new Dimension(220, 24));

        JTextField field = new JTextField(30);
        field.setFont(font(11, false));
        field.setText(placeholder);
        field.setForeground(Color.GRAY);
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (field.getText().equals(placeholder)) {
                    field.setText(""); field.setForeground(Color.BLACK);
                }
            }
            @Override public void focusLost(FocusEvent e) {
                if (field.getText().isEmpty()) {
                    field.setText(placeholder); field.setForeground(Color.GRAY);
                }
            }
        });

        row.add(lbl);
        row.add(field);
        parent.add(row);
    }

    // ── 상태바 ────────────────────────────────────────────────────────
    private JLabel buildStatusBar() {
        statusBar = new JLabel("준비");
        statusBar.setFont(font(10, false));
        statusBar.setForeground(new Color(85, 85, 85));
        statusBar.setBorder(new EmptyBorder(2, 12, 4, 12));
        return statusBar;
    }

    // ==================================================================
    //  이벤트 핸들러
    // ==================================================================
    private void openFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("CSV 파일 선택");
        fc.setFileFilter(new FileNameExtensionFilter("CSV 파일 (*.csv)", "csv"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = fc.getSelectedFile();
        try {
            headers  = new ArrayList<>(Arrays.asList(CsvReader.getHeaders(file)));
            csvData  = CsvReader.read(file);
            lblFile.setText("📄  " + file.getName() + "  (" + csvData.size() + "행)");
            lblFile.setForeground(Color.DARK_GRAY);
            refreshPreview();
            setStatus("파일 로드 완료 — " + file.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "파일을 읽을 수 없습니다:\n" + ex.getMessage(),
                    "파일 오류", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void runValidation() {
        if (csvData.isEmpty()) {
            JOptionPane.showMessageDialog(this, "CSV 파일을 먼저 불러오세요.",
                    "경고", JOptionPane.WARNING_MESSAGE);
            return;
        }
        resultData = validator.validate(csvData);
        refreshResults(resultData);
        updateSummary(resultData);
        tabs.setSelectedIndex(1);

        long err  = count(resultData, ValidationResult.Severity.ERROR);
        long warn = count(resultData, ValidationResult.Severity.WARNING);
        long ok   = count(resultData, ValidationResult.Severity.OK);
        setStatus(String.format(
                "검증 완료 — 전체: %d  |  오류: %d  경고: %d  정상: %d",
                resultData.size(), err, warn, ok));
    }

    private void exportResults() {
        if (resultData.isEmpty()) {
            JOptionPane.showMessageDialog(this, "내보낼 검증 결과가 없습니다.",
                    "경고", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("결과 저장");
        fc.setFileFilter(new FileNameExtensionFilter("CSV 파일 (*.csv)", "csv"));
        fc.setSelectedFile(new File("검증결과_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv"));

        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv"))
            file = new File(file.getAbsolutePath() + ".csv");

        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            pw.write('\uFEFF');  // BOM
            pw.println("행번호,인터페이스ID,심각도,검증항목,내용");
            for (ValidationResult r : resultData) {
                pw.printf("%d,%s,%s,%s,\"%s\"%n",
                        r.getRowNum(), r.getInterfaceId(),
                        r.getSeverity().getLabel(), r.getCheck(),
                        r.getMessage().replace("\"", "\"\""));
            }
            JOptionPane.showMessageDialog(this,
                    "저장 완료:\n" + file.getAbsolutePath(),
                    "완료", JOptionPane.INFORMATION_MESSAGE);
            setStatus("결과 내보내기 완료 — " + file.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "파일 저장 실패:\n" + ex.getMessage(),
                    "저장 오류", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void reset() {
        csvData.clear();
        headers.clear();
        resultData.clear();
        collapsedGroups.clear();
        groupRowTypes.clear();
        lblFile.setText("파일을 선택하세요");
        lblFile.setForeground(Color.GRAY);
        previewModel.setColumnCount(0);
        previewModel.setRowCount(0);
        resultModel.setRowCount(0);
        updateSummary(Collections.emptyList());
        setStatus("초기화 완료");
    }

    private void filterResults(String severity) {
        List<ValidationResult> filtered = "ALL".equals(severity)
                ? resultData
                : resultData.stream()
                        .filter(r -> r.getSeverity().name().equals(severity))
                        .collect(Collectors.toList());
        refreshResults(filtered);
    }

    // ==================================================================
    //  내부 갱신
    // ==================================================================
    private void refreshPreview() {
        previewModel.setColumnCount(0);
        previewModel.setRowCount(0);
        groupRowTypes.clear();

        if (headers.isEmpty()) return;

        // 첫 번째 열 = 토글 열, 이후 = 원본 컬럼
        previewModel.addColumn("펼치기");
        for (String h : headers) previewModel.addColumn(h);

        // 인터페이스ID 기준으로 행을 그룹화 (삽입 순서 유지)
        Map<String, List<Map<String, String>>> groups = new LinkedHashMap<>();
        for (Map<String, String> row : csvData) {
            String id = row.getOrDefault("인터페이스ID", "").trim();
            if (id.isEmpty()) id = "(ID없음)";
            groups.computeIfAbsent(id, k -> new ArrayList<>()).add(row);
        }

        int totalInterfaces = groups.size();
        int totalFields     = csvData.size();

        // 그룹별로 헤더 행 + 데이터 행 추가
        for (Map.Entry<String, List<Map<String, String>>> entry : groups.entrySet()) {
            String id   = entry.getKey();
            List<Map<String, String>> rows = entry.getValue();
            boolean collapsed = collapsedGroups.contains(id);

            // ── 그룹 헤더 행 ────────────────────────────────────────
            Object[] headerRow = new Object[headers.size() + 1];
            headerRow[0] = (collapsed ? "▶  " : "▼  ")
                         + id + "   (" + rows.size() + "개 필드)";
            // 첫 번째 데이터 행의 공통 정보(인터페이스명, 타입 등) 표시
            Map<String, String> first = rows.get(0);
            for (int i = 0; i < headers.size(); i++) {
                String h = headers.get(i);
                // 필드 단위 컬럼은 헤더에서 생략
                boolean isFieldCol = h.equals("필드명") || h.equals("필드타입")
                        || h.equals("필드길이") || h.equals("키타입");
                headerRow[i + 1] = isFieldCol ? "" : first.getOrDefault(h, "");
            }
            previewModel.addRow(headerRow);
            groupRowTypes.add("HEADER:" + id);

            // ── 데이터(필드) 행 — 접힌 경우 생략 ───────────────────
            if (!collapsed) {
                for (Map<String, String> row : rows) {
                    Object[] dataRow = new Object[headers.size() + 1];
                    dataRow[0] = "    ┗";   // 들여쓰기 시각 표시
                    for (int i = 0; i < headers.size(); i++) {
                        dataRow[i + 1] = row.getOrDefault(headers.get(i), "");
                    }
                    previewModel.addRow(dataRow);
                    groupRowTypes.add("DATA");
                }
            }
        }

        // 열 너비 조정
        previewTable.getColumnModel().getColumn(0).setPreferredWidth(230);
        for (int i = 1; i < previewTable.getColumnCount(); i++) {
            previewTable.getColumnModel().getColumn(i).setPreferredWidth(130);
        }

        setStatus(String.format("파일 로드 완료 — 인터페이스 %d개 / 전체 %d행",
                totalInterfaces, totalFields));
    }

    private void refreshResults(List<ValidationResult> list) {
        resultModel.setRowCount(0);
        for (ValidationResult r : list) resultModel.addRow(r.toTableRow());
    }

    private void updateSummary(List<ValidationResult> list) {
        lblTotal.setText("전체: "   + list.size());
        lblError.setText("오류: "   + count(list, ValidationResult.Severity.ERROR));
        lblWarning.setText("경고: " + count(list, ValidationResult.Severity.WARNING));
        lblOk.setText("정상: "      + count(list, ValidationResult.Severity.OK));
    }

    private void setStatus(String text) { statusBar.setText(text); }

    // ==================================================================
    //  헬퍼
    // ==================================================================
    private JTable buildTable(DefaultTableModel model) {
        JTable t = new JTable(model);
        t.setFont(font(11, false));
        t.getTableHeader().setFont(font(11, true));
        t.getTableHeader().setBackground(new Color(208, 216, 232));
        t.setRowHeight(26);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        t.setGridColor(new Color(220, 220, 220));
        t.setSelectionBackground(new Color(74, 144, 217));
        t.setSelectionForeground(Color.WHITE);
        return t;
    }

    private long count(List<ValidationResult> list, ValidationResult.Severity sev) {
        return list.stream().filter(r -> r.getSeverity() == sev).count();
    }

    private Font font(int size, boolean bold) {
        return new Font("맑은 고딕", bold ? Font.BOLD : Font.PLAIN, size);
    }
}
