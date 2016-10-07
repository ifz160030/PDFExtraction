import java.io.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Collections;
import java.util.Arrays;

/**
 * Organizes the xml output by pdf2xml and writes to an xml file.
 * 
 * @author (Italo Zevallos) 
 * @version (10/07/16)
 */
public class XmlOrganizer
{
    final static List<String> COMPLETE_CHARACTERS = Arrays.asList(".", "!", "?", "\"", "¿", "¡");//Maybe more ) ;
    //final static int TEXT_SPLIT = 20; nu
    final static int MAX_FONT_SIZE = 20;
    final static int TEXT_MERGE = 5;
    final static double TEXT_CONNECT = 4.5;// nu
    final static int TEXT_CONNECT_MAX = 40;
    final static int MIN_DIR_LENGTH = 5;
    final static double CHAR_WIDTH_MULTIPLIER = .45;//.45 nu
    //may want to see the optimal % seems ok
    static int ONE_SIXTH = 1;
    public static void main(String[] args){
        String inPath = args[0];
        String outPath = args[1];
        File xmlFolder = new File(inPath);
        File[] listOfFiles = xmlFolder.listFiles();
        for(File file : listOfFiles){
            String fileName = file.getName();
            if(fileName.endsWith(".XML") || fileName.endsWith(".xml")){
                organizeXml(file, outPath);
            }
        }
    }

