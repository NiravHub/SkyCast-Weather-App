package oep.skycast;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import oep.skycast.ui.DashboardController;

public class Main extends Application {

    private DashboardController controller;

    @Override
    public void start(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
            Scene scene = new Scene(loader.load(), 1000, 600);

            // attach CSS if present (optional)
            try {
                scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            } catch (Exception ignored) {}

            controller = loader.getController();

            stage.setScene(scene);
            stage.setTitle("SkyCast - Weather App");
            stage.setOnCloseRequest(ev -> {
                if (controller != null) controller.shutdown();
            });
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() throws Exception {
        if (controller != null) controller.shutdown();
        super.stop();
    }

    public static void main(String[] args) {
        launch();
    }
}
