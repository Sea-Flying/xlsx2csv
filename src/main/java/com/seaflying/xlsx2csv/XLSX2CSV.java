package com.seaflying.xlsx2csv;

import java.io.BufferedReader;

/* ====================================================================
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==================================================================== */



import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.InputStreamReader;

import org.apache.commons.io.input.ReaderInputStream;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.SAXHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.extractor.XSSFEventBasedExcelExtractor;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**  
* A rudimentary XLSX -> CSV processor modeled on the
* POI sample program XLS2CSVmra from the package
* org.apache.poi.hssf.eventusermodel.examples.
* As with the HSSF version, this tries to spot missing
*  rows and cells, and output empty entries for them.
* <p>
* Data sheets are read using a SAX parser to keep the
* memory footprint relatively small, so this should be
* able to read enormous workbooks.  The styles table and
* the shared-string table must be kept in memory.  The
* standard POI styles table class is used, but a custom
* (read-only) class is used for the shared string table
* because the standard POI SharedStringsTable grows very
* quickly with the number of unique strings.
* <p>
* For a more advanced implementation of SAX event parsing
* of XLSX files, see {@link XSSFEventBasedExcelExtractor}
* and {@link XSSFSheetXMLHandler}. Note that for many cases,
* it may be possible to simply use those with a custom 
* {@link SheetContentsHandler} and no SAX code needed of
* your own!
*/
public class XLSX2CSV {
 /**
  * Uses the XSSF Event SAX helpers to do most of the work
  *  of parsing the Sheet XML, and outputs the contents
  *  as a (basic) CSV.
  */
 private class SheetToCSV implements SheetContentsHandler {
     private boolean firstCellOfRow;
     private int currentRow = -1;
     private int currentCol = -1;
     
     private void outputMissingRows(int number) {
         for (int i=0; i<number; i++) {
             for (int j=0; j<minColumns; j++) {
                 output.append(divStr);
             }
             output.append('\n');
         }
     }

     @Override
     public void startRow(int rowNum) {
         // If there were gaps, output the missing rows
         outputMissingRows(rowNum-currentRow-1);
         // Prepare for this row
         firstCellOfRow = true;
         currentRow = rowNum;
         currentCol = -1;
     }

     @Override
     public void endRow(int rowNum) {
         // Ensure the minimum number of columns
         for (int i=currentCol; i<minColumns; i++) {
             output.append(divStr);
         }
         output.append('\n');
     }

     @Override
     public void cell(String cellReference, String formattedValue,
             XSSFComment comment) {
         if (firstCellOfRow) {
             firstCellOfRow = false;
         } else {
             output.append(divStr);
         }

         // gracefully handle missing CellRef here in a similar way as XSSFCell does
         if(cellReference == null) {
             cellReference = new CellAddress(currentRow, currentCol).formatAsString();
         }

         // Did we miss any cells?
         int thisCol = (new CellReference(cellReference)).getCol();
         int missedCols = thisCol - currentCol - 1;
         for (int i=0; i<missedCols; i++) {
             output.append(divStr);
         }
         currentCol = thisCol;
         // Number or string?
//         try {
//             //noinspection ResultOfMethodCallIgnored
//             Double.parseDouble(utf8Value);
//             output.append(utf8Value);
//         } catch (NumberFormatException e) {
//             output.append('"');
             output.append(formattedValue);
//             output.append('"');
//         }
     }

		@Override
		public void headerFooter(String arg0, boolean arg1, String arg2) {
			// TODO Auto-generated method stub
			
		}
 }


 ///////////////////////////////////////

 private final OPCPackage xlsxPackage;

 /**
  * Number of columns to read starting with leftmost
  */
 private final int minColumns;

 /**
  * Destination for data
  */
 private PrintStream output;


 private int startColumn;
 
 private int startRow;
 
 private String divStr = "\001";
 
 
 
