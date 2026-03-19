package util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * CSV 파일을 읽어 List<Map<String,String>> 형태로 반환하는 유틸리티.
 * - UTF-8 BOM 자동 제거
 * - 쌍따옴표로 감싸진 필드(콤마 포함) 처리
 * - 빈 줄 자동 스킵
 */
public class CsvReader {

    /**
     * CSV 파일을 읽어 헤더를 키로 하는 Map 목록을 반환합니다.
     */
    public static List<Map<String, String>> read(File file) throws IOException {
        List<Map<String, String>> result = new ArrayList<>();

        try (BufferedReader br = openReader(file)) {
            String headerLine = br.readLine();
            if (headerLine == null) return result;

            String[] headers = parseLine(stripBom(headerLine));

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] values = parseLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    row.put(headers[i].trim(),
                            i < values.length ? values[i].trim() : "");
                }
                result.add(row);
            }
        }
        return result;
    }

    /**
     * 헤더(컬럼명) 배열만 반환합니다.
     */
    public static String[] getHeaders(File file) throws IOException {
        try (BufferedReader br = openReader(file)) {
            String line = br.readLine();
            if (line == null) return new String[0];
            return parseLine(stripBom(line));
        }
    }

    // ------------------------------------------------------------------
    //  내부 메서드
    // ------------------------------------------------------------------
    private static BufferedReader openReader(File file) throws IOException {
        return new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(file), StandardCharsets.UTF_8));
    }

    private static String stripBom(String line) {
        return line.startsWith("\uFEFF") ? line.substring(1) : line;
    }

    /**
     * 한 줄의 CSV 문자열을 파싱합니다. 쌍따옴표 이스케이프("") 처리 포함.
     */
    private static String[] parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;               // escaped quote ""
                    } else {
                        inQuotes = false;  // closing quote
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }
}
