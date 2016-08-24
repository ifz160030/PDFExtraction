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
public class XmlParser extends DefaultHandler {
    static List<String> weekDays = Arrays.asList("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo");
    File xmlFile;
    String xml;
    ArrayList<Font> fontList;
    String date;
    String tmpValue;
    Font fontTmp;
    
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
        fontList = new ArrayList<Font>();
        date = "";
        tmpValue = "";
        xml = "";
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
        for(int i = fontList.size(); i>0; i--){
            Font f = fontList.get(i-1);
            String temp = f.getText();
            String day = temp.substring(0,temp.indexOf(" "));
            if(f.getNumChars()<150 && weekDays.contains(day)){
                date = temp;
                fontList.remove(f);
            }
            else if(f.getNumChars()<150){
                fontList.remove(f);
            }
        }
        xml += "<date>"+date+"</date>\n";
        String tempString = "";
        for(int i = 0; i<fontList.size(); i++){
            Font f = fontList.get(i);
            tempString += f.getText();
            if(f.isIncomplete()){
                //wait to append the rest of article
            }
            else if(!f.isIncomplete() || i == fontList.size()-1){
                xml += "<article>"+tempString+"</article>\n";
                tempString = "";
            }
        }
        xml += "</articles>";
    }

    private void writeXml() {
        try{
            String oldName = xmlFile.getName();
            String newName = oldName.substring(0, oldName.length()-4)+"clean.xml";
            FileOutputStream fileOut = new FileOutputStream(new File(outPath+"/"+newName));
            OutputStreamWriter  out = new OutputStreamWriter(fileOut,"UTF-8");
            out.write(xml,0,xml.length());
            out.close();
        }
        catch(Exception e){
        }
    }

    @Override
    public void startElement(String uri, String localName, String tagName, Attributes attributes) throws SAXException {
        if (tagName.equalsIgnoreCase("font")) {
            fontTmp = new Font();
        }
    }

    @Override
    public void endElement(String uri, String localName, String tagName) throws SAXException {
        // if end of font add to list
        if (tagName.equals("text")) {
            fontTmp.addText(tmpValue);
            tmpValue = "";
        }
        if (tagName.equalsIgnoreCase("font")) {
            fontList.add(fontTmp);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        tmpValue += new String(ch, start, length);
    }

    public static class Font{
        String fullText;
        int numLines;
        boolean endsIncomplete;

        public Font(){
            fullText = "";
            numLines = 0;
            endsIncomplete = false;
        }

        public void addText(String line){
            if(line.equals("")){
                return;
            }
            //check incomplete
            line = line.trim();
            line = line.replace("<","&lt;");
            line = line.replace(">","&lt;");
            line = line.replace("&","&amp;");
            if(line.endsWith("-")){
                endsIncomplete = true;
                line = line.substring(0, line.length()-1);
            }
            else{
                endsIncomplete = false;
                line += " "; 
            }
            numLines++;
            fullText += line;
        }

        public String getText(){
            return fullText;
        }

        public int getNumLines(){
            return numLines;
        }
        
        public int getNumChars(){
            return fullText.length();
        }

        public boolean isIncomplete(){
            return endsIncomplete;
        }
    }
}
