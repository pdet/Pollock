import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.*;
import com.opencsv.CSVParser;

/*
https://opencsv.sourceforge.net/apidocs/com/opencsv/CSVReader.html
CSVReader reader = new CSVReader(FileReader("yourfile.csv"), DEFAULT_SKIP_LINES,
new CSVParser(ICSVParser.DEFAULT_SEPARATOR,
        ICSVParser.DEFAULT_QUOTE_CHARACTER,
        ICSVParser.DEFAULT_ESCAPE_CHARACTER,
        ICSVParser.DEFAULT_STRICT_QUOTES,
        ICSVParser.DEFAULT_IGNORE_LEADING_WHITESPACE,
        ICSVParser.DEFAULT_IGNORE_QUOTATIONS,
        ICSVParser.DEFAULT_NULL_FIELD_INDICATOR,
        Locale.getDefault()),
DEFAULT_KEEP_CR,
DEFAULT_VERIFY_READER,
DEFAULT_MULTILINE_LIMIT,
Locale.getDefault(),
new LineValidatorAggregator(),
new RowValidatorAggregator(),
null);
*/


import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.json.JSONObject;

import static com.opencsv.CSVReader.DEFAULT_SKIP_LINES;

public class JCSV {

    public static final String dataset = System.getenv("DATASET");
    public static final String sut = "opencsv";
    public static final String IN_DIR = "/"+dataset+"/csv/";
    public static final String PARAM_DIR = "/"+dataset+"/parameters/";
    public static final String OUT_DIR = "/results/"+sut+"/"+dataset+"/loading/";
    public static final String TIME_DIR = "/results/"+sut+"/"+dataset;


    public static String processFile(File file, int i, int total) throws IOException {
        System.out.println("Processing file (" + i + "/" + total + ") " + file.getName());

        File outFile = new File(OUT_DIR + file.getName() + "_converted.csv");
        String fileTime = "\""+file.getName()+"\"";
        JSONObject sut_params = null;
        try {
            FileReader paramReader = new FileReader(PARAM_DIR + file.getName() + "_parameters.json");
            int paramChar = paramReader.read();
            String paramStr = "";
            while (paramChar != -1) {
                paramStr = paramStr + (char) paramChar;
                paramChar = paramReader.read();
            }
            sut_params = new JSONObject(paramStr);
        } catch (Exception e) {
            System.out.println("Could not read parameter file for " + file.getName());
        }

        String delim = (String) sut_params.get("delimiter");
        if (delim.equals("")) {
            delim = ",";
        }
        char delimiter = delim.charAt(0);

        String quo = (String) sut_params.get("quotechar");
        if (quo.equals("")) {
            quo = "\"";
        }
        char quote = quo.charAt(0);

        String esc = (String) sut_params.get("escapechar");
        if (esc.equals("")) {
            esc = "\"";
        }
        char escape = esc.charAt(0);
        ICSVParser parser = null;
        if (escape != quote){
            parser = new CSVParserBuilder()
                    .withSeparator(delimiter)
                    .withQuoteChar(quote)
                    .withEscapeChar(escape)
                    .withStrictQuotes(true)
                    .build();
        }
        else{
            parser = new RFC4180ParserBuilder()
                    .withSeparator(delimiter)
                    .withQuoteChar(quote)
                    .build();
        }
        String skiplines = (String) sut_params.get("preamble_lines");
        int skip_lines = (skiplines.equals("")) ? 0 : Integer.parseInt(skiplines);

        String encoding = ((String) sut_params.get("encoding")).toUpperCase();
        if (encoding.equals("ASCII")){
            encoding = "US-ASCII";
        }
        Charset charset = Charset.forName(encoding);

        for (int j = 0; j < 3; j++) {
            int n_rows = 0;
            double duration = 0;
            long startTime = System.nanoTime();
            try {
                FileReader fileReader = new FileReader(file, charset);
                CSVReader csvReader = new CSVReaderBuilder(fileReader)
                        .withCSVParser(parser)
                        .withSkipLines(skip_lines)
                        .build();

                List<String[]> rows = csvReader.readAll();
                long endTime = System.nanoTime();
                duration = (endTime - startTime) / 1000000000F;
                FileWriter fileWriter = new FileWriter(outFile);
                CSVWriter csvWriter = new CSVWriter(fileWriter);
                for (String[] r : rows) {
                    csvWriter.writeNext(r);
                    n_rows++;
                }
                csvWriter.flush();
            } catch (Exception e) {
                n_rows = 0;
                long endTime = System.nanoTime();
                duration = (endTime - startTime) / 1000000000F;
                FileWriter exceptionWriter = new FileWriter(outFile);
                exceptionWriter.write("Application Error\n" + e);
                exceptionWriter.close();
                if (j==0){
                System.out.println("Application Error: " + e);}
            }
            fileTime = fileTime + ("," + String.valueOf(duration));
        }
        return fileTime.substring(0, fileTime.length());
    }

    public static void processFiles() {
        ArrayList<File> files = new ArrayList<>(List.of(Objects.requireNonNull(new File(IN_DIR).listFiles())));
        int total = files.size();
        int i = 1;
        List<String> timeResults = new ArrayList<>();
        for (File f : files) {
            String outFilePath = OUT_DIR + f.getName() + "_converted.csv";
            File outF = new File(outFilePath);
            if (!outF.exists()) {
                try {
                    String strTime = processFile(f, i, total);
                    timeResults.add(strTime);
                } catch (IOException e) {
                    System.out.println("Fatal error occured on file " + f.getName() + "\n Could not write Output file");
                }
                i++;
            }
        }
        if (timeResults.size() == 0) {
            System.out.println("No files to process");
            return;
        }
        try {
            FileWriter timeWriter = new FileWriter(TIME_DIR + "/" + sut + "_time.csv");
            timeWriter.write("filename," + sut + "_time_0," + sut + "_time_1," + sut + "_time_2\n");
            for (String time : timeResults) {
                timeWriter.write(time + "\n");
            }
            timeWriter.close();
        } catch (IOException e) {
            System.out.println("Could not write time file");
        }
    }


    public static void main(String... args) {
        processFiles();
    }

}

