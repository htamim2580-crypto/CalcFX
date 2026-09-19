module com.example.calcfx {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.calcfx to javafx.fxml;
    exports com.example.calcfx;
}