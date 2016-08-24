Install pdf2xml. Download at launchpad.net/pdf2xml.
Download the PDFBox java library and add it to an IDE. I programmed using ver 2.0.2.
Create new java classes with the given java files.

When writing filepaths, use only the / operator, not \.

1.Run the program PDFFormatter. Use the arguments:
	I.Path containing pdf files.
	II. Path to write new pdf files.
2.Run the program RunApplication. Use the arguments:
	I.Path containing new pdf files from step 1.
	II.Path to folder where the application pdf2xml is located.
	III.Name of the application. (ex. pdf2xml.exe).
3.Run the program ParseXml. Use the arguments:
	I.Path to folder where the application pdf2xml is located.(Where the xml files are located).
	II.Path to write clean xml files.

The clean xml files contain the articles in spanish and the date.

Updates will come to the java extraction soon.
