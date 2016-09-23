import java.io.*;

/**
 * Runs the application pdf2xml by passing in all PDF files from an input directory.
 * 
 * @author (Italo Zevallos) 
 * @version (09/21/16)
 */
public class RunApplication
{
    public static void main(String[] filePath){
        String inPath = filePath[0];
        String appName = filePath[1];
        String appPath = appName.substring(0,appName.lastIndexOf("/"));
        File inFolder = new File(inPath);
        File[] files = inFolder.listFiles();
        try{
            for(File file : files){
                String fileName = file.getName();
                if(fileName.endsWith(".PDF") || fileName.endsWith(".pdf")){
                    ProcessBuilder pb = new ProcessBuilder(appName, inPath+"/"+fileName);
                    pb.directory(new File(appPath));
                    pb.start().waitFor();
                }
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }
}
