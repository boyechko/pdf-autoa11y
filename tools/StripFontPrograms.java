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
// so the checks see the same text without them.
//
// Only simple fonts are stripped. A viewer draws a simple font from a
// substitute, since its /Encoding names each glyph; a composite (Type0) font's
// codes are glyph IDs into its own subset, so without the program Acrobat has
// nothing to map them to and draws every character as a dot. A program a
// CIDFont uses stays even where a simple font shares it, as Word's output does.
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StripFontPrograms {
    private static final List<PdfName> FONT_FILE_KEYS =
            List.of(PdfName.FontFile, PdfName.FontFile2, PdfName.FontFile3);
    private static final List<PdfName> CID_FONT_SUBTYPES =
            List.of(PdfName.CIDFontType0, PdfName.CIDFontType2);

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: StripFontPrograms <in.pdf> <out.pdf>");
            System.exit(2);
        }
        int stripped = 0;
        try (PdfDocument doc = new PdfDocument(new PdfReader(args[0]), new PdfWriter(args[1]))) {
            Set<Integer> kept = compositePrograms(doc);
            for (int i = 1; i < doc.getNumberOfPdfObjects(); i++) {
                if (doc.getPdfObject(i) instanceof PdfDictionary dict
                        && PdfName.FontDescriptor.equals(dict.getAsName(PdfName.Type))) {
                    for (PdfName key : FONT_FILE_KEYS) {
                        PdfObject fontFile = dict.get(key, false);
                        if (fontFile == null
                                || fontFile instanceof PdfIndirectReference ref
                                        && kept.contains(ref.getObjNumber())) continue;
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

    /** Object numbers of the font programs used by CIDFonts (Type0 descendants). */
    private static Set<Integer> compositePrograms(PdfDocument doc) {
        Set<Integer> numbers = new HashSet<>();
        for (int i = 1; i < doc.getNumberOfPdfObjects(); i++) {
            if (doc.getPdfObject(i) instanceof PdfDictionary dict
                    && dict.getAsName(PdfName.Subtype) instanceof PdfName subtype
                    && CID_FONT_SUBTYPES.contains(subtype)
                    && dict.getAsDictionary(PdfName.FontDescriptor) instanceof PdfDictionary desc) {
                for (PdfName key : FONT_FILE_KEYS) {
                    if (desc.get(key, false) instanceof PdfIndirectReference ref) {
                        numbers.add(ref.getObjNumber());
                    }
                }
            }
        }
        return numbers;
    }
}
