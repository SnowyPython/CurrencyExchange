package ru.vatolin.currencyexchange;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ExchangeRateDao {
    private final String url = "jdbc:sqlite:C:/Users/vladi/IdeaProjects/CurrencyExchange/src/main/resources/currency_exchange.db";

    public ExchangeRateDao() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Драйвер SQLite не найден", e);
        }
    }

    public List<ExchangeRate> getAllExchangeRates() throws SQLException {
        List<ExchangeRate> rates = new ArrayList<>();
        String sql = """
            SELECT er.ID, er.Rate,
                   bc.ID AS BaseCurrencyId, bc.Code AS BaseCurrencyCode, bc.FullName AS BaseCurrencyName, bc.Sign AS BaseCurrencySign,
                   tc.ID AS TargetCurrencyId, tc.Code AS TargetCurrencyCode, tc.FullName AS TargetCurrencyName, tc.Sign AS TargetCurrencySign
            FROM ExchangeRates er
            JOIN Currencies bc ON er.BaseCurrencyId = bc.ID
            JOIN Currencies tc ON er.TargetCurrencyId = tc.ID
            """;

        try (Connection conn = DriverManager.getConnection(url);
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {

            while (rs.next()) {
                rates.add(new ExchangeRate(
                        rs.getInt("ID"),
                        new Currency(rs.getInt("BaseCurrencyId"), rs.getString("BaseCurrencyCode"), rs.getString("BaseCurrencyName"), rs.getString("BaseCurrencySign")),
                        new Currency(rs.getInt("TargetCurrencyId"), rs.getString("TargetCurrencyCode"), rs.getString("TargetCurrencyName"), rs.getString("TargetCurrencySign")),
                        rs.getDouble("Rate")
                ));
            }
        }
        return rates;
    }

    public ExchangeRate getExchangeRate(String baseCode, String targetCode) throws SQLException {
        String sql = """
            SELECT er.ID, er.Rate,
                   bc.ID AS BaseCurrencyId, bc.Code AS BaseCurrencyCode, bc.FullName AS BaseCurrencyName, bc.Sign AS BaseCurrencySign,
                   tc.ID AS TargetCurrencyId, tc.Code AS TargetCurrencyCode, tc.FullName AS TargetCurrencyName, tc.Sign AS TargetCurrencySign
            FROM ExchangeRates er
            JOIN Currencies bc ON er.BaseCurrencyId = bc.ID
            JOIN Currencies tc ON er.TargetCurrencyId = tc.ID
            WHERE bc.Code = ? AND tc.Code = ?
            """;

        try (Connection conn = DriverManager.getConnection(url);
            PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, baseCode.toUpperCase());
            statement.setString(2, targetCode.toUpperCase());

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new ExchangeRate(
                            rs.getInt("ID"),
                            new Currency(rs.getInt("BaseCurrencyId"), rs.getString("BaseCurrencyCode"), rs.getString("BaseCurrencyName"), rs.getString("BaseCurrencySign")),
                            new Currency(rs.getInt("TargetCurrencyId"), rs.getString("TargetCurrencyCode"), rs.getString("TargetCurrencyName"), rs.getString("TargetCurrencySign")),
                            rs.getDouble("Rate")
                    );
                }
            }
        }
        return null;
    }

    public ExchangeRate addExchangeRate(String baseCurrencyCode, String targetCurrencyCode, double rate) throws SQLException {
        String findCurrenciesSql = "SELECT ID, Code FROM Currencies WHERE Code IN (?, ?)";
        String insertExchangeRateSql = """
            INSERT INTO ExchangeRates (BaseCurrencyId, TargetCurrencyId, Rate)
            VALUES (?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(url)) {
            conn.setAutoCommit(false);

            int baseCurrencyId = -1;
            int targetCurrencyId = -1;

            try (PreparedStatement findStatement = conn.prepareStatement(findCurrenciesSql)) {
                findStatement.setString(1, baseCurrencyCode.toUpperCase());
                findStatement.setString(2, targetCurrencyCode.toUpperCase());

                try (ResultSet rs = findStatement.executeQuery()) {
                    while (rs.next()) {
                        if (rs.getString("Code").equalsIgnoreCase(baseCurrencyCode)) {
                            baseCurrencyId = rs.getInt("ID");
                        } else if (rs.getString("Code").equalsIgnoreCase(targetCurrencyCode)) {
                            targetCurrencyId = rs.getInt("ID");
                        }
                    }
                }
            }

            if (baseCurrencyId == -1 || targetCurrencyId == -1) {
                conn.rollback();
                throw new SQLException("Одна или обе валюты не найдены в базе данных");
            }

            try (PreparedStatement insertStatement = conn.prepareStatement(insertExchangeRateSql, Statement.RETURN_GENERATED_KEYS)) {
                insertStatement.setInt(1, baseCurrencyId);
                insertStatement.setInt(2, targetCurrencyId);
                insertStatement.setDouble(3, rate);

                insertStatement.executeUpdate();

                try (ResultSet generatedKeys = insertStatement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int exchangeRateId = generatedKeys.getInt(1);

                        return getExchangeRate(baseCurrencyCode, targetCurrencyCode);
                    } else {
                        throw new SQLException("Не удалось получить ID нового обменного курса");
                    }
                }
            } finally {
                conn.commit();
            }
        }
    }

    public Optional<ExchangeRate> updateExchangeRate(String baseCurrencyCode, String targetCurrencyCode, double rate) throws SQLException {
        String updateQuery = """
            UPDATE ExchangeRates
            SET Rate = ?
            WHERE BaseCurrencyId = (SELECT ID FROM Currencies WHERE Code = ?)
            AND TargetCurrencyId = (SELECT ID FROM Currencies WHERE Code = ?)
        """;

        String selectQuery = """
            SELECT er.ID, er.Rate,
                bc.ID AS BaseCurrencyId, bc.Code AS BaseCurrencyCode, bc.FullName AS BaseCurrencyName, bc.Sign AS BaseCurrencySign,
                tc.ID AS TargetCurrencyId, tc.Code AS TargetCurrencyCode, tc.FullName AS TargetCurrencyName, tc.Sign AS TargetCurrencySign
            FROM ExchangeRates er
            JOIN Currencies bc ON er.BaseCurrencyId = bc.ID
            JOIN Currencies tc ON er.TargetCurrencyId = tc.ID
            WHERE bc.Code = ? AND tc.Code = ?
        """;

        try (Connection conn = DriverManager.getConnection(url);
            PreparedStatement updateStatement = conn.prepareStatement(updateQuery);
            PreparedStatement selectStatement = conn.prepareStatement(selectQuery)) {

            updateStatement.setDouble(1, rate);
            updateStatement.setString(2, baseCurrencyCode);
            updateStatement.setString(3, targetCurrencyCode);

            int rowsUpdated = updateStatement.executeUpdate();
            if (rowsUpdated == 0) {
                return Optional.empty();
            }

            selectStatement.setString(1, baseCurrencyCode);
            selectStatement.setString(2, targetCurrencyCode);
            try (ResultSet rs = selectStatement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToExchangeRate(rs));
                }
            }
        }
        return Optional.empty();
    }

    private ExchangeRate mapResultSetToExchangeRate(ResultSet rs) throws SQLException {
        Currency baseCurrency = new Currency(
                rs.getInt("BaseCurrencyId"),
                rs.getString("BaseCurrencyCode"),
                rs.getString("BaseCurrencyName"),
                rs.getString("BaseCurrencySign")
        );

        Currency targetCurrency = new Currency(
                rs.getInt("TargetCurrencyId"),
                rs.getString("TargetCurrencyCode"),
                rs.getString("TargetCurrencyName"),
                rs.getString("TargetCurrencySign")
        );

        return new ExchangeRate(
                rs.getInt("ID"),
                baseCurrency,
                targetCurrency,
                rs.getDouble("Rate")
        );
    }
}
