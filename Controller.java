package SiteStats;

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextArea;
import org.apache.commons.csv.*;
import javafx.stage.FileChooser;
import javafx.stage.Popup;

import java.io.BufferedWriter;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;

import java.io.File;

public class Controller {

    public static String user      = "erik";
    public static String dbURL     = //REDACTED ON GITHUB;
    public static String password  = //REDACTED ON GITHUB;
    public static String port      = "5439";
    public static String dbName    = "ads";
    public static String queryFirst= "select\tsite_host as website,\n" +
            "\t\tcase when sum(case when sys_type = 'auction'\t\tthen 1 ELSE 0 end) > 0 then 1 else 0 end as hb,\n" +
            "\t\tcase when sum(case when sys_id ='s-dfp' \t\t\tthen 1 else 0 end) > 0 then 1 else 0 end as google,\n" +
            "\t\tcase when sum(case when sys_id ='a-casm' \t\tthen 1 else 0 end) > 0 then 1 else 0 end as indexExchange,\n" +
            "\t\tcase when sum(case when sys_id LIKE 'a-rp-%' \t\tthen 1 else 0 end) > 0 then 1 else 0 end as rubicon,\n" +
            "\t\tcase when sum(case when sys_id ='a-opnx' \t\tthen 1 else 0 end) > 0 then 1 else 0 end as openx,\n" +
            "\t\tcase when sum(case when sys_id ='a-amzn' \t\tthen 1 else 0 end) > 0 then 1 else 0 end as amazon,\n" +
            "\t\tcase when sum(case when sys_id ='a-apnx' OR sys_id = 'a-crit' OR sys_id = 'a-aol' OR sys_id = 'a-lijt' \n" +
            "\t\tOR sys_id = 'a-snbi' OR sys_id = 'w-districtm' OR sys_id = 'a-plse' OR sys_id = 'a-pbmt' \n" +
            "\t\tOR sys_id = 'a-cnvr' OR sys_id = 'a-fan' OR sys_id = 'a-r1' OR sys_id = 'a-3lift' \n" +
            "\t\tOR sys_id = 'a-yldb' OR sys_id = 'a-trustx' OR sys_id = 'a-kargo' OR sys_id = 'a-shrthru' \n" +
            "\t\tOR sys_id = 'w-mdnt' OR sys_id = 'a-jstprm' OR sys_id = 'a-komo' OR sys_id = 'a-sekindo' \n" +
            "\t\tOR sys_id = 'a-imprdig' \t\t\t\t\t\tthen 1 else 0 end) > 0 then 1 else 0 end as other,\n" +
            "\t\tcase when sum(case when sys_type = 'auction' \tthen 1 else 0 end) >= 9 then 1 else 0 end as fullstack\n" +
            "from\tsite_urls\n" +
            "where\tsys_id IS NOT null\n" +
            "AND \trun_id = ";
    public static String queryLast= "\n" +
            "group by \tsite_host;";
    public static String runIDQuery = "select \tdistinct run_id\n" +
            "from \tsite_urls\n" +
            "WHERE \tthe_time > '2018-04-27 01:00:00'\n" +
            "ORDER BY the_time;";
    public File selectedFile;
    public Popup popup = new Popup();
    public FileChooser.ExtensionFilter csvFilter = new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv");

    @FXML
    private volatile TextArea output;

    @FXML
    public ChoiceBox dropDown;

    /*
    * getRunIds()
    *
    * this method gets the current run IDs since "full batch with expert and second page 2"
    *
    * its flexible so it will include new runs as they occur*/

    public void getRunIds() {
        try(Connection connection = DriverManager.getConnection(
                Controller.dbURL + ":" + Controller.port + "/" + Controller.dbName,
                Controller.user,
                Controller.password)){
            System.out.println("connected");
            Statement statement = connection.createStatement(
                    ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY);


            ResultSet rs = statement.executeQuery(runIDQuery);
            updateDisplay("Query executed");


            while(rs.next()){
                dropDown.getItems().add(rs.getObject("run_id"));
                updateDisplay("'" + rs.getObject("run_id").toString() + "' added to choicebox");
            }
            dropDown.getSelectionModel().selectFirst();
            updateDisplay("choicebox added");

        }catch(SQLException s){
            s.printStackTrace();
        }
    }

    /*
    * updateDisplay()
    *
    * handles updating the TextArea and the Console with progress updates*/
    public void updateDisplay(String update){
        output.setText(output.getText() + update + "\n");
        System.out.println(update);
    }


    /*
    * openFileChooser()
    *
    * This is called by the "Pick a File" button in the FXML
    *
    * it opens a popup and sets the selectedFile variable
    *
    * and calls the getRunIds() method.
    */

    public void openFileChooser(){

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(csvFilter);
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home") + "\\Downloads"));
        selectedFile = fileChooser.showOpenDialog(popup);
        if (selectedFile != null){
            updateDisplay("File Selected:" + selectedFile.toString());
            getRunIds();
        }

    }

