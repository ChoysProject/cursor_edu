package validator;

/**
 * 단일 검증 결과를 담는 불변 데이터 클래스.
 */
public class ValidationResult {

    public enum Severity {
        ERROR, WARNING, OK;

        public String getLabel() {
            return name();
        }
    }

    private final Severity severity;
    private final int      rowNum;
    private final String   interfaceId;
    private final String   check;
    private final String   message;

    public ValidationResult(Severity severity, int rowNum,
                            String interfaceId, String check, String message) {
        this.severity    = severity;
        this.rowNum      = rowNum;
        this.interfaceId = interfaceId;
        this.check       = check;
        this.message     = message;
    }

    public Severity getSeverity()    { return severity;    }
    public int      getRowNum()      { return rowNum;      }
    public String   getInterfaceId() { return interfaceId; }
    public String   getCheck()       { return check;       }
    public String   getMessage()     { return message;     }

    /** JTable 행 데이터로 변환 */
    public Object[] toTableRow() {
        return new Object[]{ rowNum, interfaceId, severity.getLabel(), check, message };
    }
}
