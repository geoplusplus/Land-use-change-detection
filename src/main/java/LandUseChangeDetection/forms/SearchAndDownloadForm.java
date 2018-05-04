package LandUseChangeDetection.forms;

import LandUseChangeDetection.Utils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.converter.NumberStringConverter;
import netscape.javascript.JSObject;
import org.apache.abdera.Abdera;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.abdera.protocol.Response;
import org.apache.abdera.protocol.client.AbderaClient;
import org.apache.abdera.protocol.client.ClientResponse;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.odata4j.consumer.ODataConsumer;
import org.odata4j.consumer.ODataConsumers;
import org.odata4j.consumer.behaviors.BasicAuthenticationBehavior;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.List;

public class SearchAndDownloadForm {

    /**
     * ESA open hub portal url
     */
    private static final String ESA_OPEN_HUB_PORTAL_URL = "https://scihub.copernicus.eu";

    /**
     * ESA Open Search base
     */
    private static final String OPEN_SEARCH_QUERY_BASE = "https://scihub.copernicus.eu/apihub/search?start=0&rows=100&q=";

    /**
     * ESA open hub api url
     */
    private static final String esaOpenHubURL = "https://scihub.copernicus.eu/apihub/odata/v1/";

    public PasswordField passwordTextField;
    public TextField loginTextField;

    /**
     * OData consumer builder
     */
    private static ODataConsumer.Builder consumerBuilder = ODataConsumers.newBuilder(esaOpenHubURL);
    public DatePicker sensingStartDate;
    public DatePicker sensingFinishDate;
    public TextField maxCloudPercentage;
    public WebView webMap;
    public SplitPane splitPane;
    public Button changeButton;
    public Button loginButton;
    public ListView resultListView;
    public TabPane tab;

    /**
     * Open search client
     */
    private AbderaClient abderaClient;

    /**
     * OData consumer
     */
    private ODataConsumer consumer;

    /**
     * Geometry JS Leaflet string
     */
    private String geometryJS;

    public void changeLogin(ActionEvent actionEvent) {
        this.loginTextField.setDisable(false);
        this.passwordTextField.setDisable(false);
        this.loginButton.setDisable(false);
        this.changeButton.setDisable(true);
    }

    /**
     * JS connector
     */
    public class DownloadAndSearchApplication {
        public void callFromJavascript(String geom) {
            geometryJS = geom;
        }
    }

