package ru.vatolin.currencyexchange;

import ru.vatolin.currencyexchange.service.DatabaseConnection;

import java.sql.Connection;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        try (Connection connection = DatabaseConnection.getConnection()) {
            if (connection != null) {
                System.out.println("Успешно!");
            } else {
                System.out.println("Неудача");
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
}
