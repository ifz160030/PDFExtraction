import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import java.io.IOException;
import java.io.*;
import java.util.*;

public class PDFFormatter {
    final static List<String> PAINTING_PATH_OPS = Arrays.asList("S", "s", "F", "f", "f*", "B", "b", "B*", "b*");
    final static List<PDType1Font> DEFAULT_FONTS = Arrays.asList(PDType1Font.TIMES_ROMAN, PDType1Font.TIMES_BOLD,
            PDType1Font.TIMES_ITALIC, PDType1Font.TIMES_BOLD_ITALIC, PDType1Font.HELVETICA, PDType1Font.HELVETICA_BOLD,
            PDType1Font.HELVETICA_OBLIQUE, PDType1Font.HELVETICA_BOLD_OBLIQUE, PDType1Font.COURIER,PDType1Font.COURIER_BOLD,
            PDType1Font.COURIER_OBLIQUE, PDType1Font.COURIER_BOLD_OBLIQUE, PDType1Font.SYMBOL, PDType1Font.ZAPF_DINGBATS);

    public static void main(String[] filePath) throws IOException {
        String inPath = filePath[0];
        String outPath = filePath[1];
        File inFolder = new File(inPath);
        File[] listOfFiles = inFolder.listFiles();
        for(File file : listOfFiles){
            String fileName = file.getName();
            if(fileName.endsWith(".PDF") || fileName.endsWith(".pdf")){
                try{
                    strip(file, fileName.substring(0, fileName.length()-4)+"new.PDF", outPath);
                }
                catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    public static void strip(File pdfFileIn, String pdfFileOut, String outPath) throws Exception {

        PDDocument doc = new PDDocument();
        PDDocument toDoc = new PDDocument();
        try
        {        
            doc = PDDocument.load(pdfFileIn);
            PDPageTree pages = doc.getPages();
            Iterator<PDPage> it = pages.iterator();
            while(it.hasNext()) {
                PDPage page = it.next();

                PDFStreamParser parser = new PDFStreamParser(page);
                parser.parse();
                List tokens = parser.getTokens();
                List newTokens = new ArrayList();
                for(int i=0; i<tokens.size(); i++) {
                    Object token = tokens.get(i);
                    if( token instanceof Operator ) {
                        Operator op = (Operator)token;
                        if( op.getName().equals("Do") ) {
                            //remove the one argument to this operator
                            COSName name = (COSName)newTokens.remove( newTokens.size() -1 );
                            continue;
                        }
                        else if (PAINTING_PATH_OPS.contains(op.getName()))
                        {
                            // replace path painting operator by path no-op
                            token = Operator.getOperator("n");
                        }
                    }
                    newTokens.add( token );
                }

                PDStream newContents = new PDStream(toDoc);
                OutputStream out = newContents.createOutputStream(COSName.FLATE_DECODE);
                ContentStreamWriter writer = new ContentStreamWriter(out);
                writer.writeTokens(newTokens);
                PDPage newPage = new PDPage();
                toDoc.addPage(newPage);

                newPage.setContents(newContents);

                PDResources newResources = new PDResources();
                PDResources oldResources = page.getResources();
                //add fonts
                for(COSName font :  oldResources.getFontNames()){
                    newResources.put(font, oldResources.getFont(font));
                }
                //add color space
                for(COSName name :  oldResources.getColorSpaceNames()){
                    newResources.put(name, oldResources.getColorSpace(name));
                }

                newPage.setResources(newResources);
                out.close();
            }

            toDoc.save(new File(outPath+"/"+pdfFileOut));
        }
        finally
        {
            if( doc != null )
            {
                doc.close();
                toDoc.close();
            }
        }
    }
}