    /**
     * Download form initialization
     */
    @FXML
    void initialize(){
        maxCloudPercentage.setTextFormatter(new TextFormatter<>(new NumberStringConverter()));
        WebEngine webEngine = webMap.getEngine();
        File mapIndexFile = new File("src/resources/SaDWebForm/index.html");
        webEngine.load("file:" + mapIndexFile.getAbsolutePath());
        webEngine.getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) webEngine.executeScript("window");
                window.setMember("app", new DownloadAndSearchApplication());
            }
        });
    }

    /**
     * Check for login and password filling
     * @return empty or not
     */
    private boolean checkLoginAndPassword() {
        return loginTextField.getText().length() != 0 && passwordTextField.getText().length() != 0;
    }

    /**
     * Login acton handler
     * @param actionEvent login action event
     */
    public void loginHandler(ActionEvent actionEvent) throws URISyntaxException {
        this.loginTextField.setDisable(true);
        this.passwordTextField.setDisable(true);
        if (!checkLoginAndPassword()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Empty login or password");
            alert.setHeaderText("Error, empty login or password fields");
            alert.setContentText("Please, fill empty field or sing up in https://scihub.copernicus.eu/");
            alert.showAndWait();
            this.loginTextField.setDisable(false);
            this.passwordTextField.setDisable(false);

            return;
        }
        String login = loginTextField.getText();
        String password = passwordTextField.getText();
        // Create OpenSearchConsumer
        Abdera abdera = new Abdera();
        this.abderaClient = new AbderaClient(abdera);
        this.abderaClient.addCredentials(
                ESA_OPEN_HUB_PORTAL_URL,
                AuthScope.ANY_REALM,
                AuthScope.ANY_SCHEME,
                new UsernamePasswordCredentials(login, password)
        );
        // Create OData consumer
        consumerBuilder.setClientBehaviors(new BasicAuthenticationBehavior(login, password));
        this.consumer = consumerBuilder.build();
        this.loginButton.setDisable(true);
        this.changeButton.setDisable(false);
    }


    public void searchDataHandler(ActionEvent actionEvent) throws URISyntaxException {
        Instant sensingStartDate = null;
        Instant sensingFinishDate = null;
        if (!this.sensingStartDate.getEditor().getText().isEmpty()) {
            LocalDate startDate = this.sensingStartDate.getValue();
            sensingStartDate = Instant.from(startDate.atStartOfDay(ZoneId.systemDefault()));
        }
        if (!this.sensingFinishDate.getEditor().getText().isEmpty()) {
            LocalDate finishDate = this.sensingFinishDate.getValue();
            sensingFinishDate = Instant.from(finishDate.atStartOfDay(ZoneId.systemDefault()));
        }
        if (this.maxCloudPercentage.getText().isEmpty()) {
            Utils.showErrorMessage("Max cloud percentage error",
                    "Max cloud percentage should be integer number between 0 and 100",
                    "");
            return;
        }
        int maxCloudsPercentage = Integer.parseInt(maxCloudPercentage.getText());
        if (maxCloudsPercentage < 0 || maxCloudsPercentage > 100) {
            Utils.showErrorMessage("Max cloud percentage error",
                    "Max cloud percentage should be integer number between 0 and 100",
                    "");
            return;
        }
        if (sensingStartDate != null && sensingFinishDate != null && sensingStartDate.isAfter(sensingFinishDate)) {
            Utils.showErrorMessage("Error",
                    "Sensing start date must be before sensing finish date",
                    "");
            return;
        }
        // Create query url
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("platformname:Sentinel-2");
        // Set up periods
        if (sensingStartDate != null && sensingFinishDate != null) {
            LocalDateTime start = LocalDateTime.ofInstant(sensingStartDate, ZoneId.systemDefault());
            LocalDateTime finish = LocalDateTime.ofInstant(sensingFinishDate, ZoneId.systemDefault());
            queryBuilder.append("%20AND%20")
                    .append("beginposition:%5B")
                    .append(start.getYear() + "-" + start.getMonthValue() + "-" + start.getDayOfMonth() + "T00:00:00.000Z")
                    .append("%20TO%20")
                    .append(finish.getYear() + "-" + finish.getMonthValue() + "-" + finish.getDayOfMonth() + "T23:59:59.000Z")
                    .append("%5D");
        }
        // Coverage intersection
        if (this.geometryJS != null) {
            queryBuilder.append("%20AND%20footprint%3A%22Intersects%28")
                    .append(geometryJS
                            .replace(" ", "%20")
                            .replace(",", "%2C")
                            .replace("(", "%28")
                            .replace(")", "%29")
                    )
                    .append("%29%22");
        }
        // Set up clouds percentage
        if (maxCloudsPercentage != 100) {
            queryBuilder.append("%20AND%20cloudcoverpercentage%3A%5B0%20TO%20").append(maxCloudsPercentage).append("%5D");
        }
        // Create open search query
        System.out.println(OPEN_SEARCH_QUERY_BASE + queryBuilder.toString());
        ClientResponse response = this.abderaClient.get(OPEN_SEARCH_QUERY_BASE + queryBuilder.toString());
        List<Entry> entries = null;
        if (response.getType() == Response.ResponseType.SUCCESS) {
            Document<Feed> doc = response.getDocument();
            Feed feed = doc.getRoot();
            entries = feed.getEntries();
        } else {
            Utils.showErrorMessage("Error", "Open Search error", response.getType().toString());
            return;
        }
        if (resultListView.getItems().size() > 0) {
            resultListView.getItems().clear();
        }
        if (entries == null || entries.size() == 0) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Result");
            alert.setHeaderText("Data not found");
            alert.setContentText("Please, change request params");
            alert.showAndWait();
        } else {
            ObservableList<Entry> e = FXCollections.observableArrayList();
            e.addAll(entries);
            resultListView.setCellFactory(c -> new SentinelDataResponse());
            resultListView.setItems(e);
            tab.getSelectionModel().select(1);
        }
    }

    private class SentinelDataResponse extends ListCell<Entry> {
        private BorderPane content = new BorderPane();
        Label title = new Label();
        Label summary = new Label();
        Hyperlink link = new Hyperlink();
        ImageView imageView = new ImageView();

        public SentinelDataResponse() {
            VBox vBox = new VBox();
            vBox.setSpacing(4);
            vBox.getChildren().addAll(title, link, summary);
            content.setLeft(imageView);
            content.setCenter(vBox);
            //content.getChildren().addAll(imageView, title, link, summary);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            setGraphic(content);
        }

        @Override
        public void updateItem(Entry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (entry == null || empty) {
                setText(null);
                setGraphic(null);
            } else {
                title.setText(entry.getTitle());
                link.setText(entry.getAlternateLink().getHref().toString());
                summary.setText(entry.getSummary());
                try {
                    URLConnection uc = new java.net.URL(entry.getLink("icon").getHref().toString()).openConnection();
                    String userpass = "artur7" + ":" + "9063228328a!";
                    String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()));
                    uc.setRequestProperty ("Authorization", basicAuth);
                    InputStream in = uc.getInputStream();
                    Image image = new Image(in, 120, 120, false, false);
                    imageView.setImage(image);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                link.setOnAction(e -> {
                    if (Desktop.isDesktopSupported()){
                        try {
                            Desktop.getDesktop().browse(new URI(link.getText()));
                        } catch (IOException | URISyntaxException e1) {
                            e1.printStackTrace();
                        }
                    }
                });
                setGraphic(content);
            }
        }
    }
}