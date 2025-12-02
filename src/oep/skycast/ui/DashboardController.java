package oep.skycast.ui;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import oep.skycast.exceptions.WeatherException;
import oep.skycast.model.ForecastDay;
import oep.skycast.model.HourlyWeather;
import oep.skycast.model.WeatherData;
import oep.skycast.service.FileWeatherProvider;
import oep.skycast.service.GeocodeService;
import oep.skycast.service.WeatherProvider;
import oep.skycast.util.FileUtil;
import oep.skycast.util.PrefsUtil;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * DashboardController - main UI controller for SkyCast.
 * Updated: suggestion dropdown colors + defensive wiring for buttons.
 */
public class DashboardController {

    // ---------- FXML nodes ----------
    @FXML private TextField cityInput;
    @FXML private ListView<String> favoritesList;
    @FXML private FlowPane forecastPane;
    @FXML private Label tempLabel;
    @FXML private Label conditionLabel;
    @FXML private Label humidityLabel;
    @FXML private Label windLabel;
    @FXML private Label feelsLikeLabel;
    @FXML private Label pressureLabel;
    @FXML private Label visibilityLabel;
    @FXML private Label uvLabel;
    @FXML private Label cloudLabel;

    @FXML private ToggleButton autoRefreshToggle;
    @FXML private Label lastUpdatedLabel;
    @FXML private ImageView iconView;
    @FXML private ProgressIndicator loadingSpinner;

    @FXML private LineChart<String, Number> tempChart;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis yAxis;

    // NEW UI fields
    @FXML private ComboBox<String> locationsDropdown;
    @FXML private Label locationLabel;
    @FXML private Label coordsLabel;
    @FXML private Label sunriseLabel;
    @FXML private Label sunsetLabel;
    @FXML private Label moonLabel;
    @FXML private Label aqiLabel;

    @FXML private LineChart<String, Number> hourlyChart;
    @FXML private CategoryAxis hourlyXAxis;
    @FXML private NumberAxis hourlyYAxis;

    @FXML private VBox currentWeatherBox;
    @FXML private Button saveSnapshotBtn;
    @FXML private Button shareBtn;

    @FXML private TextArea moreDetailsArea;

    // Added controls (wire in FXML)
    @FXML private Button removeFavBtn;
    @FXML private Button themeToggleBtn;
    @FXML private Button addFavBtn;

    // spinner for refresh interval (seconds)
    @FXML private Spinner<Integer> refreshIntervalSpinner;

    private WeatherProvider provider;
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Auto-refresh scheduler
    private ScheduledExecutorService scheduler;

    // default auto-refresh interval (seconds)
    private static final int DEFAULT_REFRESH_SECONDS = 600;

    // ----------------- Geocode/autocomplete fields -----------------
    private final GeocodeService geocodeService = new GeocodeService();
    private final ScheduledThreadPoolExecutor debounceExec = new ScheduledThreadPoolExecutor(1);
    private ScheduledFuture<?> debounceFuture = null;
    private final Map<String, GeocodeService.DisplayPlace> suggestionMap = new HashMap<>();
    private GeocodeService.DisplayPlace selectedPlace = null; // set when user picks a suggestion

    // ContextMenu for inline suggestions under cityInput
    private final ContextMenu suggestionMenu = new ContextMenu();

    // Keep last saved snapshot file for quick share/open
    private File lastSavedSnapshot = null;

