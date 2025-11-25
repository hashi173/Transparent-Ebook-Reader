module com.ebook.reader {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires javafx.swing;
    requires java.sql;
    requires org.apache.pdfbox;
    requires java.desktop;
    requires jdk.jsobject;  // ✅ THÊM DÒNG NÀY

    opens com.ebook.reader to javafx.fxml;
    opens com.ebook.reader.controller to javafx.fxml;

    exports com.ebook.reader;
    exports com.ebook.reader.controller;
    exports com.ebook.reader.model;
    exports com.ebook.reader.service;
    exports com.ebook.reader.dao;
}