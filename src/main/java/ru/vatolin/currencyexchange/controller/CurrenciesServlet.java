package ru.vatolin.currencyexchange.controller;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.vatolin.currencyexchange.model.Currency;
import ru.vatolin.currencyexchange.service.CurrencyDao;

import java.io.IOException;
import java.util.List;

@WebServlet(urlPatterns = "/api/currencies/*")
public class CurrenciesServlet extends HttpServlet {
    private final CurrencyDao currencyDao = new CurrencyDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/")) {
                List<Currency> currencies = currencyDao.getAllCurrencies();
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(new Gson().toJson(currencies));
            } else {
                String code = pathInfo.substring(1).toUpperCase();
                if (code.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"message\": \"Код валюты отсутствует\"}");
                    return;
                }

                Currency currency = currencyDao.getCurrencyByCode(code);
                if (currency == null) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    response.getWriter().write("{\"message\": \"Валюта не найдена\"}");
                } else {
                    response.getWriter().write(new Gson().toJson(currency));
                    response.setStatus(HttpServletResponse.SC_OK);
                }
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"message\": \"Ошибка на сервере\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            String name = request.getParameter("name");
            String code = request.getParameter("code");
            String sign = request.getParameter("sign");

            if (name == null || code == null || sign == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"message\": \"Отсутствует нужное поле формы\"}");
                return;
            }

            code = code.toUpperCase();

            Currency existingCurrency = currencyDao.getCurrencyByCode(code);
            if (existingCurrency != null) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.getWriter().write("{\"message\": \"Валюта с таким кодом уже существует\"}");
                return;
            }

            Currency newCurrency = currencyDao.addCurrency(new Currency(0, code, name, sign));
            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().write(new Gson().toJson(newCurrency));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"message\": \"Ошибка на сервере\"}");
        }
    }
}
