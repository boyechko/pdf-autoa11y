// Extract a page range into a new tagged PDF, preserving the structure tree.
//
// Normally driven by tools/extract-fixture.sh, which resolves the classpath.
// Standalone (Java 11+ single-file source launcher):
//
//   java -cp "$(cat target/tools-classpath.txt)" \
//       tools/ExtractPages.java <in.pdf> <out.pdf> <firstPage> <lastPage>
//
// copyPagesTo carries the structure subtree for the copied pages when the
// destination is marked tagged, so the extract keeps the roles, scribbles and
// marked content the checks read. Link annotations survive; AcroForm fields do
// not, which no fixture has needed so far.

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;

public class ExtractPages {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("usage: ExtractPages <in.pdf> <out.pdf> <firstPage> <lastPage>");
            System.exit(2);
        }
        int first = Integer.parseInt(args[2]);
        int last = Integer.parseInt(args[3]);

        try (PdfDocument src = new PdfDocument(new PdfReader(args[0]));
                PdfDocument dst = new PdfDocument(new PdfWriter(args[1]))) {
            if (last > src.getNumberOfPages()) {
                System.err.println(
                        "page "
                                + last
                                + " is past the end of "
                                + args[0]
                                + " ("
                                + src.getNumberOfPages()
                                + " pages)");
                System.exit(1);
            }
            dst.setTagged();
            src.copyPagesTo(first, last, dst);
        }
        System.out.println("extracted pages " + first + "-" + last + " into " + args[1]);
    }
}