 /**
  * Creates a new XLSX -> CSV converter
  *
  * @param pkg        The XLSX package to process
  * @param output     The PrintStream to output the CSV to
  * @param minColumns The minimum number of columns to output, or -1 for no minimum
  */
 public XLSX2CSV(OPCPackage pkg, String divide, int minColumns) {
     this.xlsxPackage = pkg;
     this.divStr = divide;
     this.minColumns = minColumns;
 }

 /**
  * Parses and shows the content of one sheet
  * using the specified styles and shared-strings tables.
  *
  * @param styles The table of styles that may be referenced by cells in the sheet
  * @param strings The table of strings that may be referenced by cells in the sheet
  * @param sheetInputStream The stream to read the sheet-data from.

  * @exception java.io.IOException An IO exception from the parser,
  *            possibly from a byte stream or character stream
  *            supplied by the application.
  * @throws SAXException if parsing the XML data fails.
  */
 public void processSheet(
         StylesTable styles,
         ReadOnlySharedStringsTable strings,
         SheetContentsHandler sheetHandler, 
         InputStream sheetInputStream) throws IOException, SAXException {
     DataFormatter formatter = new DataFormatter() {
         @Override
         public String formatRawCellContents(double value, int formatIndex, String formatString, boolean use1904Windowing) {
             if ("m/d/yy".equals(formatString)) formatString = "yyyy-mm-dd";
             return super.formatRawCellContents(value, formatIndex, formatString, use1904Windowing);
         }
     };
     InputSource sheetSource = new InputSource(sheetInputStream);
     try {
         XMLReader sheetParser = SAXHelper.newXMLReader();
         ContentHandler handler = new XSSFSheetXMLHandler(
               styles, null, strings, sheetHandler, formatter, false);
         sheetParser.setContentHandler(handler);
         sheetParser.parse(sheetSource);
      } catch(ParserConfigurationException e) {
         throw new RuntimeException("SAX parser appears to be broken - " + e.getMessage());
      }
 }

 /**
  * Initiates the processing of the XLS workbook file to CSV.
  *
  * @throws IOException If reading the data from the package fails.
  * @throws SAXException if parsing the XML data fails.
  */
 public void process(String outputFile) throws IOException, OpenXML4JException, SAXException {
 	FileOutputStream out=new FileOutputStream(outputFile);
 	output = new PrintStream(out);
 	ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(this.xlsxPackage);
     XSSFReader xssfReader = new XSSFReader(this.xlsxPackage);
     StylesTable styles = xssfReader.getStylesTable();
     XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
    // int index = 0;
     while (iter.hasNext()) {
         try (InputStream stream = iter.next()) {
//        		String sheetName = iter.getSheetName();
//          	this.output.println();
//          	this.output.println(sheetName + " [index=" + index + "]:");
        	processSheet(styles, strings, new SheetToCSV(), stream);
         }
        // ++index;
     }
 }

 public static void main(String[] args) throws Exception {
     if (args.length < 1) {
         System.err.println("Use:");
         System.err.println(" java -jar XLSX2CSV.jar  <\"xlsx file path\">  <\"output file path\"> [\"divide string\"] [min columns]");
         return;
     }
 	//输入文件地址
     File xlsxFile = new File(args[0]);
     if (!xlsxFile.exists()) {
         System.err.println("Not found or not a file: " + xlsxFile.getPath());
         return;
     }

     int minColumns = -1;
     if (args.length >= 4)
         minColumns = Integer.parseInt(args[3]);
     
     String divStr = "\001";
     if (args.length >= 3)
         divStr = args[2];
     // The package open is instantaneous, as it should be.
     try (OPCPackage p = OPCPackage.open(xlsxFile.getPath(), PackageAccess.READ)) {
         XLSX2CSV xlsx2csv = new XLSX2CSV(p, divStr, minColumns);
         //输出文件地址
         String outputFile = args[1];
         xlsx2csv.process(outputFile);
         System.out.print("{\"code\":\"0\", \"file\":\""+outputFile+"\"}");
     }catch (Exception e) {
     	System.out.print("{\"code\":\"1\"}");
	 }
 }
}
