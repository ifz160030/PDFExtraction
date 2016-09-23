Download the PDFExtraction.jar included in the repository.
Install pdf2xml. Download at launchpad.net/pdf2xml.
Download the PDFBox java library ver 2.0.2. and put it in the same folder as the PDFExtraction.jar.
Run the jar. When writing filepaths, use only the / character, not \.

Read on only if you intend to run one part of the java program.

1.Run the program PDFFormatter. Use the arguments:
	I.Path containing pdf files.
	II. Path to write new pdf files.
2.Run the program RunApplication. Use the arguments:
	I.Path containing new pdf files from step 1.
	II.Name of the application. (ex. pdf2xml.exe).
	4.Run the program XmlOrganizer. Use the arguments:
	I.Path to folder where the application pdf2xml is located.(Where the xml files are located).
	II.Path to write organize xml files.
4.Run the program ParseXml. Use the arguments:
	I.Path to folder containing the xml files from step 3.
	II.Path to write clean xml files.
