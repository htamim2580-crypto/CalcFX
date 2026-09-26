module com.example.calcfx {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires java.sql;
    requires org.json;
    requires org.xerial.sqlitejdbc;
    requires exp4j;

    opens com.example.calcfx to javafx.fxml;
    exports com.example.calcfx;
}