    // ----------------- INITIALIZE -----------------
    @FXML
    public void initialize() {
        initProvider();

        // Ensure button classes are present (if FXML missed them)
        if (addFavBtn != null) {
            if (!addFavBtn.getStyleClass().contains("small-button")) addFavBtn.getStyleClass().add("small-button");
        }
        if (removeFavBtn != null) {
            if (!removeFavBtn.getStyleClass().contains("small-button")) removeFavBtn.getStyleClass().add("small-button");
            if (!removeFavBtn.getStyleClass().contains("destructive")) removeFavBtn.getStyleClass().add("destructive");
            if (removeFavBtn.getOnAction() == null) removeFavBtn.setOnAction(e -> handleRemoveFavorite());
        }

        if (themeToggleBtn != null) {
            // programmatic tooltip to avoid FXML coercion error
            if (themeToggleBtn.getTooltip() == null) themeToggleBtn.setTooltip(new Tooltip("Toggle theme"));
            themeToggleBtn.setOnAction(e -> toggleTheme());
            if (!themeToggleBtn.getStyleClass().contains("icon-button")) themeToggleBtn.getStyleClass().add("icon-button");
        }

        // favorites load
        try { if (favoritesList != null) favoritesList.getItems().addAll(FileUtil.loadFavorites()); } catch (IOException ignored) {}

        // double click favorite -> search & delete with DEL
        if (favoritesList != null) {
            favoritesList.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    String city = favoritesList.getSelectionModel().getSelectedItem();
                    if (city != null) {
                        cityInput.setText(city);
                        selectedPlace = null; // manual selection by text
                        handleSearch();
                    }
                }
            });

            favoritesList.setOnKeyPressed(ev -> {
                if (ev.getCode() == KeyCode.DELETE) {
                    String sel = favoritesList.getSelectionModel().getSelectedItem();
                    if (sel != null) {
                        favoritesList.getItems().remove(sel);
                        try { FileUtil.saveFavorites(favoritesList.getItems()); } catch (IOException ignored) {}
                    }
                }
            });
        }

        // Keep locationsDropdown for recents/favorites but inline suggestions show under cityInput
        if (locationsDropdown != null) {
            locationsDropdown.setEditable(true);
            locationsDropdown.setVisibleRowCount(6);
            // populate recents
            try {
                List<String> recent = new ArrayList<>();
                if (favoritesList != null) recent.addAll(favoritesList.getItems());
                String last = FileUtil.loadLastCity();
                if (last != null && !last.isBlank() && !recent.contains(last)) recent.add(0, last);
                if (!recent.isEmpty()) locationsDropdown.getItems().addAll(recent);
            } catch (Exception ignored) {}

            locationsDropdown.setOnAction(ev -> {
                String text = locationsDropdown.getEditor().getText();
                if (text == null || text.isBlank()) return;
                cityInput.setText(text.trim());
                selectedPlace = null;
                handleSearch();
            });
        }

        // ----------------- Autocomplete (cityInput) using ContextMenu -----------------
        if (cityInput != null) {
            cityInput.textProperty().addListener((obs, oldV, newV) -> {
                // user typed -> clear selectedPlace since typed text overrides previous pick
                selectedPlace = null;

                // cancel previous scheduled task
                if (debounceFuture != null && !debounceFuture.isDone()) debounceFuture.cancel(false);

                final String q = (newV == null) ? "" : newV.trim();
                if (q.isEmpty()) {
                    Platform.runLater(() -> {
                        suggestionMap.clear();
                        suggestionMenu.hide();
                    });
                    return;
                }

                debounceFuture = debounceExec.schedule(() -> {
                    try {
                        List<GeocodeService.DisplayPlace> res = geocodeService.search(q);
                        Platform.runLater(() -> showSuggestionsUnderInput(q, res));
                    } catch (Exception ex) {
                        Platform.runLater(() -> suggestionMenu.hide());
                    }
                }, 300, TimeUnit.MILLISECONDS);
            });

            // keyboard interactions: Down arrow opens suggestions
            cityInput.setOnKeyPressed(k -> {
                if (k.getCode() == KeyCode.DOWN) {
                    if (!suggestionMenu.isShowing()) suggestionMenu.show(cityInput, Side.BOTTOM, 0, 0);
                } else if (k.getCode() == KeyCode.ENTER) {
                    if (suggestionMenu.isShowing() && !suggestionMenu.getItems().isEmpty()) {
                        MenuItem mi = suggestionMenu.getItems().get(0);
                        if (mi instanceof CustomMenuItem) {
                            mi.fire();
                        } else {
                            selectedPlace = null;
                            handleSearch();
                        }
                    } else {
                        selectedPlace = null;
                        handleSearch();
                    }
                } else if (k.getCode() == KeyCode.ESCAPE) {
                    suggestionMenu.hide();
                }
            });
        }

        // ----------------- refresh interval spinner -----------------
        try {
            int saved = Integer.parseInt(PrefsUtil.get("autoRefreshIntervalSeconds", String.valueOf(DEFAULT_REFRESH_SECONDS)));
            int min = 30, max = 3600;
            IntegerSpinnerValueFactory vf = new IntegerSpinnerValueFactory(min, max, Math.max(min, Math.min(max, saved)), 60);
            if (refreshIntervalSpinner != null) {
                refreshIntervalSpinner.setValueFactory(vf);
                refreshIntervalSpinner.setEditable(true);
                refreshIntervalSpinner.valueProperty().addListener((obs, oldV, newV) -> {
                    if (newV != null) PrefsUtil.put("autoRefreshIntervalSeconds", String.valueOf(newV));
                });
            }
        } catch (Exception ignored) {}

        // last city auto-search
        try {
            String last = FileUtil.loadLastCity();
            if (last != null && !last.isBlank()) {
                cityInput.setText(last);
                Platform.runLater(this::handleSearch);
            }
        } catch (IOException ignored) {}

        // defaults / placeholders
        setIconSilently("default.png");
        if (aqiLabel != null) aqiLabel.setText("PM2.5: --");
        if (locationLabel != null) locationLabel.setText("Location: --");
        if (coordsLabel != null) coordsLabel.setText("Lat/Lon: --");
        if (sunriseLabel != null) sunriseLabel.setText("Sunrise: --");
        if (sunsetLabel != null) sunsetLabel.setText("Sunset: --");
        if (moonLabel != null) moonLabel.setText("Moon: --");
        if (moreDetailsArea != null) moreDetailsArea.setText("");

        // auto-refresh preference
        boolean auto = Boolean.parseBoolean(PrefsUtil.get("autoRefresh", "false"));
        if (autoRefreshToggle != null) autoRefreshToggle.setSelected(auto);
        if (auto) {
            startAutoRefresh();
            if (autoRefreshToggle != null) autoRefreshToggle.setText("Auto Refresh (ON)");
        } else if (autoRefreshToggle != null) {
            autoRefreshToggle.setText("Auto Refresh");
        }
    }

    // show suggestions under cityInput (now sets style class for label so CSS can style it)
    private void showSuggestionsUnderInput(String typed, List<GeocodeService.DisplayPlace> res) {
        suggestionMap.clear();
        suggestionMenu.getItems().clear();

        if (res == null || res.isEmpty()) {
            suggestionMenu.hide();
            return;
        }

        int max = Math.min(8, res.size());
        for (int i = 0; i < max; i++) {
            GeocodeService.DisplayPlace p = res.get(i);
            String label = p.getDisplayName() != null ? p.getDisplayName() : (p.getName() != null ? p.getName() : "");
            String unique = label;
            int idx = 1;
            while (suggestionMap.containsKey(unique)) { unique = label + " [" + idx + "]"; idx++; }
            suggestionMap.put(unique, p);

            // Create label with CSS class so context-menu CSS applies
            Label lbl = new Label(unique);
            lbl.getStyleClass().add("suggest-item"); // CSS hook
            // inline fallback style (ensures readable even if CSS file not loaded)
            lbl.setStyle("-fx-text-fill: #08304d; -fx-font-size: 13px; -fx-padding: 6 8 6 8;");

            lbl.setMaxWidth(420);
            lbl.setWrapText(true);

            CustomMenuItem item = new CustomMenuItem(lbl, true);
            item.setOnAction(evt -> {
                selectedPlace = p;
                cityInput.setText(p.getDisplayName() != null ? p.getDisplayName() : (p.getName() != null ? p.getName() : ""));
                suggestionMenu.hide();
                handleSearch(); // search by lat,lon
            });
            suggestionMenu.getItems().add(item);
        }

        // Ensure suggestion menu has sensible width and show it
        if (!suggestionMenu.isShowing()) {
            suggestionMenu.show(cityInput, Side.BOTTOM, 0, 0);
        }
    }

    private void initProvider() {
        try {
            String demoPref = PrefsUtil.get("demo.mode", "true").trim();
            String apiKey = PrefsUtil.get("weather.api.key", "").trim();
            if ("false".equalsIgnoreCase(demoPref) && !apiKey.isBlank()) {
                provider = new oep.skycast.service.ApiWeatherProvider();
            } else if (!apiKey.isBlank()) {
                provider = new oep.skycast.service.ApiWeatherProvider();
            } else {
                provider = new FileWeatherProvider("resources/sample-data/weather-surat.json");
            }
        } catch (Exception ex) {
            provider = new FileWeatherProvider("resources/sample-data/weather-surat.json");
        }
    }

    // ----------------- THEME / AUTO-REFRESH (unchanged) -----------------
    @FXML public void toggleTheme() {
        String theme = PrefsUtil.get("theme", "light");
        String newTheme = theme.equals("light") ? "dark" : "light";
        PrefsUtil.put("theme", newTheme);
        applyTheme(newTheme);
    }

    private void applyTheme(String theme) {
        if (cityInput == null || cityInput.getScene() == null) return;
        var scene = cityInput.getScene();
        String base = getClass().getResource("/css/style.css").toExternalForm();
        if (scene.getStylesheets().stream().noneMatch(s -> s.equals(base))) scene.getStylesheets().add(base);
        scene.getStylesheets().removeIf(s -> s.endsWith("skycast_light.css") || s.endsWith("skycast_dark.css"));
        String css = theme.equals("dark") ? "/css/skycast_dark.css" : "/css/skycast_light.css";
        String themeUrl = getClass().getResource(css).toExternalForm();
        scene.getStylesheets().add(themeUrl);
        PrefsUtil.put("theme", theme);
    }

    private void startAutoRefresh() {
        stopAutoRefresh();
        int interval = DEFAULT_REFRESH_SECONDS;
        try {
            if (refreshIntervalSpinner != null && refreshIntervalSpinner.getValue() != null) interval = refreshIntervalSpinner.getValue();
            else interval = Integer.parseInt(PrefsUtil.get("autoRefreshIntervalSeconds", String.valueOf(DEFAULT_REFRESH_SECONDS)));
        } catch (Exception ignored) {}
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            String city = cityInput.getText().trim();
            if (!city.isEmpty()) Platform.runLater(this::handleSearch);
        }, interval, interval, TimeUnit.SECONDS);

        if (autoRefreshToggle != null && !autoRefreshToggle.getStyleClass().contains("on-toggle"))
            autoRefreshToggle.getStyleClass().add("on-toggle");
    }

    private void stopAutoRefresh() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (autoRefreshToggle != null) autoRefreshToggle.getStyleClass().remove("on-toggle");
    }

    @FXML
    public void handleAutoToggle() {
        boolean on = (autoRefreshToggle != null) && autoRefreshToggle.isSelected();
        PrefsUtil.put("autoRefresh", String.valueOf(on));
        if (on) {
            startAutoRefresh();
            if (autoRefreshToggle != null) autoRefreshToggle.setText("Auto Refresh (ON)");
        } else {
            stopAutoRefresh();
            if (autoRefreshToggle != null) autoRefreshToggle.setText("Auto Refresh");
        }
    }

    // ----------------- SEARCH / DISPLAY (uses selectedPlace if set) -----------------
    @FXML
    public void handleSearch() {
        String raw = cityInput.getText().trim();
        if (raw.isEmpty()) return;

        showSpinner(true);
        CompletableFuture.runAsync(() -> {
            try {
                // prefer lat,lon query when user selected a place
                String query = (selectedPlace != null) ? (selectedPlace.getLat() + "," + selectedPlace.getLon()) : raw;

                WeatherData w = provider.getCurrentWeather(query);
                List<ForecastDay> forecast = provider.getForecast(query);

                Platform.runLater(() -> {
                    // update top cards
                    if (tempLabel != null) tempLabel.setText(String.format("%.1f°C", w.getTemperature()));
                    if (conditionLabel != null) conditionLabel.setText("Condition: " + safeString(w.getCondition()));
                    if (humidityLabel != null) humidityLabel.setText("Humidity: " + w.getHumidity() + "%");
                    if (windLabel != null) windLabel.setText(String.format("Wind: %.1f km/h", w.getWindSpeed()));
                    if (feelsLikeLabel != null) feelsLikeLabel.setText(String.format("Feels like: %.1f°C", w.getFeelsLike()));
                    if (pressureLabel != null) pressureLabel.setText(Double.isNaN(w.getPressureMb()) ? "Pressure: --" : "Pressure: " + w.getPressureMb() + " mb");
                    if (visibilityLabel != null) visibilityLabel.setText(Double.isNaN(w.getVisibilityKm()) ? "Visibility: --" : "Visibility: " + w.getVisibilityKm() + " km");
                    if (uvLabel != null) uvLabel.setText(Double.isNaN(w.getUv()) ? "UV: --" : "UV: " + w.getUv());
                    if (cloudLabel != null) cloudLabel.setText((w.getCloud() < 0) ? "Clouds: --" : "Clouds: " + w.getCloud() + "%");

                    // location & coords
                    if (locationLabel != null) {
                        if (w.getLocationName() != null && !w.getLocationName().isBlank()) {
                            locationLabel.setText(String.format("%s, %s, %s", safeString(w.getLocationName()), safeString(w.getRegion()), safeString(w.getCountry())));
                        } else locationLabel.setText("Location: --");
                    }
                    if (coordsLabel != null) {
                        if (!Double.isNaN(w.getLatitude()) && !Double.isNaN(w.getLongitude())) coordsLabel.setText(String.format("Lat/Lon: %.2f, %.2f", w.getLatitude(), w.getLongitude()));
                        else coordsLabel.setText("Lat/Lon: --");
                    }

                    // AQI
                    if (aqiLabel != null) {
                        if (!Double.isNaN(w.getAqiPm25())) aqiLabel.setText(String.format("PM2.5: %.1f µg/m³", w.getAqiPm25()));
                        else aqiLabel.setText("PM2.5: --");
                    }

                    // quick summary + detail generation
                    if (moreDetailsArea != null) {
                        String qs = generateQuickSummary(w, (forecast != null && !forecast.isEmpty()) ? forecast.get(0) : null);
                        String details = generateDetailedParagraph(w, (forecast != null && !forecast.isEmpty()) ? forecast.get(0) : null);
                        moreDetailsArea.setText(details);
                    }

                    // add to dropdown recent
                    if (locationsDropdown != null && !locationsDropdown.getItems().contains(raw)) locationsDropdown.getItems().add(0, raw);

                    // forecast + charts
                    populateForecastPane(forecast);
                    updateTempChart(forecast);
                    Platform.runLater(this::attachChartPointHandlers);

                    // icon - robust loading with fallback
                    try {
                        if (w.getIconUrl() != null && !w.getIconUrl().isBlank() && iconView != null) {
                            loadImageWithFallback(w.getIconUrl(), iconView);
                        } else if (iconView != null) setIconSilently(w.getCondition());
                    } catch (Exception ex) { if (iconView != null) setIconSilently(w.getCondition()); }
                    if (iconView != null) playFade(iconView);

                    // astro & hourly
                    if (forecast != null && !forecast.isEmpty()) {
                        ForecastDay today = forecast.get(0);
                        if (sunriseLabel != null) sunriseLabel.setText("Sunrise: " + safeString(today.getSunrise(), "--"));
                        if (sunsetLabel != null) sunsetLabel.setText("Sunset: " + safeString(today.getSunset(), "--"));
                        if (moonLabel != null) moonLabel.setText("Moon: " + safeString(today.getMoonPhase(), "--"));
                        populateHourlyChart(today.getHourly());
                    }

                    if (lastUpdatedLabel != null) lastUpdatedLabel.setText("Last updated: " + LocalDateTime.now().format(timeFmt));
                    try { FileUtil.saveLastCity(raw); } catch (IOException ignored) {}

                    showSpinner(false);
                });

            } catch (WeatherException ex) {
                Platform.runLater(() -> {
                    showAlert("Weather Error", ex.getMessage());
                    showSpinner(false);
                });
            }
        });
    }

    // ----------------- FAVORITES -----------------
    @FXML
    public void handleAddFavorite() {
        String city = cityInput.getText().trim();
        if (city.isEmpty() || favoritesList == null) return;
        if (!favoritesList.getItems().contains(city)) {
            favoritesList.getItems().add(city);
            try { FileUtil.saveFavorites(favoritesList.getItems()); } catch (IOException ignored) {}
        }
    }

    @FXML
    public void handleRemoveFavorite() {
        if (favoritesList == null) { showAlert("Remove Favorite", "Favorites list missing."); return; }
        String city = favoritesList.getSelectionModel().getSelectedItem();
        if (city == null) { showAlert("Remove Favorite", "Please select a favorite to remove."); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove Favorite");
        confirm.setHeaderText(null);
        confirm.setContentText("Remove \"" + city + "\" from favorites?");
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                favoritesList.getItems().remove(city);
                try { FileUtil.saveFavorites(favoritesList.getItems()); } catch (IOException ignored) {}
            }
        });
    }

    // ----------------- FORECAST CARDS -----------------
    private void populateForecastPane(List<ForecastDay> forecast) {
        if (forecastPane == null) return;
        forecastPane.getChildren().clear();
        if (forecast == null) return;

        for (ForecastDay f : forecast) {
            VBox card = new VBox(8);
            card.getStyleClass().add("forecast-card");
            card.setPrefWidth(160);

            Label day = new Label(f.getDay());
            day.getStyleClass().add("card-day");

            ImageView iv = new ImageView();
            iv.setFitWidth(44); iv.setFitHeight(44); iv.setPreserveRatio(true);
            if (f.getIconUrl() != null && !f.getIconUrl().isBlank()) {
                // robust remote load with fallback
                loadImageIntoViewWithFallback(f.getIconUrl(), iv);
            } else {
                // fallback to resource icon based on condition
                String cond = f.getCondition();
                String iconFile = "default.png";
                if (cond != null) {
                    String k = cond.toLowerCase();
                    if (k.contains("sun") || k.contains("clear")) iconFile = "sunny.png";
                    else if (k.contains("cloud")) iconFile = "cloudy.png";
                    else if (k.contains("rain") || k.contains("drizzle") || k.contains("shower")) iconFile = "rainy.png";
                    else if (k.contains("snow")) iconFile = "snowy.png";
                    else if (k.contains("thunder") || k.contains("storm") || k.contains("lightning")) iconFile = "thunderstorm.png";
                }
                try (InputStream is = getClass().getResourceAsStream("/icons/" + iconFile)) {
                    if (is != null) iv.setImage(new Image(is));
                } catch (Exception ignored) {}
            }

            Label temps = new Label(String.format("%.1f° / %.1f°", f.getMinTemp(), f.getMaxTemp()));
            temps.getStyleClass().add("card-temps");

            Label cond = new Label(safeString(f.getCondition()));
            cond.getStyleClass().add("card-cond");
            cond.setWrapText(true);

            card.getChildren().addAll(day, iv, temps, cond);

            card.setOnMouseClicked(e -> {
                showForecastDetail(f);
                populateHourlyChart(f.getHourly());
                if (sunriseLabel != null) sunriseLabel.setText("Sunrise: " + safeString(f.getSunrise(), "--"));
                if (sunsetLabel != null) sunsetLabel.setText("Sunset: " + safeString(f.getSunset(), "--"));
                if (moonLabel != null) moonLabel.setText("Moon: " + safeString(f.getMoonPhase(), "--"));
            });

            forecastPane.getChildren().add(card);
        }
    }

    private void showForecastDetail(ForecastDay f) {
        StringBuilder sb = new StringBuilder();
        sb.append("Day: ").append(f.getDay()).append("\n");
        sb.append("Condition: ").append(safeString(f.getCondition())).append("\n");
        sb.append(String.format("Min: %.1f °C   Max: %.1f °C\n", f.getMinTemp(), f.getMaxTemp()));
        try { if (f.getAvgHumidity() >= 0) sb.append("Avg Humidity: ").append(f.getAvgHumidity()).append("%\n"); } catch (Throwable ignored) {}
        try { if (f.getChanceOfRain() >= 0) sb.append("Chance of rain: ").append(f.getChanceOfRain()).append("%\n"); } catch (Throwable ignored) {}
        sb.append("Sunrise: ").append(safeString(f.getSunrise(), "--")).append("\n");
        sb.append("Sunset: ").append(safeString(f.getSunset(), "--")).append("\n");
        if (f.getIconUrl() != null) sb.append("Icon: ").append(f.getIconUrl()).append("\n");

        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(f.getDay() + " — Details");
        a.setHeaderText(null);
        a.setContentText(sb.toString());
        a.showAndWait();
    }

    // ----------------- CHARTS -----------------
    private void updateTempChart(List<ForecastDay> forecast) {
        if (tempChart == null) return;
        tempChart.getData().clear();
        if (forecast == null || forecast.isEmpty()) return;

        XYChart.Series<String, Number> minSeries = new XYChart.Series<>();
        XYChart.Series<String, Number> maxSeries = new XYChart.Series<>();
        minSeries.setName("Min °C");
        maxSeries.setName("Max °C");

        for (ForecastDay f : forecast) {
            minSeries.getData().add(new XYChart.Data<>(f.getDay(), f.getMinTemp()));
            maxSeries.getData().add(new XYChart.Data<>(f.getDay(), f.getMaxTemp()));
        }
        tempChart.getData().addAll(minSeries, maxSeries);
    }

    private void attachChartPointHandlers() {
        if (tempChart != null) {
            for (XYChart.Series<String, Number> s : tempChart.getData()) {
                for (XYChart.Data<String, Number> d : s.getData()) {
                    Node node = d.getNode();
                    if (node == null) continue;
                    Tooltip t = new Tooltip(s.getName() + "\n" + d.getXValue() + ": " + d.getYValue() + "°C");
                    Tooltip.install(node, t);
                    node.setOnMouseEntered(ev -> { node.setScaleX(1.15); node.setScaleY(1.15); });
                    node.setOnMouseExited(ev -> { node.setScaleX(1.0); node.setScaleY(1.0); });
                }
            }
        }
        if (hourlyChart != null) {
            for (XYChart.Series<String, Number> s : hourlyChart.getData()) {
                for (XYChart.Data<String, Number> d : s.getData()) {
                    Node node = d.getNode();
                    if (node == null) continue;
                    String seriesName = s.getName() != null ? s.getName() : "";
                    Tooltip.install(node, new Tooltip(seriesName + "\n" + d.getXValue() + ": " + d.getYValue() + "°C"));
                    node.setOnMouseEntered(ev -> { node.setScaleX(1.15); node.setScaleY(1.15); });
                    node.setOnMouseExited(ev -> { node.setScaleX(1.0); node.setScaleY(1.0); });
                }
            }
        }
    }

    /**
     * Build hourly chart...
     */
    private void populateHourlyChart(List<HourlyWeather> hourly) {
        if (hourlyChart == null) return;
        hourlyChart.getData().clear();
        if (hourly == null || hourly.isEmpty()) return;

        List<String> hours = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) { hours.add(String.format("%02d:00", h)); }
        if (hourlyXAxis != null) {
            Platform.runLater(() -> hourlyXAxis.setCategories(javafx.collections.FXCollections.observableArrayList(hours)));
        }

        Map<String, HourlyWeather> byHour = new HashMap<>();
        for (HourlyWeather hw : hourly) {
            String t = hw.getTime();
            if (t != null && t.contains(" ")) t = t.substring(t.lastIndexOf(' ') + 1);
            if (t != null && t.matches("^\\d:.*")) t = "0" + t;
            if (t == null) t = "";
            byHour.put(t, hw);
        }

        boolean hasMinMax = false;
        try {
            hasMinMax = hourly.get(0).getClass().getMethod("getTempMin") != null &&
                        hourly.get(0).getClass().getMethod("getTempMax") != null;
        } catch (Throwable ignored) { hasMinMax = false; }

        XYChart.Series<String, Number> minSeries = new XYChart.Series<>();
        XYChart.Series<String, Number> maxSeries = new XYChart.Series<>();
        minSeries.setName("Min °C");
        maxSeries.setName("Max °C");

        final double FALLBACK_DELTA = 1.5;

        for (String cat : hours) {
            HourlyWeather hw = byHour.get(cat);
            if (hw != null) {
                if (hasMinMax) {
                    try {
                        Number vmin = (Number) hw.getClass().getMethod("getTempMin").invoke(hw);
                        Number vmax = (Number) hw.getClass().getMethod("getTempMax").invoke(hw);
                        minSeries.getData().add(new XYChart.Data<>(cat, vmin));
                        maxSeries.getData().add(new XYChart.Data<>(cat, vmax));
                    } catch (Throwable ex) {
                        try {
                            Number tval = (Number) hw.getClass().getMethod("getTempC").invoke(hw);
                            double tmp = tval != null ? tval.doubleValue() : Double.NaN;
                            minSeries.getData().add(new XYChart.Data<>(cat, tmp - FALLBACK_DELTA));
                            maxSeries.getData().add(new XYChart.Data<>(cat, tmp + FALLBACK_DELTA));
                        } catch (Throwable e) {
                            minSeries.getData().add(new XYChart.Data<>(cat, Double.NaN));
                            maxSeries.getData().add(new XYChart.Data<>(cat, Double.NaN));
                        }
                    }
                } else {
                    try {
                        Number tval = (Number) hw.getClass().getMethod("getTempC").invoke(hw);
                        double tmp = tval != null ? tval.doubleValue() : Double.NaN;
                        minSeries.getData().add(new XYChart.Data<>(cat, tmp - FALLBACK_DELTA));
                        maxSeries.getData().add(new XYChart.Data<>(cat, tmp + FALLBACK_DELTA));
                    } catch (Throwable e) {
                        minSeries.getData().add(new XYChart.Data<>(cat, Double.NaN));
                        maxSeries.getData().add(new XYChart.Data<>(cat, Double.NaN));
                    }
                }
            } else {
                minSeries.getData().add(new XYChart.Data<>(cat, Double.NaN));
                maxSeries.getData().add(new XYChart.Data<>(cat, Double.NaN));
            }
        }

        hourlyChart.getData().addAll(minSeries, maxSeries);

        Platform.runLater(() -> {
            try {
                Node minNode = minSeries.getNode();
                Node maxNode = maxSeries.getNode();
                if (minNode != null) minNode.getStyleClass().add("series-hourly-min");
                if (maxNode != null) maxNode.getStyleClass().add("series-hourly-max");

                for (XYChart.Data<String, Number> d : minSeries.getData()) {
                    Node node = d.getNode();
                    if (node == null) continue;
                    node.getStyleClass().add("series-hourly-min");
                    Tooltip.install(node, new Tooltip(minSeries.getName() + " — " + d.getXValue() + ": " + d.getYValue() + "°C"));
                    node.setOnMouseEntered(ev -> { node.setScaleX(1.25); node.setScaleY(1.25); });
                    node.setOnMouseExited(ev -> { node.setScaleX(1.0); node.setScaleY(1.0); });
                }
                for (XYChart.Data<String, Number> d : maxSeries.getData()) {
                    Node node = d.getNode();
                    if (node == null) continue;
                    node.getStyleClass().add("series-hourly-max");
                    Tooltip.install(node, new Tooltip(maxSeries.getName() + " — " + d.getXValue() + ": " + d.getYValue() + "°C"));
                    node.setOnMouseEntered(ev -> { node.setScaleX(1.25); node.setScaleY(1.25); });
                    node.setOnMouseExited(ev -> { node.setScaleX(1.0); node.setScaleY(1.0); });
                }
            } catch (Throwable ignored) {}
        });
    }

    // ----------------- ICON / ANIMATION / UTIL -----------------
    private void setWeatherIcon(String condition) {
        String key = (condition == null) ? "" : condition.toLowerCase();
        String iconFile = "default.png";
        if (key.contains("sun") || key.contains("clear")) iconFile = "sunny.png";
        else if (key.contains("cloud")) iconFile = "cloudy.png";
        else if (key.contains("rain") || key.contains("drizzle") || key.contains("shower")) iconFile = "rainy.png";
        else if (key.contains("snow")) iconFile = "snowy.png";
        else if (key.contains("thunder") || key.contains("storm") || key.contains("lightning")) iconFile = "thunderstorm.png";
        setIconSilently(iconFile);
    }

    private void setIconSilently(String iconFile) {
        try (InputStream is = getClass().getResourceAsStream("/icons/" + iconFile)) {
            if (is != null && iconView != null) iconView.setImage(new Image(is));
        } catch (Exception ignored) {}
    }

    private void playFade(ImageView iv) {
        if (iv == null) return;
        FadeTransition ft = new FadeTransition(Duration.millis(400), iv);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }

    /**
     * Load an image (remote URL) into given ImageView and fallback to a default resource if loading fails.
     */
    private void loadImageIntoViewWithFallback(String url, ImageView iv) {
        try {
            Image img = new Image(url, true);
            img.errorProperty().addListener((obs, oldV, newV) -> {
                if (newV) {
                    Platform.runLater(() -> {
                        try (InputStream is = getClass().getResourceAsStream("/icons/default.png")) {
                            if (is != null) iv.setImage(new Image(is));
                        } catch (Exception ignored) {}
                    });
                }
            });
            iv.setImage(img);
        } catch (Exception ex) {
            try (InputStream is = getClass().getResourceAsStream("/icons/default.png")) {
                if (is != null) iv.setImage(new Image(is));
            } catch (Exception ignored) {}
        }
    }

    private void loadImageWithFallback(String url, ImageView iv) {
        loadImageIntoViewWithFallback(url, iv);
    }

    private void showSpinner(boolean b) {
        Platform.runLater(() -> {
            if (loadingSpinner != null) loadingSpinner.setVisible(b);
        });
    }

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    // ----------------- SNAPSHOT & SHARE -----------------
    @FXML
    public void handleSaveSnapshot() {
        if (currentWeatherBox == null) { showAlert("Snapshot", "No center node available."); return; }
        WritableImage image = currentWeatherBox.snapshot(new SnapshotParameters(), null);
        if (image == null) { showAlert("Snapshot", "Could not create snapshot (image is null)."); return; }

        try {
            String projectRoot = System.getProperty("user.dir");
            Path snapsDir = Paths.get(projectRoot, "snapshots");
            if (!Files.exists(snapsDir)) Files.createDirectories(snapsDir);

            String fname = "skycast_snapshot_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now()) + ".png";
            File out = snapsDir.resolve(fname).toFile();

            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out);
            lastSavedSnapshot = out;

            ClipboardContent content = new ClipboardContent();
            content.putString(out.getAbsolutePath());
            content.putImage(image);
            Clipboard.getSystemClipboard().setContent(content);

            showInfo("Snapshot saved: " + out.getAbsolutePath() + "\nImage & path copied to clipboard.");
        } catch (IOException ioe) {
            showAlert("Save error", "Could not save snapshot: " + ioe.getMessage());
        } catch (Throwable t) {
            showAlert("Snapshot error", "Unexpected error while saving snapshot.");
        }
    }

    @FXML
    public void handleShare() {
        if (lastSavedSnapshot == null || !lastSavedSnapshot.exists()) {
            showAlert("Share", "No previously saved snapshot found. Please Save Snapshot first.");
            return;
        }
        try {
            ClipboardContent content = new ClipboardContent();
            content.putString(lastSavedSnapshot.getAbsolutePath());
            Clipboard.getSystemClipboard().setContent(content);

            Desktop.getDesktop().open(lastSavedSnapshot.getParentFile());

            showInfo("Opened snapshot folder. File path copied to clipboard.");
        } catch (Exception e) {
            showAlert("Share error", "Could not open folder: " + e.getMessage());
        }
    }

    // ----------------- CLEANUP -----------------
    public void shutdown() {
        stopAutoRefresh();
        try { debounceExec.shutdownNow(); } catch (Exception ignored) {}
    }

    // ----------------- SUMMARY / DETAILS GENERATION -----------------
    private String generateQuickSummary(WeatherData w, ForecastDay today) {
        if (w == null) return "";
        String c = (w.getCondition() != null) ? w.getCondition().toLowerCase() : "";
        if (c.contains("rain") || c.contains("shower") || c.contains("drizzle")) return "Rain expected — carry an umbrella.";
        if (c.contains("snow")) return "Snow or wintry conditions expected.";
        if (c.contains("cloud") || c.contains("overcast")) return "Mostly cloudy with occasional sun.";
        if (c.contains("mist") || c.contains("fog")) return "Low visibility due to mist/fog.";
        if (c.contains("clear") || c.contains("sun")) return "Clear skies and sunny — great weather.";
        return "Typical weather conditions — check details below.";
    }

    private String generateDetailedParagraph(WeatherData w, ForecastDay today) {
        if (w == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Temperature %.1f°C (feels like %.1f°C). ", w.getTemperature(), w.getFeelsLike()));
        if (w.getCondition() != null) sb.append(w.getCondition()).append(". ");
        if (w.getHumidity() >= 0) sb.append("Humidity ").append(w.getHumidity()).append("%, ");
        if (!Double.isNaN(w.getWindSpeed())) sb.append(String.format("wind %.1f km/h. ", w.getWindSpeed()));
        if (!Double.isNaN(w.getVisibilityKm())) sb.append(String.format("Visibility %.1f km. ", w.getVisibilityKm()));
        if (!Double.isNaN(w.getPressureMb())) sb.append(String.format("Pressure %.0f mb. ", w.getPressureMb()));
        if (!Double.isNaN(w.getUv())) sb.append(String.format("UV index %.1f. ", w.getUv()));
        if (w.getCloud() >= 0) sb.append("Cloud cover ").append(w.getCloud()).append("%. ");

        if (today != null) {
            if (today.getSunrise() != null) sb.append("Sunrise: ").append(today.getSunrise()).append(". ");
            if (today.getSunset() != null) sb.append("Sunset: ").append(today.getSunset()).append(". ");
            if (today.getMoonPhase() != null) sb.append("Moon: ").append(today.getMoonPhase()).append(". ");
        }

        if (!Double.isNaN(w.getAqiPm25())) {
            sb.append(String.format("Air quality (PM2.5) %.1f µg/m³. ", w.getAqiPm25()));
            if (w.getAqiPm25() > 35) sb.append("Air quality is moderate/poor — sensitive groups should take care. ");
        }

        return sb.toString().trim();
    }

    // ----------------- HELPERS -----------------
    private static String safeString(String s) { return s == null ? "--" : s; }
    private static String safeString(String s, String fallback) { return s == null ? fallback : s; }
}
