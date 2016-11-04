import java.io.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Comparator;
import java.util.Collections;
import java.util.Arrays;

/**
 * Organizes the xml output by pdf2xml and writes to an xml file.
 * 
 * @author (Italo Zevallos) 
 * @version (11/04/16)
 */
public class XmlOrganizer
{
    //direction/incomplete chars for different languages
    //pictures
    //headline?
    final static List<String> COMPLETE_CHARACTERS = Arrays.asList(".", "!", "?", "\"", "¿", "¡");
    static NodeComparator comparator = new NodeComparator();
    final static int MAX_FONT_SIZE = 20;
    final static int TEXT_MERGE = 5;
    final static int TEXT_CONNECT_MAX = 40;
    final static int MIN_DIR_LENGTH = 5;
    final static int MERGE_LENGTH = 400;
    final static int MIN_HEADLINE_LENGTH = 8;
    
    static int ONE_FIFTH_Y = 1;
    static int ONE_SIXTH_X = 1;
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

            Document newDoc = docBuilder.newDocument();
            Element root = newDoc.createElement("root");
            newDoc.appendChild(root);

            NodeList pages = doc.getElementsByTagName("page");
            for(int l = 0; l<pages.getLength() ; l++){
                //set constant for 1/6 of the page
                ONE_FIFTH_Y = (int)((1.0/5.0)*Integer.parseInt(pages.item(l).getAttributes().getNamedItem("height").getNodeValue()));
                ONE_SIXTH_X = (int)((1.0/6.0)*Integer.parseInt(pages.item(l).getAttributes().getNamedItem("width").getNodeValue()));
                //seperate wrongly clustered chunks
                splitChunks(pages.item(l), doc);

                //sort by top/bot
                ArrayList<Node> fontList = getChildrenByTagName(pages.item(l), "font");
                Collections.sort(fontList, comparator);

                //set starting columns of articles
                connectHeadline(fontList);

                //prevent merges with two seperate articles by failure to guard
                secureColumns(fontList);

                //connect pieces
                ArrayList<ArrayList<Node>> groupList = new ArrayList<ArrayList<Node>>();
                connect(groupList, fontList, doc);

                //sort and connect distant parts
                for(int i = 0; i<groupList.size() ; i++){
                    ArrayList<Node> list = groupList.get(i);
                    if(list != null){
                        Collections.sort(list, comparator);
                        /*
                        for(Node  n : groupList.get(i)){
                        System.out.println(n.getTextContent());
                        }
                        System.out.println("newarea");
                         */
                    }
                }

                Node[] fontArr = fontList.toArray(new Node[fontList.size()]);

                //merge more distant chunks
                mergeDistant(groupList, fontArr);

                //merge chunks divided by section header
                mergeSections(groupList, fontArr);

                //see if ends are incomplete
                for(int i = 0; i<groupList.size(); i++){
                    ArrayList<Node> list = groupList.get(i);
                    if(list!=null){
                        Node firstChunk = list.get(0);
                        if(Integer.parseInt(((Element)firstChunk).getAttribute("size"))<MAX_FONT_SIZE){
                            ArrayList<Node> textsF = getChildrenByTagName(firstChunk, "text");

                            boolean hasLeftDir = false;
                            int index = Arrays.binarySearch(fontArr, firstChunk, comparator);
                            int minY = Integer.parseInt(((Element)firstChunk).getAttribute("minY"));
                            for(int j = 0; j<fontArr.length; j++){
                                //is at most 3 fontsizes above and one line that contains num
                                if(j!=index){
                                    Node direction = fontArr[j];
                                    if(minY>=Integer.parseInt(((Element)direction).getAttribute("minY")) && Integer.parseInt(((Element)direction).getAttribute("size"))<MAX_FONT_SIZE){
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
                                                hasLeftDir = connectionY(direction, firstChunk, Integer.parseInt(((Element)firstChunk).getAttribute("size")), 4);
                                                if(hasLeftDir){
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            String first = textsF.get(0).getTextContent().trim();
                            if((startsIncomplete(first)&& first.length()>=MIN_DIR_LENGTH && textsF.size()>1) || hasLeftDir){
                                ((Element)firstChunk).setAttribute("leftIncomplete", "true");
                            }
                            else{
                                ((Element)firstChunk).setAttribute("leftIncomplete", "false");
                            }
                        }
                        else{
                            ((Element)firstChunk).setAttribute("leftIncomplete", "false");
                        }

                        Node lastChunk = list.get(list.size()-1);
                        if(Integer.parseInt(((Element)lastChunk).getAttribute("size"))<MAX_FONT_SIZE){
                            ArrayList<Node> textsL = getChildrenByTagName(lastChunk, "text");

                            boolean hasRightDir = false;
                            int index = Arrays.binarySearch(fontArr, lastChunk, comparator);
                            int minY = Integer.parseInt(((Element)lastChunk).getAttribute("minY"));
                            for(int j = 0; j<fontArr.length; j++){
                                if(j!=index){
                                    Node direction = fontArr[j];
                                    if(minY<=Integer.parseInt(((Element)direction).getAttribute("minY")) && Integer.parseInt(((Element)direction).getAttribute("size"))<MAX_FONT_SIZE){
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
                                                hasRightDir = connectionY(lastChunk, direction, Integer.parseInt(((Element)lastChunk).getAttribute("size")), 4);
                                                if(hasRightDir){
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            String last = textsL.get(textsL.size()-1).getTextContent().trim();
                            if((endsIncomplete(last) && last.length()>=MIN_DIR_LENGTH && textsL.size()>1) || hasRightDir){
                                ((Element)lastChunk).setAttribute("rightIncomplete", "true");
                            }
                            else{
                                ((Element)lastChunk).setAttribute("rightIncomplete", "false");
                            }
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
                /*
                Element page = newDoc.createElement("page");
                root.appendChild(page);
                 */
                ArrayList<ArrayList<Node>> groupList = pageGroups.get(i);
                for(int j = 0; j<groupList.size(); j++){
                    ArrayList<Node> list = groupList.get(j);
                    if(list!=null){
                        NamedNodeMap lastAttrs = list.get(list.size()-1).getAttributes();
                        String rightInc = lastAttrs.getNamedItem("rightIncomplete").getNodeValue();//null
                        String font = lastAttrs.getNamedItem("face").getNodeValue();
                        if(font.indexOf("+")>=0){
                            font = font.substring(font.indexOf("+")+1);
                        }
                        if(rightInc.equals("true")){
                            mergeIncompleteArticle(pageGroups, list, i, font, lastAttrs.getNamedItem("size").getNodeValue());
                        }
                        /*
                        for(int k = 0; k<list.size(); k++){
                        page.appendChild(newDoc.importNode(list.get(k), true));
                        }
                        page.appendChild(newDoc.createElement("break"));
                         */
                    }
                }
            }

            //backwards merge articles
            ArrayDeque<Node> softIncomplete = new ArrayDeque<Node>();
            for(int i = 0; i<pageGroups.size(); i++){
                ArrayList<ArrayList<Node>> groupList = pageGroups.get(i);
                for(int j = 0; j<groupList.size(); j++){
                    ArrayList<Node> list = groupList.get(j);
                    if(list!=null){
                        Element first = (Element)list.get(0);
                        if(Boolean.parseBoolean(first.getAttribute("leftMerge")) && Integer.parseInt(first.getAttribute("minX"))<ONE_SIXTH_X){//one-sixth for each
                            //should be whole text content? check
                            String content = first.getTextContent().trim();
                            if(content.length()>MERGE_LENGTH){
                                first.setAttribute("page", i+"");
                                softIncomplete.addFirst(first);
                            }
                        }
                    }
                }
            }

            for(int i = pageGroups.size()-2; i>=0; i--){
                ArrayList<ArrayList<Node>> groupList = pageGroups.get(i);
                //get list of node i+1
                while(!softIncomplete.isEmpty()){
                    Element inc = (Element)softIncomplete.getFirst();
                    if(!inc.getAttribute("page").equals((i+1)+"")){
                        break;
                    }
                    inc = (Element)softIncomplete.removeFirst();
                    int min = 100000;
                    int maxY2 = Integer.parseInt(inc.getAttribute("maxY"));
                    int minPos = -1;
                    String size = inc.getAttribute("size");
                    String font = inc.getAttribute("face");
                    if(font.indexOf("+")>=0){
                        font = font.substring(font.indexOf("+")+1);
                    }
                    for(int j = 0; j<groupList.size(); j++){
                        ArrayList<Node> list = groupList.get(j);
                        if(list!=null){
                            Element last = (Element)list.get(list.size()-1);
                            //face!
                            String otherFont = last.getAttribute("face");
                            if(otherFont.indexOf("+")>=0){
                                otherFont = otherFont.substring(otherFont.indexOf("+")+1);
                            }
                            if(otherFont.equals(font) && last.getAttribute("size").equals(size) && 
                            !last.getAttribute("group").equals("-1") && !last.getAttribute("group").equals("-2")){//see if needs further to allow -1,-2
                                //check last text 
                                int minX1 = Integer.parseInt(last.getAttribute("minX"));
                                int maxY1 = Integer.parseInt(last.getAttribute("maxY"));
                                if(minX1>4*ONE_SIXTH_X){//make sure to get the 1/6 for each page!!
                                    int distance = Math.abs(maxY1-maxY2);
                                    if(min>distance){
                                        minPos = j;
                                        min = distance;
                                    }
                                }
                            }
                        }
                    }
                    if(minPos!=-1){
                        //merge lists
                        ArrayList<Node> newGroup = groupList.get(minPos);
                        int oldGroupNum = Integer.parseInt(((Element)inc).getAttribute("group"));
                        ArrayList<Node> oldGroup = pageGroups.get(i+1).get(oldGroupNum);
                        for(int k = 0; k<oldGroup.size(); k++){
                            ((Element)oldGroup.get(k)).setAttribute("group", "-2");
                        }
                        newGroup.addAll(oldGroup);
                        pageGroups.get(i+1).set(oldGroupNum, null);
                        break;
                    }
                }
                //WHEN two merge in one instance?
            }

            for(int i = 0; i<pageGroups.size(); i++){
                Element page = newDoc.createElement("page");
                root.appendChild(page);
                ArrayList<ArrayList<Node>> groupList = pageGroups.get(i);
                for(int j = 0; j<groupList.size(); j++){
                    ArrayList<Node> list = groupList.get(j);
                    if(list!=null){
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
                int fullX = (int)(x+Integer.parseInt(text.getAttributes().getNamedItem("width").getNodeValue()));

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
                        int mIndex = -1;
                        int nIndex = -1;
                        if(mHasGroup && !nHasGroup){
                            mIndex = Integer.parseInt(((Element)m).getAttribute("group"));
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
                            nIndex = Integer.parseInt(((Element)n).getAttribute("group"));
                            ((Element)m).setAttribute("group", nIndex+"");
                            groupList.get(nIndex).add(m);
                            added = true;
                        }
                        else if((nIndex = Integer.parseInt(((Element)n).getAttribute("group")))!=(mIndex = Integer.parseInt(((Element)m).getAttribute("group")))){
                            //merge groups
                            ArrayList<Node> mGroup = groupList.get(mIndex);
                            for(int a = 0; a<mGroup.size(); a++){
                                ((Element)mGroup.get(a)).setAttribute("group", nIndex+"");
                            }

                            groupList.get(nIndex).addAll(mGroup);
                            groupList.set(mIndex, null);
                            added = true;
                        }
                    }
                    else{
                        //see if one word/ letter, can add and same x coord.
                        if(mergeLetter(n,m) || mergeLetter(m,n)){
                            break;
                        }
                    }
                }
                else{
                    //see if one word/ letter, can add and same x coord.
                    if(mergeLetter(n,m) || mergeLetter(m,n)){
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
        int connectX = (int)Math.min(3*TEXT_CONNECT_MAX, (maxX1-minX1)/4.5);
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

    public static boolean connectionY(Node top, Node bot, int fontSize, int multiplier){
        NamedNodeMap n1a = top.getAttributes();
        NamedNodeMap n2a = bot.getAttributes();

        int maxY1 = Integer.parseInt(n1a.getNamedItem("maxY").getNodeValue());
        int minX1 = Integer.parseInt(n1a.getNamedItem("minX").getNodeValue());
        int maxX1 = Integer.parseInt(n1a.getNamedItem("maxX").getNodeValue());

        int minY2 = Integer.parseInt(n2a.getNamedItem("minY").getNodeValue());
        int minX2 = Integer.parseInt(n2a.getNamedItem("minX").getNodeValue());
        int maxX2 = Integer.parseInt(n2a.getNamedItem("maxX").getNodeValue());

        int connectY = multiplier*fontSize;
        if(maxY1+connectY>=minY2 && maxY1-20<=minY2 && (minX1<=maxX2 && minX2<=maxX1)){
            return true;
        }
        return false;
    }

    public static void mergeDistant(ArrayList<ArrayList<Node>> groupList, Node[] fontArr){
        //maybe no merge into noLeftMerge
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
                            int middleX1 = (int)(.5*(Integer.parseInt(last.getAttributes().getNamedItem("minX").getNodeValue())+Integer.parseInt(last.getAttributes().getNamedItem("maxX").getNodeValue())));
                            int minX2 = Integer.parseInt(next.getAttributes().getNamedItem("minX").getNodeValue());
                            int maxYDiff = Math.abs(Integer.parseInt(last.getAttributes().getNamedItem("maxY").getNodeValue())-Integer.parseInt(next.getAttributes().getNamedItem("maxY").getNodeValue()));
                            boolean canMerge = Boolean.parseBoolean(next.getAttributes().getNamedItem("leftMerge").getNodeValue()) || maxYDiff<=5;
                            if(middleX1<minX2 && canMerge){
                                ArrayList<Node> nextNodes = getChildrenByTagName(next, "text");
                                int nextGroupIndex = Integer.parseInt(((Element)next).getAttribute("group"));
                                boolean atBeginning = groupList.get(nextGroupIndex).get(0).isEqualNode(next) ? true : false;
                                if(atBeginning){
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
        }
    }

    public static void mergeSections(ArrayList<ArrayList<Node>> groupList, Node[] fontArr){
        for(int i = 0; i<groupList.size(); i++){
            ArrayList<Node> top = groupList.get(i);
            if(top!=null){
                //for all in top?
                Node one = top.get(top.size()-1);
                //if font > 20 do not merge
                if(Integer.parseInt(one.getAttributes().getNamedItem("size").getNodeValue())<MAX_FONT_SIZE){
                    //search list from original list
                    resetLoop:
                    for(int j = 0; j<groupList.size(); j++){
                        if(i!=j){
                            ArrayList<Node> bottom = groupList.get(j);
                            if(bottom!=null){
                                Node two = bottom.get(0);
                                if(Integer.parseInt(two.getAttributes().getNamedItem("size").getNodeValue())<MAX_FONT_SIZE){
                                    if(one.getAttributes().getNamedItem("size").getNodeValue().equals(two.getAttributes().getNamedItem("size").getNodeValue()) &&
                                    one.getAttributes().getNamedItem("face").getNodeValue().equals(two.getAttributes().getNamedItem("face").getNodeValue())){
                                        //check if nodes are within 6fs of each other. both sides
                                        for(int n = 0; n<top.size(); n++){
                                            Node last = top.get(n);
                                            int fontSize = Integer.parseInt(last.getAttributes().getNamedItem("size").getNodeValue());
                                            for(int m = 0; m<bottom.size(); m++){
                                                Node first = bottom.get(m);
                                                if(connectionY(last, first, fontSize, 6)){
                                                    //search through fontArr for connecting node
                                                    Node connect1 = last;
                                                    Node connect2 = first;
                                                    for(int l = 0; l<fontArr.length; l++){
                                                        Node connect = fontArr[l];
                                                        if(!last.getAttributes().getNamedItem("size").getNodeValue().equals(connect.getAttributes().getNamedItem("size").getNodeValue()) ||
                                                        !last.getAttributes().getNamedItem("face").getNodeValue().equals(connect.getAttributes().getNamedItem("face").getNodeValue())){
                                                            int height = Integer.parseInt(connect.getAttributes().getNamedItem("maxY").getNodeValue())-Integer.parseInt(connect.getAttributes().getNamedItem("minY").getNodeValue());
                                                            if(height<4*fontSize){
                                                                if(connectionY(last, connect, fontSize, 2)){
                                                                    connect1 = connect;
                                                                }
                                                            }
                                                        }
                                                    }
                                                    for(int f = 0; f<fontArr.length; f++){
                                                        Node connect = fontArr[f];
                                                        if(!first.getAttributes().getNamedItem("size").getNodeValue().equals(connect.getAttributes().getNamedItem("size").getNodeValue()) ||
                                                        !first.getAttributes().getNamedItem("face").getNodeValue().equals(connect.getAttributes().getNamedItem("face").getNodeValue())){
                                                            int height = Integer.parseInt(connect.getAttributes().getNamedItem("maxY").getNodeValue())-Integer.parseInt(connect.getAttributes().getNamedItem("minY").getNodeValue());
                                                            if(height<4*fontSize){
                                                                if(connectionY(connect, first, fontSize, 2)){
                                                                    connect2 = connect;
                                                                }
                                                            }
                                                        }
                                                    }
                                                    if(connect1.isSameNode(connect2)){
                                                        //merge the sets.
                                                        int nextGroupIndex = Integer.parseInt(((Element)first).getAttribute("group"));
                                                        String newGroup = ((Element)last).getAttribute("group");

                                                        for(int k = 0; k<bottom.size(); k++){
                                                            ((Element)bottom.get(k)).setAttribute("group", newGroup);
                                                        }
                                                        /*
                                                        //change addition
                                                        for(int k = 0; k<m; k++){
                                                        top.add(n, bottom.get(k));
                                                        }

                                                        for(int k = m; k<bottom.size(); k++){
                                                        top.add(n+1, bottom.get(k));
                                                        }
                                                         */
                                                        top.addAll(bottom);
                                                        groupList.set(nextGroupIndex, null);
                                                        i--;

                                                        Collections.sort(top, comparator);
                                                        break resetLoop;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static void secureColumns(ArrayList<Node> fontList){
        for(int i = 0; i<fontList.size(); i++){
            Element node = (Element)fontList.get(i);
            if(!Boolean.parseBoolean(node.getAttribute("leftMerge"))){
                int fontSize = Integer.parseInt(node.getAttribute("size"));
                for(int j = 0; j<fontList.size(); j++){
                    if(i!=j){
                        Element below = (Element)fontList.get(j);
                        if(node.getAttribute("face").equals(below.getAttribute("face")) && node.getAttribute("size").equals(below.getAttribute("size"))){
                            if(connectionY((Node)node, (Node)below, fontSize, 2)){
                                ((Element)fontList.get(j)).setAttribute("leftMerge","false");
                            }
                        }
                    }
                }
                Element connected = null;
                for(int j = 0; j<fontList.size(); j++){
                    Element connect = (Element)fontList.get(j);
                    if(!node.getAttribute("size").equals(connect.getAttribute("size")) ||
                    !node.getAttribute("face").equals(connect.getAttribute("face"))){
                        int height = Integer.parseInt(connect.getAttributes().getNamedItem("maxY").getNodeValue())-Integer.parseInt(connect.getAttributes().getNamedItem("minY").getNodeValue());
                        if(height<4*fontSize){
                            if(connectionY((Node)node, (Node)connect, fontSize, 2)){
                                connected = connect;
                            }
                        }
                    }
                }
                if(connected!=null){
                    for(int k = 0; k<fontList.size(); k++){
                        Element section = (Element)fontList.get(k);
                        if(i!=k){
                            if(node.getAttribute("size").equals(section.getAttribute("size")) &&
                            node.getAttribute("face").equals(section.getAttribute("face"))){
                                if(connectionY((Node)connected, (Node)section, fontSize, 2)){
                                    section.setAttribute("leftMerge", "false");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static void connectHeadline(ArrayList<Node> fontList){
        //set default to left merge
        for(int i = 0; i<fontList.size(); i++){
            ((Element)fontList.get(i)).setAttribute("leftMerge", "true");
        }

        for(int i = 0; i<fontList.size(); i++){
            Element headline = (Element)fontList.get(i);
            if(Integer.parseInt(headline.getAttribute("size"))>=MAX_FONT_SIZE){//correct size?
                String text = "";
                ArrayList<Node> texts = getChildrenByTagName((Node)headline, "text");
                for(int t = 0; t<texts.size(); t++){
                    text += texts.get(t).getTextContent().trim();
                }
                boolean uppercase = Character.isUpperCase(text.charAt(0));
                boolean letter = Character.isLetter(text.charAt(0));
                if(text.length()>MIN_HEADLINE_LENGTH && ((uppercase && letter) || (!uppercase && !letter))){
                    int minX1 = Integer.parseInt(headline.getAttribute("minX"));
                    int maxY = Integer.parseInt(headline.getAttribute("maxY"));
                    for(int j = 0; j<fontList.size(); j++){
                        if(j!=i){
                            Element leftCol = (Element)fontList.get(j);
                            int minX2 = Integer.parseInt(leftCol.getAttribute("minX"));
                            int minY = Integer.parseInt(leftCol.getAttribute("minY"));
                            if(minX2<minX1+10 && minX2>minX1-10 && maxY+ONE_FIFTH_Y>=minY && maxY<=minY){
                                leftCol.setAttribute("leftMerge", "false");
                            }
                        }
                    }
                }
            }
        }
    }

    public static void mergeIncompleteArticle(ArrayList<ArrayList<ArrayList<Node>>> pageGroup, ArrayList<Node> nodeList, int currentIndex, String font, String size){
        boolean found = false;
        int inc = 1;
        while(!found && currentIndex+inc<pageGroup.size()){
            ArrayList<ArrayList<Node>> groupList = pageGroup.get(currentIndex+inc);
            for(int i = 0; i<groupList.size(); i++){
                ArrayList<Node> nextGroup = groupList.get(i);
                if(nextGroup != null){
                    NamedNodeMap attrs = nextGroup.get(0).getAttributes();
                    String leftInc = attrs.getNamedItem("leftIncomplete").getNodeValue();
                    String otherFont = attrs.getNamedItem("face").getNodeValue();
                    if(otherFont.indexOf("+")>=0){
                        otherFont = otherFont.substring(otherFont.indexOf("+")+1);
                    }
                    String otherSize = attrs.getNamedItem("size").getNodeValue();
                    if(leftInc.equals("true") && font.equals(otherFont) && size.equals(otherSize)){
                        //merge lists
                        //String newGroup = ((Element)last).getAttribute("group");
                        for(int k = 0; k<nextGroup.size(); k++){
                            ((Element)nextGroup.get(k)).setAttribute("group", "-1");
                        }
                        ((Element)nodeList.get(nodeList.size()-1)).setAttribute("rightIncomplete", "merged");
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
                mergeIncompleteArticle(pageGroup, nodeList, currentIndex+inc, font, size);
            }
        }
    }

    public static boolean mergeLetter(Node n, Node m){
        //see if one word/ letter, can add and same x coord.
        int x1 = Integer.parseInt(((Element)n).getAttribute("minX"));
        int x2 = Integer.parseInt(((Element)m).getAttribute("minX"));
        int connect = 2*TEXT_MERGE;
        if((x1+connect>=x2 && x1-connect<=x2)){
            int y1 = Integer.parseInt(((Element)n).getAttribute("minY"));
            int y2 = Integer.parseInt(((Element)m).getAttribute("minY"));
            ArrayList<Node> nNodes = getChildrenByTagName(n, "text");
            ArrayList<Node> mNodes = getChildrenByTagName(m, "text");
            if((y1+connect>=y2 && y1-connect<=y2)&& nNodes.size()==1){
                String firstLine = nNodes.get(0).getTextContent().trim();
                String[] spaceArr = firstLine.split(" ");
                if(spaceArr.length==1 && Character.isUpperCase(spaceArr[0].charAt(0)) && firstLine.length()==1){
                    mNodes.get(0).setTextContent(firstLine.trim()+mNodes.get(0).getTextContent().trim());
                    return true;
                }
            }
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
        return !s.startsWith("¿") && !s.startsWith("¡") && !s.startsWith("\"") && s.endsWith("“")|| Character.isLowerCase(s.charAt(0));
    }

    public static boolean endsIncomplete(String s){
        return !s.endsWith(".") && !s.endsWith("?") && !s.endsWith("!") && !s.endsWith("\"") && !s.endsWith("”");
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
