package utils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Gerador minimalista de PDF (PDF 1.4, WinAnsi/Helvetica) sem dependências externas.
 * Suporta multi-página com texto simples — suficiente para extractos e comprovativos.
 */
public final class PdfUtil {
    private PdfUtil() {}

    public static byte[] document(List<Line> lines) {
        return new Layout(lines).render();
    }

    public static final class Line {
        public final String text; public final int size; public final boolean bold;
        public Line(String text, int size, boolean bold) { this.text = text; this.size = size; this.bold = bold; }
        public static Line title(String t)     { return new Line(t, 17, true); }
        public static Line subtitle(String t)  { return new Line(t, 12, true); }
        public static Line text(String t)      { return new Line(t, 10, false); }
        public static Line small(String t)     { return new Line(t, 8, false); }
        public static Line smallBold(String t) { return new Line(t, 9, true); }
    }

    /** Distribui as linhas por páginas e constrói o ficheiro PDF. */
    private static final class Layout {
        private static final int PAGE_W = 595, PAGE_H = 842;
        private static final int MARGIN_X = 40, MARGIN_TOP = 760, BOTTOM = 40;

        private final List<Line> source;
        private record Placed(String text, int size, boolean bold, int y) {}
        private final List<List<Placed>> pages = new ArrayList<>();

        Layout(List<Line> source) { this.source = source; }

        byte[] render() {
            List<Placed> current = new ArrayList<>();
            pages.add(current);
            int y = MARGIN_TOP;
            for (Line l : source) {
                int lh = l.size + 4;
                if (y - lh < BOTTOM) { current = new ArrayList<>(); pages.add(current); y = MARGIN_TOP; }
                current.add(new Placed(clean(l.text), l.size, l.bold, y));
                y -= lh;
                if (l.size >= 17) y -= 6;
            }
            return assemble();
        }

        private byte[] assemble() {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                StringBuilder offsets = new StringBuilder();
                int firstPageObj = 5;
                List<String> objs = new ArrayList<>();
                objs.add("<< /Type /Catalog /Pages 2 0 R >>");
                StringBuilder kidsSb = new StringBuilder();
                for (int i = 0; i < pages.size(); i++) {
                    kidsSb.append(i > 0 ? " " : "").append(firstPageObj + i * 2).append(" 0 R");
                }
                objs.add("<< /Type /Pages /Count " + pages.size() + " /Kids [" + kidsSb + "] >>");
                objs.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
                objs.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");

                for (int i = 0; i < pages.size(); i++) {
                    StringBuilder sb = new StringBuilder();
                    for (Placed p : pages.get(i)) {
                        sb.append("BT /").append(p.bold() ? "F2" : "F1").append(' ').append(p.size())
                          .append(" Tf ").append(MARGIN_X).append(' ').append(p.y())
                          .append(" Td (").append(p.text()).append(") Tj ET\n");
                    }
                    String content = sb.toString();
                    int contentObjNo = firstPageObj + i * 2;
                    objs.add("<< /Length " + content.getBytes(StandardCharsets.ISO_8859_1).length
                            + " >>\nstream\n" + content + "endstream");
                    int pageObjNo = contentObjNo + 1;
                    objs.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + PAGE_W + " " + PAGE_H + "] "
                            + "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents "
                            + contentObjNo + " 0 R >>");
                }
                out.write("%PDF-1.4\n%€¢¥¨\n".getBytes(StandardCharsets.ISO_8859_1));
                for (int i = 0; i < objs.size(); i++) {
                    offsets.append(String.format("%010d 00000 n \n", out.size()));
                    out.write((i + 1 + " 0 obj\n" + objs.get(i) + "\nendobj\n").getBytes(StandardCharsets.ISO_8859_1));
                }
                int xrefPos = out.size();
                StringBuilder xref = new StringBuilder();
                xref.append("xref\n0 ").append(objs.size() + 1).append("\n0000000000 65535 f \n").append(offsets);
                xref.append("trailer\n<< /Size ").append(objs.size() + 1)
                    .append(" /Root 1 0 R >>\nstartxref\n").append(xrefPos).append("\n%%EOF");
                out.write(xref.toString().getBytes(StandardCharsets.ISO_8859_1));
                return out.toByteArray();
            } catch (Exception e) {
                throw new RuntimeException("Falha ao gerar PDF: " + e.getMessage(), e);
            }
        }
    }

    private static String clean(String t) {
        if (t == null) t = "";
        StringBuilder s = new StringBuilder();
        for (char c : t.toCharArray()) {
            if (c > 0 && c < 256 && c != '(' && c != ')' && c != '\\') s.append(c);
            else s.append('?');
        }
        return s.toString();
    }
}
