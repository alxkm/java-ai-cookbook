package com.example.cookbook;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Twenty tools across four domains, which is where the problem this recipe solves starts.
 *
 * Every tool definition - name, description, parameter schema - is prompt text, sent on every
 * request and paid for on every request. Twenty is already enough to notice; the internal API
 * surface of a real company is hundreds, and sending all of them costs money on every turn and
 * measurably hurts tool selection, because the model is choosing from a longer list of things
 * that look alike.
 *
 * The descriptions are written to be searchable, not pretty: the words a user would actually use
 * are in them, because that is what the index matches on.
 */
@Component
public class ToolCatalog {

    // ---- orders -------------------------------------------------------------------------------

    @Tool(description = "Look up the current delivery status of an order by its id.")
    String orderStatus(@ToolParam(description = "Order id, like A-1001") String orderId) {
        return "shipped";
    }

    @Tool(description = "Cancel an order that has not shipped yet.")
    String cancelOrder(@ToolParam(description = "Order id") String orderId) {
        return "cancelled";
    }

    @Tool(description = "List the line items and quantities on an order.")
    String orderItems(@ToolParam(description = "Order id") String orderId) {
        return "2 x widget, 1 x gasket";
    }

    @Tool(description = "Change the delivery address on an order that has not shipped.")
    String updateOrderAddress(@ToolParam(description = "Order id") String orderId,
                              @ToolParam(description = "New postal address") String address) {
        return "address updated";
    }

    @Tool(description = "Find recent orders placed by a customer.")
    String ordersForCustomer(@ToolParam(description = "Customer id") String customerId) {
        return "A-1001, A-1042";
    }

    // ---- billing ------------------------------------------------------------------------------

    @Tool(description = "Issue a refund for an order, in full or in part.")
    String refundOrder(@ToolParam(description = "Order id") String orderId,
                       @ToolParam(description = "Amount to refund in cents") int amountCents) {
        return "refund issued";
    }

    @Tool(description = "Fetch an invoice and its payment status.")
    String invoice(@ToolParam(description = "Invoice number") String invoiceNumber) {
        return "paid";
    }

    @Tool(description = "Apply a discount code to an unpaid invoice.")
    String applyDiscount(@ToolParam(description = "Invoice number") String invoiceNumber,
                         @ToolParam(description = "Discount code") String code) {
        return "discount applied";
    }

    @Tool(description = "Charge a saved payment method for an outstanding balance.")
    String chargeBalance(@ToolParam(description = "Customer id") String customerId) {
        return "charged";
    }

    @Tool(description = "Explain why a payment was declined by the processor.")
    String declineReason(@ToolParam(description = "Payment id") String paymentId) {
        return "insufficient funds";
    }

    // ---- shipping -----------------------------------------------------------------------------

    @Tool(description = "Track a parcel with the carrier and return its last scan.")
    String trackParcel(@ToolParam(description = "Tracking number") String trackingNumber) {
        return "out for delivery";
    }

    @Tool(description = "Book a courier collection for a return.")
    String bookCollection(@ToolParam(description = "Order id") String orderId) {
        return "collection booked";
    }

    @Tool(description = "Print a prepaid return shipping label.")
    String returnLabel(@ToolParam(description = "Order id") String orderId) {
        return "label-9912.pdf";
    }

    @Tool(description = "Estimate a delivery date for a postcode and shipping class.")
    String estimateDelivery(@ToolParam(description = "Destination postcode") String postcode,
                            @ToolParam(description = "Shipping class") String shippingClass) {
        return "Thursday";
    }

    @Tool(description = "List the carriers that serve a destination country.")
    String carriersFor(@ToolParam(description = "Country code") String country) {
        return "DHL, UPS";
    }

    // ---- inventory ----------------------------------------------------------------------------

    @Tool(description = "Check how many units of a product are in stock.")
    String stockLevel(@ToolParam(description = "Product sku") String sku) {
        return "42";
    }

    @Tool(description = "Reserve stock for an order so it cannot be sold twice.")
    String reserveStock(@ToolParam(description = "Product sku") String sku,
                        @ToolParam(description = "Quantity") int quantity) {
        return "reserved";
    }

    @Tool(description = "Find the warehouse a product ships from.")
    String warehouseFor(@ToolParam(description = "Product sku") String sku) {
        return "LEJ-2";
    }

    @Tool(description = "Report when a discontinued product was last available.")
    String discontinuedOn(@ToolParam(description = "Product sku") String sku) {
        return "2026-03-14";
    }

    @Tool(description = "Suggest a replacement product for one that is out of stock.")
    String replacementFor(@ToolParam(description = "Product sku") String sku) {
        return "WID-2210";
    }
}
