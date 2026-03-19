package validator;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * EAI 인터페이스 CSV 검증 엔진.
 *
 * ┌─────────────────────────────────────────────┐
 * │  인터페이스(SOURCE)  ──매핑모델──  서비스(TARGET) │
 * │  인터페이스모델 필드              서비스모델 필드   │
 * └─────────────────────────────────────────────┘
 *
 * validate(rows) 호출 시 두 단계로 검증합니다.
 *  Phase 1 : 행 단위 검증 (필드값 형식, 타입 등)
 *  Phase 2 : 인터페이스 단위 검증 (서비스 존재, GROUP 타입 등)
 */
public class InterfaceValidator {

    // ==================================================================
    //  허용 값 상수
    // ==================================================================
    private static final Set<String> VALID_INTERFACE_TYPES = new HashSet<>(
            Arrays.asList("DB-DB", "DB-FILE", "FILE-FILE", "FILE-DB"));

    /** GROUP 은 FILE 측 모델에 반드시 1개 필요한 특수 타입 */
    private static final Set<String> VALID_FIELD_TYPES = new HashSet<>(
            Arrays.asList("VARCHAR", "NUMBER", "DATE", "CHAR",
                          "TIMESTAMP", "CLOB", "BLOB", "GROUP"));

    private static final Set<String> VALID_KEY_TYPES = new HashSet<>(
            Arrays.asList("PK", "FK", "UK", ""));

    private static final Set<String> VALID_MAPPING_VALUES = new HashSet<>(
            Arrays.asList("Y", "N", ""));

    /** 이 타입은 소스가 DB → 인터페이스쿼리 필수 */
    private static final Set<String> DB_SOURCE_TYPES = new HashSet<>(
            Arrays.asList("DB-DB", "DB-FILE"));

    /** 이 타입은 소스가 FILE → 인터페이스쿼리 불필요 */
    private static final Set<String> FILE_SOURCE_TYPES = new HashSet<>(
            Arrays.asList("FILE-FILE", "FILE-DB"));

    /** 매핑모델(Y)이 반드시 있어야 하는 타입 */
    private static final Set<String> MAPPING_REQUIRED_TYPES = new HashSet<>(
            Arrays.asList("DB-DB", "FILE-DB", "DB-FILE"));

    /** 모델구분 허용값 */
    private static final Set<String> VALID_MODEL_TYPES = new HashSet<>(
            Arrays.asList("인터페이스모델", "서비스모델"));

    private static final Pattern ALLOWED_PROCESS_CHARS =
            Pattern.compile("^[a-zA-Z0-9가-힣_,\\s\\[\\]\\.]+$");

    // ==================================================================
    //  네이밍룰 상수
    // ==================================================================
    private static final List<String> VALID_TAGS =
            Arrays.asList("배치", "디퍼드", "온라인");

    /**
     * 인터페이스명 전체 패턴.
     * [태그]소스시스템명(코드)->타겟시스템명(코드) 설명 전송 (타입)
     * - 시스템명(코드) 은 형식만 체크하며, 값 자체는 검증하지 않음
     */
    private static final Pattern FULL_NAME_PATTERN = buildNamePattern();

    private static Pattern buildNamePattern() {
        String tags  = String.join("|", VALID_TAGS);
        String types = "DB-DB|DB-FILE|FILE-FILE|FILE-DB";
        return Pattern.compile(
                "^\\[(" + tags + ")\\]"                  +  // group(1) [태그]
                "([가-힣A-Za-z0-9]+)\\(([A-Z0-9]+)\\)"  +  // group(2) 소스시스템명  group(3) 코드
                "->"                                      +  // 화살표
                "([가-힣A-Za-z0-9]+)\\(([A-Z0-9]+)\\)"  +  // group(4) 타겟시스템명  group(5) 코드
                " (.+) "                                  +  // group(6) 설명
                "\\((" + types + ")\\)$"                     // group(7) 타입
        );
    }

    // ==================================================================
    //  내부 헬퍼 클래스
    // ==================================================================
    /** 행 번호와 데이터를 함께 보관 (Phase 2 그룹 검증용) */
    private static class IndexedRow {
        final int rowNum;
        final Map<String, String> data;
        IndexedRow(int rowNum, Map<String, String> data) {
            this.rowNum = rowNum;
            this.data   = data;
        }
    }

