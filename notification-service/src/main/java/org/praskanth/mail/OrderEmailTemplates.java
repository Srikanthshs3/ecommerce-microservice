package org.praskanth.mail;

import org.praskanth.events.OrderNotificationEvent;
import org.praskanth.events.OrderNotificationItem;

import java.util.List;

/**
 * Builds the subject, HTML and plain-text bodies of the order e-mails. Pure functions (no Spring, no mail server), so
 * every stage can be unit-tested. All customer-supplied text is HTML-escaped.
 */
public final class OrderEmailTemplates {

    public record Mail(String subject, String html, String text) {}

    private OrderEmailTemplates() {
    }

    /** @return the e-mail for this event, or null when the stage is unknown (the caller skips it). */
    public static Mail build(OrderNotificationEvent e, String trackUrl) {
        String stage = e.getStage() == null ? "" : e.getStage().toString();
        long id = e.getOrderId();
        String name = firstName(e);

        String subject;
        String heading;
        String lead;
        switch (stage) {
            case "ORDER_PLACED" -> {
                subject = "Order #" + id + " is confirmed";
                heading = "Your order is placed";
                lead = "Thanks, " + name + "! We have received your order and sent it to the seller to pack.";
            }
            case "PACKED" -> {
                subject = "Order #" + id + " is packed";
                heading = "Your order is packed";
                lead = "Good news, " + name + ": the seller has packed your order. A delivery partner will pick it up soon.";
            }
            case "OUT_FOR_DELIVERY" -> {
                subject = "Order #" + id + " is on its way";
                heading = "Out for delivery";
                lead = name + ", a delivery partner has picked up your order and is bringing it to you. Keep your phone nearby.";
            }
            case "DELIVERED" -> {
                subject = "Order #" + id + " was delivered";
                heading = "Delivered";
                lead = "Your order has been delivered, " + name + ". We hope you love it!";
            }
            case "CANCELLED" -> {
                subject = "Order #" + id + " was cancelled";
                heading = "Order cancelled";
                lead = "Hi " + name + ", your order was cancelled." + reasonLine(e)
                        + " If you already paid, the amount is refunded to your original payment method.";
            }
            case "OUT_OF_STOCK" -> {
                subject = "Order #" + id + " could not be placed: out of stock";
                heading = "Sorry, an item is out of stock";
                lead = "Hi " + name + ", one or more items in your order ran out of stock just now, so we could not place it."
                        + " No payment was taken. You can try again later or pick something similar.";
            }
            default -> {
                return null;
            }
        }
        return new Mail(subject, html(e, heading, lead, trackUrl), text(e, heading, lead, trackUrl));
    }

    // ------------------------------------------------------------------------------------------ bodies

    private static String html(OrderNotificationEvent e, String heading, String lead, String trackUrl) {
        StringBuilder rows = new StringBuilder();
        for (OrderNotificationItem i : items(e)) {
            rows.append("<tr><td style=\"padding:6px 0\">").append(esc(itemName(i))).append("</td>")
                    .append("<td style=\"padding:6px 8px;text-align:center\">").append(i.getQuantity()).append("</td>")
                    .append("<td style=\"padding:6px 0;text-align:right\">&#8377;").append(esc(String.valueOf(i.getUnitPrice()))).append("</td></tr>");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:auto;color:#222\">")
                .append("<h2 style=\"margin-bottom:4px\">").append(esc(heading)).append("</h2>")
                .append("<p style=\"color:#555\">Order #").append(e.getOrderId()).append("</p>")
                .append("<p>").append(esc(lead)).append("</p>");
        if (rows.length() > 0) {
            sb.append("<table style=\"width:100%;border-collapse:collapse;border-top:1px solid #ddd;border-bottom:1px solid #ddd\">")
                    .append("<tr style=\"color:#777;font-size:12px\"><td>Item</td><td style=\"text-align:center\">Qty</td><td style=\"text-align:right\">Price</td></tr>")
                    .append(rows).append("</table>")
                    .append("<p style=\"text-align:right\"><strong>Total: &#8377;").append(esc(String.valueOf(e.getTotalAmount()))).append("</strong></p>");
        }
        if (notBlank(e.getShippingAddress())) {
            sb.append("<p style=\"color:#555\"><strong>Delivering to:</strong><br>");
            if (notBlank(e.getRecipientName())) {
                sb.append(esc(e.getRecipientName().toString())).append("<br>");
            }
            sb.append(esc(e.getShippingAddress().toString())).append("</p>");
        }
        if (notBlank(trackUrl)) {
            sb.append("<p><a href=\"").append(esc(trackUrl)).append("\" style=\"background:#0d6efd;color:#fff;padding:10px 18px;")
                    .append("border-radius:6px;text-decoration:none\">View my orders</a></p>");
        }
        sb.append("<p style=\"color:#999;font-size:12px\">You are receiving this because you placed an order with us.</p></div>");
        return sb.toString();
    }

    private static String text(OrderNotificationEvent e, String heading, String lead, String trackUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append(heading).append(" - order #").append(e.getOrderId()).append("\n\n").append(lead).append("\n\n");
        for (OrderNotificationItem i : items(e)) {
            sb.append("- ").append(itemName(i)).append(" x").append(i.getQuantity()).append("  Rs ").append(i.getUnitPrice()).append('\n');
        }
        if (!items(e).isEmpty()) {
            sb.append("Total: Rs ").append(e.getTotalAmount()).append("\n\n");
        }
        if (notBlank(e.getShippingAddress())) {
            sb.append("Delivering to: ").append(e.getShippingAddress()).append("\n\n");
        }
        if (notBlank(trackUrl)) {
            sb.append("View my orders: ").append(trackUrl).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------------------------ helpers

    private static List<OrderNotificationItem> items(OrderNotificationEvent e) {
        return e.getItems() == null ? List.of() : e.getItems();
    }

    private static String itemName(OrderNotificationItem i) {
        String name = String.valueOf(i.getProductName());
        return notBlank(i.getVariantLabel()) ? name + " (" + i.getVariantLabel() + ")" : name;
    }

    private static String reasonLine(OrderNotificationEvent e) {
        return notBlank(e.getReason()) ? " Reason: " + e.getReason() + "." : "";
    }

    private static String firstName(OrderNotificationEvent e) {
        if (!notBlank(e.getCustomerName())) {
            return "there";
        }
        String full = e.getCustomerName().toString().trim();
        int space = full.indexOf(' ');
        return space > 0 ? full.substring(0, space) : full;
    }

    private static boolean notBlank(CharSequence s) {
        return s != null && !s.toString().isBlank();
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
