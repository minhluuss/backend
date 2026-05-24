package com.example.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SepayService {

    private static final Pattern BOOKING_CODE_PATTERN = Pattern.compile("(?i)(?:BK|DH)\\s*[-_:#]?\\s*(\\d{1,9})");

    @Value("${payment.sepay.merchant-id:}")
    private String merchantId;

    @Value("${payment.sepay.secret-key:}")
    private String secretKey;

    @Value("${payment.sepay.checkout-url:https://pay.sepay.vn/v1/checkout/init}")
    private String checkoutUrl;

    @Value("${payment.sepay.success-url:http://localhost:8080/api/payments/sepay/success}")
    private String successUrl;

    @Value("${payment.sepay.error-url:http://localhost:8080/api/payments/sepay/error}")
    private String errorUrl;

    @Value("${payment.sepay.cancel-url:http://localhost:8080/api/payments/sepay/cancel}")
    private String cancelUrl;

    @Value("${payment.sepay.frontend-result-url:http://localhost:5173/payment-result}")
    private String frontendResultUrl;

    @Value("${payment.sepay.qr-image-base-url:https://qr.sepay.vn/img}")
    private String qrImageBaseUrl;

    @Value("${payment.sepay.qr-account}")
    private String qrAccount;

    @Value("${payment.sepay.qr-bank}")
    private String qrBank;

    @Value("${payment.sepay.qr-description-prefix:TKPNBV}")
    private String qrDescriptionPrefix;

    public boolean isConfigured() {
        return !isBlank(merchantId) && !isBlank(secretKey);
    }

    public String getFrontendResultUrl() {
        return frontendResultUrl;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public String buildTransferDescription(Integer bookingId) {
        String suffix = bookingId == null ? "" : " DH" + bookingId;
        return (qrDescriptionPrefix == null ? "" : qrDescriptionPrefix.trim()) + suffix;
    }

    public String buildQrImageUrl(Integer bookingId, BigDecimal amount) {
        String amountString = amount == null
                ? "0"
                : amount.setScale(0, RoundingMode.HALF_UP).toPlainString();

        return qrImageBaseUrl
                + "?acc=" + encode(qrAccount)
                + "&bank=" + encode(qrBank)
                + "&amount=" + encode(amountString)
                + "&des=" + encode(buildTransferDescription(bookingId));
    }

    public String getQrAccount() {
        return qrAccount;
    }

    public String getQrBank() {
        return qrBank;
    }

    public Map<String, String> buildPaymentFields(Integer bookingId, BigDecimal amount, Integer userId) {
        if (!isConfigured()) {
            throw new IllegalStateException("SePay is not configured. Please set payment.sepay.merchant-id and payment.sepay.secret-key.");
        }

        String invoiceNumber = "BK" + bookingId;
        String orderAmount = amount == null ? "0" : amount.setScale(0, RoundingMode.HALF_UP).toPlainString();

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("order_amount", orderAmount);
        fields.put("merchant", merchantId);
        fields.put("currency", "VND");
        fields.put("operation", "PURCHASE");
        fields.put("order_description", "Thanh toan don dat ve #" + bookingId);
        fields.put("order_invoice_number", invoiceNumber);
        fields.put("customer_id", userId == null ? "" : String.valueOf(userId));
        fields.put("payment_method", "BANK_TRANSFER");
        fields.put("success_url", successUrl + "?bookingId=" + bookingId);
        fields.put("error_url", errorUrl + "?bookingId=" + bookingId);
        fields.put("cancel_url", cancelUrl + "?bookingId=" + bookingId);

        fields.put("signature", signFields(fields));
        return fields;
    }

    public Integer parseBookingIdFromInvoice(String invoice) {
        if (invoice == null || !invoice.startsWith("BK")) {
            return null;
        }
        try {
            return Integer.parseInt(invoice.substring(2));
        } catch (Exception ex) {
            return null;
        }
    }

    public Integer parseBookingIdFromIpnPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }

        Object orderObj = payload.get("order");
        if (orderObj instanceof Map<?, ?> orderMap) {
            Integer id = parseBookingIdFromInvoice(String.valueOf(orderMap.get("order_invoice_number")));
            if (id != null) {
                return id;
            }

            id = parseBookingIdFromText(String.valueOf(orderMap.get("order_description")));
            if (id != null) {
                return id;
            }

            id = parseBookingIdFromText(String.valueOf(orderMap.get("description")));
            if (id != null) {
                return id;
            }
        }

        String[] candidateKeys = {
                "content",
                "description",
                "transfer_content",
                "transaction_content",
                "add_info",
                "note",
                "reference"
        };

        for (String key : candidateKeys) {
            Integer id = parseBookingIdFromText(String.valueOf(payload.get(key)));
            if (id != null) {
                return id;
            }
        }

        return parseBookingIdFromText(payload.toString());
    }

    public Integer parseBookingIdFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Matcher matcher = BOOKING_CODE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception ex) {
            return null;
        }
    }

    private String signFields(Map<String, String> fields) {
        String[] order = {
                "order_amount",
                "merchant",
                "currency",
                "operation",
                "order_description",
                "order_invoice_number",
                "customer_id",
                "payment_method",
                "success_url",
                "error_url",
                "cancel_url"
        };

        StringBuilder signedString = new StringBuilder();
        for (String field : order) {
            String value = fields.get(field);
            if (value == null || value.isBlank()) {
                continue;
            }
            if (signedString.length() > 0) {
                signedString.append(',');
            }
            signedString.append(field).append('=').append(value);
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmac = mac.doFinal(signedString.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmac);
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate SePay signature", e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String encode(String value) {
        String safe = value == null ? "" : value;
        return URLEncoder.encode(safe, StandardCharsets.UTF_8);
    }
}
