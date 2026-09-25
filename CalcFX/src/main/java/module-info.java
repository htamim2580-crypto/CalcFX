module com.example.calcfx {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;   // ← new: for HttpClient
    requires org.json;        // ← new: for parsing the API response

    opens com.example.calcfx to javafx.fxml;
    exports com.example.calcfx;
}