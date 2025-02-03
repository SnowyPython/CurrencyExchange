package ru.vatolin.currencyexchange.controller;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.vatolin.currencyexchange.model.Currency;
import ru.vatolin.currencyexchange.service.CurrencyDao;
import ru.vatolin.currencyexchange.model.ExchangeRate;
import ru.vatolin.currencyexchange.service.ExchangeRateDao;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;

@WebServlet(urlPatterns = "/api/exchangeRates/*")
public class ExchangeRatesServlet extends HttpServlet {
    private final ExchangeRateDao exchangeRateDao = new ExchangeRateDao();
    private final CurrencyDao currencyDao = new CurrencyDao();

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(request.getMethod())) {
            doPatch(request, response);
        } else {
            super.service(request, response);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            String pathInfo = request.getPathInfo();
            
            if (pathInfo == null || pathInfo.equals("/")) {
                List<ExchangeRate> rates = exchangeRateDao.getAllExchangeRates();
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(new Gson().toJson(rates));
            } else if (pathInfo.startsWith("/exchange")) {
                handleCurrencyExchange(request, response);
            } else {
                String[] codes = pathInfo.substring(1).split("(?<=\\G.{3})");
                if (codes.length != 2) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"message\": \"Неверный формат валютной пары\"}");
                    return;
                }

                ExchangeRate rate = exchangeRateDao.getExchangeRate(codes[0], codes[1]);
                if (rate == null) {
                    ExchangeRate reverseRate = exchangeRateDao.getExchangeRate(codes[1], codes[0]);
                    if (reverseRate != null) {
                        double invertedRate = 1.0/ reverseRate.getRate();

                        Currency baseCurrency = currencyDao.getCurrencyByCode(codes[0]);
                        Currency targetCurrency = currencyDao.getCurrencyByCode(codes[1]);
                        rate = new ExchangeRate(
                                0,
                                baseCurrency,
                                targetCurrency,
                                invertedRate
                        );
                    }
                } else {
                    ExchangeRate usdToBase = exchangeRateDao.getExchangeRate("USD", codes[0]);
                    ExchangeRate usdToTarget = exchangeRateDao.getExchangeRate("USD", codes[1]);

                    if (usdToBase != null && usdToTarget != null) {
                        double calculatedRate = usdToTarget.getRate() / usdToBase.getRate();

                        Currency baseCurrency = currencyDao.getCurrencyByCode(codes[0]);
                        Currency targetCurrency = currencyDao.getCurrencyByCode(codes[1]);
                        rate = new ExchangeRate(
                                0,
                                baseCurrency,
                                targetCurrency,
                                calculatedRate
                        );
                    }
                }

                if (rate == null) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    response.getWriter().write("{\"message\": \"Обменный курс не найден\"}");
                } else {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.getWriter().write(new Gson().toJson(rate));
                }
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"message\": \"Ошибка сервера\"}");
        }
    }

    private void handleCurrencyExchange(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String from = request.getParameter("from");
        String to = request.getParameter("to");
        String amountStr = request.getParameter("amount");

        if (from == null || to == null || amountStr == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"message\": \"Отсутствуют необходимые параметры\"}");
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);

            ExchangeRate rate = exchangeRateDao.getExchangeRate(from.toUpperCase(), to.toUpperCase());

            double rateValue;
            Currency baseCurrency;
            Currency targetCurrency;

            if (rate != null) {
                rateValue = rate.getRate();
                baseCurrency = currencyDao.getCurrencyByCode(from.toUpperCase());
                targetCurrency = currencyDao.getCurrencyByCode(to.toUpperCase());
            } else {
                ExchangeRate reverseRate = exchangeRateDao.getExchangeRate(to.toUpperCase(), from.toUpperCase());
                if (reverseRate != null) {
                    rateValue = 1.0 / reverseRate.getRate();
                    baseCurrency = currencyDao.getCurrencyByCode(from.toUpperCase());
                    targetCurrency = currencyDao.getCurrencyByCode(to.toUpperCase());
                } else {
                    ExchangeRate rateFromUSD = exchangeRateDao.getExchangeRate("USD", from.toUpperCase());
                    ExchangeRate rateToUSD = exchangeRateDao.getExchangeRate("USD", to.toUpperCase());

                    if (rateFromUSD != null && rateToUSD != null) {
                        rateValue = rateToUSD.getRate() / rateFromUSD.getRate();
                        baseCurrency = currencyDao.getCurrencyByCode(from.toUpperCase());
                        targetCurrency = currencyDao.getCurrencyByCode(to.toUpperCase());
                    } else {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        response.getWriter().write("{\"message\": \"Курс обмена не найден\"}");
                        return;
                    }
                }
            }

            double convertedAmount = rateValue * amount;

            Map<String, Object> result = new HashMap<>();
            result.put("baseCurrency", Map.of(
                    "id", baseCurrency.getId(),
                    "name", baseCurrency.getFullName(),
                    "code", baseCurrency.getCode(),
                    "sign", baseCurrency.getSign()
            ));
            result.put("targetCurrency", Map.of(
                    "id", targetCurrency.getId(),
                    "name", targetCurrency.getFullName(),
                    "code", targetCurrency.getCode(),
                    "sign", targetCurrency.getSign()
            ));
            result.put("rate", rateValue);
            result.put("amount", amount);
            result.put("convertedAmount", convertedAmount);

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(new Gson().toJson(result));
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"message\": \"Некорректное значение amount\"}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"message\": \"Ошибка сервера\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            String baseCode = request.getParameter("baseCurrencyCode");
            String targetCode = request.getParameter("targetCurrencyCode");
            String rateStr = request.getParameter("rate");

            if (baseCode == null || targetCode == null || rateStr == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"message\": \"Отсутствует одно или несколько полей\"}");
                return;
            }

            double rate = Double.parseDouble(rateStr);

            ExchangeRate existing = exchangeRateDao.getExchangeRate(baseCode, targetCode);
            if (existing != null) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.getWriter().write("{\"message\": \"Обменный курс уже существует\"}");
                return;
            }

            ExchangeRate newRate = exchangeRateDao.addExchangeRate(baseCode, targetCode, rate);
            if (newRate == null) {
                throw new RuntimeException("Ошибка при добавлении обменного курса");
            }

            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().write(new Gson().toJson(newRate));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"message\": \"Ошибка сервера\"}");
        }
    }

    protected void doPatch(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();

        if (pathInfo == null || pathInfo.length() <= 1) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"message\": \"Неверный URL\"}");
            return;
        }

        String[] currencies = pathInfo.substring(1).split("(?<=\\G.{3})");
        if (currencies.length != 2) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"message\": \"Неверный формат валютной пары\"}");
            return;
        }

        String baseCurrencyCode = currencies[0];
        String targetCurrencyCode = currencies[1];

        String rateParam = request.getParameter("rate");
        if (rateParam == null) {
            BufferedReader reader = request.getReader();
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            String requestBody = body.toString();
            Map<String, String> parameters = parseUrlEncoded(requestBody);
            rateParam = parameters.get("rate");
        }

        if (rateParam == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"message\": \"Отсутствует параметр rate\"}");
            return;
        }

        double rate;
        try {
            rate = Double.parseDouble(rateParam);
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"message\": \"Некоректный параметр rate\"}");
            return;
        }

        try {
            Optional<ExchangeRate> updateExchangeRate = exchangeRateDao.updateExchangeRate(baseCurrencyCode, targetCurrencyCode, rate);
            if (updateExchangeRate.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("{\"message\": \"Пара не найдена\"}");
                return;
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(new Gson().toJson(updateExchangeRate));
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"message\": \"Ошибка сервера\"}");
        }
    }

    private Map<String, String> parseUrlEncoded(String body) {
        Map<String, String> params = new HashMap<>();
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                params.put(URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8));
            }
        }
        return params;
    }

}
