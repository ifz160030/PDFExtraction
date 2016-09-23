import java.io.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.Arrays;

/**
 * Organizes the xml output by pdf2xml and writes to an xml file.
 * 
 * @author (Italo Zevallos) 
 * @version (09/21/16)
 */
public class XmlOrganizer
{
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
            Document newDoc = docBuilder.newDocument();
            Element root = newDoc.createElement("root");
            newDoc.appendChild(root);
            NodeList pages = doc.getElementsByTagName("page");
            for(int l = 0; l<pages.getLength() ; l++){
                //Question, should we merge insignificant chunks? will be left
                //but not part bc of attributes. I think not
                //seperate small chunks, copy attributes
                splitChunks(pages.item(l), doc);
                //sort by top/bot
                ArrayList<Node> fontList = getChildrenByTagName(pages.item(l), "font");
                NodeComparator comparator = new NodeComparator();
                Collections.sort(fontList, comparator);
                //connect pieces
                ArrayList<ArrayList<Node>> groupList = new ArrayList<ArrayList<Node>>();
                connect(groupList, fontList, doc);
                //sort and connect distant parts
                for(int i = 0; i<groupList.size() ; i++){
                    /*
                    for(Node  n : groupList.get(i)){
                    System.out.println(n.getTextContent());
                    }
                    System.out.println("newarea");
                     */
                    Collections.sort(groupList.get(i), comparator);
                }

                Node[] fontArr = fontList.toArray(new Node[fontList.size()]);
                for(int i = 0; i<groupList.size(); i++){
                    ArrayList<Node> list = groupList.get(i);
                    if(list!=null){
                        Node last = list.get(list.size()-1);
                        if(last.getAttributes().getNamedItem("incomplete").getNodeValue().equals("true")){
                            //search list from original list
                            int index = Arrays.binarySearch(fontArr, last, comparator);
                            for(int j = index+1; j<fontArr.length; j++){
                                Node next = fontArr[j];
                                if(last.getAttributes().getNamedItem("size").getNodeValue().equals(next.getAttributes().getNamedItem("size").getNodeValue()) &&
                                last.getAttributes().getNamedItem("face").getNodeValue().equals(next.getAttributes().getNamedItem("face").getNodeValue())){
                                    //merge the sets.
                                    int group = Integer.parseInt(((Element)next).getAttribute("group"));
                                    ArrayList<Node> temp = groupList.get(group);
                                    String newGroup = ((Element)last).getAttribute("group");
                                    for(int k = 0; k<temp.size(); k++){
                                        ((Element)temp.get(k)).setAttribute("group", newGroup);
                                    }
                                    list.addAll(temp);
                                    groupList.set(group, null);
                                    Collections.sort(list, comparator);
                                    i--;
                                    break;
                                }
                            }
                        }
                    }
                }
                //attach to doc
                Element page = newDoc.createElement("page");
                root.appendChild(page);
                for(int i = 0; i<groupList.size(); i++){
                    ArrayList<Node> list = groupList.get(i);
                    if(list!=null){
                        for(int j = 0; j<list.size(); j++){
                            page.appendChild(newDoc.importNode(list.get(j), true));
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
            int minX = 10000;
            int maxX = -10000;
            int prevMinX = 10000;
            int prevMaxX = -10000;
            String minY = "";
            String maxY = "";
            for(int j = 0; j<texts.size(); j++){
                //check get attr
                Node text = texts.get(j);
                int x = Integer.parseInt(text.getAttributes().getNamedItem("x").getNodeValue());
                int fullX = x+Integer.parseInt(text.getAttributes().getNamedItem("width").getNodeValue());
                if(x<minX){
                    minX = x;
                }
                if(fullX>maxX){
                    maxX = fullX;
                }
                if(prevX==-10000){
                    prevX = x;
                    minY = text.getAttributes().getNamedItem("y").getNodeValue();
                    prevTexts.add(text);
                    prevMinX = minX;
                    prevMaxX = maxX;
                    continue;
                }
                if(x>=prevX-20 && x<=prevX+20){
                    prevX = x;
                    prevTexts.add(text);
                }
                else{
                    modified = true;
                    //merge code?
                    Node lastText = prevTexts.get(prevTexts.size()-1);
                    maxY = lastText.getAttributes().getNamedItem("y").getNodeValue();
                    Element newFont = doc.createElement("font");
                    copyAttributes((Element)font,newFont);
                    newFont.setAttribute("minX",""+prevMinX);
                    newFont.setAttribute("maxX",""+prevMaxX);
                    newFont.setAttribute("minY",minY);
                    newFont.setAttribute("maxY",maxY);
                    String incomplete = "";
                    String lastString = lastText.getTextContent().trim();
                    if(!lastString.endsWith(".")  && !lastString.endsWith("?") && !lastString.endsWith("!") && !lastString.endsWith("\"") || lastString.endsWith("-")){
                        incomplete = "true";
                    }
                    else{
                        incomplete = "false";
                    }
                    newFont.setAttribute("incomplete", ""+incomplete);

                    for(Node prevText : prevTexts){
                        newFont.appendChild(prevText);
                    }

                    page.appendChild(newFont);
                    prevTexts.clear();
                    minX = x;
                    maxX = fullX;

                    prevX = x;
                    prevTexts.add(text);
                    minY = text.getAttributes().getNamedItem("y").getNodeValue();
                }
                prevMinX = minX;
                prevMaxX = maxX;
            }
            if(texts.size()>0){
                if(modified){
                    //merge code?
                    Node lastText = prevTexts.get(prevTexts.size()-1);
                    maxY = lastText.getAttributes().getNamedItem("y").getNodeValue();

                    Element newFont = doc.createElement("font");
                    copyAttributes((Element)font,newFont);
                    newFont.setAttribute("minX",""+minX);
                    newFont.setAttribute("maxX",""+maxX);
                    newFont.setAttribute("minY",minY);
                    newFont.setAttribute("maxY",maxY);
                    String lastString = lastText.getTextContent().trim();
                    String incomplete = "";
                    if(!lastString.endsWith(".")  && !lastString.endsWith("?") && !lastString.endsWith("!") && !lastString.endsWith("\"") || lastString.endsWith("-")){
                        incomplete = "true";
                    }
                    else{
                        incomplete = "false";
                    }
                    newFont.setAttribute("incomplete", ""+incomplete);

                    for(Node prevText : prevTexts){
                        newFont.appendChild(prevText);
                    }
                    page.appendChild(newFont);
                    page.removeChild(font);
                }
                else{
                    Node lastText = prevTexts.get(prevTexts.size()-1);
                    maxY = lastText.getAttributes().getNamedItem("y").getNodeValue();
                    //change, maybe make new element?
                    ((Element)font).setAttribute("minX",""+minX);
                    ((Element)font).setAttribute("maxX",""+maxX);
                    ((Element)font).setAttribute("minY",minY);
                    ((Element)font).setAttribute("maxY",maxY);
                    String incomplete = "";
                    String lastString = lastText.getTextContent().trim();
                    if(!lastString.endsWith(".")  && !lastString.endsWith("?") && !lastString.endsWith("!") && !lastString.endsWith("\"") || lastString.endsWith("-")){
                        incomplete = "true";
                    }
                    else{
                        incomplete = "false";
                    }
                    ((Element)font).setAttribute("incomplete", ""+incomplete);
                }
                prevTexts.clear();
            }
            else{
                page.removeChild(font);
            }
        }
    }

    public static void connect(ArrayList<ArrayList<Node>> groupList, ArrayList<Node> fontList, Document doc){
        //why does 0 connect to all?
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
                        int x1 = Integer.parseInt(((Element)n).getAttribute("minX"));
                        int x2 = Integer.parseInt(((Element)m).getAttribute("minX"));
                        if(x1==x2){
                            int y1 = Integer.parseInt(((Element)n).getAttribute("minY"));
                            int y2 = Integer.parseInt(((Element)m).getAttribute("minY"));
                            ArrayList<Node> nNodes = getChildrenByTagName(n, "text");
                            ArrayList<Node> mNodes = getChildrenByTagName(m, "text");
                            if((y1+5>y2 && y1-5<y2)&& nNodes.size()==1){
                                String s = nNodes.get(0).getTextContent();
                                String[] a = s.split(" ");
                                if(a.length==1 && Character.isUpperCase(a[0].charAt(0))){
                                    mNodes.get(0).setTextContent(s.trim()+mNodes.get(0).getTextContent().trim());
                                }
                            }
                        }
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

        if((maxY1+9>=minY2 && maxY1<=minY2 && (minX1<=maxX2 && minX2<=maxX1)) || (maxX1+9>=minX2 && maxX1<=minX2 && (minY1<=maxY2 && minY2<=maxY1))){
            return true;
        }
        return false;
    }

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
