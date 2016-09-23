import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
/**
 * Extracts xml from XmlOrganizer and writes to an xml file with article and paragraph tags.
 * 
 * @author (Italo Zevallos) 
 * @version (09/21/16)
 */
public class XmlParser extends DefaultHandler {
    File xmlFile;
    String xml;
    ArrayList<Font> articleList;
    String tmpValue;
    Font fontTmp;
    boolean hasParagraph;
    int prevX = -10000;

    String outPath;

    public static void main(String[] filePath) {
        String inPath = filePath[0];
        String outPath = filePath[1];
        File xmlFolder = new File(inPath);
        File[] listOfFiles = xmlFolder.listFiles();
        for (File file : listOfFiles){
            String fileName = file.getName();
            if(fileName.endsWith(".XML") || fileName.endsWith(".xml")){
                new XmlParser(file, outPath);
            }
        }
    }

    public XmlParser(File file, String outPath) {
        this.xmlFile = file;
        articleList = new ArrayList<Font>();
        fontTmp = new Font();
        tmpValue = "";
        xml = "";
        hasParagraph = false;
        this.outPath = outPath;
        parseDocument();
        cleanXml();
        writeXml();
    }

    private void parseDocument() {
        // parse
        SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            SAXParser parser = factory.newSAXParser();
            parser.parse(xmlFile, this);
        } catch (ParserConfigurationException e) {
            System.out.println("ParserConfig error");
            e.printStackTrace();
        } catch (SAXException e) {
            System.out.println("SAXException : xml not well formed");
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("IO error");
            e.printStackTrace();
        }
    }

    private void cleanXml(){
        xml += "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n";
        xml += "<articles>\n";
        for(int i = articleList.size(); i>0; i--){
            Font f = articleList.get(i-1);
            if(f.getNumChars()<150){
                articleList.remove(f);
            }
        }
        for(int i = 0; i<articleList.size(); i++){
            Font f = articleList.get(i);
            xml += "<article>\n"+f.getText()+"\n</article>\n";
        }
        xml += "</articles>";
    }

    private void writeXml() {
        try{
            String oldName = xmlFile.getName();
            String newName = oldName.substring(0, oldName.length()-4)+"clean.xml";
            FileOutputStream fileOut = new FileOutputStream(new File(outPath+File.separator+newName));
            OutputStreamWriter  out = new OutputStreamWriter(fileOut,"UTF-8");
            out.write(xml,0,xml.length());
            out.close();
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void startElement(String uri, String localName, String tagName, Attributes attributes) throws SAXException {
        if(tagName.equalsIgnoreCase("text")){
            int x = Integer.parseInt(attributes.getValue("x"));
            if(prevX != -10000){
                if(x<prevX && (prevX-15)<x && (prevX-x)>2 && !hasParagraph){
                    //add [SP] to beginning
                    fontTmp.addFront("<paragraph>");
                    hasParagraph = true;
                }
                else if(x>prevX && (prevX+15)>x && (x-prevX)>2){
                    //add [EP][SP] to end
                    tmpValue+= "</paragraph>\n<paragraph>";
                }
            }
            prevX = x;
        }
        if(tagName.equalsIgnoreCase("break")){
            if(!hasParagraph){
                fontTmp.addFront("<paragraph>");
            }
            fontTmp.addText("</paragraph>");
            articleList.add(fontTmp);
            fontTmp = new Font();
            prevX = -10000;
            hasParagraph = false;
        }
    }

    @Override
    public void endElement(String uri, String localName, String tagName) throws SAXException {
        if (tagName.equals("text")) {
            fontTmp.addText(tmpValue);
            tmpValue = "";
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        tmpValue += new String(ch, start, length);
    }

    public static class Font{
        String fullText;

        public Font(){
            fullText = "";
        }

        public void addText(String line){
            if(line.equals("")){
                return;
            }
            
            line = line.trim();

            if(line.endsWith("-")){
                line = line.substring(0, line.length()-1);
            }
            else{
                line += " "; 
            }
            fullText += line;
        }

        public void addFront(String ch){
            fullText = ch + fullText;
        }

        public void trimEnd(){
            fullText = fullText.substring(0, fullText.length()-1);
        }

        public String getText(){
            return fullText;
        }

        public int getNumChars(){
            return fullText.length();
        }
        //shouldn't matter
        public boolean startsLowerCase(){
            return Character.isLowerCase(fullText.charAt(0));
        }
    }
}
