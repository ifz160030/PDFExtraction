import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import java.io.IOException;
import java.io.*;
import java.util.*;

/**
 * Removes graphics and merges text for easy text extraction.
 * 
 * @author (Italo Zevallos) 
 * @version (10/07/16)
 */
public class PDFFormatter {
    final static List<String> PAINTING_PATH_OPS = Arrays.asList("S", "s", "F", "f", "f*", "B", "b", "B*", "b*");
    //final static List<PDType1Font> DEFAULT_FONTS = Arrays.asList(PDType1Font.TIMES_ROMAN, PDType1Font.TIMES_BOLD,
    //        PDType1Font.TIMES_ITALIC, PDType1Font.TIMES_BOLD_ITALIC, PDType1Font.HELVETICA, PDType1Font.HELVETICA_BOLD,
    //        PDType1Font.HELVETICA_OBLIQUE, PDType1Font.HELVETICA_BOLD_OBLIQUE, PDType1Font.COURIER,PDType1Font.COURIER_BOLD,
    //        PDType1Font.COURIER_OBLIQUE, PDType1Font.COURIER_BOLD_OBLIQUE, PDType1Font.SYMBOL, PDType1Font.ZAPF_DINGBATS);
    final static int TJ_SPACING_LIMIT = 100;

    public static void main(String[] filePath){
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
                ArrayList<Object> tokens = (ArrayList<Object>)parser.getTokens();
                ArrayList<Object> newTokens = new ArrayList<Object>();
                COSNumber tc = COSNumber.get("0");
                int tcIndex = 0;
                for(int i=0; i<tokens.size(); i++) {
                    Object token = tokens.get(i);
                    if( token instanceof Operator ) {
                        Operator op = (Operator)token;
                        if(op.getName().equals("Do")) {
                            //remove the one argument to this operator
                            COSName name = (COSName)newTokens.remove(newTokens.size()-1);
                            continue;
                        }
                        else if (PAINTING_PATH_OPS.contains(op.getName()))
                        {
                            // replace path painting operator by path no-op
                            token = Operator.getOperator("n");
                        }
                        else if(op.getName().equals("Tc") || op.getName().equals("\"")){
                            if(tc.floatValue()!=0){
                                newTokens.set(tcIndex, COSNumber.get("0"));
                                tc = COSNumber.get("0");
                            }
                            tc = (COSNumber)tokens.get(i-1);
                            tcIndex = newTokens.size()-1;
                        }
                        else if(op.getName().equals("TJ")){
                            //maybe check if tc and tw give space / scaling ops
                            COSArray array = (COSArray)tokens.get(i-1);
                            newTokens.set(newTokens.size()-1, mergeArray(array, tc.floatValue()));
                        }
                    }
                    newTokens.add(token);
                }
                tokens.set(tcIndex, COSNumber.get("0"));

                PDStream newContents = new PDStream(toDoc);
                OutputStream out = newContents.createOutputStream(COSName.FLATE_DECODE);
                //OutputStream out = newContents.createOutputStream();
                ContentStreamWriter writer = new ContentStreamWriter(out);
                writer.writeTokens(newTokens);
                PDPage newPage = new PDPage(page.getMediaBox());
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

            toDoc.save(new File(outPath+File.separator+pdfFileOut));

            doc.close();
            toDoc.close();
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

    private static COSArray mergeArray(COSArray array, float tc){
        //check to see if number in TJ is seperating maybe something with the tm?
        //unflatedecode
        COSArray newArray = new COSArray();
        String merge = "";
        float tempNum = 0;
        Object previous = null;
        for (int i = 0; i < array.size(); i++) {
            Object element = array.getObject(i);
            if (element instanceof COSString){
                COSString string = (COSString)element;
                if(previous instanceof COSNumber){
                    if(Math.abs(tempNum)>TJ_SPACING_LIMIT){
                        merge += " "+formatString(string.getString(), tc);
                    }
                    else{
                        merge += formatString(string.getString(), tc);
                    }
                    tempNum = 0;
                }
                else{
                    merge += formatString(string.getString(), tc);
                }
            }
            else if(element instanceof COSNumber){
                COSNumber num = (COSNumber)element;
                if(previous==null){
                    element = null;
                }
                else if(previous instanceof COSNumber){
                    tempNum += num.floatValue()-(1000*tc);
                }
                else{
                    tempNum = num.floatValue()-(1000*tc);
                }
            }
            previous = element;
        }
        COSString merged = new COSString(merge);
        newArray.add(merged);
        return newArray;
    }

    public static String formatString(String s, float tc){
        String merge = "";
        if(Math.abs(1000*tc)>TJ_SPACING_LIMIT){
            for(int i = 0; i<s.length()-1; i++){
                merge += s.charAt(i)+" ";
            }
            merge += s.charAt(s.length()-1);
            return merge;
        }
        else{
            return s;
        }
    }
}