    public static void organizeXml(File file, String outPath){
        try{
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(file);

            ArrayList<ArrayList<ArrayList<Node>>> pageGroups = new ArrayList<ArrayList<ArrayList<Node>>>();

            NodeComparator comparator = new NodeComparator();

            Document newDoc = docBuilder.newDocument();
            Element root = newDoc.createElement("root");
            newDoc.appendChild(root);

            NodeList pages = doc.getElementsByTagName("page");
            for(int l = 0; l<pages.getLength() ; l++){
                //seperate wrongly clustered chunks
                ONE_SIXTH = (int)((1.0/6.0)*Integer.parseInt(pages.item(l).getAttributes().getNamedItem("height").getNodeValue()));
                splitChunks(pages.item(l), doc);

                //sort by top/bot
                ArrayList<Node> fontList = getChildrenByTagName(pages.item(l), "font");
                Collections.sort(fontList, comparator);

                //set starting columns of articles
                connectHeadline(fontList);

                //connect pieces
                ArrayList<ArrayList<Node>> groupList = new ArrayList<ArrayList<Node>>();
                connect(groupList, fontList, doc);
                //sort and connect distant parts
                for(int i = 0; i<groupList.size() ; i++){
                    Collections.sort(groupList.get(i), comparator);
                    /*
                    for(Node  n : groupList.get(i)){
                    System.out.println(n.getTextContent());
                    }
                    System.out.println("newarea");
                     */
                }

                //merge more distant chunks
                Node[] fontArr = fontList.toArray(new Node[fontList.size()]);
                for(int i = 0; i<groupList.size(); i++){
                    ArrayList<Node> list = groupList.get(i);
                    if(list!=null){
                        Node last = list.get(list.size()-1);
                        //if font > 20 do not merge
                        if(last.getAttributes().getNamedItem("incomplete").getNodeValue().equals("true") && Integer.parseInt(last.getAttributes().getNamedItem("size").getNodeValue())<MAX_FONT_SIZE){
                            //search list from original list
                            int index = Arrays.binarySearch(fontArr, last, comparator);
                            for(int j = index+1; j<fontArr.length; j++){
                                Node next = fontArr[j];
                                if(last.getAttributes().getNamedItem("size").getNodeValue().equals(next.getAttributes().getNamedItem("size").getNodeValue()) &&
                                last.getAttributes().getNamedItem("face").getNodeValue().equals(next.getAttributes().getNamedItem("face").getNodeValue())){
                                    ArrayList<Node> nextNodes = getChildrenByTagName(next, "text");
                                    int nextGroupIndex = Integer.parseInt(((Element)next).getAttribute("group"));
                                    boolean atBeginning = groupList.get(nextGroupIndex).get(0).isEqualNode(next) ? true : false;
                                    if(//Character.isLowerCase(nextNodes.get(0).getTextContent().trim().charAt(0)) && 
                                    atBeginning){
                                        //merge the sets.
                                        ArrayList<Node> nextGroup = groupList.get(nextGroupIndex);
                                        String newGroup = ((Element)last).getAttribute("group");
                                        for(int k = 0; k<nextGroup.size(); k++){
                                            ((Element)nextGroup.get(k)).setAttribute("group", newGroup);
                                        }
                                        list.addAll(nextGroup);
                                        groupList.set(nextGroupIndex, null);
                                        i--;
                                        //Collections.sort(groupList.get(i), comparator);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                //see if ends are incomplete
                for(int i = 0; i<groupList.size(); i++){
                    ArrayList<Node> list = groupList.get(i);
                    if(list!=null){
                        Node firstChunk = list.get(0);
                        ArrayList<Node> textsF = getChildrenByTagName(firstChunk, "text");

                        boolean hasLeftDir = false;
                        int index = Arrays.binarySearch(fontArr, firstChunk, comparator);
                        for(int j = 0; j<index; j++){
                            //is at most 3 fontsizes above and one line that contains num
                            Node direction = fontArr[j];
                            ArrayList<Node> text = getChildrenByTagName(direction, "text");
                            String line = text.get(0).getTextContent().trim();
                            if(text.size()==1 && line.length()>=3){
                                boolean isDir = false;
                                int[] lastChars = new int[]{(int)line.charAt(line.length()-1), (int)line.charAt(line.length()-2), (int)line.charAt(line.length()-3)};
                                for(int lastChar : lastChars){
                                    if(lastChar<58 && lastChar>47){
                                        isDir = true;
                                    }
                                }
                                if(isDir){
                                    hasLeftDir = incompleteConnection(direction, firstChunk);
                                    if(hasLeftDir){
                                        break;
                                    }
                                }
                            }
                        }

                        String first = textsF.get(0).getTextContent().trim();
                        if((startsIncomplete(first)&& first.length()>=MIN_DIR_LENGTH) || hasLeftDir){
                            ((Element)firstChunk).setAttribute("leftIncomplete", "true");
                        }
                        else{
                            ((Element)firstChunk).setAttribute("leftIncomplete", "false");
                        }

                        Node lastChunk = list.get(list.size()-1);
                        ArrayList<Node> textsL = getChildrenByTagName(lastChunk, "text");

                        boolean hasRightDir = false;
                        index = Arrays.binarySearch(fontArr, lastChunk, comparator);
                        for(int j = 0; j<index; j++){
                            Node direction = fontArr[j];
                            ArrayList<Node> text = getChildrenByTagName(direction, "text");
                            String line = text.get(0).getTextContent().trim();
                            if(text.size()==1 && line.length()>=3){
                                boolean isDir = false;
                                int[] lastChars = new int[]{(int)line.charAt(line.length()-1), (int)line.charAt(line.length()-2), (int)line.charAt(line.length()-3)};
                                for(int lastChar : lastChars){
                                    if(lastChar<58 && lastChar>47){
                                        isDir = true;
                                    }
                                }
                                if(isDir){
                                    hasRightDir = incompleteConnection(direction, lastChunk);
                                    if(hasRightDir){
                                        break;
                                    }
                                }
                            }
                        }

                        String last = textsL.get(textsL.size()-1).getTextContent().trim();
                        if((endsIncomplete(last) && last.length()>=MIN_DIR_LENGTH) || hasRightDir){
                            ((Element)lastChunk).setAttribute("rightIncomplete", "true");
                        }
                        else{
                            ((Element)lastChunk).setAttribute("rightIncomplete", "false");
                        }
                    }
                }

                pageGroups.add(groupList);
            }

            //move Nodes and merge incomplete articles across pages
            for(int i = 0; i<pageGroups.size(); i++){
                Element page = newDoc.createElement("page");
                root.appendChild(page);
                ArrayList<ArrayList<Node>> groupList = pageGroups.get(i);
                for(int j = 0; j<groupList.size(); j++){
                    ArrayList<Node> list = groupList.get(j);
                    if(list!=null){
                        NamedNodeMap lastAttrs = list.get(list.size()-1).getAttributes();
                        String rightInc = lastAttrs.getNamedItem("rightIncomplete").getNodeValue();
                        if(rightInc.equals("true")){
                            mergeIncomplete(pageGroups, list, i, lastAttrs.getNamedItem("face").getNodeValue(), lastAttrs.getNamedItem("size").getNodeValue());
                        }
                        for(int k = 0; k<list.size(); k++){
                            page.appendChild(newDoc.importNode(list.get(k), true));
                        }
                        page.appendChild(newDoc.createElement("break"));
                    }
                }
            }

            //write xml to file
            TransformerFactory tFactory = TransformerFactory.newInstance();
            Transformer transformer = tFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(newDoc);
            String oldName = file.getName();
            String newName = oldName.substring(0, oldName.length()-4)+"order.xml";
            StreamResult result = new StreamResult(new File(outPath+File.separator+newName));
            transformer.transform(source, result);
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    public static void splitChunks(Node page, Document doc){
        ArrayList<Node> fonts = getChildrenByTagName(page, "font");
        for(int i = 0; i<fonts.size(); i++){
            Node font = fonts.get(i);

            ArrayList<Node> texts = getChildrenByTagName(font, "text");
            ArrayList<Node> prevTexts = new ArrayList<Node>();

            boolean modified = false;

            int prevX = -10000;
            int prevFullX = -10000;
            int prevY = -10000;

            int minX = 10000;
            int maxX = -10000;

            int prevMinX = 10000;
            int prevMaxX = -10000;

            String minY = "";
            int fontSize = Integer.parseInt(font.getAttributes().getNamedItem("size").getNodeValue());
            for(int j = 0; j<texts.size(); j++){
                //check get attr
                Node text = texts.get(j);
                int x = Integer.parseInt(text.getAttributes().getNamedItem("x").getNodeValue());
                int y = Integer.parseInt(text.getAttributes().getNamedItem("y").getNodeValue());
                //use only if width is 70-80 of the projected amount
                int fullX;
                boolean ignore = Integer.parseInt(text.getAttributes().getNamedItem("width").getNodeValue())>2*(maxX-minX);
                if(ignore && prevX!=-10000){
                    fullX = prevFullX;
                }
                else{
                    fullX = (int)(x+Integer.parseInt(text.getAttributes().getNamedItem("width").getNodeValue()));
                }

                if(x<minX){
                    minX = x;
                }
                if(fullX>maxX){
                    maxX = fullX;
                }

                if(prevX==-10000){
                    prevX = x;
                    prevFullX = fullX;
                    prevY = y;

                    prevTexts.add(text);

                    prevMinX = minX;
                    prevMaxX = maxX;

                    minY = text.getAttributes().getNamedItem("y").getNodeValue();
                    continue;
                }
                //int prevY = Integer.parseInt(prevTexts.get(prevTexts.size()-1).getAttributes().getNamedItem("y").getNodeValue());
                if(prevY+(2*fontSize)>=y && prevY<=y && (prevX<=fullX && x<=prevFullX)){

                }
                else{
                    //Split Text
                    modified = true;
                    Element newFont = doc.createElement("font");
                    updateFont(font, newFont, prevTexts, minY, prevMinX, prevMaxX);
                    page.appendChild(newFont);

                    prevTexts.clear();

                    minX = x;
                    maxX = fullX;

                    minY = text.getAttributes().getNamedItem("y").getNodeValue();
                }
                prevX = x;
                prevFullX = fullX;
                prevY = y;

                prevTexts.add(text);

                prevMinX = minX;
                prevMaxX = maxX;
            }
            if(texts.size()>0){
                if(modified){
                    Element newFont = doc.createElement("font");
                    updateFont(font, newFont, prevTexts, minY, minX, maxX);
                    page.appendChild(newFont);
                    page.removeChild(font);
                }
                else{
                    updateFont(font, null, prevTexts, minY, minX, maxX);
                }
                prevTexts.clear();
            }
            else{
                page.removeChild(font);
            }
        }
    }

    public static Node updateFont(Node oldFont, Element newFont, ArrayList<Node> prevTexts, String minY, int minX, int maxX){
        Node lastText = prevTexts.get(prevTexts.size()-1);
        int maxY = Integer.parseInt(lastText.getAttributes().getNamedItem("y").getNodeValue());
        int fontSize = Integer.parseInt(oldFont.getAttributes().getNamedItem("size").getNodeValue());
        if(newFont != null){
            copyAttributes((Element)oldFont,newFont);
            for(Node prevText : prevTexts){
                newFont.appendChild(prevText);
            }
        }
        else{
            newFont = (Element)oldFont;
        }
        newFont.setAttribute("minX",""+minX);
        newFont.setAttribute("maxX",""+maxX);
        newFont.setAttribute("minY",minY);
        newFont.setAttribute("maxY",""+(maxY+fontSize));

        String incomplete = "";
        String lastString = lastText.getTextContent().trim();
        if(endsIncomplete(lastString)){
            incomplete = "true";
        }
        else{
            incomplete = "false";
        }
        newFont.setAttribute("incomplete", ""+incomplete);
        return newFont;
    }

    public static void connect(ArrayList<ArrayList<Node>> groupList, ArrayList<Node> fontList, Document doc){
        int index = 0;
        for(int i = 0; i<fontList.size(); i++){
            Node n = fontList.get(i);
            boolean added = false;
            for(int j = i+1; j<fontList.size(); j++){
                Node m = fontList.get(j);
                if(connection(n,m)){
                    if(((Element)n).getAttribute("face").equals(((Element)m).getAttribute("face")) && ((Element)n).getAttribute("size").equals(((Element)m).getAttribute("size"))){
                        boolean nHasGroup = ((Element)n).hasAttribute("group");
                        boolean mHasGroup = ((Element)m).hasAttribute("group");
                        if(mHasGroup && !nHasGroup){
                            int mIndex = Integer.parseInt(((Element)m).getAttribute("group"));
                            ((Element)n).setAttribute("group", mIndex+"");
                            groupList.get(mIndex).add(n);
                            added = true;
                        }
                        else if(!mHasGroup){
                            if(!nHasGroup){
                                //make new group
                                ((Element)n).setAttribute("group", index+"");
                                ArrayList<Node> temp = new ArrayList<Node>();
                                groupList.add(temp);
                                temp.add(n);

                                index++;
                            }
                            //add group num to m
                            int nIndex = Integer.parseInt(((Element)n).getAttribute("group"));
                            ((Element)m).setAttribute("group", nIndex+"");
                            groupList.get(nIndex).add(m);
                            added = true;
                        }
                    }
                    else{
                        //see if one word/ letter, can add and same x coord.
                        if(mergeLetter(n,m)){
                            break;
                        }
                    }
                }
                else{
                    //see if one word/ letter, can add and same x coord.
                    if(mergeLetter(n,m)){
                        break;
                    }
                }
            }
            if(!added && !((Element)n).hasAttribute("group")){
                ((Element)n).setAttribute("group", index+"");
                ArrayList<Node> temp = new ArrayList<Node>();
                groupList.add(temp);
                temp.add(n);

                index++;
            }
        }
    }

    private static boolean connection(Node n1, Node n2){
        NamedNodeMap n1a = n1.getAttributes();
        NamedNodeMap n2a = n2.getAttributes();

        int minY1 = Integer.parseInt(n1a.getNamedItem("minY").getNodeValue());
        int maxY1 = Integer.parseInt(n1a.getNamedItem("maxY").getNodeValue());
        int minX1 = Integer.parseInt(n1a.getNamedItem("minX").getNodeValue());
        int maxX1 = Integer.parseInt(n1a.getNamedItem("maxX").getNodeValue());

        int minY2 = Integer.parseInt(n2a.getNamedItem("minY").getNodeValue());
        int maxY2 = Integer.parseInt(n2a.getNamedItem("maxY").getNodeValue());
        int minX2 = Integer.parseInt(n2a.getNamedItem("minX").getNodeValue());
        int maxX2 = Integer.parseInt(n2a.getNamedItem("maxX").getNodeValue());

        int connectY = 2*Math.min(TEXT_CONNECT_MAX, Integer.parseInt(n1a.getNamedItem("size").getNodeValue()));
        //maybe check
        //int connectX = Math.min(TEXT_CONNECT_MAX, (int)Math.ceil(TEXT_CONNECT*Integer.parseInt(n1a.getNamedItem("size").getNodeValue())*CHAR_WIDTH_MULTIPLIER));
        int connectX = Math.min(3*TEXT_CONNECT_MAX, (maxX1-minX1)/3);
        boolean leftMerge = Boolean.parseBoolean(n2a.getNamedItem("leftMerge").getNodeValue());
        if(leftMerge){
            if((maxY1+connectY>=minY2 && maxY1-20<=minY2 && (minX1<=maxX2 && minX2<=maxX1)) || (maxX1+connectX>=minX2 && maxX1-20<=minX2 && (minY1<=maxY2 && minY2<=maxY1))){
                return true;
            }
        }
        else{
            if((maxY1+connectY>=minY2 && maxY1-20<=minY2 && (minX1<=maxX2 && minX2<=maxX1))){
                return true;
            }
        }

        return false;
    }

    public static void connectHeadline(ArrayList<Node> fontList){
        //set default to left merge
        for(int i = 0; i<fontList.size(); i++){
            ((Element)fontList.get(i)).setAttribute("leftMerge", "true");
        }

        for(int i = 0; i<fontList.size(); i++){
            Element headline = (Element)fontList.get(i);
            if(Integer.parseInt(headline.getAttribute("size"))>=MAX_FONT_SIZE){//correct size?
                System.out.print(headline.getTextContent());
                int minX1 = Integer.parseInt(headline.getAttribute("minX"));
                int maxY = Integer.parseInt(headline.getAttribute("maxY"));
                for(int j = 0; j<fontList.size(); j++){
                    if(j!=i){
                        Element leftCol = (Element)fontList.get(j);
                        int minX2 = Integer.parseInt(leftCol.getAttribute("minX"));
                        int minY = Integer.parseInt(leftCol.getAttribute("minY"));
                        if(minX2<minX1+10 && minX2>minX1-10 && maxY+ONE_SIXTH>=minY){
                            leftCol.setAttribute("leftMerge", "false");
                        }
                    }
                }
            }
        }
    }

    public static void mergeIncomplete(ArrayList<ArrayList<ArrayList<Node>>> pageGroup, ArrayList<Node> nodeList, int currentIndex, String font, String size){
        boolean found = false;
        int inc = 1;
        while(!found && currentIndex+inc<pageGroup.size()){
            ArrayList<ArrayList<Node>> groupList = pageGroup.get(currentIndex+inc);
            for(int i = 0; i<groupList.size(); i++){
                if(groupList.get(i) != null){
                    NamedNodeMap attrs = groupList.get(i).get(0).getAttributes();
                    String leftInc = attrs.getNamedItem("leftIncomplete").getNodeValue();
                    String otherFont = attrs.getNamedItem("face").getNodeValue();
                    String otherSize = attrs.getNamedItem("size").getNodeValue();
                    if(leftInc.equals("true") && font.equals(otherFont) && size.equals(otherSize)){
                        //merge lists
                        ArrayList<Node> nextGroup = groupList.get(i);
                        /* does this matter?
                        String newGroup = ((Element)last).getAttribute("group");
                        for(int k = 0; k<nextGroup.size(); k++){
                        ((Element)nextGroup.get(k)).setAttribute("group", newGroup);
                        }
                         */
                        nodeList.addAll(nextGroup);
                        groupList.set(i, null);
                        found = true;
                        break;
                    }
                }
            }
            inc++;
        }
        if(found){
            String rightInc = nodeList.get(nodeList.size()-1).getAttributes().getNamedItem("rightIncomplete").getNodeValue();
            if(rightInc.equals("true")){
                mergeIncomplete(pageGroup, nodeList, currentIndex+inc, font, size);
            }
        }
    }

    public static boolean mergeLetter(Node n, Node m){
        //see if one word/ letter, can add and same x coord.
        int x1 = Integer.parseInt(((Element)n).getAttribute("minX"));
        int x2 = Integer.parseInt(((Element)m).getAttribute("minX"));
        if((x1+TEXT_MERGE>x2 && x1-TEXT_MERGE<x2)){
            int y1 = Integer.parseInt(((Element)n).getAttribute("minY"));
            int y2 = Integer.parseInt(((Element)m).getAttribute("minY"));
            ArrayList<Node> nNodes = getChildrenByTagName(n, "text");
            ArrayList<Node> mNodes = getChildrenByTagName(m, "text");
            if((y1+TEXT_MERGE>y2 && y1-TEXT_MERGE<y2)&& nNodes.size()==1){
                String firstLine = nNodes.get(0).getTextContent();
                String[] spaceArr = firstLine.split(" ");
                if(spaceArr.length==1 && Character.isUpperCase(spaceArr[0].charAt(0))){
                    mNodes.get(0).setTextContent(firstLine.trim()+mNodes.get(0).getTextContent().trim());
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean incompleteConnection(Node n1, Node n2){
        NamedNodeMap n1a = n1.getAttributes();
        NamedNodeMap n2a = n2.getAttributes();

        int maxY1 = Integer.parseInt(n1a.getNamedItem("maxY").getNodeValue());
        int minX1 = Integer.parseInt(n1a.getNamedItem("minX").getNodeValue());
        int maxX1 = Integer.parseInt(n1a.getNamedItem("maxX").getNodeValue());

        int minY2 = Integer.parseInt(n2a.getNamedItem("minY").getNodeValue());
        int minX2 = Integer.parseInt(n2a.getNamedItem("minX").getNodeValue());
        int maxX2 = Integer.parseInt(n2a.getNamedItem("maxX").getNodeValue());

        int connectY = 4*Math.min(TEXT_CONNECT_MAX, Integer.parseInt(n2a.getNamedItem("size").getNodeValue()));

        if((maxY1+connectY>=minY2 && maxY1<=minY2 && (minX1<=maxX2 && minX2<=maxX1))){
            return true;
        }
        return false;
    }

    //helper methods
    public static ArrayList<Node> getChildrenByTagName(Node parent, String name) {
        ArrayList<Node> nodeList = new ArrayList<Node>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && 
            name.equals(child.getNodeName())) {
                nodeList.add((Node) child);
            }
        }

        return nodeList;
    }

    public static void copyAttributes(Element from, Element to) {
        NamedNodeMap attributes = from.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr node = (Attr) attributes.item(i);
            to.setAttribute(node.getName(), node.getValue());
        }
    }

    public static boolean startsIncomplete(String s){
        //spanish punctuation ? ! "
        return !s.startsWith("¿") && !s.startsWith("¡") && !s.startsWith("\"") || Character.isLowerCase(s.charAt(0));
    }

    public static boolean endsIncomplete(String s){
        return !s.endsWith(".") && !s.endsWith("?") && !s.endsWith("!") && !s.endsWith("\"");
    }
}
class NodeComparator implements Comparator<Node>{
    @Override
    public int compare(Node n1, Node n2){
        int n1x = Integer.parseInt(n1.getAttributes().getNamedItem("minX").getNodeValue());
        int n2x = Integer.parseInt(n2.getAttributes().getNamedItem("minX").getNodeValue());
        int n1y = Integer.parseInt(n1.getAttributes().getNamedItem("minY").getNodeValue());
        int n2y = Integer.parseInt(n2.getAttributes().getNamedItem("minY").getNodeValue());
        if(n1x != n2x){
            return n1x-n2x;
        }
        else{
            return n1y-n2y;
        }
    }
}
