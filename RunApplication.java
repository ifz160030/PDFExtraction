]import java.io.*;

/**
 * Runs the application pdf2xml by passing in all PDF files from an input directory.
 * 
 * @author (Italo Zevallos) 
 * @version (11/04/16)
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
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    
                    InputStreamReader is = new  InputStreamReader(p.getInputStream());
                    BufferedReader br = new BufferedReader(is);
                    String lineRead;
                    while ((lineRead = br.readLine()) != null) {
                        // destroy line
                    }
                    p.waitFor();
                    is.close();
                    br.close();
                }
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }
}