    /*
    * askTheDB()
    *
    * each step is documented with its own comments*/
    public void askTheDB() {

        //Gets the input from the dropdown and concatenates single quotes for the upcoming SQL
        String selectedRun = "'" + (String) dropDown.getValue() + "'";
        int finalRows = 0;
        //Checks to make sure a file was selected in the openFileChooser() method. If not, insults the user in the console :)
        if(selectedFile != null) {

            //Pops the 'save as' dialogue so the user can find the file.
            FileChooser saveAs = new FileChooser();
            saveAs.getExtensionFilters().add(csvFilter);
            saveAs.setInitialDirectory(new File(System.getProperty("user.home") + "\\Downloads"));
            File saveDirectory = saveAs.showSaveDialog(popup);

            //Attempts to start the CSVParser and the Database Connection
            try (Reader reader = Files.newBufferedReader(Paths.get(selectedFile.toString()));
                 CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT
                         .withFirstRecordAsHeader()
                         .withIgnoreHeaderCase()
                         .withTrim());

                 Connection connection = DriverManager.getConnection(
                         dbURL + ":" + port + "/" + dbName,
                         user,
                         password)) {

                updateDisplay("parser started");
                updateDisplay("Connected to DB");

                //Starts the CSVPrinter and initalizes the header and columns
                BufferedWriter writer = Files.newBufferedWriter(Paths.get(String.valueOf(saveDirectory)));
                CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT
                        .withHeader("Email", "First", "Last", "Company", "Website", "Monthly Traffic",
                                "Has HB", "Google", "Index", "Rubicon", "OpenX",
                                "Amazon", "Other Bidders", "Full Stack"));

                updateDisplay("writer started");

                //sets the permissions of the ResultSet before its retrieved. Read Only is selected to protect data integrity on the Database.
                // Scroll Sensitive is necessary to allow the reuse of the same ResultSet, and therefore fewer server calls.
                Statement statement = connection.createStatement(
                        ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY);

                //runs the SQL and stores the data in a ResultSet
                ResultSet resultSet = statement.executeQuery(queryFirst + selectedRun + queryLast);

                updateDisplay("Data received from AWS");

                //Loops through the INPUT CSV
                for (CSVRecord record : parser) {
                    String siteURL = record.get("website");
                    siteURL = siteNameFixer(siteURL);

                    //Reset the ResultSet cursor to before the first record
                    resultSet.beforeFirst();

                    //Loops through the ResultSet looking for matching URLs
                    while (resultSet.next()) {
                        //Skips null objects
                        if (resultSet.getObject("website") != null) {
                            //Skips non matches
                            if (resultSet.getObject("website").toString().equalsIgnoreCase(siteURL)) {
                                updateDisplay(siteURL + " found!");
                                updateDisplay("Writing " + siteURL + "'s data to csv");
                                printer.printRecord("", "", "", "", siteURL, "",
                                        resultSet.getObject("hb").toString(),
                                        resultSet.getObject("google").toString(),
                                        resultSet.getObject("indexexchange").toString(),
                                        resultSet.getObject("rubicon").toString(),
                                        resultSet.getObject("openx").toString(),
                                        resultSet.getObject("amazon").toString(),
                                        resultSet.getObject("other").toString(),
                                        resultSet.getObject("fullstack").toString());
                                finalRows++;
                                break;
                            }
                            //If the URL is not found, it is printed to the output CSV with NO DATA tags
                            if (resultSet.isAfterLast() || resultSet.isLast()) {
                                updateDisplay(siteURL + " NOT found :(");
                                printer.printRecord("NO DATA", "NO DATA", "NO DATA", "NO DATA", siteURL);
                            }
                        }
                    }
                }
                //Finalizes the CSV print
                printer.flush();
                updateDisplay("Finished. Results saved to " + saveDirectory + ".csv");
                updateDisplay("Final .csv contains " + finalRows + " found contacts");

            } catch (SQLException e) {
                updateDisplay("!_!_!_!Connection failure.!_!_!_!");
                e.printStackTrace();
            } catch (NullPointerException n) {
                updateDisplay("!_!_!_!Null pointer exception!_!_!_!");
                n.printStackTrace();
            } catch (Exception x) {
                updateDisplay("!_!_!_!Unidentified Exception!_!_!_!");
                x.printStackTrace();
            }

        }else{
            updateDisplay("nobody chose an input file?!?! AMATEURS!");
        }
    }



    /*
    * siteNameFixer()
    *
    * This gets around the different possible naming conventions of URLs
    *
    * and converts them to the format found in the DB
    *
    * PARAM: String of the input URL
    *
    * RETURNS: Normalized URL String
    * */
    public String siteNameFixer(String url) {
        String answer = url;

        if(url.length()>11){
            if (url.substring(0, 11).equalsIgnoreCase("http://www.")) {
                answer = url.substring(11);
            }
        }

        if(url.length()>4){
            if (url.substring(0,4).equalsIgnoreCase("www.")) {
                answer = url.substring(4);
            }
        }

        if(url.length()>7){
            if (url.substring(0,7).equalsIgnoreCase("http://")) {
                answer = url.substring(7);
            }
        }

        return answer;
    }
}
