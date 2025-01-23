import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.vatolin.currencyexchange.ExchangeRate;
import ru.vatolin.currencyexchange.ExchangeRateDao;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@WebServlet(urlPatterns = "/api/exchangeRates/*")
public class ExchangeRatesServlet extends HttpServlet {
    private final ExchangeRateDao exchangeRateDao = new ExchangeRateDao();

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
            } else {
                String[] codes = pathInfo.substring(1).split("(?<=\\G.{3})");
                if (codes.length != 2) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"message\": \"Неверный формат валютной пары\"}");
                    return;
                }

                ExchangeRate rate = exchangeRateDao.getExchangeRate(codes[0], codes[1]);
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
