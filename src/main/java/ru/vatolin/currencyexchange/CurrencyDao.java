package ru.vatolin.currencyexchange;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CurrencyDao {
    private final String url = "jdbc:sqlite:C:/Users/vladi/IdeaProjects/CurrencyExchange/src/main/resources/currency_exchange.db";

    public CurrencyDao() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Драйвер SQLite не найден", e);
        }
    }

    public List<Currency> getAllCurrencies() throws SQLException {
        List<Currency> currencies = new ArrayList<>();
        String sql = "SELECT * FROM Currencies";

        try (Connection conn = DriverManager.getConnection(url);
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {

            while (rs.next()) {
                currencies.add(new Currency(
                        rs.getInt("ID"),
                        rs.getString("Code"),
                        rs.getString("FullName"),
                        rs.getString("Sign")
                ));
            }
        }
        return currencies;
    }

    public Currency getCurrencyByCode(String code) throws SQLException {
        String sql = "SELECT * FROM Currencies WHERE Code = ?";
        try (Connection conn = DriverManager.getConnection(url);
            PreparedStatement statement = conn.prepareStatement(sql)) {

            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new Currency(
                            rs.getInt("ID"),
                            rs.getString("Code"),
                            rs.getString("FullName"),
                            rs.getString("Sign")
                    );
                }
            }
        }
        return null;
    }

    public Currency addCurrency(Currency currency) throws SQLException {
        String sql = "INSERT INTO Currencies (Code, FullName, Sign) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(url);
            PreparedStatement statement = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, currency.getCode());
            statement.setString(2, currency.getFullName());
            statement.setString(3, currency.getSign());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    currency.setId(generatedKeys.getInt(1));
                } else {
                    throw new SQLException("Не удалось получить ID новой валюты.");
                }
            }
        }
        return currency;
    }
}
