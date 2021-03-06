package SiteStats;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("layout.fxml"));

        primaryStage.setTitle("SiteStats v0.4");
        primaryStage.setScene(new Scene(root, 800, 700));
        primaryStage.getIcons().add(new Image("SiteStats/res/letter-p.png"));
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }



}
