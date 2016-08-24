import java.io.*;

/**
 * Write a description of class RunApplication here.
 * 
 * @author (your name) 
 * @version (a version number or a date)
 */
public class RunApplication
{
    public static void main(String[] filePath){
        String inPath = filePath[0];
        String appPath = filePath[1];
        String appName = filePath[2];
        File inFolder = new File(inPath);
        File[] files = inFolder.listFiles();
        try{
            for(File file : files){
                String fileName = file.getName();
                if(fileName.endsWith(".PDF") || fileName.endsWith(".pdf")){
                    ProcessBuilder pb = new ProcessBuilder(appPath+"/"+appName, inPath+"/"+fileName);
                    pb.directory(new File(appPath));
                    pb.start();
                }
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }
}