    // ==================================================================
    //  진입점
    // ==================================================================
    public List<ValidationResult> validate(List<Map<String, String>> rows) {
        List<ValidationResult> all = new ArrayList<>();

        // ── Phase 1: 행 단위 검증 ─────────────────────────────────
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int rowNum = i + 2;

            List<ValidationResult> rowResults = new ArrayList<>();
            rowResults.addAll(checkRequiredFields(rowNum, row));
            rowResults.addAll(checkInterfaceType(rowNum, row));
            rowResults.addAll(checkQuery(rowNum, row));
            rowResults.addAll(checkPrePostProcess(rowNum, row));
            rowResults.addAll(checkFieldInfo(rowNum, row));
            rowResults.addAll(checkMappingModel(rowNum, row));
            rowResults.addAll(checkFileFileColumns(rowNum, row));
            rowResults.addAll(checkNamingRule(rowNum, row));

            if (rowResults.isEmpty()) {
                rowResults.add(ok(rowNum, row, "행 검증", "모든 항목 정상"));
            }
            all.addAll(rowResults);
        }

        // ── Phase 2: 인터페이스 단위 검증 ────────────────────────
        Map<String, List<IndexedRow>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            String id = rows.get(i).getOrDefault("인터페이스ID", "").trim();
            if (id.isEmpty()) continue;
            grouped.computeIfAbsent(id, k -> new ArrayList<>())
                   .add(new IndexedRow(i + 2, rows.get(i)));
        }
        for (Map.Entry<String, List<IndexedRow>> entry : grouped.entrySet()) {
            all.addAll(checkInterfaceGroup(entry.getKey(), entry.getValue()));
        }

        return all;
    }

    // ==================================================================
    //  Phase 1 검증 메서드
    // ==================================================================

    // ── 1. 필수값 검증 ──────────────────────────────────────────────
    private List<ValidationResult> checkRequiredFields(int rowNum, Map<String, String> row) {
        List<ValidationResult> results = new ArrayList<>();
        String itype = get(row, "인터페이스타입").toUpperCase();

        // 모든 타입 공통 필수값
        String[][] common = {
            {"인터페이스ID",  "인터페이스ID"},
            {"인터페이스명",  "인터페이스명"},
            {"인터페이스타입","인터페이스타입"},
            {"서비스ID",      "서비스ID"},
            {"서비스명",      "서비스명"},
        };
        for (String[] req : common) {
            if (get(row, req[0]).isEmpty()) {
                results.add(error(rowNum, row, "필수값 검증",
                        "[" + req[1] + "] 값이 비어있습니다."));
            }
        }

        if ("FILE-FILE".equals(itype)) {
            // FILE-FILE : 소스/타겟 파일 정보만 필수 (필드 없음)
            String[][] fileRequired = {
                {"소스파일명", "소스파일명"},
                {"소스경로",   "소스경로"},
                {"타겟파일명", "타겟파일명"},
                {"타겟경로",   "타겟경로"},
            };
            for (String[] req : fileRequired) {
                if (get(row, req[0]).isEmpty()) {
                    results.add(error(rowNum, row, "FILE-FILE 필수값",
                            "[" + req[1] + "] 값이 비어있습니다."));
                }
            }
        } else {
            // 그 외 타입 : 모델구분, 필드명 필수
            if (get(row, "모델구분").isEmpty()) {
                results.add(error(rowNum, row, "필수값 검증", "[모델구분] 값이 비어있습니다."));
            }
            if (get(row, "필드명").isEmpty()) {
                results.add(error(rowNum, row, "필수값 검증", "[필드명] 값이 비어있습니다."));
            }
            // 모델구분 허용값 체크
            String modelType = get(row, "모델구분");
            if (!modelType.isEmpty() && !VALID_MODEL_TYPES.contains(modelType)) {
                results.add(error(rowNum, row, "모델구분 검증",
                        "모델구분은 '인터페이스모델' 또는 '서비스모델' 이어야 합니다: '" + modelType + "'"));
            }
        }
        return results;
    }

    // ── 2. 인터페이스타입 검증 ─────────────────────────────────────
    private List<ValidationResult> checkInterfaceType(int rowNum, Map<String, String> row) {
        String itype = get(row, "인터페이스타입").toUpperCase();
        if (!itype.isEmpty() && !VALID_INTERFACE_TYPES.contains(itype)) {
            return Collections.singletonList(error(rowNum, row, "인터페이스타입 검증",
                    "허용되지 않는 타입 '" + itype
                    + "' (허용값: DB-DB, DB-FILE, FILE-FILE, FILE-DB)"));
        }
        return Collections.emptyList();
    }

    // ── 3. 인터페이스쿼리 검증 ────────────────────────────────────
    //  쿼리는 인터페이스(SOURCE) 쪽에 속함
    //  DB-DB / DB-FILE : 인터페이스쿼리 필수 (SELECT 등)
    //  FILE-DB / FILE-FILE : 쿼리 없음
    private List<ValidationResult> checkQuery(int rowNum, Map<String, String> row) {
        List<ValidationResult> results = new ArrayList<>();
        String itype = get(row, "인터페이스타입").toUpperCase();
        String query = get(row, "인터페이스쿼리");

        if (DB_SOURCE_TYPES.contains(itype)) {
            if (query.isEmpty()) {
                results.add(error(rowNum, row, "인터페이스쿼리 검증",
                        "'" + itype + "' 타입은 인터페이스쿼리가 필수입니다."));
            } else {
                results.addAll(validateSqlSyntax(rowNum, row, query));
            }
        } else if (FILE_SOURCE_TYPES.contains(itype) && !query.isEmpty()) {
            results.add(warning(rowNum, row, "인터페이스쿼리 검증",
                    "'" + itype + "' 타입(FILE 소스)에는 인터페이스쿼리가 필요하지 않습니다."));
        }
        return results;
    }

    private List<ValidationResult> validateSqlSyntax(int rowNum,
                                                      Map<String, String> row,
                                                      String query) {
        List<ValidationResult> results = new ArrayList<>();
        String upper = query.toUpperCase().trim();
        String[] validStarts = {"SELECT", "INSERT", "UPDATE", "DELETE", "MERGE"};
        boolean valid = Arrays.stream(validStarts).anyMatch(upper::startsWith);
        if (!valid) {
            String preview = query.length() > 40 ? query.substring(0, 40) + "..." : query;
            results.add(error(rowNum, row, "SQL 구문 검증",
                    "쿼리가 올바른 DML 구문으로 시작하지 않습니다: '" + preview + "'"));
        }
        // TODO: 바인드변수(:변수명) 형식, 선처리 파라미터 일치 여부 등 추가
        return results;
    }

    // ── 4. 선처리/후처리 검증 (선택사항 — 있을 때만 형식 체크) ────
    private List<ValidationResult> checkPrePostProcess(int rowNum, Map<String, String> row) {
        List<ValidationResult> results = new ArrayList<>();
        String[][] checks = {
            {"선처리입력값", get(row, "선처리입력값")},
            {"후처리입력값", get(row, "후처리입력값")},
        };
        for (String[] c : checks) {
            String val = c[1];
            // 값이 있을 때만 허용 문자 체크 (없어도 오류 아님)
            if (!val.isEmpty() && !ALLOWED_PROCESS_CHARS.matcher(val).matches()) {
                results.add(warning(rowNum, row, "선후처리 검증",
                        "[" + c[0] + "]에 허용되지 않는 문자가 포함될 수 있습니다: '" + val + "'"));
            }
        }
        // TODO: 회사 내부 규칙 (파라미터 형식 등) 추가
        return results;
    }

    // ── 5. 필드 검증 (타입 / 길이 / 키타입) ───────────────────────
    private List<ValidationResult> checkFieldInfo(int rowNum, Map<String, String> row) {
        List<ValidationResult> results = new ArrayList<>();
        // FILE-FILE 은 필드가 없으므로 필드 검증 생략
        if ("FILE-FILE".equals(get(row, "인터페이스타입").toUpperCase())) return results;

        String fieldType = get(row, "필드타입").toUpperCase();
        String fieldLen  = get(row, "필드길이");
        String keyType   = get(row, "키타입").toUpperCase();

        // 필드타입
        if (fieldType.isEmpty()) {
            results.add(error(rowNum, row, "필드타입 검증", "필드타입이 비어있습니다."));
        } else if (!VALID_FIELD_TYPES.contains(fieldType)) {
            results.add(error(rowNum, row, "필드타입 검증",
                    "허용되지 않는 필드타입 '" + fieldType
                    + "' (허용값: " + String.join(", ", VALID_FIELD_TYPES) + ")"));
        }

        // GROUP 타입은 길이/키타입 불필요
        if ("GROUP".equals(fieldType)) return results;

        // 필드길이
        if (!fieldLen.isEmpty()) {
            try {
                int len = Integer.parseInt(fieldLen);
                if (len <= 0) {
                    results.add(error(rowNum, row, "필드길이 검증",
                            "필드길이는 양수여야 합니다: " + fieldLen));
                }
            } catch (NumberFormatException e) {
                results.add(error(rowNum, row, "필드길이 검증",
                        "필드길이는 숫자여야 합니다: '" + fieldLen + "'"));
            }
        } else if ("VARCHAR".equals(fieldType) || "CHAR".equals(fieldType)) {
            results.add(warning(rowNum, row, "필드길이 검증",
                    "'" + fieldType + "' 타입은 길이 지정이 권장됩니다."));
        }

        // 키타입
        if (!keyType.isEmpty() && !VALID_KEY_TYPES.contains(keyType)) {
            results.add(error(rowNum, row, "키타입 검증",
                    "허용되지 않는 키타입 '" + keyType + "' (허용값: PK, FK, UK)"));
        }
        return results;
    }

    // ── 6. 매핑모델 검증 ──────────────────────────────────────────
    //  DB-DB / FILE-DB / DB-FILE : Y 필수
    //  FILE-FILE                  : 필드/매핑 없음 → 검증 생략
    private List<ValidationResult> checkMappingModel(int rowNum, Map<String, String> row) {
        List<ValidationResult> results = new ArrayList<>();
        String mapping = get(row, "매핑모델여부").toUpperCase();
        String itype   = get(row, "인터페이스타입").toUpperCase();

        // FILE-FILE 은 매핑모델 없음 → 검증 생략
        if ("FILE-FILE".equals(itype)) return results;

        if (MAPPING_REQUIRED_TYPES.contains(itype)) {
            if (mapping.isEmpty()) {
                results.add(error(rowNum, row, "매핑모델 검증",
                        "'" + itype + "' 타입은 매핑모델여부가 Y 여야 합니다. (현재: 비어있음)"));
            } else if (!"Y".equals(mapping)) {
                results.add(error(rowNum, row, "매핑모델 검증",
                        "'" + itype + "' 타입은 매핑모델여부가 반드시 Y 여야 합니다. (현재: '" + mapping + "')"));
            }
        }
        return results;
    }

    // ── 7. FILE-FILE 전용 검증 ────────────────────────────────────
    //  소스/타겟 파일명·경로 필수 (checkRequiredFields 에서 이미 처리)
    //  여기서는 필드 컬럼이 잘못 채워져 있는지 추가 경고
    private List<ValidationResult> checkFileFileColumns(int rowNum, Map<String, String> row) {
        List<ValidationResult> results = new ArrayList<>();
        if (!"FILE-FILE".equals(get(row, "인터페이스타입").toUpperCase())) return results;

        // FILE-FILE 인데 필드명이 채워져 있으면 경고
        if (!get(row, "필드명").isEmpty()) {
            results.add(warning(rowNum, row, "FILE-FILE 구조 검증",
                    "FILE-FILE 타입에는 필드가 없어야 합니다. [필드명] 값이 입력되어 있습니다: '"
                    + get(row, "필드명") + "'"));
        }
        // FILE-FILE 인데 모델구분이 채워져 있으면 경고
        if (!get(row, "모델구분").isEmpty()) {
            results.add(warning(rowNum, row, "FILE-FILE 구조 검증",
                    "FILE-FILE 타입에는 모델구분이 필요하지 않습니다. (현재: '"
                    + get(row, "모델구분") + "')"));
        }
        return results;
    }

    // ── 8. 인터페이스명 네이밍룰 검증 ─────────────────────────────
    //  형식: [태그]소스시스템명(코드)->타겟시스템명(코드) 설명 전송 (타입)
    private List<ValidationResult> checkNamingRule(int rowNum, Map<String, String> row) {
        List<ValidationResult> results = new ArrayList<>();
        String name  = get(row, "인터페이스명");
        String itype = get(row, "인터페이스타입").toUpperCase();
        if (name.isEmpty()) return results;

        Matcher m = FULL_NAME_PATTERN.matcher(name);
        if (!m.matches()) {
            results.add(error(rowNum, row, "인터페이스명 네이밍룰",
                    "형식 오류 — 올바른 형식: [태그]소스시스템명(코드)->타겟시스템명(코드) 설명 전송 (타입) "
                    + "| 허용 태그: " + VALID_TAGS));
            return results;
        }

        String desc     = m.group(6);
        String nameType = m.group(7);

        // ① 설명이 "전송" 으로 끝나야 함
        if (!desc.endsWith("전송")) {
            results.add(error(rowNum, row, "인터페이스명 설명 검증",
                    "설명 부분이 '전송' 으로 끝나야 합니다. (현재: '" + desc + "')"));
        }

        // ② 인터페이스명 내 타입 vs 컬럼 타입 일치
        if (!itype.isEmpty() && !nameType.equals(itype)) {
            results.add(error(rowNum, row, "인터페이스명-타입 일치 검증",
                    "인터페이스명 끝 타입 '" + nameType
                    + "' 이 인터페이스타입 컬럼 '" + itype + "' 과 다릅니다."));
        }
        return results;
    }

    // ==================================================================
    //  Phase 2 : 인터페이스 단위 검증
    // ==================================================================
    private List<ValidationResult> checkInterfaceGroup(String ifaceId,
                                                        List<IndexedRow> rows) {
        List<ValidationResult> results = new ArrayList<>();
        int firstRowNum              = rows.get(0).rowNum;
        Map<String, String> firstRow = rows.get(0).data;
        String itype = firstRow.getOrDefault("인터페이스타입", "").trim().toUpperCase();

        // ── 1. 서비스(TARGET) 존재 여부 ────────────────────────────
        boolean hasService = rows.stream()
                .anyMatch(r -> !r.data.getOrDefault("서비스ID", "").trim().isEmpty());
        if (!hasService) {
            results.add(error(firstRowNum, firstRow, "서비스 존재 검증",
                    "[" + ifaceId + "] 인터페이스에 서비스ID 가 없습니다. 서비스는 필수입니다."));
        }

        // ── 2. FILE-DB : 인터페이스모델에 GROUP 타입 정확히 1개 ────
        if ("FILE-DB".equals(itype)) {
            long cnt = rows.stream()
                    .filter(r -> "인터페이스모델".equals(
                            r.data.getOrDefault("모델구분", "").trim()))
                    .filter(r -> "GROUP".equalsIgnoreCase(
                            r.data.getOrDefault("필드타입", "").trim()))
                    .count();
            if (cnt == 0) {
                results.add(error(firstRowNum, firstRow, "GROUP 타입 검증",
                        "[" + ifaceId + "] FILE-DB 타입은 인터페이스모델에 "
                        + "GROUP 타입 필드가 정확히 1개 있어야 합니다. (현재: 0개)"));
            } else if (cnt > 1) {
                results.add(error(firstRowNum, firstRow, "GROUP 타입 검증",
                        "[" + ifaceId + "] FILE-DB 타입의 인터페이스모델 GROUP 타입은 "
                        + "1개여야 합니다. (현재: " + cnt + "개)"));
            }
        }

        // ── 3. DB-FILE : 서비스모델에 GROUP 타입 정확히 1개 ────────
        if ("DB-FILE".equals(itype)) {
            long cnt = rows.stream()
                    .filter(r -> "서비스모델".equals(
                            r.data.getOrDefault("모델구분", "").trim()))
                    .filter(r -> "GROUP".equalsIgnoreCase(
                            r.data.getOrDefault("필드타입", "").trim()))
                    .count();
            if (cnt == 0) {
                results.add(error(firstRowNum, firstRow, "GROUP 타입 검증",
                        "[" + ifaceId + "] DB-FILE 타입은 서비스모델에 "
                        + "GROUP 타입 필드가 정확히 1개 있어야 합니다. (현재: 0개)"));
            } else if (cnt > 1) {
                results.add(error(firstRowNum, firstRow, "GROUP 타입 검증",
                        "[" + ifaceId + "] DB-FILE 타입의 서비스모델 GROUP 타입은 "
                        + "1개여야 합니다. (현재: " + cnt + "개)"));
            }
        }

        return results;
    }

    // ==================================================================
    //  헬퍼 메서드
    // ==================================================================
    private String get(Map<String, String> row, String key) {
        String v = row.get(key);
        return v == null ? "" : v.trim();
    }

    private String resolveId(Map<String, String> row) {
        String id = row.get("인터페이스ID");
        return (id == null || id.trim().isEmpty()) ? "-" : id.trim();
    }

    private ValidationResult error(int r, Map<String, String> row,
                                   String check, String msg) {
        return new ValidationResult(
                ValidationResult.Severity.ERROR, r, resolveId(row), check, msg);
    }

    private ValidationResult warning(int r, Map<String, String> row,
                                     String check, String msg) {
        return new ValidationResult(
                ValidationResult.Severity.WARNING, r, resolveId(row), check, msg);
    }

    private ValidationResult ok(int r, Map<String, String> row,
                                String check, String msg) {
        return new ValidationResult(
                ValidationResult.Severity.OK, r, resolveId(row), check, msg);
    }
}
