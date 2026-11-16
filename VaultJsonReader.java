package za.co.quickchat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


final class VaultJsonReader {

    private final String jsonSource;
    private int cursor;

    private VaultJsonReader(String jsonSource) {
        this.jsonSource = jsonSource;
    }

    static List<Map<String, String>> parseArray(String jsonText) throws IOException {
        VaultJsonReader reader = new VaultJsonReader(jsonText);
        List<Map<String, String>> records = reader.readArray();
        reader.skipWhitespace();
        if (reader.cursor != reader.jsonSource.length()) {
            throw reader.fail("Unexpected content after the closing bracket");
        }
        return records;
    }

    private List<Map<String, String>> readArray() throws IOException {
        List<Map<String, String>> records = new ArrayList<>();
        skipWhitespace();
        expect('[');
        skipWhitespace();
        if (peek() == ']') {
            cursor++;
            return records;
        }
        while (true) {
            records.add(readObject());
            skipWhitespace();
            char separator = take();
            if (separator == ',') {
                continue;
            }
            if (separator == ']') {
                return records;
            }
            throw fail("Expected ',' or ']'");
        }
    }

    private Map<String, String> readObject() throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        skipWhitespace();
        expect('{');
        skipWhitespace();
        if (peek() == '}') {
            cursor++;
            return fields;
        }
        while (true) {
            skipWhitespace();
            String fieldName = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            fields.put(fieldName, readValue());
            skipWhitespace();
            char separator = take();
            if (separator == ',') {
                continue;
            }
            if (separator == '}') {
                return fields;
            }
            throw fail("Expected ',' or '}'");
        }
    }

    private String readValue() throws IOException {
        if (peek() == '"') {
            return readString();
        }
        int startAt = cursor;
        while (cursor < jsonSource.length() && "-+.eE0123456789".indexOf(jsonSource.charAt(cursor)) >= 0) {
            cursor++;
        }
        if (startAt == cursor) {
            throw fail("Expected a string or a number");
        }
        return jsonSource.substring(startAt, cursor);
    }

    private String readString() throws IOException {
        expect('"');
        StringBuilder collected = new StringBuilder();
        while (true) {
            char glyph = take();
            if (glyph == '"') {
                return collected.toString();
            }
            if (glyph != '\\') {
                collected.append(glyph);
                continue;
            }
            char escapeCode = take();
            switch (escapeCode) {
                case '"':
                    collected.append('"');
                    break;
                case '\\':
                    collected.append('\\');
                    break;
                case '/':
                    collected.append('/');
                    break;
                case 'b':
                    collected.append('\b');
                    break;
                case 'f':
                    collected.append('\f');
                    break;
                case 'n':
                    collected.append('\n');
                    break;
                case 'r':
                    collected.append('\r');
                    break;
                case 't':
                    collected.append('\t');
                    break;
                case 'u':
                    collected.append(readUnicodeEscape());
                    break;
                default:
                    throw fail("Unknown escape sequence \\" + escapeCode);
            }
        }
    }

    private char readUnicodeEscape() throws IOException {
        if (cursor + 4 > jsonSource.length()) {
            throw fail("Incomplete \\u escape");
        }
        int codeUnit = 0;
        for (int step = 0; step < 4; step++) {
            int hexDigit = Character.digit(jsonSource.charAt(cursor + step), 16);
            if (hexDigit < 0) {
                throw fail("Invalid \\u escape");
            }
            codeUnit = codeUnit * 16 + hexDigit;
        }
        cursor += 4;
        return (char) codeUnit;
    }

    private void skipWhitespace() {
        while (cursor < jsonSource.length() && Character.isWhitespace(jsonSource.charAt(cursor))) {
            cursor++;
        }
    }

    private char peek() {
        return cursor < jsonSource.length() ? jsonSource.charAt(cursor) : '\0';
    }

    private char take() throws IOException {
        if (cursor >= jsonSource.length()) {
            throw fail("Unexpected end of file");
        }
        return jsonSource.charAt(cursor++);
    }

    private void expect(char wanted) throws IOException {
        if (take() != wanted) {
            cursor--;
            throw fail("Expected '" + wanted + "'");
        }
    }

    private IOException fail(String reason) {
        return new IOException("Malformed JSON at position " + cursor + ": " + reason);
    }
}
