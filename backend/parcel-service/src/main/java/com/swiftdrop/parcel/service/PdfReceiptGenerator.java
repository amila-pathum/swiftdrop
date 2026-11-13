package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.Parcel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PdfReceiptGenerator {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private PdfReceiptGenerator() { }

    public static byte[] generate(Parcel parcel, Instant deliveredAt) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            List<Long> offsets = new ArrayList<>();

            // Header
            write(out, "%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n");

            // Object 1: Catalog
            offsets.add((long) out.size());
            write(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

            // Object 2: Pages
            offsets.add((long) out.size());
            write(out, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

            // Object 3: Page (US Letter: 612 x 792 pt)
            offsets.add((long) out.size());
            write(out, "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                    + "/Contents 4 0 R /Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> >>\nendobj\n");

            // Object 4: Content Stream
            byte[] streamContent = buildContentStream(parcel, deliveredAt);
            offsets.add((long) out.size());
            write(out, "4 0 obj\n<< /Length " + streamContent.length + " >>\nstream\n");
            out.write(streamContent);
            write(out, "\nendstream\nendobj\n");

            // Object 5: Font F1 (Helvetica Regular)
            offsets.add((long) out.size());
            write(out, "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n");

            // Object 6: Font F2 (Helvetica-Bold)
            offsets.add((long) out.size());
            write(out, "6 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n");

            // Cross-Reference Table
            long startXref = out.size();
            write(out, "xref\n0 7\n");
            write(out, "0000000000 65535 f \r\n");
            for (Long offset : offsets) {
                write(out, String.format(Locale.ROOT, "%010d 00000 n \r\n", offset));
            }

            // Trailer
            write(out, "trailer\n<< /Size 7 /Root 1 0 R >>\nstartxref\n" + startXref + "\n%%EOF\n");

            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate PDF receipt", e);
        }
    }

    private static byte[] buildContentStream(Parcel parcel, Instant deliveredAt) {
        StringBuilder sb = new StringBuilder();

        // 1. Top brand accent bar
        addRect(sb, 50, 745, 512, 4, 0.08, 0.38, 0.74, null, null, null);

        // 2. Header
        addText(sb, "F2", 22, 50, 715, "SWIFTDROP", 0.08, 0.38, 0.74);
        addText(sb, "F2", 12, 50, 695, "PARCEL DELIVERY RECEIPT", 0.25, 0.25, 0.25);
        addText(sb, "F1", 9, 50, 680, "Official Customer Proof of Delivery", 0.50, 0.50, 0.50);
        addLine(sb, 50, 668, 562, 668, 0.85, 0.85, 0.85, 1.0);

        // 3. Receipt Summary Box
        addRect(sb, 50, 585, 512, 72, 0.96, 0.97, 0.99, 0.85, 0.88, 0.93);

        addText(sb, "F2", 10, 65, 638, "Tracking Number:", 0.20, 0.20, 0.20);
        addText(sb, "F2", 10, 165, 638, parcel.getTrackingNumber(), 0.08, 0.38, 0.74);

        addText(sb, "F2", 9, 65, 622, "Parcel ID:", 0.40, 0.40, 0.40);
        addText(sb, "F1", 9, 165, 622, parcel.getId() != null ? parcel.getId().toString() : "N/A", 0.30, 0.30, 0.30);

        addText(sb, "F2", 9, 65, 606, "Created Date:", 0.40, 0.40, 0.40);
        addText(sb, "F1", 9, 165, 606, parcel.getCreatedAt() != null ? DATE_FORMATTER.format(parcel.getCreatedAt()) : "N/A", 0.30, 0.30, 0.30);

        addText(sb, "F2", 9, 65, 592, "Delivered Date:", 0.40, 0.40, 0.40);
        addText(sb, "F1", 9, 165, 592, deliveredAt != null ? DATE_FORMATTER.format(deliveredAt) : "N/A", 0.13, 0.55, 0.13);

        // DELIVERED Status Badge
        addRect(sb, 435, 615, 110, 26, 0.13, 0.55, 0.13, null, null, null);
        addText(sb, "F2", 11, 452, 623, "DELIVERED", 1.0, 1.0, 1.0);

        // 4. Sender & Recipient Information Section
        addText(sb, "F2", 11, 50, 558, "SENDER & RECIPIENT INFORMATION", 0.20, 0.20, 0.20);
        addLine(sb, 50, 550, 562, 550, 0.88, 0.88, 0.88, 0.8);

        addText(sb, "F2", 9, 50, 533, "Sender ID:", 0.40, 0.40, 0.40);
        addText(sb, "F1", 9, 165, 533, parcel.getSenderId() != null ? parcel.getSenderId().toString() : "N/A", 0.20, 0.20, 0.20);

        addText(sb, "F2", 9, 50, 513, "Pickup Address:", 0.40, 0.40, 0.40);
        addText(sb, "F1", 9, 165, 513, parcel.getPickupAddress(), 0.20, 0.20, 0.20);

        addText(sb, "F2", 9, 50, 488, "Receiver Name:", 0.40, 0.40, 0.40);
        addText(sb, "F1", 9, 165, 488, parcel.getReceiverName(), 0.20, 0.20, 0.20);

        addText(sb, "F2", 9, 50, 468, "Receiver Phone:", 0.40, 0.40, 0.40);
        addText(sb, "F1", 9, 165, 468, parcel.getReceiverPhone(), 0.20, 0.20, 0.20);

        addText(sb, "F2", 9, 50, 448, "Delivery Address:", 0.40, 0.40, 0.40);
        addText(sb, "F1", 9, 165, 448, parcel.getDeliveryAddress(), 0.20, 0.20, 0.20);

        // 5. Parcel Specifications Section
        addText(sb, "F2", 11, 50, 415, "PARCEL SPECIFICATIONS", 0.20, 0.20, 0.20);
        addLine(sb, 50, 407, 562, 407, 0.88, 0.88, 0.88, 0.8);

        addText(sb, "F2", 9, 50, 390, "Parcel Type:", 0.40, 0.40, 0.40);
        addText(sb, "F1", 9, 165, 390, parcel.getParcelType(), 0.20, 0.20, 0.20);

        addText(sb, "F2", 9, 50, 370, "Package Size:", 0.40, 0.40, 0.40);
        addText(sb, "F1", 9, 165, 370, parcel.getSize(), 0.20, 0.20, 0.20);

        addText(sb, "F2", 9, 50, 350, "Weight:", 0.40, 0.40, 0.40);
        addText(sb, "F1", 9, 165, 350, (parcel.getWeight() != null ? parcel.getWeight().toPlainString() : "0") + " kg", 0.20, 0.20, 0.20);

        // 6. Payment & Billing Summary Section
        addText(sb, "F2", 11, 50, 315, "PAYMENT SUMMARY", 0.20, 0.20, 0.20);
        addLine(sb, 50, 307, 562, 307, 0.88, 0.88, 0.88, 0.8);

        addRect(sb, 50, 230, 512, 65, 0.95, 0.97, 1.0, 0.82, 0.88, 0.95);
        addText(sb, "F2", 10, 65, 272, "Total Delivery Charge:", 0.20, 0.20, 0.20);
        addText(sb, "F2", 16, 420, 268, "$" + (parcel.getCharge() != null ? parcel.getCharge().toPlainString() : "0.00"), 0.08, 0.38, 0.74);
        addText(sb, "F1", 9, 65, 252, "Payment Status: PAID", 0.13, 0.55, 0.13);
        addText(sb, "F1", 9, 65, 238, "Payment Method: Account Charge / Paid upon booking", 0.40, 0.40, 0.40);

        // 7. Footer
        addLine(sb, 50, 110, 562, 110, 0.85, 0.85, 0.85, 0.8);
        addText(sb, "F1", 8, 50, 95, "This is an electronically generated official receipt for your SwiftDrop delivery.", 0.50, 0.50, 0.50);
        addText(sb, "F2", 8, 50, 82, "Thank you for choosing SwiftDrop!", 0.30, 0.30, 0.30);
        addText(sb, "F1", 8, 50, 69, "SwiftDrop Logistics Platform | For support, email support@swiftdrop.com", 0.50, 0.50, 0.50);

        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static void addText(StringBuilder sb, String font, int size, double x, double y,
                                String text, double r, double g, double b) {
        sb.append("BT\n")
          .append("/").append(font).append(" ").append(size).append(" Tf\n")
          .append(String.format(Locale.ROOT, "%.2f %.2f %.2f rg\n", r, g, b))
          .append(String.format(Locale.ROOT, "1 0 0 1 %.2f %.2f Tm\n", x, y))
          .append("(").append(escapePdfText(text)).append(") Tj\n")
          .append("ET\n");
    }

    private static void addLine(StringBuilder sb, double x1, double y1, double x2, double y2,
                                double r, double g, double b, double width) {
        sb.append("q\n")
          .append(String.format(Locale.ROOT, "%.2f %.2f %.2f RG\n", r, g, b))
          .append(String.format(Locale.ROOT, "%.2f w\n", width))
          .append(String.format(Locale.ROOT, "%.2f %.2f m %.2f %.2f l S\n", x1, y1, x2, y2))
          .append("Q\n");
    }

    private static void addRect(StringBuilder sb, double x, double y, double w, double h,
                                double fillR, double fillG, double fillB,
                                Double strokeR, Double strokeG, Double strokeB) {
        sb.append("q\n")
          .append(String.format(Locale.ROOT, "%.2f %.2f %.2f rg\n", fillR, fillG, fillB));
        if (strokeR != null) {
            sb.append(String.format(Locale.ROOT, "%.2f %.2f %.2f RG\n", strokeR, strokeG, strokeB))
              .append("1 w\n")
              .append(String.format(Locale.ROOT, "%.2f %.2f %.2f %.2f re B\n", x, y, w, h));
        } else {
            sb.append(String.format(Locale.ROOT, "%.2f %.2f %.2f %.2f re f\n", x, y, w, h));
        }
        sb.append("Q\n");
    }

    private static String escapePdfText(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '\\' || c == '(' || c == ')') {
                sb.append('\\').append(c);
            } else if (c >= 32 && c <= 126) {
                sb.append(c);
            } else if (c == '\n' || c == '\r' || c == '\t') {
                sb.append(' ');
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private static void write(ByteArrayOutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
    }
}

