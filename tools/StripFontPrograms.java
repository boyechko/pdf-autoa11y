// Strip embedded font programs from a PDF, keeping every object number.
//
// Run on every extract by tools/extract-pages.sh. Standalone (Java 11+
// single-file source launcher), e.g. to slim a fixture cut before that:
//
//   java -cp "$(cat target/tools-classpath.txt)" \
//       tools/StripFontPrograms.java <in.pdf> <out.pdf>
//
// A page extract carries the source document's font subsets whole, so a
// one-page fixture is mostly font programs. Text extraction reads only the
// font dictionaries (/Widths, /Encoding, /ToUnicode), never the glyph outlines,
// so the checks see the same text without them; viewers substitute fonts.
//
// Object numbers must survive: tests, goal files and --scope all address
// elements by them. qpdf and pikepdf renumber on save; iText's reader/writer
// pair does not, and freeing the dropped streams leaves gaps rather than
// shifting anything.

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfIndirectReference;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import java.util.List;

public class StripFontPrograms {
    private static final List<PdfName> FONT_FILE_KEYS =
            List.of(PdfName.FontFile, PdfName.FontFile2, PdfName.FontFile3);

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: StripFontPrograms <in.pdf> <out.pdf>");
            System.exit(2);
        }
        int stripped = 0;
        try (PdfDocument doc = new PdfDocument(new PdfReader(args[0]), new PdfWriter(args[1]))) {
            for (int i = 1; i < doc.getNumberOfPdfObjects(); i++) {
                if (doc.getPdfObject(i) instanceof PdfDictionary dict
                        && PdfName.FontDescriptor.equals(dict.getAsName(PdfName.Type))) {
                    for (PdfName key : FONT_FILE_KEYS) {
                        PdfObject fontFile = dict.get(key, false);
                        if (fontFile == null) continue;
                        dict.remove(key);
                        if (fontFile instanceof PdfIndirectReference ref) {
                            ref.setFree();
                        }
                        stripped++;
                    }
                }
            }
        }
        System.out.println("stripped " + stripped + " font program(s) into " + args[1]);
    }
}
